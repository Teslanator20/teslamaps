/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.slayer;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Bosses/hour + combat XP/hour tracker. XP comes from the action bar
// ("+163.7 Combat (141,560,940/0)" or "+163.7 Combat (52.30%)"). The gain text
// persists across action bar updates, so gains are deduped via the absolute
// progress value; in percent mode a gain only counts when the display changes.
public class SlayerStats {

    private static final Pattern COMBAT = Pattern.compile("\\+([\\d,.]+)\\s+Combat\\s+\\(([^)]+)\\)");
    private static final long IDLE_CAP_MS = 30_000; // clock pauses after this much inactivity (both rates)

    private static double xpGained = 0;
    private static int bossKills = 0;
    private static int cocoons = 0;
    private static long activeMs = 0;
    private static long lastActivity = 0;
    private static double lastAbs = -1;
    private static String lastSegment = "";

    public static void onActionBar(String raw) {
        if (!TeslaMapsConfig.get().slayerStats) return;
        String text = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");
        Matcher m = COMBAT.matcher(text);
        if (!m.find()) return;
        double gain = parseNum(m.group(1));
        String paren = m.group(2);
        String segment = m.group();
        if (paren.contains("/")) {
            double abs = parseNum(paren.substring(0, paren.indexOf('/')));
            if (lastAbs >= 0) {
                double d = abs - lastAbs;
                if (d > 0) addXp(d);
                else if (d < 0) addXp(gain); // level-up rollover
            } else {
                addXp(gain);
            }
            lastAbs = abs;
        } else if (!segment.equals(lastSegment)) {
            addXp(gain);
        }
        lastSegment = segment;
    }

    public static void reset() {
        xpGained = 0;
        bossKills = 0;
        cocoons = 0;
        activeMs = 0;
        lastActivity = 0;
        lastAbs = -1;
        lastSegment = "";
    }

    public static void onBossKill() {
        bossKills++;
        touch();
    }

    public static void onCocoon() {
        cocoons++;
        touch();
    }

    public static double bossesPerHour() {
        double h = hours();
        return h > 0 ? bossKills / h : 0;
    }

    private static void addXp(double xp) {
        xpGained += xp;
        touch();
    }

    private static void touch() {
        long now = System.currentTimeMillis();
        if (lastActivity > 0) activeMs += Math.min(now - lastActivity, IDLE_CAP_MS);
        lastActivity = now;
    }

    private static long liveActiveMs() {
        if (lastActivity == 0) return 0;
        return activeMs + Math.min(System.currentTimeMillis() - lastActivity, IDLE_CAP_MS);
    }

    private static double hours() {
        return liveActiveMs() / 3_600_000.0;
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.slayerStats) return;
        if (bossKills == 0 && xpGained <= 0) return;
        if (System.currentTimeMillis() - lastActivity > 10 * 60_000) return; // fade out when idle

        Minecraft mc = Minecraft.getInstance();
        int x = cfg.slayerStatsX, y = cfg.slayerStatsY;
        float scale = cfg.slayerStatsScale;
        boolean warmup = liveActiveMs() < 30_000;
        double h = hours();

        String bph = warmup || h <= 0 ? "…" : String.format("%.1f", bossKills / h);
        String xph = warmup || h <= 0 ? "…" : fmt(xpGained / h);
        String cocoon = cocoons + "§8/§e" + bossKills;
        if (bossKills > 0) cocoon += " §8(" + Math.round(100.0 * cocoons / bossKills) + "%)";
        draw(ctx, mc, x, y, scale, lines(bph, bossKills, cocoon, xph, fmt(xpGained)));
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        draw(ctx, mc, x, y, scale, sampleLines());
    }

    private static String[] sampleLines() {
        return lines("14.2", 7, "6§8/§e7 §8(86%)", "2.31M", "890k");
    }

    private static String[] lines(String bph, int kills, String cocoon, String xph, String total) {
        return new String[]{
            "§c§lSlayer",
            "§7Bosses/h: §e" + bph + " §8(" + kills + ")",
            "§7Cocoon: §a" + cocoon,
            "§7Combat/h: §b" + xph + " §8(" + total + ")"
        };
    }

    // background sized to the widest line so nothing overflows the box
    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String[] ls) {
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.fill(-4, -4, w + 4, ls.length * 10, 0xB0000000);
        for (int i = 0; i < ls.length; i++) ctx.text(mc.font, ls[i], 0, i * 10, 0xFFFFFFFF);
        pose.popMatrix();
    }

    // unscaled box size (incl. padding) for the HUD editor
    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        String[] ls = sampleLines();
        int w = 0;
        for (String l : ls) w = Math.max(w, font.width(l));
        return new int[]{w + 8, ls.length * 10 + 4};
    }

    private static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    private static double parseNum(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
