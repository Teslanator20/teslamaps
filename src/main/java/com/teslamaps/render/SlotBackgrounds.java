package com.teslamaps.render;

import com.teslamaps.features.RarityBackground;
import com.teslamaps.features.foraging.HotF;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/**
 * Shared draw pass for what goes behind a container slot's item icon, so the icon
 * stays readable. Called from SlotBackgroundMixin. The overlay pass on top of the
 * items lives in ContainerOverlays.
 */
public class SlotBackgrounds {

    public static void draw(GuiGraphicsExtractor context, Slot slot) {
        RarityBackground.draw(context, slot.x, slot.y, slot.getItem());
        HotF.drawHighlight(context, slot);
        com.teslamaps.features.AuctionPriceCompare.drawHighlight(context, slot);
    }
}
