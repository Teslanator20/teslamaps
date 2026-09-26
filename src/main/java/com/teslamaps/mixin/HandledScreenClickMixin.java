package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TermGui;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class HandledScreenClickMixin {

    @Unique
    private static void teslamaps$ate(String who, int button) {
        com.teslamaps.TeslaMaps.debug("[ClickDbg] button {} swallowed by {}", button, who);
    }

    // Q inside an inventory goes through slotClicked with THROW, never through mouseClicked
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void teslamaps$blockSlotDrop(net.minecraft.world.inventory.Slot slot, int slotId, int button,
                                         net.minecraft.world.inventory.ContainerInput type, CallbackInfo ci) {
        com.teslamaps.features.ExperimentHelper.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot, type);
        com.teslamaps.features.AnimatedDyeFix.observeWardrobe((AbstractContainerScreen<?>) (Object) this, slot, button, type);
        com.teslamaps.features.WardrobeKeys.onSlotClicked((AbstractContainerScreen<?>) (Object) this, slot, button, type);
        com.teslamaps.features.StorageGuiScale.onClick((AbstractContainerScreen<?>) (Object) this);
        com.teslamaps.features.qol.TiaRelayHelper.onSlotClick(slot, slotId, button, type);
        com.teslamaps.features.mining.HotmPowderGoal.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, type);
        if (com.teslamaps.features.foraging.HotfWhisperGoal.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.DnaAnalyzerSolver.onSlotClick((AbstractContainerScreen<?>) (Object) this, slotId, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.gardenqol.GardenHooks.blockSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)
                || com.teslamaps.features.GardenMenuHelpers.blockClick((AbstractContainerScreen<?>) (Object) this, slot)) {
            ci.cancel();
            return;
        }
        com.teslamaps.features.VisitorStats.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot);
        if (com.teslamaps.features.bazaar.PurchaseGuard.blockClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)) {
            ci.cancel();
            return;
        }
        com.teslamaps.features.bazaar.BazaarOrderTracker.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot);
        com.teslamaps.features.bazaar.BazaarReorder.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot);
        if (com.teslamaps.features.PartyFinderHelper.rightClick(slot, button, type)
                || com.teslamaps.features.OldSkyblockMenu.onClick(slot)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.RenderHiders.middleClickMenu(slot, button, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.PetShiftClick.handle(
                (AbstractContainerScreen<?>) (Object) this, slot, slotId, button, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.croesus.CroesusProfit.blockReroll((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.MenuHighlights.onSlotClick(slot, type)
                || com.teslamaps.features.garden.JacobContestMenus.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.MinionFeatures.blockClick((AbstractContainerScreen<?>) (Object) this, slot, type)
                || com.teslamaps.features.ClickFixes.blockHuntrap((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.slayer.SlayerHiders.blockClick((AbstractContainerScreen<?>) (Object) this, slot)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.foraging.ForagingBeacon.blockClick(slot)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.GardenPlotMenu.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.SlotLock.blockSlotClick(slot, button, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.ItemProtection.blockMenuClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.ReforgeStop.blockClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.HideNotClickable.blockClick(slot)
                || com.teslamaps.features.InfernoMinion.blockClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.kuudra.KuudraMisc.blockPerkClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.slayer.SlayerTierLock.blockClick((AbstractContainerScreen<?>) (Object) this, slot)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.OpheliaPotLock.blockClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.SlotHighlighter.blockSalvageClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.CommandHelpers.blockCoopClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.BazaarOwnOrders.blockClick((AbstractContainerScreen<?>) (Object) this, slot)
                || com.teslamaps.features.QuickCraftConfirm.blockClick(slot)
                || com.teslamaps.features.SupercraftHelper.blockClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)
                || com.teslamaps.features.AbiphoneHelper.blockClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.AutoShiftClick.handle((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, type)) {
            ci.cancel();
            return;
        }
        if ((type != net.minecraft.world.inventory.ContainerInput.THROW
                && com.teslamaps.features.FillerItems.blockClick((AbstractContainerScreen<?>) (Object) this, slot))
                || com.teslamaps.features.MiddleClickGui.handle(
                (AbstractContainerScreen<?>) (Object) this, slot, slotId, button, type)) {
            ci.cancel();
            return;
        }
        if (type != net.minecraft.world.inventory.ContainerInput.THROW) return;
        if (com.teslamaps.features.ItemProtection.blockSlotDrop(slot)
                || com.teslamaps.features.LimitDroppingItems.block()) ci.cancel();
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "mouseClicked",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasInfiniteMaterials()Z"))
    private boolean teslamaps$middleClickFix(boolean original) {
        return original || com.teslamaps.features.ClickFixes.middleClickFix();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideFillerTooltip(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (com.teslamaps.features.FillerItems.hideTooltip((AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.BazaarOwnOrders.hideTooltip((AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.DnaAnalyzerSolver.hideTooltip((AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.mining.FossilExcavatorHelper.hideTooltip((AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot())) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (com.teslamaps.features.bazaar.PurchaseGuard.onMouse(click)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.ContainerPanelMover.click(mouseX, mouseY, button)) {
            teslamaps$ate("ContainerPanelMover", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.CustomWardrobe.click((AbstractContainerScreen<?>) (Object) this, click)
                || com.teslamaps.features.FancyWarpMenu.click((AbstractContainerScreen<?>) (Object) this, click)
                || com.teslamaps.features.FancyPartyFinder.click((AbstractContainerScreen<?>) (Object) this, click)
                || com.teslamaps.features.FancyAuctionHouse.click((AbstractContainerScreen<?>) (Object) this, click)
                || com.teslamaps.features.mining.CommissionPanel.click((AbstractContainerScreen<?>) (Object) this, click)
                || com.teslamaps.features.RadialMenus.click((AbstractContainerScreen<?>) (Object) this, click)) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.SackTable.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("SackTable", button);
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.garden.ComposterOverlay.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("ComposterOverlay", button);
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.SackValueHud.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("SackValueHud", button);
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.garden.JacobContestTimes.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("JacobContestTimes", button);
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.gardenqol.GardenHooks.click(mouseX, mouseY, button)) {
            teslamaps$ate("GardenHooks", button);
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.EcoOverlays.click(mouseX, mouseY, button)) {
            teslamaps$ate("EcoOverlays", button);
            cir.setReturnValue(true);
            return;
        }

        com.teslamaps.features.AuctionPriceInput.onSlotClick(
                (AbstractContainerScreen<?>) (Object) this, ((HandledScreenAccessor) (Object) this).getFocusedSlot());

        com.teslamaps.features.GuideHighlight.onSlotClick(
                (AbstractContainerScreen<?>) (Object) this, ((HandledScreenAccessor) (Object) this).getFocusedSlot());

        // A hotbar key bound to a mouse button produces no KeyEvent at all, so the wardrobe
        // picker never saw it from ContainerKeyMixin. Same logic, mouse entry point.
        if (com.teslamaps.features.WardrobeKeys.handleMouse((AbstractContainerScreen<?>) (Object) this, click)) {
            teslamaps$ate("WardrobeKeys", button);
            cir.setReturnValue(true);
            return;
        }

        // Binds live in one int for both devices, so a mouse-bound slot bind or slot lock has to
        // reach the same handlers ContainerKeyMixin calls for keyboard binds.
        int asBind = com.teslamaps.utils.KeyNames.forMouse(button);
        if (com.teslamaps.features.TerminalSolver.onTerminalKey(asBind,
                        ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.BackpackPreview.onKey(asBind)
                || com.teslamaps.features.SlotBinds.onKey(asBind)
                || com.teslamaps.features.SlotLock.onKey(asBind,
                        ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.ItemProtection.onKey(asBind, (AbstractContainerScreen<?>) (Object) this,
                        ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.GetFromSacks.onKey(asBind,
                        ((HandledScreenAccessor) (Object) this).getFocusedSlot())) {
            teslamaps$ate("SlotBinds/SlotLock/GetFromSacks", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.MiscQol.onClick(((HandledScreenAccessor) (Object) this).getFocusedSlot(), button)) {
            teslamaps$ate("CopyPlaytime", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.CopyItemName.onClick(
                ((HandledScreenAccessor) (Object) this).getFocusedSlot(), button)) {
            teslamaps$ate("CopyItemName", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.handleClick(mouseX, mouseY, button)) {
                teslamaps$ate("StorageOverlay.handleClick", button);
                cir.setReturnValue(true);
            }
            return;
        }
        if (com.teslamaps.features.StorageOverlay.handleEnableClick(mouseX, mouseY)) {
            teslamaps$ate("StorageOverlay.handleEnableClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.config.TeslaMapsConfig.get().section("Shards")
                && com.teslamaps.features.hunting.ShardOverlay.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("ShardOverlay.handleClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (TermGui.clickActive(mouseX, mouseY, button)) {
            teslamaps$ate("TermGui.clickActive", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.BackpackPreview.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("BackpackPreview.frozen", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.accessories.AccessoryHelper.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("AccessoryHelper", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.Searchbar.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("Searchbar.handleClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.SlotLock.blockSlot(((HandledScreenAccessor) (Object) this).getFocusedSlot())) {
            teslamaps$ate("SlotLock.blockSlot", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.ItemProtection.blockCursorDrop(
                (AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot() != null)) {
            teslamaps$ate("ItemProtection.blockCursorDrop", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.ItemProtection.blockDestructiveClick(
                (AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot(),
                net.minecraft.client.Minecraft.getInstance().player != null
                        && net.minecraft.client.Minecraft.getInstance().player.isShiftKeyDown())) {
            teslamaps$ate("ItemProtection.blockDestructiveClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (LeapOverlay.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("LeapOverlay.handleClick", button);
            cir.setReturnValue(true);
        }
    }
}
