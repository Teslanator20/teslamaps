package com.teslamaps.features;

// Ported from SkyHanni's GardenBestCropTime.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Crops ranked by time to their next milestone at the measured speed. */
public final class BestCropTime {

    public static final String[] TYPES = {"SkyBlock Level", "Garden XP"};
    private static final int LINE = 10;

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width = 100;
    private static int ticks;

    private BestCropTime() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("bestCropTime", "Best Crop Time").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().bestCropTime)
                .size(() -> width + 4, () -> Math.max(1, lines.size()) * LINE)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of(
                        "§eBest Crop Time §7(§bSkyBlock Level§7)",
                        "§71# §eWheat 30➜31 §b12m 30s",
                        "§72# §7§nCarrot§7 28➜29 §b25m 3s")));
        Feature.of("BestCropTime").section("Garden").when(() -> cfg().bestCropTime)
                .tick(BestCropTime::tick)
                .hudHiddenInInventory("bestcroptime", BestCropTime::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crop Milestones");
                    b.toggle("Best Crop Time", () -> c.bestCropTime, v -> c.bestCropTime = v)
                            .desc("Ranks every crop by the time to its next milestone at your measured speed. "
                                    + "Farm each crop a bit and open Crop Milestones once to fill it in.")
                            .tags("milestone", "skyblock xp", "garden xp");
                    b.dropdown("  Rank By", TYPES, () -> c.bestCropTimeType, v -> c.bestCropTimeType = v);
                    b.slider("  Show Best", 1f, 13f, () -> (float) c.bestCropTimeTop,
                            v -> c.bestCropTimeTop = Math.round(v), 0);
                    b.toggle("  Always Show Current Crop", () -> c.bestCropTimeCurrent, v -> c.bestCropTimeCurrent = v);
                    b.toggle("  Compact", () -> c.bestCropTimeCompact, v -> c.bestCropTimeCompact = v);
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        build();
    }

    private static void build() {
        TeslaMapsConfig c = cfg();
        boolean gardenXp = "Garden XP".equals(c.bestCropTimeType);
        boolean compact = c.bestCropTimeCompact;
        String current = GardenMilestoneData.currentCrop();
        Set<String> contest = contestCrops();

        record Row(String crop, int tier, double seconds, double sort) {}
        List<Row> rows = new ArrayList<>();
        boolean anyKnown = false, allMaxed = true;
        for (String crop : GardenMilestoneData.CROPS.keySet()) {
            long count = GardenMilestoneData.counter(crop);
            if (count < 0) { allMaxed = false; continue; }
            anyKnown = true;
            int tier = GardenMilestoneData.tierOf(crop, count);
            if (tier < GardenMilestoneData.maxTier(crop)) allMaxed = false;
            else continue;
            double speed = GardenMilestoneData.speed(crop);
            if (speed <= 0) continue;
            long missing = GardenMilestoneData.totalForTier(crop, tier + 1) - count;
            double seconds = missing / speed;
            double sort = gardenXp ? seconds / gardenXpFor(tier + 1) : seconds;
            rows.add(new Row(crop, tier, seconds, sort));
        }
        rows.sort((a, b) -> Double.compare(a.sort, b.sort));

        List<String> out = new ArrayList<>();
        out.add(compact ? "§eBest Crop Time" : "§eBest Crop Time §7(" + (gardenXp ? "§2Garden Experience" : "§bSkyBlock Level") + "§7)");
        if (anyKnown && allMaxed) out.add("§eAll Crops Maxed!");
        else if (rows.isEmpty()) out.add(anyKnown ? "§cFarm crops to add them to this list!" : "§cOpen Crop Milestones in the desk once");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean isCurrent = r.crop.equals(current);
            if (i >= c.bestCropTimeTop && !(c.bestCropTimeCurrent && isCurrent)) continue;
            StringBuilder sb = new StringBuilder();
            if (!compact) sb.append("§7").append(i + 1).append("# ");
            sb.append(isCurrent ? "§e" : "§7");
            if (contest.contains(r.crop)) sb.append("§n");
            if (!compact) sb.append(r.crop).append(isCurrent ? "§e" : "§7").append(' ').append(r.tier).append('➜').append(r.tier + 1).append("§r ");
            else sb.append(r.crop).append("§r ");
            sb.append("§b").append(duration(r.seconds));
            if (gardenXp && !compact) sb.append(" §7(§2").append(gardenXpFor(r.tier + 1)).append(" §7Exp)");
            out.add(sb.toString());
        }
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        width = w;
        lines = out;
    }

    private static int gardenXpFor(int tier) {
        return tier > 30 ? 300 : tier * 10;
    }

    /** Crops listed under the Jacob's Contest block of the tab list. */
    private static Set<String> contestCrops() {
        Set<String> out = new HashSet<>();
        List<String> tab = TabListUtils.getOrderedLines();
        for (int i = 0; i < tab.size(); i++) {
            if (!tab.get(i).contains("Jacob's Contest")) continue;
            for (int j = i + 1; j < Math.min(tab.size(), i + 5); j++) {
                String l = StringUtil.stripTrim(tab.get(j));
                for (String crop : GardenMilestoneData.CROPS.keySet()) if (l.contains(crop)) out.add(crop);
            }
            break;
        }
        return out;
    }

    private static String duration(double seconds) {
        long s = (long) seconds;
        long d = s / 86400, h = s / 3600 % 24, m = s / 60 % 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty() || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, mc.font.width(s));
        draw(g, mc, x, y, scale, l, w);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
