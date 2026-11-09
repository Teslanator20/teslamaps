package com.teslamaps.features;

// Crop data after SkyHanni's CropType, CropMilestonesApi, GardenCropSpeed and FarmingFortuneDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.garden.GardenCropData;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared per-crop data: latest blocks/s and latest farming fortune; milestone counters live in GardenMilestoneData. */
public final class CropStats {

    public record Crop(String key, String name, double baseDrops, String eliteLb, String[] aliases) {}

    public static final List<Crop> CROPS = List.of(
            new Crop("WHEAT", "Wheat", 1.0, "wheat", new String[]{"Seeds"}),
            new Crop("CARROT_ITEM", "Carrot", 3.0, "carrot", new String[]{}),
            new Crop("POTATO_ITEM", "Potato", 3.0, "potato", new String[]{}),
            new Crop("PUMPKIN", "Pumpkin", 1.0, "pumpkin", new String[]{}),
            new Crop("MELON", "Melon", 5.0, "melon", new String[]{"Melon Slice"}),
            new Crop("SUGAR_CANE", "Sugar Cane", 2.0, "sugarcane", new String[]{"Cane"}),
            new Crop("CACTUS", "Cactus", 2.0, "cactus", new String[]{}),
            new Crop("INK_SACK:3", "Cocoa Beans", 3.0, "cocoa", new String[]{"Cocoa"}),
            new Crop("NETHER_STALK", "Nether Wart", 2.5, "netherwart", new String[]{"Wart", "Warts"}),
            new Crop("MUSHROOM_COLLECTION", "Mushroom", 1.0, "mushroom", new String[]{"Red Mushroom", "Brown Mushroom"}),
            new Crop("DOUBLE_PLANT", "Sunflower", 2.0, "sunflower", new String[]{}),
            new Crop("MOONFLOWER", "Moonflower", 2.0, "moonflower", new String[]{}),
            new Crop("WILD_ROSE", "Wild Rose", 2.0, "wildrose", new String[]{"Rose"}));

    private static final long STRETCH_GAP_MS = 2000L;
    private static final long STRETCH_MIN_MS = 10_000L;

    private static String curCrop, lastCrop;
    private static long stretchStart, lastBreak, lastLiveSave;
    private static int stretchBlocks;
    private static int ticks;

    private CropStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static boolean wanted() {
        TeslaMapsConfig c = cfg();
        return c.cropCalcCommands || c.jacobContestFF
                || GardenCropData.needed();
    }

    public static void register() {
        Feature.of("CropStats").section("Garden").when(CropStats::wanted).tick(CropStats::tick);
    }

    public static Crop byKey(String key) {
        for (Crop c : CROPS) if (c.key.equals(key)) return c;
        return null;
    }

    public static Crop byName(String name) {
        String n = name.trim();
        for (Crop c : CROPS) {
            if (c.name.equalsIgnoreCase(n) || c.key.equalsIgnoreCase(n) || c.eliteLb.equalsIgnoreCase(n.replace(" ", ""))) return c;
            for (String a : c.aliases) if (a.equalsIgnoreCase(n)) return c;
        }
        return null;
    }

    public static Crop byBlock(BlockState state, long dayTime) {
        Block b = state.getBlock();
        String key;
        if (b == Blocks.WHEAT) key = "WHEAT";
        else if (b == Blocks.CARROTS) key = "CARROT_ITEM";
        else if (b == Blocks.POTATOES) key = "POTATO_ITEM";
        else if (b == Blocks.PUMPKIN || b == Blocks.CARVED_PUMPKIN) key = "PUMPKIN";
        else if (b == Blocks.MELON) key = "MELON";
        else if (b == Blocks.SUGAR_CANE) key = "SUGAR_CANE";
        else if (b == Blocks.CACTUS) key = "CACTUS";
        else if (b == Blocks.COCOA) key = "INK_SACK:3";
        else if (b == Blocks.NETHER_WART) key = "NETHER_STALK";
        else if (b == Blocks.RED_MUSHROOM || b == Blocks.BROWN_MUSHROOM
                || b == Blocks.RED_MUSHROOM_BLOCK || b == Blocks.BROWN_MUSHROOM_BLOCK) key = "MUSHROOM_COLLECTION";
        else if (b == Blocks.ROSE_BUSH) key = "WILD_ROSE";
        else if (b == Blocks.SUNFLOWER) key = dayTime % 24_000L >= 12_000L ? "MOONFLOWER" : "DOUBLE_PLANT";
        else return null;
        return byKey(key);
    }

    /** Called from FarmSpeed for every crop the player broke in the Garden. */
    public static void onCropBreak(BlockState state, long dayTime) {
        if (!wanted()) return;
        Crop crop = byBlock(state, dayTime);
        if (crop == null) return;
        long now = System.currentTimeMillis();
        if (!crop.key.equals(curCrop) || now - lastBreak > STRETCH_GAP_MS) {
            finishStretch();
            curCrop = crop.key;
            stretchStart = now;
            stretchBlocks = 0;
        }
        stretchBlocks++;
        lastBreak = now;
        lastCrop = crop.key;
    }

    private static void finishStretch() {
        if (curCrop == null) return;
        long span = lastBreak - stretchStart;
        if (span >= STRETCH_MIN_MS && stretchBlocks > 1) {
            double bps = Math.round(stretchBlocks * 100_000.0 / span) / 100.0;
            cfg().cropLatestBps.put(curCrop, bps);
            TeslaMapsConfig.save();
        }
        curCrop = null;
    }

    private static void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        if (curCrop != null && now - lastBreak > STRETCH_GAP_MS) finishStretch();
        if (curCrop != null && ticks % 20 == 0) {
            readTabFortune(curCrop);
            long span = lastBreak - stretchStart;
            if (span >= STRETCH_MIN_MS && now - lastLiveSave > 5000L) {
                lastLiveSave = now;
                cfg().cropLatestBps.put(curCrop, Math.round(stretchBlocks * 100_000.0 / span) / 100.0);
            }
        }
    }

    private static void readTabFortune(String key) {
        Crop crop = byKey(key);
        if (crop == null) return;
        double universal = -1, specific = 0;
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            int i = l.indexOf(" Fortune: ");
            if (i <= 0) continue;
            String stat = l.substring(0, i);
            double v = number(l.substring(i + 10));
            if (Double.isNaN(v)) continue;
            if (stat.equals("Farming")) universal = v;
            else if (crop == byName(stat)) specific = v;
        }
        if (universal >= 0) cfg().cropLatestFortune.put(key, universal + specific);
    }

    private static double number(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch) || ch == '.') b.append(ch);
            else if (ch == ',' || b.isEmpty()) continue;
            else break;
        }
        if (b.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(b.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Crop broken most recently, or null. */
    public static Crop lastCrop() { return lastCrop == null ? null : byKey(lastCrop); }

    /** True while crops are being broken right now. */
    public static boolean farming() { return System.currentTimeMillis() - lastBreak < 3000L; }

    public static Double latestBps(Crop c) { return cfg().cropLatestBps.get(c.key); }

    public static Double latestFortune(Crop c) { return cfg().cropLatestFortune.get(c.key); }

    /** Crops per broken block: base drops scaled by the latest fortune. */
    public static Double cropsPerBlock(Crop c) {
        Double ff = latestFortune(c);
        return ff == null ? null : c.baseDrops * (100.0 + ff) / 100.0;
    }

    /** Crops per second while farming this crop, null without speed data. */
    public static Double cropsPerSecond(Crop c) {
        Double bps = latestBps(c);
        Double per = cropsPerBlock(c);
        if (bps == null || per == null || bps <= 0) return null;
        return bps * per;
    }

    public static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 || Math.abs(v) >= 1000) return String.format(Locale.ROOT, "%,d", Math.round(v));
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    public static String duration(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (d > 0 || h > 0) b.append(h).append("h ");
        if (d == 0 && (h > 0 || m > 0)) b.append(m).append("m ");
        if (d == 0 && h == 0) b.append(sec).append("s");
        return b.toString().trim();
    }

    public static int linesWidth(List<String> lines) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    public static int linesHeight(int n) {
        return n * 10 + 6;
    }

    public static void drawLines(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> lines, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, linesHeight(lines.size()));
        for (int i = 0; i < lines.size(); i++) g.text(mc.font, lines.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    static Map<String, Long> milestones() {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, Long> e : GardenMilestoneData.get().counters.entrySet()) {
            String key = GardenMilestoneData.CROPS.get(e.getKey());
            if (key != null && e.getValue() != null) out.put(key, e.getValue());
        }
        return out;
    }
}
