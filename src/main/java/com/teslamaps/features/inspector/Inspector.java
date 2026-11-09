package com.teslamaps.features.inspector;

// Ported from Bax's EntityInspector / ItemInspector (ImGui replaced by InspectorScreen)
import com.mojang.authlib.properties.Property;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Entity and item inspector: rows for the browser screen, key handling, live world highlight. */
public final class Inspector {

    /** header = section title row. copy = full value for the clipboard. */
    public record Row(String label, String value, String copy, int color, boolean header) {
        static Row header(String title) { return new Row(title, "", title, 0xFFFFAA00, true); }
    }

    private static final Pattern TEXTURE_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final EquipmentSlot[] EQUIP = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Object[][] ATTRS = {
            {"Max Health", Attributes.MAX_HEALTH}, {"Movement Speed", Attributes.MOVEMENT_SPEED},
            {"Attack Damage", Attributes.ATTACK_DAMAGE}, {"Armor", Attributes.ARMOR},
            {"Armor Toughness", Attributes.ARMOR_TOUGHNESS}, {"Knockback Resist", Attributes.KNOCKBACK_RESISTANCE},
            {"Follow Range", Attributes.FOLLOW_RANGE}, {"Scale", Attributes.SCALE}};

    /** Entity id highlighted in the world, -1 = none. */
    public static int highlighted = -1;
    private static long highlightUntil;
    private static boolean entityKeyDown;

    private Inspector() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        com.teslamaps.registry.Feature.of("Inspector")
                .when(() -> cfg().devInspector || highlighted != -1)
                .tick(Inspector::tick)
                .world(Inspector::render);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("debug")
                        .then(ClientCommands.literal("inspect")
                                .executes(c -> { openEntities(null, 32); return 1; })
                                .then(ClientCommands.literal("item").executes(c -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) openItem(mc.player.getMainHandItem().copy());
                                    return 1;
                                }))
                                .then(ClientCommands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                                        .executes(c -> {
                                            openEntities(null, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"));
                                            return 1;
                                        }))))));
    }

    /** Rows under Dev Tools. */
    public static void config(com.teslamaps.screen.config.ConfigBuilder b) {
        b.toggle("Entity & Item Inspector", () -> cfg().devInspector, v -> cfg().devInspector = v)
                .desc("Browser for nearby entities (type, position, nametag stack, head texture, equipment, attributes, effects,"
                        + " synched data) with a live highlight, and for items (components, custom data, lore with & codes, \\uXXXX glyphs)."
                        + " Click a row to copy it.")
                .tags("inspector", "debug", "entities", "nbt", "components");
        b.keybind("  Inspect Looked-At Entity Key", () -> cfg().entityInspectorKey, v -> cfg().entityInspectorKey = v);
        b.keybind("  Inspect Hovered Item Key (over slot)", () -> cfg().itemInspectorKey, v -> cfg().itemInspectorKey = v);
        b.info("/tmap debug inspect [radius], /tmap debug inspect item (held item)");
    }

    private static void openEntities(Entity focus, int radius) {
        com.teslamaps.utils.TickScheduler.schedule(1, () ->
                Minecraft.getInstance().gui.setScreen(new InspectorScreen(focus, radius)));
    }

    private static void openItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen parent = mc.gui.screen();
        com.teslamaps.utils.TickScheduler.schedule(1, () ->
                mc.gui.setScreen(new InspectorScreen(stack, parent instanceof net.minecraft.client.gui.screens.ChatScreen ? null : parent)));
    }

    /** Container key over a slot; true = consumed. */
    public static boolean onContainerKey(int key, Slot hovered) {
        TeslaMapsConfig c = cfg();
        if (!c.devInspector || key == -1 || key != c.itemInspectorKey || hovered == null || hovered.getItem().isEmpty()) return false;
        openItem(hovered.getItem().copy());
        return true;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = cfg().devInspector && mc.gui.screen() == null && KeyNames.isDown(cfg().entityInspectorKey);
        if (down && !entityKeyDown) {
            Entity e = lookedAt(mc, 64);
            if (e != null) openEntities(e, 32);
        }
        entityKeyDown = down;
        if (!(mc.gui.screen() instanceof InspectorScreen) && highlighted != -1 && System.currentTimeMillis() > highlightUntil) highlighted = -1;
    }

    /** Keeps the highlight a few seconds after the browser closes. */
    static void closed() {
        highlightUntil = System.currentTimeMillis() + 5000;
    }

    private static void render(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 cam) {
        if (highlighted == -1) return;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(highlighted);
        if (e == null) return;
        AABB box = ESPRenderer.lerpBox(e);
        ESPRenderer.drawBoxOutline(pose, box, 0xFF55FFFF, 2.5f, cam, true);
        ESPRenderer.drawTracerFromCamera(pose, box.getCenter(), 0xFF55FFFF, cam);
    }

    /** Entity closest to the crosshair ray, within ~4 degrees. */
    static Entity lookedAt(Minecraft mc, double range) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range), x -> true)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 0.1 || dist > range) continue;
            double cos = to.dot(look) / dist;
            if (cos < 0.9975) continue;
            double score = (1 - cos) * 1000 + dist * 0.01;
            if (e.getBoundingBox().inflate(0.3).clip(eye, eye.add(look.scale(range))).isPresent()) score -= 10;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // ---- summaries ----

    static String summary(Entity e) {
        if (e instanceof ItemEntity ie) {
            ItemStack st = ie.getItem();
            return StringUtil.strip(st.getHoverName().getString()) + (st.getCount() > 1 ? " x" + st.getCount() : "");
        }
        ArmorStand tag = nametag(e);
        if (tag != null && tag.getCustomName() != null) return StringUtil.strip(tag.getCustomName().getString());
        return StringUtil.strip(e.getName().getString());
    }

    static String typeName(Entity e) {
        return EntityType.getKey(e.getType()).getPath();
    }

    // ---- entity rows ----

    public static List<Row> entity(Entity e, boolean raw) {
        List<Row> out = new ArrayList<>();
        if (e == null) return out;
        if (e instanceof ItemEntity ie) {
            safe(out, "Dropped Item", () -> {
                List<Row> r = new ArrayList<>();
                add(r, "Item", StringUtil.strip(ie.getItem().getHoverName().getString()), 0xFF55FFFF);
                add(r, "Count", String.valueOf(ie.getItem().getCount()));
                add(r, "SkyBlock ID", ItemUtil.skyblockId(ie.getItem()), 0xFF55FFFF);
                add(r, "Age", ie.getAge() / 20 + "s on ground");
                return r;
            });
            out.addAll(item(ie.getItem(), raw));
        } else {
            safe(out, "SkyBlock", () -> skyblock(e));
        }
        safe(out, "Identity", () -> {
            List<Row> r = new ArrayList<>();
            add(r, "Type", EntityType.getKey(e.getType()).toString());
            add(r, "Name", e.getName().getString());
            if (e.getCustomName() != null) add(r, "Custom Name", codes(e.getCustomName()));
            add(r, "Class", e.getClass().getSimpleName());
            add(r, "UUID", e.getUUID().toString());
            add(r, "Network ID", String.valueOf(e.getId()));
            return r;
        });
        safe(out, "Position", () -> {
            List<Row> r = new ArrayList<>();
            Vec3 p = e.position();
            add(r, "Position", fmt("%.2f, %.2f, %.2f", p.x, p.y, p.z));
            add(r, "Block", e.blockPosition().getX() + ", " + e.blockPosition().getY() + ", " + e.blockPosition().getZ());
            add(r, "Rotation", fmt("yaw %.1f  pitch %.1f", e.getYRot(), e.getXRot()));
            Vec3 v = e.getDeltaMovement();
            add(r, "Velocity", fmt("%.3f, %.3f, %.3f", v.x, v.y, v.z));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player != e) add(r, "Distance", fmt("%.2f m", e.distanceTo(mc.player)));
            if (e.getVehicle() != null) add(r, "Riding", typeName(e.getVehicle()) + " #" + e.getVehicle().getId());
            if (!e.getPassengers().isEmpty()) {
                List<String> p2 = new ArrayList<>();
                for (Entity x : e.getPassengers()) p2.add(typeName(x) + " #" + x.getId());
                add(r, "Passengers", String.join(", ", p2));
            }
            return r;
        });
        safe(out, "State", () -> {
            List<Row> r = new ArrayList<>();
            add(r, "On Ground", String.valueOf(e.onGround()));
            add(r, "Pose", e.getPose().name());
            add(r, "Age (ticks)", String.valueOf(e.tickCount));
            if (e.isInvisible()) add(r, "Invisible", "true");
            if (e.isCurrentlyGlowing()) add(r, "Glowing", "true");
            if (e.isNoGravity()) add(r, "No Gravity", "true");
            if (e.isSilent()) add(r, "Silent", "true");
            if (e.isOnFire()) add(r, "On Fire", "true", 0xFFFF5555);
            if (e.isShiftKeyDown()) add(r, "Sneaking", "true");
            if (e.isRemoved()) add(r, "Removed", "true", 0xFFFF5555);
            if (e.getTeam() != null) add(r, "Team", e.getTeam().getName());
            add(r, "Bounding Box", fmt("%.2f w x %.2f h", e.getBbWidth(), e.getBbHeight()));
            return r;
        });
        if (e instanceof LivingEntity le) {
            safe(out, "Living", () -> living(le));
            safe(out, "Attributes", () -> attributes(le));
        }
        if (e instanceof Player p) safe(out, "Player", () -> {
            List<Row> r = new ArrayList<>();
            add(r, "Profile Name", p.getGameProfile().name());
            java.util.UUID id = p.getGameProfile().id();
            if (id != null) {
                add(r, "Profile UUID", id.toString());
                add(r, "Likely NPC", id.version() == 4 ? "no" : "yes", id.version() == 4 ? 0 : 0xFFFFAA00);
            }
            return r;
        });
        if (raw) safe(out, "Synched Data", () -> {
            List<Row> r = new ArrayList<>();
            SynchedEntityData data = e.getEntityData();
            List<SynchedEntityData.DataValue<?>> values = data.getNonDefaultValues();
            if (values != null) {
                for (SynchedEntityData.DataValue<?> dv : values) {
                    String val = String.valueOf(dv.value());
                    r.add(new Row("data[" + dv.id() + "]", truncate(val, 160), val, 0, false));
                }
            }
            return r;
        });
        return out;
    }

    private static List<Row> skyblock(Entity e) {
        List<Row> r = new ArrayList<>();
        ArmorStand tag = nametag(e);
        if (tag != null && tag.getCustomName() != null) {
            Component name = tag.getCustomName();
            add(r, "Nametag", StringUtil.strip(name.getString()), 0xFF55FFFF);
            add(r, "Nametag (codes)", codes(name));
            String esc = escapeNonAscii(StringUtil.strip(name.getString()));
            if (!esc.equals(StringUtil.strip(name.getString()))) add(r, "Nametag (unicode)", esc, 0xFF55FFFF);
            if (tag != e) add(r, "Nametag Entity", "#" + tag.getId());
        } else {
            add(r, "Nametag", "none linked");
        }
        List<String> stack = nametagStack(e);
        if (stack.size() > 1) add(r, "Nametag Lines (" + stack.size() + ")", String.join("  |  ", stack));
        if (e instanceof LivingEntity le) {
            ItemStack head = le.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty()) {
                add(r, "Head Owner", headOwner(head));
                add(r, "Head Texture", headTexture(head), 0xFF55FFFF);
                add(r, "Head Texture Hash", com.teslamaps.features.HeadESP.hashOf(head));
                add(r, "Head SkyBlock ID", ItemUtil.skyblockId(head));
            }
        }
        return r;
    }

    private static List<Row> living(LivingEntity le) {
        List<Row> r = new ArrayList<>();
        add(r, "Vanilla Health", fmt("%.1f / %.1f", le.getHealth(), le.getMaxHealth()));
        if (le.getAbsorptionAmount() > 0) add(r, "Absorption", fmt("%.1f", le.getAbsorptionAmount()));
        add(r, "Armor Value", String.valueOf(le.getArmorValue()));
        if (le.isDeadOrDying()) add(r, "Dying", "deathTime " + le.deathTime, 0xFFFF5555);
        if (le.hurtTime > 0) add(r, "Hurt Time", String.valueOf(le.hurtTime));
        if (le.isBaby()) add(r, "Baby", "true");
        List<String> fx = new ArrayList<>();
        for (MobEffectInstance m : le.getActiveEffects()) {
            String n = m.getEffect().unwrapKey().map(k -> k.identifier().getPath()).orElse("effect");
            fx.add(n + " " + (m.getAmplifier() + 1) + " (" + m.getDuration() / 20 + "s)");
        }
        if (!fx.isEmpty()) add(r, "Effects (" + fx.size() + ")", String.join(", ", fx));
        for (EquipmentSlot slot : EQUIP) {
            ItemStack it = le.getItemBySlot(slot);
            if (it.isEmpty()) continue;
            String id = ItemUtil.skyblockId(it);
            String n = StringUtil.strip(it.getHoverName().getString());
            if (n.isEmpty()) n = BuiltInRegistries.ITEM.getKey(it.getItem()).toString();
            add(r, slotName(slot), id.isEmpty() ? n : n + "  (" + id + ")");
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private static List<Row> attributes(LivingEntity le) {
        List<Row> r = new ArrayList<>();
        for (Object[] a : ATTRS) {
            try {
                AttributeInstance inst = le.getAttribute((Holder<Attribute>) a[1]);
                if (inst != null) add(r, (String) a[0], trim(inst.getValue()));
            } catch (Throwable ignored) {}
        }
        return r;
    }

    /** The named armor stand floating over an entity (or the stand itself). */
    static ArmorStand nametag(Entity e) {
        if (e instanceof ArmorStand as && as.hasCustomName()) return as;
        ArmorStand best = null;
        double bestSq = 2.25;
        for (ArmorStand as : stands(e)) {
            double dx = as.getX() - e.getX(), dz = as.getZ() - e.getZ();
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = as;
            }
        }
        return best;
    }

    private static List<String> nametagStack(Entity e) {
        List<ArmorStand> found = new ArrayList<>(stands(e));
        found.removeIf(as -> {
            double dx = as.getX() - e.getX(), dz = as.getZ() - e.getZ();
            return dx * dx + dz * dz >= 2.25;
        });
        found.sort((a, b) -> Double.compare(b.getY(), a.getY()));
        List<String> out = new ArrayList<>();
        for (ArmorStand as : found) if (as.getCustomName() != null) out.add(StringUtil.strip(as.getCustomName().getString()));
        return out;
    }

    private static List<ArmorStand> stands(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        AABB box = new AABB(e.getX() - 1.5, e.getY() - 0.5, e.getZ() - 1.5,
                e.getX() + 1.5, e.getY() + e.getBbHeight() + 2.5, e.getZ() + 1.5);
        return mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName);
    }

    // ---- item rows ----

    public static List<Row> item(ItemStack stack, boolean raw) {
        List<Row> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = cd == null ? null : cd.copyTag();
        safe(out, "SkyBlock", () -> {
            List<Row> r = new ArrayList<>();
            if (data == null) { add(r, "SkyBlock Data", "none"); return r; }
            add(r, "SkyBlock ID", ItemUtil.skyblockId(stack), 0xFF55FFFF);
            add(r, "UUID", data.getString("uuid").orElse(null));
            add(r, "Reforge", data.getString("modifier").orElse(null), 0xFF55FF55);
            add(r, "Timestamp", data.get("timestamp") == null ? null : data.get("timestamp").toString());
            add(r, "Pet Info", data.getString("petInfo").orElse(null));
            return r;
        });
        safe(out, "Vanilla", () -> {
            List<Row> r = new ArrayList<>();
            add(r, "Item ID", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            add(r, "Name", StringUtil.strip(stack.getHoverName().getString()));
            add(r, "Name (codes)", codes(stack.getHoverName()));
            add(r, "Count", stack.getCount() + " / " + stack.getMaxStackSize());
            if (stack.isDamageableItem()) add(r, "Durability", (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage());
            add(r, "Skull Owner", headOwner(stack));
            add(r, "Skull Texture", headTexture(stack), 0xFF55FFFF);
            add(r, "Skull Texture Hash", com.teslamaps.features.HeadESP.hashOf(stack));
            return r;
        });
        safe(out, "Lore", () -> {
            List<Row> r = new ArrayList<>();
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) return r;
            int i = 0;
            for (Component line : lore.lines()) {
                String clean = StringUtil.strip(line.getString());
                r.add(new Row(String.valueOf(i++), truncate(clean, 200), codes(line), 0, false));
            }
            return r;
        });
        safe(out, "Unicode", () -> {
            List<Row> r = new ArrayList<>();
            Map<Character, Integer> found = new LinkedHashMap<>();
            collect(stack.getHoverName().getString(), found);
            for (String l : ItemUtil.loreLines(stack)) collect(l, found);
            for (Map.Entry<Character, Integer> en : found.entrySet()) {
                String esc = String.format(Locale.ROOT, "\\u%04X", (int) en.getKey());
                r.add(new Row(esc, en.getKey() + "  x" + en.getValue(), esc, 0xFF55FFFF, false));
            }
            return r;
        });
        safe(out, "Custom Data", () -> {
            List<Row> r = new ArrayList<>();
            if (data == null) return r;
            for (String k : data.keySet()) {
                String v = String.valueOf(data.get(k));
                r.add(new Row(k, truncate(v, 200), v, 0, false));
            }
            return r;
        });
        if (raw) safe(out, "Components", () -> {
            List<Row> r = new ArrayList<>();
            for (TypedDataComponent<?> tc : stack.getComponents()) {
                var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(tc.type());
                String v = String.valueOf(tc.value());
                r.add(new Row(id == null ? String.valueOf(tc.type()) : id.toString(), truncate(v, 200), v, 0, false));
            }
            return r;
        });
        return out;
    }

    static String headOwner(ItemStack stack) {
        ResolvableProfile p = stack.get(DataComponents.PROFILE);
        if (p == null) return null;
        String n = p.partialProfile().name();
        return n == null || n.isBlank() ? null : n;
    }

    static String headTexture(ItemStack stack) {
        ResolvableProfile p = stack.get(DataComponents.PROFILE);
        if (p == null) return null;
        try {
            for (Property prop : p.partialProfile().properties().get("textures")) {
                Matcher m = TEXTURE_URL.matcher(new String(Base64.getDecoder().decode(prop.value())));
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ---- helpers ----

    private static void safe(List<Row> out, String title, Supplier<List<Row>> build) {
        try {
            List<Row> rows = build.get();
            if (rows == null || rows.isEmpty()) return;
            out.add(Row.header(title));
            out.addAll(rows);
        } catch (Throwable t) {
            out.add(Row.header(title));
            out.add(new Row("error", String.valueOf(t), String.valueOf(t), 0xFFFF5555, false));
        }
    }

    private static void add(List<Row> r, String label, String value) { add(r, label, value, 0); }

    private static void add(List<Row> r, String label, String value, int color) {
        if (value != null && !value.isBlank()) r.add(new Row(label, value, value, color, false));
    }

    /** Legacy &-codes rebuilt from the component styles. */
    static String codes(Component c) {
        StringBuilder sb = new StringBuilder();
        c.visit((Style style, String text) -> {
            if (text.isEmpty()) return java.util.Optional.empty();
            sb.append("&r");
            TextColor col = style.getColor();
            if (col != null) {
                ChatFormatting f = ChatFormatting.getByName(col.serialize());
                if (f != null) sb.append('&').append(f.getChar());
                else sb.append("&#").append(col.serialize().replace("#", ""));
            }
            if (style.isBold()) sb.append("&l");
            if (style.isItalic()) sb.append("&o");
            if (style.isUnderlined()) sb.append("&n");
            if (style.isStrikethrough()) sb.append("&m");
            if (style.isObfuscated()) sb.append("&k");
            sb.append(text.replace('§', '&'));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static String escapeNonAscii(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 0x20 || ch >= 0x7F) sb.append(String.format(Locale.ROOT, "\\u%04X", (int) ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static void collect(String s, Map<Character, Integer> out) {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§') { i++; continue; }
            if (ch < 0x20 || ch >= 0x7F) out.merge(ch, 1, Integer::sum);
        }
    }

    private static String fmt(String f, Object... a) { return String.format(Locale.ROOT, f, a); }

    private static String trim(double d) {
        return d == Math.floor(d) && !Double.isInfinite(d) ? String.valueOf((long) d) : fmt("%.3f", d);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String slotName(EquipmentSlot s) {
        return switch (s) {
            case MAINHAND -> "Hand";
            case OFFHAND -> "Off Hand";
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            case FEET -> "Boots";
            default -> s.name();
        };
    }
}
