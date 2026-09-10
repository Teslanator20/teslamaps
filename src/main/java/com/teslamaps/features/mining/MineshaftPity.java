package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Glacite Mineshaft pity counter, read off the "Pity:" tab widget.
 * Needs the widget enabled via /tab > Pity > Glacite Tunnels.
 */
public class MineshaftPity {

    public static final int MAX = 2000;

    private static final Pattern PITY = Pattern.compile("Glacite Mineshafts: ([\\d,]+)/[\\d,]+");
    private static final String FOUND = "You found a Glacite Mineshaft";

    private static final String[] SAMPLE = {
            "§9§lMineshaft Pity", "§3Pity: §e561§6/§e2000", "§3Chance: §e1§6/§e1439 §7(0.069%)"
    };

    private static final String[] MISSING = {
            "§9§lMineshaft Pity", "§cPity tab widget missing",
            "§c/tab §7> §cPity §7> §cGlacite Tunnels"
    };

    private static int progress = -1;   // -1 while the widget was never seen
    private static int sessionFound = 0;
    private static long lastFound = 0;
    private static int ticks = 0;

    public static void tick() {
        if (++ticks < 10) return;
        ticks = 0;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.mineshaftPityDisplay && !c.mineshaftFoundChat) return;
        if (!Mining.onMiningIsland()) return;

        for (String raw : TabListUtils.getTabListLines()) {
            Matcher m = PITY.matcher(StringUtil.stripTrim(raw));
            if (m.find()) {
                progress = Integer.parseInt(m.group(1).replace(",", ""));
                return;
            }
        }
    }

    public static void onChatMessage(String text) {
        if (!StringUtil.strip(text).contains(FOUND)) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        sessionFound++;
        c.mineshaftTotalFound++;
        TeslaMapsConfig.save();

        if (c.mineshaftFoundChat) {
            StringBuilder sb = new StringBuilder("§b[TeslaMaps] §7Mineshaft at pity §e");
            sb.append(progress < 0 ? "?" : progress).append("§7/§e").append(MAX);
            if (progress >= 0) sb.append(" §7(§e1§6/§e").append(String.format("%.1f", chance())).append("§7)");
            if (lastFound > 0) sb.append(" §7after §b").append(time(System.currentTimeMillis() - lastFound));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(sb.toString()));
        }

        lastFound = System.currentTimeMillis();
        progress = 0;
    }

    /** Denominator of the spawn chance, so 1439.0 means 1/1439 per block. */
    private static double chance() {
        int left = Math.max(1, MAX - Math.max(0, progress));
        return left / (1 + TeslaMapsConfig.get().mineshaftChanceBonus / 100.0);
    }

    private static String time(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    public static List<String> lines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (progress < 0) return List.of(MISSING);

        List<String> out = new ArrayList<>();
        out.add("§9§lMineshaft Pity");
        out.add("§3Pity: §e" + progress + "§6/§e" + MAX);
        double chance = chance();
        out.add("§3Chance: §e1§6/§e" + String.format("%.1f", chance)
                + " §7(" + String.format("%.3f", 100.0 / chance) + "%)");
        if (lastFound > 0) out.add("§3Last: §e" + time(System.currentTimeMillis() - lastFound));
        if (sessionFound > 0 || c.mineshaftTotalFound > 0) {
            out.add("§3Found: §e" + sessionFound + " §7session §8| §e" + c.mineshaftTotalFound + " §7total");
        }
        return out;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.mineshaftPityDisplay) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (!Mining.onMiningIsland()) return;

        draw(context, Minecraft.getInstance(), c.mineshaftPityX, c.mineshaftPityY, c.mineshaftPityScale, lines());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, progress < 0 ? List.of(SAMPLE) : lines());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
            row++;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(Font font) {
        List<String> lines = progress < 0 ? List.of(SAMPLE) : lines();
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }

    public static void resetStats() {
        sessionFound = 0;
        lastFound = 0;
        TeslaMapsConfig.get().mineshaftTotalFound = 0;
        TeslaMapsConfig.save();
    }
}
