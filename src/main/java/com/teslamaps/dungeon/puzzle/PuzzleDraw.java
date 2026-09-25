package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EspStyle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Per-solver box style: ESP Default follows the global filled-ESP switch. */
public final class PuzzleDraw {

    public static final String ESP_DEFAULT = "ESP Default";
    public static final String[] STYLES = {EspStyle.BOTH, EspStyle.FILLED, EspStyle.OUTLINE};
    public static final String[] STYLES_WITH_DEFAULT = {ESP_DEFAULT, EspStyle.BOTH, EspStyle.FILLED, EspStyle.OUTLINE};

    private PuzzleDraw() {}

    public static void box(PoseStack pose, AABB box, int argb, String style, float lineWidth, Vec3 cam, boolean throughWalls) {
        if (style == null || ESP_DEFAULT.equals(style)) {
            // like drawESPBox
            if (com.teslamaps.config.TeslaMapsConfig.get().filledESP) ESPRenderer.drawFilledBox(pose, box, argb, cam, throughWalls);
            else ESPRenderer.drawBoxOutline(pose, box, argb, 3.0f, cam, throughWalls);
            return;
        }
        boolean fill = EspStyle.FILLED.equals(style) || EspStyle.BOTH.equals(style);
        boolean outline = !EspStyle.FILLED.equals(style);
        if (fill) ESPRenderer.drawFilledBox(pose, box, argb, cam, throughWalls);
        if (outline) ESPRenderer.drawBoxOutline(pose, box, argb | 0xFF000000, lineWidth, cam, throughWalls);
    }

    /** Scales the alpha byte, 1 = unchanged. */
    public static int alpha(int argb, float factor) {
        if (factor >= 1f) return argb;
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, factor));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
