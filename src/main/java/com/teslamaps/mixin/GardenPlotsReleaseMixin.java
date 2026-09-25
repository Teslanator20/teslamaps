package com.teslamaps.mixin;

import com.teslamaps.features.GardenPlotsOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ends a panel drag. Only AbstractContainerScreen declares mouseReleased; the
 * recipe-book screens inherit it, so this is the class to inject into.
 */
@Mixin(AbstractContainerScreen.class)
public class GardenPlotsReleaseMixin {

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void teslamaps$panelScroll(double mouseX, double mouseY, double dx, double dy,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.HudDragInInventory.handleScroll(mouseX, mouseY, dy)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void teslamaps$plotsRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.HudDragInInventory.handleRelease(click.x(), click.y())) {
            cir.setReturnValue(true);
            return;
        }
        if (GardenPlotsOverlay.handleRelease()) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.LeapOverlay.handleRelease(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }
}
