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
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// Standalone kills/hour + combat XP/hour tracker, independent of slayers.
// Kills come from the held item's lore counter ("Kills: 13,714"), XP from the
// action bar like SlayerStats. Own idle-capped clock so AFK doesn't dilute rates.
public class CombatTracker {

    private static final Pattern COMBAT = Pattern.compile("\\+([\\d,.]+)\\s+Combat\\s+\\(([^)]+)\\)");
    private static final Pattern KILLS = Pattern.compile("Kills: ([\\d,]+)");
    private static final long IDLE_CAP_MS = 30_000;

    private static double xpGained = 0;
    private static long killsGained = 0;
    private static long activeMs = 0;
    private static long lastActivity = 0;
    private static double lastAbs = -1;
    private static String lastSegment = "";
    private static String lastItemId = "";
    private static long lastKills = -1;
    private static int tickCounter = 0;

    public static void reset() {
        xpGained = 0;
        killsGained = 0;
        activeMs = 0;
        lastActivity = 0;
        lastAbs = -1;
        lastSegment = "";
        lastItemId = "";
        lastKills = -1;
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().combatTracker) return;
        if (++tickCounter % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        long kills = readKills(held);
        if (kills < 0) return;

        String id = ItemUtil.skyblockId(held);
        if (id.equals(lastItemId) && lastKills >= 0) {
            long d = kills - lastKills;
            if (d > 0) {
                killsGained += d;
                touch();
            }
        }
        lastItemId = id;
        lastKills = kills;
    }

    private static long readKills(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            String text = line.getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
            Matcher m = KILLS.matcher(text);
            if (m.find()) return parseLong(m.group(1));
        }
        return -1;
    }

    // same dedup as SlayerStats: gain text persists across action bar updates,
    // so count via the absolute progress value / display change
    public static void onActionBar(String raw) {
        if (!TeslaMapsConfig.get().combatTracker) return;
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
        if (!cfg.combatTracker) return;
        if (killsGained == 0 && xpGained <= 0) return;
        if (System.currentTimeMillis() - lastActivity > 10 * 60_000) return; // fade out when idle

        Minecraft mc = Minecraft.getInstance();
        boolean warmup = liveActiveMs() < 30_000;
        double h = hours();

        String kph = warmup || h <= 0 ? "…" : fmt(killsGained / h);
        String xph = warmup || h <= 0 ? "…" : fmt(xpGained / h);
        draw(ctx, mc, cfg.combatTrackerX, cfg.combatTrackerY, cfg.combatTrackerScale,
                lines(kph, killsGained, xph, fmt(xpGained)));
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        draw(ctx, mc, x, y, scale, sampleLines());
    }

    private static String[] sampleLines() {
        return lines("4.2k", 1337, "2.31M", "890k");
    }

    private static String[] lines(String kph, long kills, String xph, String total) {
        return new String[]{
            "§6§lCombat",
            "§7Kills/h: §e" + kph + " §8(" + kills + ")",
            "§7Combat/h: §b" + xph + " §8(" + total + ")"
        };
    }

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

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
