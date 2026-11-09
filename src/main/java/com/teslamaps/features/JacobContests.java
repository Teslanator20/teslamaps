package com.teslamaps.features;

// Ported from SkyHanni's GardenNextJacobContest.kt and Skyblocker's JacobsContestWidget.java
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Crops of the running / next Jacob's contest, placement from the tab widget, and a warning for chosen crops. */
public final class JacobContests {

    public static final String[] CROPS = {"Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cactus",
            "Sugar Cane", "Nether Wart", "Cocoa Beans", "Sunflower", "Moonflower", "Wild Rose"};

    private static final long SB_EPOCH = 1559829300000L;
    private static final long SB_YEAR = 124L * 60 * 60 * 1000;
    private static final long SB_MONTH = SB_YEAR / 12;
    private static final long SB_DAY = 20L * 60 * 1000;
    private static final long DURATION = 20L * 60 * 1000;
    private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer",
            "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

    private static final Pattern MONTH = Pattern.compile("((?:Early |Late )?(?:Spring|Summer|Autumn|Winter)), Year (\\d+)");
    private static final Pattern DAY = Pattern.compile("Day (\\d+)");
    private static final Pattern TOP = Pattern.compile("Top ([\\d.]+%)");
    private static final Pattern SCORE_CROP = Pattern.compile("^\\s*[○\\uE051☘] (.+) \\S+$");

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("jacob_contests.json");

    private static final TreeMap<Long, String[]> contests = new TreeMap<>();
    private static final Map<Long, String> boosted = new java.util.HashMap<>();
    private static boolean loaded;
    private static int ticks;
    private static long warnedStart = Long.MIN_VALUE;
    private static Screen scannedScreen;
    private static int scanTicks;

    private static String placement;
    private static String placementCrop;
    private static String currentCrop;

    private JacobContests() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("JacobContests").section("Contest Timers")
                .when(() -> cfg().contestCrops || cfg().contestCropWarn)
                .tick(JacobContests::tick);
    }

    /** Crop being scored in the running contest (scoreboard), or null. */
    public static String currentCrop() {
        return currentCrop;
    }

    private static void tick() {
        if (!loaded) load();
        scanCalendar();
        if (++ticks < 20) return;
        ticks = 0;
        long now = System.currentTimeMillis();
        readScoreboard();
        readTab(now);
        warn(now);
    }

    // --- sources ---

    public static void readScoreboard() {
        String crop = null;
        boolean contest = false;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = StringUtil.strip(raw);
            if (line.contains("Jacob's Contest")) contest = true;
            Matcher m = SCORE_CROP.matcher(line);
            if (m.matches()) crop = cropName(m.group(1).trim());
        }
        currentCrop = contest ? crop : null;
    }

    private static void readTab(long now) {
        List<String> lines = TabListUtils.getOrderedLines();
        boolean in = false;
        List<String> crops = new ArrayList<>(3);
        String boost = null;
        String top = null;
        String topCrop = null;
        for (String raw : lines) {
            String line = StringUtil.stripTrim(raw);
            if (!in) {
                if (line.startsWith("Jacob's Contest")) in = true;
                continue;
            }
            if (line.isEmpty() || line.endsWith(":")) break;
            if (line.startsWith("Starts")) continue;
            char icon = line.charAt(0);
            if (line.length() < 3 || line.charAt(1) != ' ') continue;
            String rest = line.substring(2);
            Matcher t = TOP.matcher(rest);
            String pct = t.find() ? t.group(1) : null;
            int cut = rest.indexOf('◆');
            if (cut >= 0) rest = rest.substring(0, cut);
            String crop = cropName(rest.trim());
            if (crop == null) continue;
            crops.add(crop);
            if (icon != '○') boost = crop;
            if (pct != null) { top = pct; topCrop = crop; }
        }
        placement = top;
        placementCrop = topCrop;
        if (crops.size() != 3) return;
        long start = slot(now);
        String[] have = contests.get(start);
        if (have == null || !java.util.Arrays.equals(have, crops.toArray(new String[0]))) {
            contests.put(start, crops.toArray(new String[0]));
            save();
        }
        if (boost != null) boosted.put(start, boost);
    }

    /** Start of the running contest, or of the next one. */
    private static long slot(long now) {
        long hour = 60 * 60_000L;
        long start = Math.floorDiv(now - 15 * 60_000L, hour) * hour + 15 * 60_000L;
        return now - start < DURATION ? start : start + hour;
    }

    private static void scanCalendar() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ContainerScreen screen)) { scannedScreen = null; return; }
        if (screen != scannedScreen) { scannedScreen = screen; scanTicks = 0; }
        if (scanTicks++ % 20 != 10) return;
        Matcher title = MONTH.matcher(StringUtil.strip(screen.getTitle().getString()));
        if (!title.find()) return;
        int month = monthIndex(title.group(1));
        int year = Integer.parseInt(title.group(2));
        if (month < 0) return;
        boolean changed = false;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher d = DAY.matcher(StringUtil.strip(stack.getHoverName().getString()));
            if (!d.matches()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            boolean jacob = false;
            List<String> crops = new ArrayList<>(3);
            String boost = null;
            for (String l : lore) {
                String line = StringUtil.stripTrim(l);
                if (line.contains("Jacob's Farming Contest")) { jacob = true; continue; }
                if (!jacob || line.length() < 3 || line.charAt(1) != ' ') continue;
                String crop = cropName(line.substring(2).trim());
                if (crop == null || crops.contains(crop)) continue;
                crops.add(crop);
                if (line.charAt(0) != '○') boost = crop;
            }
            if (!jacob || crops.size() != 3) continue;
            long start = SB_EPOCH + year * SB_YEAR + month * SB_MONTH + (Integer.parseInt(d.group(1)) - 1) * SB_DAY;
            if (contests.containsKey(start)) continue;
            contests.put(start, crops.toArray(new String[0]));
            if (boost != null) boosted.put(start, boost);
            changed = true;
        }
        if (changed) save();
    }

    private static int monthIndex(String name) {
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(name)) return i;
        return -1;
    }

    static String cropName(String text) {
        if (text.equals("Melon Slice")) return "Melon";
        if (text.equals("Red Mushroom") || text.equals("Brown Mushroom")) return "Mushroom";
        for (String c : CROPS) if (c.equalsIgnoreCase(text)) return c;
        return null;
    }

    // --- output ---

    private static Map.Entry<Long, String[]> next(long now) {
        Map.Entry<Long, String[]> e = contests.ceilingEntry(now - DURATION + 1);
        if (e == null || e.getKey() - now > 4 * SB_DAY) return null;
        return e;
    }

    private static String cropList(long start, String[] crops) {
        String boost = boosted.get(start);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < crops.length; i++) {
            if (i > 0) sb.append("§7, ");
            sb.append(crops[i].equals(boost) ? "§6" : "§a").append(crops[i]);
        }
        return sb.toString();
    }

    /** Coloured crop list of the contest starting at that time, null when unknown. */
    public static String cropsFor(long start) {
        if (!loaded) load();
        String[] crops = contests.get(start);
        return crops == null ? null : cropList(start, crops);
    }

    /** Extra lines for the Contest Timers HUD, built once a second there. */
    public static void appendLines(List<String> out, long now) {
        TeslaMapsConfig c = cfg();
        if (!c.contestCrops || !c.section("Contest Timers")) return;
        if (!loaded) load();
        Map.Entry<Long, String[]> e = next(now);
        if (e == null) {
            out.add(" §8Crops: open /calendar");
            return;
        }
        out.add(" §7" + cropList(e.getKey(), e.getValue()));
        boolean live = now >= e.getKey();
        if (live && c.contestCropsPlacement && placement != null) {
            out.add(" §7You: §eTop " + placement + (placementCrop == null ? "" : " §8(" + placementCrop + ")"));
        }
    }

    private static void warn(long now) {
        TeslaMapsConfig c = cfg();
        if (!c.contestCropWarn || !SkyblockUtils.isInSkyblock()) return;
        Map.Entry<Long, String[]> e = next(now);
        if (e == null) return;
        long start = e.getKey();
        long left = start - now;
        if (left <= 0 || left > c.contestCropWarnSeconds * 1000L || warnedStart == start) return;
        if (!c.contestCropWarnFor.isEmpty()) {
            boolean any = false;
            for (String crop : e.getValue()) any |= c.contestCropWarnFor.contains(crop);
            if (!any) return;
        }
        warnedStart = start;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String crops = cropList(start, e.getValue());
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §eNext farming contest in §f"
                + (left / 1000 / 60) + "m§e: " + crops));
        mc.gui.hud.setTimes(0, 50, 10);
        mc.gui.hud.setTitle(Component.literal("§eFarming Contest!"));
        mc.gui.hud.setSubtitle(Component.literal(crops));
        if (c.contestAlertVolume > 0f) LoudSound.play(SoundOptions.resolve(c.contestAlertSound), c.contestAlertVolume, 1.0f);
        if (c.contestCropWarnFlash && !mc.isWindowActive() && mc.getWindow() != null) {
            org.lwjgl.sdl.SDLVideo.SDL_FlashWindow(mc.getWindow().handle(), org.lwjgl.sdl.SDLVideo.SDL_FLASH_BRIEFLY);
        }
    }

    // --- storage ---

    private static void load() {
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            long cutoff = System.currentTimeMillis() - SB_YEAR;
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                long start = Long.parseLong(e.getKey());
                if (start < cutoff) continue;
                List<String> crops = new ArrayList<>(3);
                for (JsonElement c : e.getValue().getAsJsonArray()) crops.add(c.getAsString());
                if (crops.size() == 3) contests.put(start, crops.toArray(new String[0]));
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Jacob] load failed: {}", e.toString());
        }
    }

    private static void save() {
        long cutoff = System.currentTimeMillis() - DURATION;
        contests.headMap(cutoff).clear();
        JsonObject o = new JsonObject();
        for (Map.Entry<Long, String[]> e : contests.entrySet()) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (String c : e.getValue()) a.add(c);
            o.add(Long.toString(e.getKey()), a);
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, o.toString());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Jacob] save failed: {}", e.toString());
        }
    }
}
