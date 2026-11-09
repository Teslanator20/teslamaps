package com.teslamaps.features;

// Ported from bax's features/highlight/HighlightRenderer + HighlightConfig.CustomEntry (alert idea from shizo's CustomESP)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CustomEspRules {

    /** One rule; every non-empty filter must match. */
    public static class EspRule {
        public boolean enabled = true;
        public String entityType = "";
        public String name = "";
        public String helmet = "";
        public String chestplate = "";
        public String leggings = "";
        public String boots = "";
        public String heldItem = "";
        public boolean invisible;
        public boolean npcOnly;
        public String island = "";
        public float minHealth;
        public String color = "FFFFFF";
        public boolean tracer;
        public boolean alert;

        public boolean hasFilters() {
            return !blank(entityType) || !blank(name) || !blank(helmet) || !blank(chestplate) || !blank(leggings)
                    || !blank(boots) || !blank(heldItem) || invisible || npcOnly || !blank(island) || minHealth > 0;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (!blank(entityType)) sb.append(entityType);
            if (!blank(name)) sb.append(sb.isEmpty() ? "" : " ").append('"').append(name).append('"');
            List<String> tags = new ArrayList<>();
            if (!blank(helmet)) tags.add("H:" + helmet);
            if (!blank(chestplate)) tags.add("C:" + chestplate);
            if (!blank(leggings)) tags.add("L:" + leggings);
            if (!blank(boots)) tags.add("B:" + boots);
            if (!blank(heldItem)) tags.add("W:" + heldItem);
            if (invisible) tags.add("INV");
            if (npcOnly) tags.add("NPC");
            if (minHealth > 0) tags.add("HP>=" + (int) minHealth);
            if (!blank(island)) tags.add(island);
            if (!tags.isEmpty()) sb.append(sb.isEmpty() ? "" : "  ").append(String.join(" ", tags));
            return sb.isEmpty() ? "(empty)" : sb.toString();
        }
    }

    private record Match(Entity entity, int color, boolean tracer, String label) {}

    private static final double MAX_DIST = 100.0;
    private static final List<Match> matches = new ArrayList<>();
    private static final Set<Integer> alerted = new HashSet<>();
    private static Object world;
    private static long lastAlert;

    private CustomEspRules() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static boolean blank(String s) { return s == null || s.isBlank(); }

    public static void register() {
        Feature.of("CustomEspRules").section("ESP").legit().when(() -> cfg().customEspRules)
                .tick(CustomEspRules::tick)
                .world(CustomEspRules::render)
                .config("ESP", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Custom ESP Rules");
                    b.toggle("Custom ESP Rules", () -> c.customEspRules, v -> c.customEspRules = v).cheat()
                            .desc("Highlight mobs by type, name, armor, held item, island, invisibility or health.");
                    b.button("  Edit Rules", () -> Minecraft.getInstance()
                            .gui.setScreen(new com.teslamaps.screen.EspRuleScreen(b.screen())));
                    b.toggle("  Labels + Distance", () -> c.customEspRuleLabels, v -> c.customEspRuleLabels = v);
                    b.slider("  Line Width", 1f, 8f, () -> c.customEspRuleLineWidth, v -> c.customEspRuleLineWidth = v, 1);
                    b.soundDropdown("  Alert Sound", SoundOptions.keys(), () -> c.customEspRuleSound, v -> c.customEspRuleSound = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            world = mc.level;
            alerted.clear();
        }
        matches.clear();
        if (LegitMode.blocksCheats()) return;
        List<EspRule> active = new ArrayList<>();
        boolean anyName = false;
        for (EspRule r : cfg().customEspRuleList) {
            if (r == null || !r.enabled || !r.hasFilters()) continue;
            active.add(r);
            if (!blank(r.name)) anyName = true;
        }
        if (active.isEmpty()) return;
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        boolean labels = cfg().customEspRuleLabels;
        Set<Entity> done = new HashSet<>();
        List<Entity> all = EntityCache.all();
        double max = MAX_DIST * MAX_DIST;

        for (Entity e : all) {
            if (e == mc.player || invalid(e) || e instanceof ArmorStand) continue;
            if (e.distanceToSqr(mc.player) > max) continue;
            EspRule r = find(e, active, area);
            if (r == null) continue;
            add(mc, e, r, labels);
            done.add(e);
        }

        if (anyName) {
            for (Entity e : all) {
                if (!(e instanceof ArmorStand) || !e.isAlive()) continue;
                if (e.distanceToSqr(mc.player) > max) continue;
                String shown = StringUtil.stripTrim(e.getName().getString()).toLowerCase(Locale.ROOT);
                if (shown.isEmpty()) continue;
                EspRule hit = null;
                for (EspRule r : active) {
                    if (blank(r.name) || !shown.contains(r.name.toLowerCase(Locale.ROOT))) continue;
                    if (!blank(r.island) && !area.contains(r.island.toLowerCase(Locale.ROOT))) continue;
                    hit = r;
                    break;
                }
                if (hit == null) continue;
                Entity target = below(mc, e);
                if (target == null) {
                    if (blank(hit.entityType)) continue;
                    target = e;
                }
                if (invalid(target) || done.contains(target)) continue;
                if (blank(hit.entityType) && !realMob(target)) continue;
                if (!equipmentMatches(target, hit)) continue;
                add(mc, target, hit, labels);
                done.add(target);
            }
        }
        if (alerted.size() > 4096) alerted.clear();
    }

    private static void add(Minecraft mc, Entity e, EspRule r, boolean labels) {
        int color = TeslaMapsConfig.parseColor(r.color) | 0xFF000000;
        String label = labels ? r.summary() + " §7" + (int) e.distanceTo(mc.player) + "m" : null;
        matches.add(new Match(e, color, r.tracer, label));
        if (r.alert && alerted.add(e.getId())) alert(mc, e, r);
    }

    private static void alert(Minecraft mc, Entity e, EspRule r) {
        long now = System.currentTimeMillis();
        if (now - lastAlert < 2000) return;
        lastAlert = now;
        String name = StringUtil.stripTrim(e.getName().getString());
        mc.gui.hud.setTitle(Component.literal("§c" + name));
        mc.gui.hud.setSubtitle(Component.literal("§e" + r.summary()));
        mc.gui.hud.setTimes(5, 40, 10);
        LoudSound.play(SoundOptions.resolve(cfg().customEspRuleSound), 1f, 1f);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (matches.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float w = cfg().customEspRuleLineWidth;
        for (Match m : matches) {
            Entity e = m.entity;
            if (e.isRemoved()) continue;
            double dx = (e.xo - e.getX()) * (1 - pt), dy = (e.yo - e.getY()) * (1 - pt), dz = (e.zo - e.getZ()) * (1 - pt);
            AABB box = e.getBoundingBox();
            if (box.getSize() < 0.05) box = box.inflate(0.3);
            box = box.move(dx, dy, dz);
            ESPRenderer.drawBoxOutline(pose, box, m.color, w, cam, true);
            if (m.tracer) ESPRenderer.drawTracerFromCamera(pose, box.getCenter(), m.color, cam, w);
            if (m.label != null) {
                ESPRenderer.drawText(pose, m.label, new Vec3(box.getCenter().x, box.maxY + 0.5, box.getCenter().z),
                        1f, cam, true, true, m.color, 0);
            }
        }
    }

    private static EspRule find(Entity e, List<EspRule> rules, String area) {
        for (EspRule r : rules) if (matches(e, r, area)) return r;
        return null;
    }

    private static boolean matches(Entity e, EspRule r, String area) {
        if (!blank(r.island) && !area.contains(r.island.toLowerCase(Locale.ROOT))) return false;
        if (blank(r.entityType) && !realMob(e)) return false;
        if (!blank(r.entityType) && !idMatches(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), r.entityType)) return false;
        if (!blank(r.name)) {
            String shown = StringUtil.stripTrim(e.getName().getString()).toLowerCase(Locale.ROOT);
            if (!shown.contains(r.name.toLowerCase(Locale.ROOT))) return false;
        }
        if (r.npcOnly && (!(e instanceof Player) || e.getUUID().version() == 4)) return false;
        return equipmentMatches(e, r);
    }

    private static boolean equipmentMatches(Entity e, EspRule r) {
        boolean gear = !blank(r.helmet) || !blank(r.chestplate) || !blank(r.leggings) || !blank(r.boots) || !blank(r.heldItem);
        if (gear || r.invisible || r.minHealth > 0) {
            if (!(e instanceof LivingEntity l)) return false;
            if (!blank(r.helmet) && !slot(l.getItemBySlot(EquipmentSlot.HEAD), r.helmet)) return false;
            if (!blank(r.chestplate) && !slot(l.getItemBySlot(EquipmentSlot.CHEST), r.chestplate)) return false;
            if (!blank(r.leggings) && !slot(l.getItemBySlot(EquipmentSlot.LEGS), r.leggings)) return false;
            if (!blank(r.boots) && !slot(l.getItemBySlot(EquipmentSlot.FEET), r.boots)) return false;
            if (!blank(r.heldItem) && !slot(l.getMainHandItem(), r.heldItem)) return false;
            if (r.invisible && !l.isInvisible()) return false;
            if (r.minHealth > 0 && l.getHealth() < r.minHealth) return false;
        }
        return true;
    }

    private static boolean slot(ItemStack s, String needle) {
        if (s.isEmpty()) return false;
        String n = needle.toLowerCase(Locale.ROOT);
        if (StringUtil.stripTrim(s.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(n)) return true;
        return com.teslamaps.utils.ItemUtil.skyblockId(s).toLowerCase(Locale.ROOT).contains(n);
    }

    static boolean idMatches(Identifier id, String target) {
        String t = target.toLowerCase(Locale.ROOT).trim();
        return id.toString().contains(t) || id.getPath().contains(t);
    }

    private static Entity below(Minecraft mc, Entity stand) {
        Vec3 p = stand.position();
        AABB area = new AABB(p.x - 1, p.y - 3, p.z - 1, p.x + 1, p.y, p.z + 1);
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(stand, area)) {
            if (e instanceof ArmorStand || e == mc.player || invalid(e)) continue;
            double d = e.position().distanceToSqr(p);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static boolean realMob(Entity e) {
        return e instanceof Player || (e instanceof LivingEntity && !(e instanceof ArmorStand));
    }

    private static boolean invalid(Entity e) {
        if (!e.isAlive() || e instanceof AbstractArrow) return true;
        return e instanceof LivingEntity l && l.isDeadOrDying();
    }

    /** Entity type ids starting with or containing the text, for the rule editor. */
    public static List<String> typeSuggestions(String prefix, int max) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT).trim();
        if (p.isEmpty()) return out;
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String path = id.getPath();
            if (path.startsWith(p) && !path.equals(p)) out.add(path);
            if (out.size() >= max) return out;
        }
        if (out.size() < max) {
            for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                String path = id.getPath();
                if (!path.startsWith(p) && path.contains(p)) out.add(path);
                if (out.size() >= max) break;
            }
        }
        return out;
    }
}
