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
        com.teslamaps.features.BackpackPreview.drawSlotTint(context, slot);
        RarityBackground.draw(context, slot.x, slot.y, com.teslamaps.features.AnimatedDyeFix.displayStack(slot));
        com.teslamaps.features.LegacyAttributeBackground.draw(context, slot.x, slot.y, slot.getItem());
        com.teslamaps.features.MuseumDonations.drawSlot(context, slot.x, slot.y, slot.getItem());
        com.teslamaps.features.AnvilHelper.drawHighlight(context, slot);
        HotF.drawHighlight(context, slot);
        com.teslamaps.features.foraging.HotfWhisperGoal.drawSlot(context, slot);
        com.teslamaps.features.mining.HotmPerks.drawHighlight(context, slot);
        com.teslamaps.features.AuctionPriceCompare.drawHighlight(context, slot);
        com.teslamaps.features.EcoOverlays.slot(context, slot);
        com.teslamaps.features.PartyFinderHelper.drawHighlight(context, slot);
        com.teslamaps.features.DuplicateItems.drawHighlight(context, slot);
        com.teslamaps.features.SearchHighlight.drawSlot(context, slot);
        com.teslamaps.features.DnaAnalyzerSolver.drawHighlight(context, slot);
        com.teslamaps.features.garden.JacobContestMenus.drawSlot(context, slot);
        com.teslamaps.features.GardenPlots.drawDeskBackground(context, slot);
        com.teslamaps.features.StereoHarmony.drawHighlight(context, slot);
        com.teslamaps.features.GardenPlotMenu.drawHighlight(context, slot);
        com.teslamaps.features.mining.FossilExcavatorSolver.drawHighlight(context, slot);
        com.teslamaps.features.foraging.ForagingBeacon.drawHighlight(context, slot);
        com.teslamaps.features.kuudra.KuudraQol.drawSlot(context, slot);
    }
}
