package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SprintingOverlay {

    public static final String SAMPLE_TEXT = "Not Sprinting"; // widest state, for HUD-editor sizing/preview

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().sprintingOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

        boolean sprinting = mc.player.isSprinting();
        String text = (sprinting ? "§aSprinting" : "§cNot Sprinting");
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, mc, config.sprintingX, config.sprintingY, config.sprintingScale, text);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, "§aSprinting");
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
