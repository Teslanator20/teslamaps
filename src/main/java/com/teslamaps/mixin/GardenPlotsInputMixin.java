package com.teslamaps.mixin;

import com.teslamaps.features.GardenPlotsOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Click and drag routing for the garden plots overlay. Targets the recipe-book
 * screen because that is what the player inventory is, and it overrides these
 * two methods rather than inheriting them. Release is handled separately in
 * GardenPlotsReleaseMixin, since this class does not declare mouseReleased.
 */
@Mixin(AbstractRecipeBookScreen.class)
public class GardenPlotsInputMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void teslamaps$plotsClick(MouseButtonEvent click, boolean doubleClick,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.HudDragInInventory.handleClick(click.x(), click.y(),
                click.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (GardenPlotsOverlay.handleClick(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void teslamaps$plotsDrag(MouseButtonEvent click, double dx, double dy,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.HudDragInInventory.handleDrag(click.x(), click.y())) {
            cir.setReturnValue(true);
            return;
        }
        if (GardenPlotsOverlay.handleDrag(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }
}
