package com.teslamaps.render;

import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared draw pass for the extra overlays rendered on top of container screens.
 * Called from both the plain container screens (ContainerOverlayMixin) and the
 * recipe-book screens like the player inventory / crafting table
 * (RecipeBookOverlayMixin), since those use a different render path.
 */
public class ContainerOverlays {

    public static void renderAll(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (LeapOverlay.shouldRender()) {
            LeapOverlay.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.croesus.CroesusProfit.shouldRender()) {
            com.teslamaps.features.croesus.CroesusProfit.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.ContainerValue.shouldRender()) {
            com.teslamaps.features.ContainerValue.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.config.TeslaMapsConfig.get().section("Shards")) {
            com.teslamaps.debug.Perf.begin("ShardOverlay.render");
            com.teslamaps.features.hunting.ShardOverlay.renderMenuExtras(context);
            if (com.teslamaps.features.hunting.ShardOverlay.shouldRender()) {
                com.teslamaps.features.hunting.ShardOverlay.render(context, mouseX, mouseY);
            }
            com.teslamaps.debug.Perf.end();
        }

        if (com.teslamaps.features.ItemValueOverlay.shouldRender()) {
            com.teslamaps.features.ItemValueOverlay.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.SlotHighlighter.shouldRender()) {
            com.teslamaps.features.SlotHighlighter.render(context);
        }

        com.teslamaps.features.BazaarOwnOrders.render(context);
        com.teslamaps.features.ProgressHighlight.render(context);
        com.teslamaps.features.GuideHighlight.render(context);
        com.teslamaps.features.CommissionHighlight.render(context);
        com.teslamaps.features.PartyFinderHelper.render(context);

        if (com.teslamaps.features.foraging.HotF.shouldRender()) {
            com.teslamaps.features.foraging.HotF.render(context);
        }

        if (com.teslamaps.features.Searchbar.shouldRender()) {
            com.teslamaps.features.Searchbar.render(context);
        }

        com.teslamaps.features.BackpackPreview.renderOpenPageBorder(context);
        com.teslamaps.features.BackpackPreview.renderFrozen(context, mouseX, mouseY);
        com.teslamaps.features.FarmProfitOverlay.drawTooltip(context,
                net.minecraft.client.Minecraft.getInstance(), mouseX, mouseY);
        com.teslamaps.features.GardenPlots.scanConfigureScreen();
        if (com.teslamaps.features.GardenPlotsOverlay.shouldRender()) {
            com.teslamaps.features.GardenPlotsOverlay.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.StorageOverlay.active()) {
            com.teslamaps.features.StorageOverlay.render(context, mouseX, mouseY);
        } else {
            com.teslamaps.features.StorageOverlay.renderEnableButton(context);
        }
    }
}
