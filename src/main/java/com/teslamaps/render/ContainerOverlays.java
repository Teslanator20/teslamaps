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
        if (com.teslamaps.features.SackTable.shouldRender()) {
            com.teslamaps.debug.Perf.begin("SackTable.render");
            com.teslamaps.features.SackTable.render(context, mouseX, mouseY);
            com.teslamaps.debug.Perf.end();
        }

        if (com.teslamaps.config.TeslaMapsConfig.get().section("Shards")) {
            com.teslamaps.debug.Perf.begin("ShardOverlay.render");
            com.teslamaps.features.hunting.ShardOverlay.renderMenuExtras(context);
            com.teslamaps.features.hunting.HuntingBox.renderSyphonReady(context);
            if (com.teslamaps.features.hunting.ShardOverlay.shouldRender()) {
                com.teslamaps.features.hunting.ShardOverlay.render(context, mouseX, mouseY);
            }
            com.teslamaps.debug.Perf.end();
        }

        com.teslamaps.features.ContainerPanelMover.render(context, mouseX, mouseY);

        if (com.teslamaps.features.ItemValueOverlay.shouldRender()) {
            com.teslamaps.features.ItemValueOverlay.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.SlotHighlighter.shouldRender()) {
            com.teslamaps.features.SlotHighlighter.render(context);
        }
        if (com.teslamaps.features.TrashHighlighter.shouldRender()) {
            com.teslamaps.debug.Perf.begin("TrashHighlighter.render");
            com.teslamaps.features.TrashHighlighter.render(context);
            com.teslamaps.debug.Perf.end();
        }

        com.teslamaps.features.BingoCardTips.render(context);
        com.teslamaps.features.TabWidgetGuide.render(context);
        if (com.teslamaps.features.EnchantTablePanel.shouldRender()) {
            com.teslamaps.features.EnchantTablePanel.render(context);
        }
        if (com.teslamaps.features.HoppityCollectionStats.shouldRender()) {
            com.teslamaps.features.HoppityCollectionStats.render(context);
        }

        com.teslamaps.features.BazaarOwnOrders.render(context);
        com.teslamaps.features.TradeValue.render(context);
        com.teslamaps.features.BitsShopHelper.render(context);
        com.teslamaps.features.PesthunterProfit.render(context, mouseX, mouseY);
        com.teslamaps.features.bazaar.ManageAuctions.render(context);
        com.teslamaps.features.bazaar.BazaarPurchaseHelper.render(context);
        com.teslamaps.features.ProgressHighlight.render(context);
        com.teslamaps.features.DevTools.render(context);
        com.teslamaps.features.HideNotClickable.render(context);
        com.teslamaps.features.QuickCraftConfirm.render(context);
        com.teslamaps.features.GuideHighlight.render(context);
        com.teslamaps.features.CommissionHighlight.render(context);
        com.teslamaps.features.MenuHighlights.render(context);
        com.teslamaps.features.CakeTracker.renderSlots(context);
        com.teslamaps.features.MuseumHelper.render(context);
        com.teslamaps.features.MinionHelper.renderPanel(context);
        com.teslamaps.features.PartyFinderHelper.render(context);
        com.teslamaps.features.Experimentation.render(context);
        com.teslamaps.features.ExperimentStats.render(context);
        com.teslamaps.features.qol.CityProject.render(context);
        com.teslamaps.features.EcoOverlays.render(context, mouseX, mouseY);
        com.teslamaps.features.gardenqol.GardenHooks.render(context);
        com.teslamaps.features.GardenMenuHelpers.render(context, mouseX, mouseY);
        com.teslamaps.features.AbiphoneHelper.render(context);

        if (com.teslamaps.features.foraging.HotF.shouldRender()) {
            com.teslamaps.features.foraging.HotF.render(context);
        }

        if (com.teslamaps.features.Searchbar.shouldRender()) {
            com.teslamaps.features.Searchbar.render(context);
        }

        com.teslamaps.features.accessories.AccessoryHelper.renderSlots(context);
        com.teslamaps.features.accessories.AccessoryHelper.render(context, mouseX, mouseY);
        com.teslamaps.features.ItemHighlight.render(context);
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
        com.teslamaps.features.bazaar.PurchaseGuard.render(context);
    }
}
