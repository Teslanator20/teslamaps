package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers what was in every container the player opened, so /tmap search can find an item
 * anywhere - museum, sacks, vault, island chests, storage pages.
 */
public class ItemIndex {

    public static final String STORAGE = "Storage";
    public static final String MUSEUM = "Museum";
    public static final String SACKS = "Sacks";
    public static final String VAULT = "Vault";
    public static final String CHESTS = "Chests";
    public static final String WARDROBE = "Wardrobe";
    public static final String ACCESSORIES = "Accessories";
    public static final String BAGS = "Bags";
    public static final String FORGE = "Forge";
    public static final String INVENTORY = "Inventory";

    public static final List<String> TYPES = List.of(
            INVENTORY, STORAGE, CHESTS, MUSEUM, SACKS, VAULT, WARDROBE, ACCESSORIES, BAGS, FORGE);

    private static final int MAX_SOURCES = 400;
    private static final long CHEST_CLICK_VALID_MS = 4000;

    public static class Src {
        public String name;
        public String type;
        public long seen;
        public LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        public LinkedHashMap<Integer, String> names;      // plain item names, so a search never decodes everything
        public LinkedHashMap<Integer, Integer> amounts;   // sacks store their count in the lore
        public LinkedHashMap<Integer, String> lore;       // stripped lower-case lore, for lore search
        public Boolean island;                            // captured by the island chest tracker
    }

    /** One item found by a search. pos is only set for island chests. */
    public record Hit(ItemStack stack, int count, String source, String type, BlockPos pos) {}

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Src>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("item_index.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("item_index.json"));
    private static Path path;

    private static Map<String, Src> data = new LinkedHashMap<>();
    private static final Map<String, ItemStack> decoded = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static boolean dirty = false;

    private static String lastKey = null;
    private static long openedMs = 0, lastCaptureMs = 0;
    private static boolean packetTaken = false;
    private static BlockPos chestPos = null, chestPos2 = null;
    private static long chestClickMs = 0;
    private static int chestTicks = 0;

    private static final Pattern STORED = Pattern.compile("Stored:\\s*([\\d,.]+)\\s*([kKmMbB]?)");

    private static final Set<String> NAV = Set.of(
            "Go Back", "Close", "Next Page", "Previous Page", "Back", "Search", "Sort",
            "Collect All", "Refresh", "Navigate Pages", "Sack of Sacks");

    private static void ensure() {
        if (!loaded) load();
    }

    static {
        com.teslamaps.utils.SbProfile.onChange(() -> {
            if (loaded && FILE.stale()) { save(); load(); }
        });
    }

    public static void load() {
        pending.join();
        data = new LinkedHashMap<>();
        decoded.clear();
        dirty = false;
        path = FILE.path();
        try {
            if (Files.exists(path)) {
                Map<String, Src> d = GSON.fromJson(Files.readString(path), MAP_TYPE);
                if (d != null) {
                    d.entrySet().removeIf(e -> e.getValue() == null || e.getValue().slots == null);
                    data = d;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load item_index.json", e);
        }
        loaded = true;
    }

    private static final long SAVE_EVERY_MS = 30_000;
    private static long lastSaveMs = 0;
    private static java.util.concurrent.CompletableFuture<Void> pending = java.util.concurrent.CompletableFuture.completedFuture(null);

    public static void registerEvents() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> save());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            save();
            pending.join();
        });
    }

    /** Serialized here, written on the io pool. */
    public static void save() {
        if (!dirty || path == null) return;
        dirty = false;
        lastSaveMs = System.currentTimeMillis();
        Path p = path;
        String json = GSON.toJson(data, MAP_TYPE);
        pending = pending.thenRunAsync(() -> {
            try {
                Files.createDirectories(p.getParent());
                Files.writeString(p, json);
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to save item_index.json", e);
            }
        }, net.minecraft.util.Util.ioPool());
    }

    private static void saveThrottled() {
        if (dirty && System.currentTimeMillis() - lastSaveMs >= SAVE_EVERY_MS) save();
    }

    public static void clear() {
        ensure();
        data.clear();
        decoded.clear();
        dirty = true;
        save();
    }

    /** Every stored slot as (source key, snbt). */
    public static void forEachStored(java.util.function.BiConsumer<String, String> out) {
        ensure();
        for (Map.Entry<String, Src> e : data.entrySet()) for (String snbt : e.getValue().slots.values()) out.accept(e.getKey(), snbt);
    }

    /** Key of the container being captured right now, null if none. */
    public static String openKey() { return lastKey; }

    public static int sourceCount() {
        ensure();
        return data.size();
    }

    public static int itemCount() {
        ensure();
        int n = 0;
        for (Src s : data.values()) n += s.slots.size();
        return n;
    }

    /** Remembers where an island chest was opened so its snapshot gets a stable key. */
    public static void onChestClick(BlockPos pos) {
        if (com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;   // secret chests are not storage
        chestPos = pos;
        chestPos2 = null;
        chestClickMs = System.currentTimeMillis();
        if (!TeslaMapsConfig.get().islandChestTracker) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState st = mc.level.getBlockState(pos);
        if (!st.hasProperty(BlockStateProperties.CHEST_TYPE)) return;
        ChestType t = st.getValue(BlockStateProperties.CHEST_TYPE);
        if (t == ChestType.SINGLE) return;
        BlockPos diff = switch (st.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case EAST -> new BlockPos(0, 0, 1);
            case WEST -> new BlockPos(0, 0, -1);
            case NORTH -> new BlockPos(1, 0, 0);
            case SOUTH -> new BlockPos(-1, 0, 0);
            default -> BlockPos.ZERO;
        };
        if (t != ChestType.LEFT) diff = diff.multiply(-1);
        BlockPos other = pos.offset(diff);
        // the RIGHT half holds container slots 0-26
        if (t == ChestType.RIGHT) { chestPos = pos; chestPos2 = other; }
        else { chestPos = other; chestPos2 = pos; }
    }

    private static boolean onPrivateIsland() {
        return "Private Island".equals(com.teslamaps.utils.SkyblockUtils.getArea());
    }

    private static boolean vanillaChestTitle(Component title) {
        return title.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("container.chest");
    }

    private static String chestKey(BlockPos p) {
        return "chest:" + p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static void removeChest(BlockPos p) {
        if (p == null) return;
        String k = chestKey(p);
        if (data.remove(k) != null) { dropDecoded(k); dirty = true; }
    }

    public static void clearChests() {
        ensure();
        List<String> keys = new ArrayList<>();
        for (String k : data.keySet()) if (k.startsWith("chest:")) keys.add(k);
        for (String k : keys) { data.remove(k); dropDecoded(k); }
        dirty = true;
        save();
    }

    /** Broken island chests drop out of the index. */
    private static void sweepChests(Minecraft mc) {
        if (++chestTicks % 40 != 0 || !onPrivateIsland()) return;
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, Src> e : data.entrySet()) {
            if (Boolean.FALSE.equals(e.getValue().island)) continue;
            String k = e.getKey();
            BlockPos p = chestPosFromKey(k);
            if (p == null || !mc.level.isLoaded(p)) continue;
            BlockState st = mc.level.getBlockState(p);
            if (!st.is(Blocks.CHEST) && !st.is(Blocks.TRAPPED_CHEST) && !st.is(Blocks.BARREL)) gone.add(k);
        }
        for (String k : gone) { data.remove(k); dropDecoded(k); dirty = true; }
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().itemSearchIndex) return;
        ensure();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean tracker = TeslaMapsConfig.get().islandChestTracker;
        if (tracker) sweepChests(mc);
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { flush(); return; }

        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (title.isEmpty()) { flush(); return; }
        if (BackpackPreview.keyFromTitle(title) != null) return;  // StorageCache owns those

        String type = typeFor(title);
        String key, name;
        if (type != null) {
            key = type + ":" + title;
            name = title;
        } else {
            long now = System.currentTimeMillis();
            if (chestPos == null || now - chestClickMs > CHEST_CLICK_VALID_MS) { flush(); return; }
            if (tracker) {
                if (!onPrivateIsland()) { flush(); return; }
                if (title.equals("Minion Chest")) { removeChest(chestPos); flush(); return; }
                if (!vanillaChestTitle(cs.getTitle())) { flush(); return; }
            }
            type = CHESTS;
            key = "chest:" + chestPos.getX() + "," + chestPos.getY() + "," + chestPos.getZ();
            name = title + " " + chestPos.getX() + " " + chestPos.getY() + " " + chestPos.getZ();
        }

        long now = System.currentTimeMillis();
        if (!key.equals(lastKey)) {
            lastKey = key;
            openedMs = now;
            lastCaptureMs = 0;
            packetTaken = false;
        }
        // first full fill is taken at once, everything after goes through the half-second timer
        boolean packetReady = !packetTaken
                && com.teslamaps.utils.ContainerState.fullyOpened(cs.getMenu().containerId);
        if (!packetReady) {
            if (now - openedMs < 500) return;
            if (now - lastCaptureMs < 500) return;
        }
        if (packetReady) packetTaken = true;
        lastCaptureMs = now;
        if (tracker && type.equals(CHESTS) && chestPos2 != null) captureDouble(cs, title);
        else capture(cs, key, type, name);
    }

    /** A large chest is stored as its two blocks, 27 slots each, so a hit points at the right half. */
    private static void captureDouble(AbstractContainerScreen<?> cs, String title) {
        BlockPos a = chestPos, b = chestPos2;
        capture(cs, chestKey(a), CHESTS, title + " " + a.getX() + " " + a.getY() + " " + a.getZ(), 0);
        capture(cs, chestKey(b), CHESTS, title + " " + b.getX() + " " + b.getY() + " " + b.getZ(), 27);
    }

    private static void capture(AbstractContainerScreen<?> cs, String key, String type, String name) {
        capture(cs, key, type, name, -1);
    }

    /** @param half -1 = whole menu, 0/27 = only that half of a large chest, renumbered from 0 */
    private static void capture(AbstractContainerScreen<?> cs, String key, String type, String name, int half) {
        LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        LinkedHashMap<Integer, String> names = new LinkedHashMap<>();
        LinkedHashMap<Integer, Integer> amounts = null;
        LinkedHashMap<Integer, String> lore = new LinkedHashMap<>();
        int filled = 0;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int cslot = slot.getContainerSlot();
            if (half >= 0) {
                if (cslot < half || cslot >= half + 27) continue;
                cslot -= half;
            }
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            filled++;
            if (junk(st, type)) continue;
            int amount = type.equals(SACKS) ? storedAmount(st) : st.getCount();
            if (amount <= 0) continue;
            String snbt = StorageCache.encode(st);
            if (snbt == null) continue;
            slots.put(cslot, snbt);
            names.put(cslot, StringUtil.stripTrim(st.getHoverName().getString()));
            String l = loreText(st);
            if (!l.isEmpty()) lore.put(cslot, l);
            if (amount != st.getCount()) {
                if (amounts == null) amounts = new LinkedHashMap<>();
                amounts.put(cslot, amount);
            }
        }

        // a hypixel menu that is still loading is all-empty - never let that wipe a good snapshot
        if (filled == 0 && !type.equals(CHESTS)) return;

        Src src = data.get(key);
        if (src == null) {
            src = new Src();
            data.put(key, src);
        }
        src.name = name;
        src.type = type;
        src.seen = System.currentTimeMillis();
        src.slots = slots;
        src.names = names;
        src.amounts = amounts;
        src.lore = lore;
        src.island = type.equals(CHESTS) ? Boolean.valueOf(TeslaMapsConfig.get().islandChestTracker && onPrivateIsland()) : null;
        dropDecoded(key);
        dirty = true;
        trim();
    }

    private static void trim() {
        while (data.size() > MAX_SOURCES) {
            String oldest = null;
            long best = Long.MAX_VALUE;
            for (Map.Entry<String, Src> e : data.entrySet()) {
                if (e.getValue().seen < best) { best = e.getValue().seen; oldest = e.getKey(); }
            }
            if (oldest == null) return;
            data.remove(oldest);
            dropDecoded(oldest);
        }
    }

    private static void dropDecoded(String key) {
        decoded.keySet().removeIf(k -> k.startsWith(key + "#"));
    }

    private static void flush() {
        lastKey = null;
        saveThrottled();
    }

    private static String typeFor(String title) {
        if (title.contains("Museum")) return MUSEUM;
        if (title.equals("Sack of Sacks") || title.endsWith(" Sack") || title.endsWith(" Sacks")) return SACKS;
        if (title.startsWith("Personal Vault") || title.startsWith("Vault")) return VAULT;
        if (title.startsWith("Wardrobe") || title.startsWith("Equipment")) return WARDROBE;
        if (title.startsWith("Accessory Bag")) return ACCESSORIES;
        if (title.startsWith("Quiver") || title.startsWith("Potion Bag")
                || title.startsWith("Fishing Bag") || title.startsWith("Talisman Bag")
                || title.startsWith("Hunting Box")) return BAGS;
        if (title.startsWith("The Forge")) return FORGE;
        return null;
    }

    private static boolean junk(ItemStack st, String type) {
        var item = st.getItem();
        if (item == Items.BLACK_STAINED_GLASS_PANE || item == Items.GRAY_STAINED_GLASS_PANE
                || item == Items.LIGHT_GRAY_STAINED_GLASS_PANE || item == Items.BARRIER
                || item == Items.ARROW) return true;
        String n = StringUtil.stripTrim(st.getHoverName().getString());
        if (n.isEmpty() || NAV.contains(n)) return true;
        if (type.equals(MUSEUM) || type.equals(FORGE)) {
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String l = StringUtil.stripTrim(line.getString());
                    if (l.startsWith("Not Donated") || l.startsWith("You have not donated")
                            || l.equals("Empty Forge Slot")) return true;
                }
            }
        }
        return false;
    }

    /** Sacks keep the real amount in a "Stored: 1,234" lore line, the stack size is always 1. */
    static int storedAmount(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return 0;
        for (Component line : lore.lines()) {
            Matcher m = STORED.matcher(StringUtil.stripTrim(line.getString()));
            if (!m.find()) continue;
            try {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                String unit = m.group(2).toLowerCase(Locale.ROOT);
                if (unit.equals("k")) v *= 1_000;
                else if (unit.equals("m")) v *= 1_000_000;
                else if (unit.equals("b")) v *= 1_000_000_000L;
                return (int) v;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String loreText(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Component line : lore.lines()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(StringUtil.strip(line.getString()));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Plain item name -> stored amount over every indexed sack page. */
    public static Map<String, Integer> sackAmounts() {
        ensure();
        Map<String, Integer> out = new java.util.HashMap<>();
        for (Src src : data.values()) {
            if (!SACKS.equals(src.type) || src.names == null) continue;
            for (Map.Entry<Integer, String> e : src.names.entrySet()) {
                int amount = src.amounts == null ? 0 : src.amounts.getOrDefault(e.getKey(), 0);
                if (amount > 0) out.merge(e.getValue(), amount, Math::max);
            }
        }
        return out;
    }

    /** @param typeFilter null or "" = every source type. */
    public static List<Hit> search(String query, String typeFilter) {
        return search(query, typeFilter, false);
    }

    private static boolean withLore;

    public static List<Hit> search(String query, String typeFilter, boolean lore) {
        ensure();
        withLore = lore;
        List<Hit> out = new ArrayList<>();
        String q = StringUtil.strip(query).toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return out;

        if (typeFilter == null || typeFilter.isEmpty() || typeFilter.equals(INVENTORY)) {
            collectInventory(q, out);
        }
        if (typeFilter == null || typeFilter.isEmpty() || typeFilter.equals(STORAGE)) {
            for (int i = 1; i <= 9; i++) collectStorage("epage_" + i, "Ender Chest " + i, q, out);
            for (int i = 1; i <= 18; i++) collectStorage("backpack_" + i, "Backpack " + i, q, out);
        }
        for (Map.Entry<String, Src> e : data.entrySet()) {
            Src src = e.getValue();
            if (src.type == null) continue;
            if (typeFilter != null && !typeFilter.isEmpty() && !typeFilter.equals(src.type)) continue;
            collectSource(e.getKey(), src, q, out);
        }
        return out;
    }

    private static void collectSource(String key, Src src, String q, List<Hit> out) {
        BlockPos pos = chestPosFromKey(key);
        for (Map.Entry<Integer, String> e : src.slots.entrySet()) {
            int slot = e.getKey();
            // the plain name list keeps a search off the SNBT parser for everything that misses
            String name = src.names == null ? null : src.names.get(slot);
            boolean nameHit = name != null && name.toLowerCase(Locale.ROOT).contains(q);
            if (name != null && !nameHit) {
                if (!withLore) continue;
                if (src.lore != null) {
                    String l = src.lore.get(slot);
                    if (l == null || !l.contains(q)) continue;
                }
            }

            ItemStack st = stack(key, slot, e.getValue());
            if (st.isEmpty()) continue;
            if (!nameHit && !matches(st, q)) continue;

            int count = st.getCount();
            if (src.amounts != null) count = src.amounts.getOrDefault(slot, count);
            out.add(new Hit(st, count, src.name == null ? key : src.name, src.type, pos));
        }
    }

    private static BlockPos chestPosFromKey(String key) {
        if (!key.startsWith("chest:")) return null;
        String[] p = key.substring(6).split(",");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ItemStack stack(String key, int slot, String snbt) {
        String id = key + "#" + slot;
        ItemStack cached = decoded.get(id);
        if (cached != null) return cached;
        ItemStack st = StorageCache.decodeStack(snbt);
        if (st == null) st = ItemStack.EMPTY;
        if (decoded.size() > 4000) decoded.clear();
        decoded.put(id, st);
        return st;
    }

    private static void collectInventory(String q, List<Hit> out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !matches(st, q)) continue;
            out.add(new Hit(st, st.getCount(), "Inventory", INVENTORY, null));
        }
    }

    private static void collectStorage(String key, String name, String q, List<Hit> out) {
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (items == null) return;
        for (ItemStack st : items.values()) {
            if (st == null || st.isEmpty() || !matches(st, q)) continue;
            out.add(new Hit(st, st.getCount(), name, STORAGE, null));
        }
    }

    private static boolean matches(ItemStack st, String q) {
        if (StringUtil.strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(q)) return true;
        return withLore && loreText(st).contains(q);
    }
}
