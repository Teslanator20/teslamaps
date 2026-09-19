package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Vanilla only clamps a tooltip against the bottom of the screen, so a long SkyBlock item
 * (rods, bows, anything with a full enchant block) runs off the top and the enchants are gone.
 * Shrink it until it fits instead.
 */
public class TooltipFit {

    private static final float MIN_SCALE = 0.4f;

    private static float scale = 1f;

    /** Height vanilla computes for the same list, plus the background padding. */
    public static float begin(Font font, List<ClientTooltipComponent> lines, int guiHeight) {
        scale = 1f;
        if (!TeslaMapsConfig.get().tooltipFitScreen || lines.isEmpty()) return 1f;

        int height = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : lines) height += line.getHeight(font);
        height += 8;

        if (height <= guiHeight) return 1f;
        scale = Math.max(MIN_SCALE, (float) guiHeight / height);
        return scale;
    }

    public static float scale() {
        return scale;
    }

    /** Positions in the scaled space, so the tooltip still lands next to the cursor. */
    public static ClientTooltipPositioner wrap(ClientTooltipPositioner base) {
        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            float f = scale;
            if (f >= 1f) return base.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height);
            Vector2ic pos = base.positionTooltip(
                    Math.round(screenWidth / f), Math.round(screenHeight / f),
                    Math.round(mouseX / f), Math.round(mouseY / f), width, height);
            return new Vector2i(Math.max(2, pos.x()), Math.max(2, pos.y()));
        };
    }
}
