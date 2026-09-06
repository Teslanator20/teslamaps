package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD with the remaining Honeycomb lather time per tree. */
public class LatherOverlay {

    private static final String HEADER = "§6Honeycomb";

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(HEADER);
        for (LatherReminder.Tree tree : LatherReminder.Tree.values()) {
            long left = LatherReminder.secondsLeft(tree);
            String value = left < 0 ? "§7ready" : color(left) + String.format("%d:%02d", left / 60, left % 60);
            out.add(tree.color + tree.name + "§8: " + value);
        }
        return out;
    }

    private static String color(long seconds) {
        if (seconds <= 30) return "§c";
        if (seconds <= 120) return "§e";
        return "§a";
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.latherOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (c.latherOverlayForagingOnly && !Foraging.onForagingIsland()) return;
        if (c.latherOverlayOnlyWhenActive && !LatherReminder.anyRunning()) return;

        draw(context, mc, c.latherOverlayX, c.latherOverlayY, c.latherOverlayScale, lines());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, lines());
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
        List<String> lines = lines();
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
