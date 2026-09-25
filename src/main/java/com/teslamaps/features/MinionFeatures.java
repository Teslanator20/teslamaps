package com.teslamaps.features;

// Ported from SkyHanni's MinionFeatures / MinionXP, SkyblockAddons' minion warnings, Skytils' onlyCollectEnchantedItems
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hunting.ShardData;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinionFeatures {

    private static final Pattern TITLE = Pattern.compile("^(.+ Minion) ([IVXL]+)$");
    private static final Pattern UPGRADE = Pattern.compile("^You have upgraded your Minion to Tier ([IVXL\\d]+)");
    private static final Pattern COINS = Pattern.compile("^You received [\\d,.]+ coins!");
    private static final int[][] STORAGE_ROWS = {{21, 26}, {30, 35}, {39, 44}};
    private static final long HIGHLIGHT_MS = 5000;

    public static final class Minion {
        public String name = "";
        public long emptied;
    }

    private record Label(Vec3 at, String text) {}
    private record Storage(BlockPos chest, Map<String, Double> xp, long time) {}
    private record Xp(String skill, double amount) {}

    private static final Gson GSON = new Gson();
    private static final ProfiledFile FILE = new ProfiledFile("minions.json", null);
    private static Map<String, Minion> minions = new HashMap<>();
    private static boolean loaded, dirty;
    private static Path path;

    private static BlockPos looked, lastMinion, lastChest;
    private static long lookedAt, closedAt, chestAt;
    private static boolean minionOpen, storageOpen;
    private static int ticks;
    private static Object world;

    private static volatile List<Label> labels = List.of();
    private static final Set<Entity> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Integer> warned = new HashSet<>();
    private static long lastFullWarn, lastReachWarn, lastBlockMsg;

    private static ItemStack hopperStack;
    private static String hopperLine;
    private static Map<ItemStack, String> xpLines = new IdentityHashMap<>();
    private static ItemStack collectStack;
    private static List<String> collectLines = List.of();
    private static final List<Storage> storages = new ArrayList<>();
    private static Map<String, Xp> xpTable;

    private static Feature feature;

    private MinionFeatures() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean anyOn() {
        TeslaMapsConfig c = cfg();
        return c.minionOverlay || c.minionWarnings || c.minionHideLocationWarning || c.minionOnlyEnchanted || c.minionXpTooltip;
    }

    public static void register() {
        feature = Feature.of("MinionFeatures").section("Inventory")
                .when(MinionFeatures::anyOn)
                .tick(MinionFeatures::tick)
                .world(MinionFeatures::render)
                .serverChat(MinionFeatures::onChat);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active()) return;
            onTooltip(stack, lines);
        });
    }

    public static void config(ConfigBuilder b) {
        TeslaMapsConfig c = b.config();
        b.toggle("Minion Overlay", () -> c.minionOverlay, v -> c.minionOverlay = v)
                .desc("Private Island: name + tier and time since the hopper was emptied above each minion you opened once. Stored per profile.");
        b.toggle("  Name + Tier", () -> c.minionOverlayName, v -> c.minionOverlayName = v);
        b.toggle("    Only Tier", () -> c.minionOverlayOnlyTier, v -> c.minionOverlayOnlyTier = v);
        b.toggle("  Hopper Emptied Time", () -> c.minionOverlayEmptied, v -> c.minionOverlayEmptied = v);
        b.slider("  Distance", 4f, 48f, () -> c.minionOverlayDistance, v -> c.minionOverlayDistance = v, 0);
        b.toggle("  Highlight Last Opened Minion", () -> c.minionLastOpened, v -> c.minionLastOpened = v);
        b.color("    Highlight Color", () -> c.minionLastOpenedColor, v -> c.minionLastOpenedColor = v);
        b.toggle("  Coins/Day in Hopper Tooltip", () -> c.minionCoinsPerDay, v -> c.minionCoinsPerDay = v);
        b.toggle("  Hide Mob Nametags Near Minions", () -> c.minionHideMobNametags, v -> c.minionHideMobNametags = v);
        b.button("  Remove Bugged Minion Spots", () -> removeBugged(true));
        b.toggle("Minion Warnings", () -> c.minionWarnings, v -> c.minionWarnings = v)
                .desc("Title + sound when a minion nametag says its storage is full or it can't reach its mobs");
        b.toggle("  Storage Full", () -> c.minionWarnFull, v -> c.minionWarnFull = v);
        b.toggle("  Can't Reach Mobs", () -> c.minionWarnReach, v -> c.minionWarnReach = v);
        b.soundDropdown("  Sound", SoundOptions.keys(), () -> c.minionWarnSound, v -> c.minionWarnSound = v);
        b.toggle("Hide 'Location isn't perfect'", () -> c.minionHideLocationWarning, v -> c.minionHideLocationWarning = v);
        b.toggle("Only Collect Enchanted Items", () -> c.minionOnlyEnchanted, v -> c.minionOnlyEnchanted = v)
                .desc("Minion menu: blocks clicks on raw items while enchanted items sit in its storage");
        b.toggle("Minion XP in Tooltips", () -> c.minionXpTooltip, v -> c.minionXpTooltip = v)
                .desc("Minion and minion storage menus: skill XP for collecting each item, total on Collect All (Moar Skillz included)");
    }

    // --- storage ---

    private static void ensure() {
        if (!loaded) load();
        else if (FILE.stale()) {
            save();
            load();
        }
    }

    private static void load() {
        minions = new HashMap<>();
        dirty = false;
        path = FILE.path();
        try {
            if (path != null && Files.exists(path)) {
                Map<String, Minion> read = GSON.fromJson(Files.readString(path), new TypeToken<Map<String, Minion>>() {}.getType());
                if (read != null) minions.putAll(read);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load minions.json", e);
        }
        loaded = true;
    }

    private static void save() {
        if (!dirty || path == null) return;
        dirty = false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(minions));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save minions.json", e);
        }
    }

    private static String key(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static BlockPos pos(String key) {
        String[] s = key.split(",");
        try {
            return new BlockPos(Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]));
        } catch (Exception e) {
            return null;
        }
    }

    // --- tick ---

    private static void tick() {
        ensure();
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level != world) {
            world = mc.level;
            warned.clear();
            hidden.clear();
            storages.clear();
            lastMinion = null;
            minionOpen = storageOpen = false;
        }
        if (mc.screen == null && mc.crosshairPickEntity instanceof ArmorStand as) {
            looked = as.blockPosition();
            lookedAt = now;
        }
        boolean island = SkyblockUtils.isInArea("Private Island");

        AbstractContainerScreen<?> cs = mc.screen instanceof AbstractContainerScreen<?> s ? s : null;
        String title = cs == null ? "" : StringUtil.stripTrim(cs.getTitle().getString());
        Matcher tm = TITLE.matcher(title);
        boolean isMinion = cs != null && tm.matches() && "Collect All".equals(slotName(cs, 48));
        if (isMinion && !minionOpen) onMinionOpen(tm.group(1) + " " + ShardData.romanToInt(tm.group(2)), island, now);
        if (!isMinion && minionOpen) {
            minionOpen = false;
            closedAt = now;
        }
        storageOpen = cs != null && !isMinion && now - chestAt < 3000 && (title.contains("Minion") || title.endsWith("Storage"));

        if (++ticks % 10 == 0) {
            if (cs != null && (minionOpen || storageOpen)) readMenu(cs, now);
            else if (!xpLines.isEmpty() || hopperStack != null) {
                xpLines = new IdentityHashMap<>();
                collectStack = null;
                hopperStack = null;
            }
            if (island) scanStands(mc, now);
            else hidden.clear();
            buildLabels(mc, island, now);
        }
        if (dirty && ticks % 100 == 0) save();
    }

    private static String slotName(AbstractContainerScreen<?> cs, int idx) {
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= idx) return "";
        return StringUtil.stripTrim(slots.get(idx).getItem().getHoverName().getString());
    }

    private static void onMinionOpen(String name, boolean island, long now) {
        minionOpen = true;
        BlockPos p = now - lookedAt < 2000 ? looked : null;
        if (p == null) return;
        lastMinion = p;
        if (!island) return;
        removeBugged(false);
        Minion m = minions.computeIfAbsent(key(p), k -> new Minion());
        if (!name.equals(m.name)) {
            m.name = name;
            dirty = true;
        }
    }

    public static void onChestClick(BlockPos pos) {
        lastChest = pos;
        chestAt = System.currentTimeMillis();
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInArea("Private Island")) return;
        String s = StringUtil.stripTrim(raw);
        Minion m = lastMinion == null ? null : minions.get(key(lastMinion));
        if (COINS.matcher(s).find()) {
            if (m != null && (minionOpen || System.currentTimeMillis() - closedAt < 2000)) {
                m.emptied = System.currentTimeMillis();
                dirty = true;
            }
        } else if (s.startsWith("You picked up a minion!")) {
            if (lastMinion != null && minions.remove(key(lastMinion)) != null) dirty = true;
            lastMinion = null;
        } else {
            Matcher u = UPGRADE.matcher(s);
            if (u.find() && m != null) {
                String t = u.group(1);
                int tier = Character.isDigit(t.charAt(0)) ? Integer.parseInt(t) : ShardData.romanToInt(t);
                int sp = m.name.lastIndexOf(' ');
                if (tier > 0 && sp > 0) {
                    m.name = m.name.substring(0, sp) + " " + tier;
                    dirty = true;
                }
            }
        }
    }

    private static void removeBugged(boolean manual) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!SkyblockUtils.isInArea("Private Island")) {
            if (manual) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Only works on your Private Island."));
            return;
        }
        ensure();
        Set<BlockPos> stands = new HashSet<>();
        for (ArmorStand as : EntityCache.armorStands()) stands.add(as.blockPosition());
        int removed = 0;
        var it = minions.entrySet().iterator();
        while (it.hasNext()) {
            BlockPos p = pos(it.next().getKey());
            if (p == null) { it.remove(); removed++; continue; }
            if (p.distSqr(mc.player.blockPosition()) > 30 * 30) continue;
            if (!stands.contains(p)) { it.remove(); removed++; }
        }
        if (removed > 0) dirty = true;
        if (manual || removed > 0) mc.player.sendSystemMessage(Component.literal(removed == 0
                ? "§b[TeslaMaps] §7No bugged minions found nearby."
                : "§b[TeslaMaps] §fRemoved §e" + removed + " §fwrong/bugged minion spot" + (removed == 1 ? "" : "s") + "."));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("fixminions").executes(c -> {
            removeBugged(true);
            return 1;
        });
    }

    // --- nametags ---

    private static void scanStands(Minecraft mc, long now) {
        TeslaMapsConfig c = cfg();
        hidden.clear();
        List<BlockPos> spots = new ArrayList<>();
        if (c.minionOverlay && c.minionHideMobNametags) for (String k : minions.keySet()) {
            BlockPos p = pos(k);
            if (p != null) spots.add(p);
        }
        List<ArmorStand> signs = new ArrayList<>();
        List<ArmorStand> perfect = new ArrayList<>();
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            Component cn = as.getCustomName();
            if (cn == null) continue;
            String raw = cn.getString();
            String n = StringUtil.stripTrim(raw);
            if (c.minionWarnings) {
                if (c.minionWarnFull && n.contains("My storage is full!")) warn(as, true, now);
                else if (c.minionWarnReach && n.contains("I can't reach any")) warn(as, false, now);
            }
            if (c.minionHideLocationWarning) {
                if (n.contains("This location isn't perfect!")) { hidden.add(as); perfect.add(as); }
                else if (n.equals("/!\\")) signs.add(as);
            }
            if (!spots.isEmpty() && raw.contains("❤") && as.isAlive()) {
                for (BlockPos p : spots) {
                    if (as.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 25) { hidden.add(as); break; }
                }
            }
        }
        for (ArmorStand s : signs) {
            for (ArmorStand p : perfect) if (s.distanceToSqr(p) < 2.25) { hidden.add(s); break; }
        }
    }

    private static void warn(ArmorStand as, boolean full, long now) {
        if (!warned.add(as.getId())) return;
        if (full ? now - lastFullWarn < 30000 : now - lastReachWarn < 30000) return;
        if (full) lastFullWarn = now;
        else lastReachWarn = now;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTitle(Component.literal(full ? "§cMinion Storage Full!" : "§cMinion Can't Reach Mobs!"));
        mc.gui.setSubtitle(Component.empty());
        mc.gui.setTimes(5, 40, 10);
        LoudSound.play(SoundOptions.resolve(cfg().minionWarnSound), 1f, 1f);
    }

    public static boolean shouldHide(Entity e) {
        return !hidden.isEmpty() && e instanceof ArmorStand && hidden.contains(e) && feature != null && feature.active();
    }

    // --- overlay ---

    private static void buildLabels(Minecraft mc, boolean island, long now) {
        TeslaMapsConfig c = cfg();
        if (!island || !c.minionOverlay || mc.player == null || !(c.minionOverlayName || c.minionOverlayEmptied)) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        double max = c.minionOverlayDistance * c.minionOverlayDistance;
        List<Label> out = new ArrayList<>();
        for (Map.Entry<String, Minion> e : minions.entrySet()) {
            BlockPos p = pos(e.getKey());
            if (p == null) continue;
            Vec3 base = new Vec3(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
            if (mc.player.distanceToSqr(base) > max) continue;
            Minion m = e.getValue();
            if (c.minionOverlayName && !m.name.isEmpty()) {
                String name = c.minionOverlayOnlyTier ? m.name.substring(m.name.lastIndexOf(' ') + 1) : m.name;
                out.add(new Label(base.add(0, 0.65, 0), "§6" + name));
            }
            if (c.minionOverlayEmptied && m.emptied > 0) {
                out.add(new Label(base.add(0, 1.15, 0), "§eHopper Emptied: " + ago(now - m.emptied)));
            }
        }
        labels = out;
    }

    private static String ago(long ms) {
        long s = ms / 1000, d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (d > 0 || h > 0) sb.append(h).append("h ");
        if (d == 0) sb.append(m).append("m ");
        if (d == 0 && h == 0) sb.append(s % 60).append("s ");
        return sb.append("ago").toString();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!c.minionOverlay) return;
        for (Label l : labels) ESPRenderer.drawText(pose, l.text, l.at, 1f, cam, false, true);
        BlockPos p = lastMinion;
        if (c.minionLastOpened && p != null && !minionOpen && closedAt > 0
                && System.currentTimeMillis() - closedAt < HIGHLIGHT_MS) {
            int rgb = TeslaMapsConfig.parseColor(c.minionLastOpenedColor) & 0xFFFFFF;
            AABB box = new AABB(p.getX() + 0.25, p.getY(), p.getZ() + 0.25, p.getX() + 0.75, p.getY() + 1.2, p.getZ() + 0.75);
            ESPRenderer.drawFilledBox(pose, box, 0x66000000 | rgb, cam, true);
        }
    }

    // --- menu ---

    private static boolean inStorage(int idx) {
        for (int[] r : STORAGE_ROWS) if (idx >= r[0] && idx <= r[1]) return true;
        return false;
    }

    private static void readMenu(AbstractContainerScreen<?> cs, long now) {
        TeslaMapsConfig c = cfg();
        List<Slot> slots = cs.getMenu().slots;
        hopperStack = null;
        if (minionOpen && c.minionOverlay && c.minionCoinsPerDay && slots.size() > 28) {
            ItemStack h = slots.get(28).getItem();
            for (String l : ItemUtil.loreLines(h)) {
                String s = StringUtil.stripTrim(l);
                if (!s.startsWith("Held Coins: ")) continue;
                hopperStack = h;
                hopperLine = coinsPerDay(s.substring(12), StringUtil.strip(h.getHoverName().getString()), now);
                break;
            }
        }
        if (!c.minionXpTooltip) return;
        Map<String, Xp> table = xpTable();
        Map<ItemStack, String> lines = new IdentityHashMap<>();
        Map<String, Double> total = new LinkedHashMap<>();
        double mult = com.teslamaps.api.ElectionApi.hasPerk("Moar Skillz") ? 1.5 : 1.0;
        for (Slot slot : slots) {
            if (slot.container instanceof Inventory) continue;
            if (minionOpen && !inStorage(slot.index)) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Xp xp = table.get(ItemUtil.skyblockId(st));
            if (xp == null || xp.amount <= 0) continue;
            double amount = xp.amount * st.getCount() * mult;
            lines.put(st, collectLine(xp.skill, amount));
            total.merge(xp.skill, amount, Double::sum);
        }
        xpLines = lines;
        if (storageOpen) {
            if (lastChest != null) {
                storages.removeIf(s -> s.chest.equals(lastChest) || now - s.time > 20 * 60_000L);
                storages.add(new Storage(lastChest, total, now));
            }
            return;
        }
        List<String> out = new ArrayList<>();
        boolean missing = false;
        if (lastMinion != null && hasAdjacentChest(lastMinion)) {
            Storage found = null;
            for (Storage s : storages) {
                if (now - s.time < 20 * 60_000L && s.chest.distSqr(lastMinion) <= 2.5) { found = s; break; }
            }
            if (found == null) missing = true;
            else for (Map.Entry<String, Double> e : found.xp.entrySet()) total.merge(e.getKey(), e.getValue(), Double::sum);
        }
        for (Map.Entry<String, Double> e : total.entrySet()) out.add(collectLine(e.getKey(), e.getValue()));
        if (missing) {
            out.add("");
            out.add("§cNo minion storage data");
            out.add("§eOpen the storage for the correct value");
        }
        if (!out.isEmpty()) out.add("");
        collectLines = out;
        collectStack = slots.size() > 48 ? slots.get(48).getItem() : null;
    }

    private static boolean hasAdjacentChest(BlockPos p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        return level.getBlockState(p.east()).getBlock() instanceof ChestBlock
                || level.getBlockState(p.west()).getBlock() instanceof ChestBlock
                || level.getBlockState(p.north()).getBlock() instanceof ChestBlock
                || level.getBlockState(p.south()).getBlock() instanceof ChestBlock;
    }

    private static String collectLine(String skill, double amount) {
        String n = amount == Math.floor(amount) ? String.format("%,d", (long) amount) : String.format("%,.1f", amount);
        return "§7Collect to get: §b" + n + " §e" + skill + " XP";
    }

    private static String coinsPerDay(String coinsText, String hopperName, long now) {
        Minion m = lastMinion == null ? null : minions.get(key(lastMinion));
        if (m == null || m.emptied <= 0) return "§cCoins/day: no time data yet (empty the hopper once)";
        double coins;
        try {
            coins = Double.parseDouble(coinsText.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
        long dur = Math.max(1, now - m.emptied);
        long perDay = (long) (coins / dur * 86_400_000d);
        return "§7Coins/day with " + hopperName + "§7: §6" + String.format("%,d", perDay) + " coins";
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty()) return;
        if (hopperStack == stack && hopperLine != null) lines.add(Component.literal(hopperLine));
        if (!cfg().minionXpTooltip) return;
        String l = xpLines.get(stack);
        if (l != null) {
            lines.add(Component.empty());
            lines.add(Component.literal(l));
        }
        if (minionOpen && collectStack == stack && !collectLines.isEmpty()) {
            int at = Math.min(1, lines.size());
            List<Component> add = new ArrayList<>();
            for (String s : collectLines) add.add(Component.literal(s));
            lines.addAll(at, add);
        }
    }

    private static Map<String, Xp> xpTable() {
        if (xpTable != null) return xpTable;
        Map<String, Xp> out = new HashMap<>();
        try (var in = MinionFeatures.class.getResourceAsStream("/assets/teslamaps/data/minion_xp.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> skill : root.getAsJsonObject("minion_xp").entrySet()) {
                    for (Map.Entry<String, JsonElement> e : skill.getValue().getAsJsonObject().entrySet()) {
                        out.put(e.getKey().replace('-', ':'), new Xp(skill.getKey(), e.getValue().getAsDouble()));
                    }
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load minion_xp.json", e);
        }
        return xpTable = out;
    }

    // --- only collect enchanted ---

    public static boolean blockClick(AbstractContainerScreen<?> cs, Slot slot, ContainerInput type) {
        TeslaMapsConfig c = cfg();
        if (!c.minionOnlyEnchanted || !minionOpen || slot == null || feature == null || !feature.active()) return false;
        if (slot.container instanceof Inventory || !inStorage(slot.index) || type == ContainerInput.QUICK_CRAFT) return false;
        String id = ItemUtil.skyblockId(slot.getItem());
        if (id == null || id.isEmpty() || id.startsWith("ENCHANTED_")) return false;
        boolean enchanted = false;
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory || !inStorage(s.index)) continue;
            String o = ItemUtil.skyblockId(s.getItem());
            if (o != null && o.startsWith("ENCHANTED_")) { enchanted = true; break; }
        }
        if (!enchanted) return false;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (now - lastBlockMsg > 5000 && mc.player != null) {
            lastBlockMsg = now;
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §7Blocked collecting a raw item §8(Inventory > Only Collect Enchanted Items)"));
        }
        return true;
    }
}
