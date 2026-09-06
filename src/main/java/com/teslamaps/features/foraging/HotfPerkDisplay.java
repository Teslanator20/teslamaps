// Ported from SkyHanni's HotxFeatures.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD readout of the current daily Lottery and Beekeeper effects. */
public class HotfPerkDisplay {

    private static final String[] SAMPLE = { "§bLottery§8: §6+50 Fig Fortune", "§bBeekeeper§8: §a2x §6Honeycomb" };

    public static List<String> lines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        List<String> out = new ArrayList<>();
        if (!c.hotfLotteryPerk.isEmpty()) out.add("§bLottery§8: " + c.hotfLotteryPerk);
        if (!c.hotfBeekeeperPerk.isEmpty()) out.add("§bBeekeeper§8: " + c.hotfBeekeeperPerk);
        return out;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hotfPerkDisplay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (c.hotfPerkDisplayForagingOnly && !Foraging.onForagingIsland()) return;

        List<String> lines = lines();
        if (lines.isEmpty()) return;
        draw(context, mc, c.hotfPerkDisplayX, c.hotfPerkDisplayY, c.hotfPerkDisplayScale, lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        List<String> lines = lines();
        draw(context, mc, x, y, scale, lines.isEmpty() ? List.of(SAMPLE) : lines);
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

    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        List<String> lines = lines();
        if (lines.isEmpty()) lines = List.of(SAMPLE);
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
