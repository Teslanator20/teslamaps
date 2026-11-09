package com.teslamaps.features;

// Crop milestone counters after SkyHanni's CropMilestonesApi / WrongDataFix / GardenCropSpeed
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.GardenRegistry;
import com.teslamaps.features.garden.CropMilestoneHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ContainerState;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-profile garden numbers shared by the garden HUDs: milestone crops per crop (menu, tab,
 * level-up line, tool counter), measured crops/s, crop start + last farmed spots, garden xp.
 */
public final class GardenMilestoneData {

    public static class Data {
        public Map<String, Long> counters = new HashMap<>();
        public Map<String, Double> speed = new HashMap<>();
        public Map<String, double[]> start = new HashMap<>();
        public Map<String, double[]> last = new HashMap<>();
        public long gardenXp = -1;
    }

    /** Crop display name -> GardenRegistry key. */
    public static final Map<String, String> CROPS = new LinkedHashMap<>();
    static {
        for (GardenRegistry.Crop c : GardenRegistry.CROPS) CROPS.put(c.display(), c.key());
    }

    private static final String[][] TOOLS = {
            {"THEORETICAL_HOE_WHEAT", "Wheat"}, {"THEORETICAL_HOE_CARROT", "Carrot"},
            {"THEORETICAL_HOE_POTATO", "Potato"}, {"THEORETICAL_HOE_WARTS", "Nether Wart"},
            {"PUMPKIN_DICER", "Pumpkin"}, {"MELON_DICER", "Melon"}, {"COCO_CHOPPER", "Cocoa Beans"},
            {"THEORETICAL_HOE_CANE", "Sugar Cane"}, {"CACTUS_KNIFE", "Cactus"}, {"FUNGI_CUTTER", "Mushroom"},
            {"THEORETICAL_HOE_SUNFLOWER", "Sunflower"}, {"THEORETICAL_HOE_WILD_ROSE", "Wild Rose"},
    };

    private static final Pattern TAB = Pattern.compile("^\\s*([A-Za-z ]+?) (\\d+): (?:([\\d.]+)%|MAX)\\s*$");
    private static final Pattern LEVEL_UP = Pattern.compile("^\\s*GARDEN MILESTONE (.+?) (\\S+)➜(\\S+)$");
    private static final Pattern HARVEST = Pattern.compile("^Harvest (.+?) on .*");
    private static final Pattern TOTAL = Pattern.compile("^Total: ([\\d,]+)$");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProfiledFile FILE = new ProfiledFile("garden_data.json", null);
    private static Data data;
    private static Path dataPath;
    private static boolean dirty;
    private static int ticks;
    private static int version;

    private static String lastBroken;
    private static long lastBreakAt;
    private static final Map<String, Long> toolCounters = new HashMap<>();
    private static long gainedThisSecond;
    private static final List<Long> perSecond = new ArrayList<>();
    private static String speedCrop;
    private static int scannedVersion = -1;

    private GardenMilestoneData() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GardenMilestoneData").when(GardenMilestoneData::wanted)
                .tick(GardenMilestoneData::tick)
                .serverChat(GardenMilestoneData::onChat);
    }

    /** Some feature needs the numbers. */
    public static boolean wanted() {
        TeslaMapsConfig c = cfg();
        return (c.section("Garden") && (c.bestCropTime || c.cropStartLocation || c.gardenLevelHud || c.cropFeverTracker
                || c.cropMilestoneHud || c.mooshroomPerkHud || c.cropCalcCommands))
                || (c.section("Skills") && c.skillOverflow && c.cropMilestoneOverflow);
    }

    /** Bumped on every change, for HUDs that cache their lines. */
    public static int version() { return version; }

    public static Data get() {
        if (data == null || FILE.stale()) {
            dataPath = FILE.path();
            data = read(dataPath);
            if (data == null) data = new Data();
            if (data.counters == null) data.counters = new HashMap<>();
            if (data.speed == null) data.speed = new HashMap<>();
            if (data.start == null) data.start = new HashMap<>();
            if (data.last == null) data.last = new HashMap<>();
            version++;
        }
        return data;
    }

    public static void markDirty() {
        dirty = true;
        version++;
    }

    private static Data read(Path p) {
        if (p == null || !Files.isRegularFile(p)) return null;
        try {
            return GSON.fromJson(Files.readString(p), Data.class);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[GardenData] could not read {}: {}", p, e.toString());
            return null;
        }
    }

    private static void save() {
        dirty = false;
        if (data == null || dataPath == null) return;
        try {
            Files.createDirectories(dataPath.getParent());
            Files.writeString(dataPath, GSON.toJson(data));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[GardenData] could not write {}: {}", dataPath, e.toString());
        }
    }

    // ---- tiers ----

    public static long[] brackets(String crop) {
        String key = CROPS.get(crop);
        return key == null ? null : GardenRegistry.brackets(key);
    }

    public static int maxTier(String crop) {
        long[] b = brackets(crop);
        return b == null ? 46 : b.length;
    }

    /** Crops needed from zero to reach the tier, overflow tiers repeat the last bracket. */
    public static long totalForTier(String crop, int tier) {
        long[] b = brackets(crop);
        if (b == null || tier <= 0) return 0;
        long total = 0;
        for (int i = 0; i < Math.min(tier, b.length); i++) total += b[i];
        if (tier > b.length) total += (tier - b.length) * b[b.length - 1];
        return total;
    }

    /** Crops needed for this one tier. */
    public static long tierAmount(String crop, int tier) {
        long[] b = brackets(crop);
        if (b == null || tier <= 0) return 0;
        return b[Math.min(tier - 1, b.length - 1)];
    }

    public static int tierOf(String crop, long count) {
        long[] b = brackets(crop);
        if (b == null) return 0;
        long total = 0;
        for (int tier = 0; tier < b.length; tier++) {
            if (total + b[tier] > count) return tier;
            total += b[tier];
        }
        long last = b[b.length - 1];
        return b.length + (int) Math.min(100_000, (count - total) / Math.max(1, last));
    }

    /** Milestone crops, -1 when not known yet. */
    public static long counter(String crop) {
        Long v = get().counters.get(crop);
        return v == null ? -1 : v;
    }

    /** Last measured crops per second for the crop, 0 if never measured. */
    public static double speed(String crop) {
        Double v = get().speed.get(crop);
        return v == null ? 0 : v;
    }

    private static void setCounter(String crop, long value) {
        Long old = get().counters.put(crop, value);
        if (old == null || old != value) markDirty();
    }

    // ---- current crop ----

    /** Crop the held tool is made for, null for a generic tool or none. */
    public static String toolCrop(ItemStack stack) {
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return null;
        for (String[] t : TOOLS) {
            if (!id.startsWith(t[0])) continue;
            if (t[1].equals("Sunflower") && night()) return "Moonflower";
            return t[1];
        }
        return null;
    }

    public static String heldToolCrop() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : toolCrop(mc.player.getMainHandItem());
    }

    /** Crop being farmed right now: the tool's crop, else the last broken one within 10 s. */
    public static String currentCrop() {
        String tool = heldToolCrop();
        if (tool != null) return tool;
        return System.currentTimeMillis() - lastBreakAt < 10_000 ? lastBroken : null;
    }

    public static String lastBrokenCrop() { return lastBroken; }

    private static boolean night() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getOverworldClockTime() % 24000 >= 12000;
    }

    public static String cropOf(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("mushroom")) return "Mushroom";
        return switch (path) {
            case "wheat", "hay_block" -> "Wheat";
            case "carrots" -> "Carrot";
            case "potatoes" -> "Potato";
            case "pumpkin", "carved_pumpkin" -> "Pumpkin";
            case "melon" -> "Melon";
            case "sugar_cane" -> "Sugar Cane";
            case "cactus" -> "Cactus";
            case "cocoa" -> "Cocoa Beans";
            case "nether_wart" -> "Nether Wart";
            case "sunflower" -> night() ? "Moonflower" : "Sunflower";
            case "rose_bush" -> "Wild Rose";
            default -> path.contains("moonflower") ? "Moonflower" : null;
        };
    }

    /** From FarmSpeed, one crop block broken in the garden. */
    public static void onCropBreak(BlockState state) {
        if (!wanted()) return;
        String crop = cropOf(state);
        if (crop == null) return;
        lastBroken = crop;
        lastBreakAt = System.currentTimeMillis();
        TeslaMapsConfig c = cfg();
        if (c.section("Garden")) {
            if (c.cropStartLocation) CropStartLocation.onCropBreak(crop);
            if (c.cropFeverTracker) CropFeverTracker.onCropBreak(crop);
        }
    }

    // ---- tick ----

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (SkyblockUtils.isInGarden()) {
            if (ticks % 4 == 0) readToolCounter(mc);
            if (ticks % 20 == 0) {
                speedSecond();
                readTab();
            }
        }
        if (mc.screen instanceof AbstractContainerScreen<?> cs && ticks % 10 == 0) readMenu(cs);
        if (dirty && ticks % 600 == 0) save();
    }

    private static void readToolCounter(Minecraft mc) {
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;
        CustomData d = held.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        CompoundTag tag = d.copyTag();
        long counter = tag.getLongOr("farmed_cultivating", -1L);
        if (counter < 0) counter = tag.getLongOr("mined_crops", -1L);
        if (counter < 0) return;
        String uuid = tag.getStringOr("uuid", "");
        if (uuid.isEmpty()) return;
        Long old = toolCounters.put(uuid, counter);
        if (old == null || counter <= old) return;
        long gained = counter - old;
        if (gained > 5_000_000) return;
        String crop = currentCrop();
        if (crop == null) return;
        if (crop.equals("Wheat")) gained = Math.round(gained * 0.4);
        addGained(crop, gained);
    }

    private static void addGained(String crop, long gained) {
        if (cfg().cropMilestoneHud || cfg().mooshroomPerkHud) CropMilestoneHud.onGain(gained);
        if (!crop.equals(speedCrop)) {
            speedCrop = crop;
            perSecond.clear();
            gainedThisSecond = 0;
        }
        gainedThisSecond += gained;
        long before = counter(crop);
        if (before < 0) return;
        long after = before + gained;
        setCounter(crop, after);
        int oldTier = tierOf(crop, before), newTier = tierOf(crop, after);
        if (newTier > oldTier) CropMilestoneMenu.onTierUp(crop, oldTier, newTier);
    }

    private static void speedSecond() {
        long got = gainedThisSecond;
        gainedThisSecond = 0;
        if (speedCrop == null) return;
        if (System.currentTimeMillis() - lastBreakAt > 5_000) {
            perSecond.clear();
            return;
        }
        perSecond.add(got);
        if (perSecond.size() > 60) perSecond.remove(0);
        if (perSecond.size() < 10) return;
        long sum = 0;
        for (int i = 2; i < perSecond.size(); i++) sum += perSecond.get(i);
        double cps = (double) sum / (perSecond.size() - 2);
        if (cps <= 0) return;
        get().speed.put(speedCrop, cps);
        markDirty();
    }

    private static void readTab() {
        for (String raw : TabListUtils.getTabListLines()) {
            if (raw.indexOf(':') < 0) continue;
            String line = StringUtil.strip(raw);
            Matcher m = TAB.matcher(line);
            if (!m.matches()) continue;
            String crop = cropName(m.group(1));
            if (!CROPS.containsKey(crop)) continue;
            int tier = Integer.parseInt(m.group(2));
            long base = totalForTier(crop, tier);
            long have = counter(crop);
            if (m.group(3) == null) {
                if (have < base) setCounter(crop, base);
                continue;
            }
            double pct;
            try { pct = Double.parseDouble(m.group(3)); } catch (NumberFormatException e) { continue; }
            long step = tierAmount(crop, tier + 1);
            long value = base + (long) (step * pct / 100.0);
            if (have < 0 || Math.abs(have - value) > step / 100) setCounter(crop, value);
        }
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Crop Milestones")) return;
        int v = ContainerState.version();
        if (v == scannedVersion || !ContainerState.fullyOpened(cs.getMenu().containerId)) return;
        scannedVersion = v;
        boolean any = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String crop = null;
            long total = -1;
            for (String l : ItemUtil.loreLines(stack)) {
                String s = StringUtil.stripTrim(l);
                Matcher h = HARVEST.matcher(s);
                if (crop == null && h.matches()) crop = cropName(h.group(1));
                Matcher t = TOTAL.matcher(s);
                if (total < 0 && t.matches()) total = Long.parseLong(t.group(1).replace(",", ""));
            }
            if (crop == null || total < 0 || !CROPS.containsKey(crop)) continue;
            setCounter(crop, total);
            any = true;
        }
        if (!any) return;
        save();
    }

    /** Hypixel's alternative crop names mapped onto the milestone names. */
    public static String cropName(String name) {
        String n = name.trim();
        return switch (n) {
            case "Melon Slice" -> "Melon";
            case "Red Mushroom", "Brown Mushroom" -> "Mushroom";
            default -> n;
        };
    }

    /** Milestone crop of a Crop Milestones item, null for anything else. */
    public static String menuCrop(ItemStack stack) {
        for (String l : ItemUtil.loreLines(stack)) {
            Matcher h = HARVEST.matcher(StringUtil.stripTrim(l));
            if (h.matches()) {
                String crop = cropName(h.group(1));
                return CROPS.containsKey(crop) ? crop : null;
            }
        }
        return null;
    }

    private static void onChat(String raw) {
        String line = StringUtil.strip(raw);
        if (!line.contains("GARDEN MILESTONE")) return;
        Matcher m = LEVEL_UP.matcher(line);
        if (!m.matches()) return;
        String crop = cropName(m.group(1));
        if (!CROPS.containsKey(crop)) return;
        int tier = parseTier(m.group(3));
        if (tier <= 0) return;
        long base = totalForTier(crop, tier);
        if (counter(crop) < base) setCounter(crop, base);
    }

    public static int parseTier(String s) {
        s = s.trim();
        if (s.isEmpty()) return -1;
        if (Character.isDigit(s.charAt(0))) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
        }
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> -1;
            };
            if (v < 0) return -1;
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
    }

    private static final int[] ROMAN_V = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_S = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    public static String toRoman(int n) {
        if (n <= 0) return String.valueOf(n);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_V.length; i++) {
            while (n >= ROMAN_V[i]) { sb.append(ROMAN_S[i]); n -= ROMAN_V[i]; }
        }
        return sb.toString();
    }

    public static String sep(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v);
    }
}
