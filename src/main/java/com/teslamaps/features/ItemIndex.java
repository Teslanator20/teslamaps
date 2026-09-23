package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
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
    }

    /** One item found by a search. pos is only set for island chests. */
    public record Hit(ItemStack stack, int count, String source, String type, BlockPos pos) {}

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Src>>() {}.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("item_index.json");

    private static Map<String, Src> data = new LinkedHashMap<>();
    private static final Map<String, ItemStack> decoded = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static boolean dirty = false;

    private static String lastKey = null;
    private static long openedMs = 0, lastCaptureMs = 0;
    private static int lastVersion = -1;
    private static BlockPos chestPos = null;
    private static long chestClickMs = 0;

    private static final Pattern STORED = Pattern.compile("Stored:\\s*([\\d,.]+)\\s*([kKmMbB]?)");

    private static final Set<String> NAV = Set.of(
            "Go Back", "Close", "Next Page", "Previous Page", "Back", "Search", "Sort",
            "Collect All", "Refresh", "Navigate Pages", "Sack of Sacks");

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                Map<String, Src> d = GSON.fromJson(Files.readString(PATH), MAP_TYPE);
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

    public static void save() {
        if (!dirty) return;
        dirty = false;
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(data, MAP_TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save item_index.json", e);
        }
    }

    public static void clear() {
        if (!loaded) load();
        data.clear();
        decoded.clear();
        dirty = true;
        save();
    }

    public static int sourceCount() {
        if (!loaded) load();
        return data.size();
    }

    public static int itemCount() {
        if (!loaded) load();
        int n = 0;
        for (Src s : data.values()) n += s.slots.size();
        return n;
    }

    /** Remembers where an island chest was opened so its snapshot gets a stable key. */
    public static void onChestClick(BlockPos pos) {
        if (com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;   // secret chests are not storage
        chestPos = pos;
        chestClickMs = System.currentTimeMillis();
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().itemSearchIndex) return;
        if (!loaded) load();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
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
            type = CHESTS;
            key = "chest:" + chestPos.getX() + "," + chestPos.getY() + "," + chestPos.getZ();
            name = title + " " + chestPos.getX() + " " + chestPos.getY() + " " + chestPos.getZ();
        }

        long now = System.currentTimeMillis();
        if (!key.equals(lastKey)) {
            lastKey = key;
            openedMs = now;
            lastCaptureMs = 0;
            lastVersion = -1;
        }
        // packets say when the menu is filled; the half-second timer stays as fallback and resync
        int version = com.teslamaps.utils.ContainerState.version();
        boolean packetReady = version != lastVersion
                && com.teslamaps.utils.ContainerState.fullyOpened(cs.getMenu().containerId);
        if (!packetReady) {
            if (now - openedMs < 500) return;
            if (now - lastCaptureMs < 500) return;
        }
        lastCaptureMs = now;
        lastVersion = version;
        capture(cs, key, type, name);
    }

    private static void capture(AbstractContainerScreen<?> cs, String key, String type, String name) {
        LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        LinkedHashMap<Integer, String> names = new LinkedHashMap<>();
        LinkedHashMap<Integer, Integer> amounts = null;
        int filled = 0;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            filled++;
            if (junk(st, type)) continue;
            int amount = type.equals(SACKS) ? storedAmount(st) : st.getCount();
            if (amount <= 0) continue;
            String snbt = StorageCache.encode(st);
            if (snbt == null) continue;
            int cslot = slot.getContainerSlot();
            slots.put(cslot, snbt);
            names.put(cslot, StringUtil.stripTrim(st.getHoverName().getString()));
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
        if (lastKey == null) return;
        lastKey = null;
        save();
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

    /** @param typeFilter null or "" = every source type. */
    public static List<Hit> search(String query, String typeFilter) {
        if (!loaded) load();
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
            if (name != null && !name.toLowerCase(Locale.ROOT).contains(q)) continue;

            ItemStack st = stack(key, slot, e.getValue());
            if (st.isEmpty()) continue;
            if (name == null && !matches(st, q)) continue;

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
        return StringUtil.strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(q);
    }
}
