package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TermGui;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class HandledScreenClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        com.teslamaps.features.AuctionPriceInput.onSlotClick(
                (AbstractContainerScreen<?>) (Object) this, ((HandledScreenAccessor) (Object) this).getFocusedSlot());

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.handleClick(mouseX, mouseY, button)) cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.StorageOverlay.handleEnableClick(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }

        if (TermGui.clickActive(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.Searchbar.handleClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.SlotLock.blockSlot(((HandledScreenAccessor) (Object) this).getFocusedSlot())) {
            cir.setReturnValue(true);
            return;
        }

        if (LeapOverlay.handleClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
