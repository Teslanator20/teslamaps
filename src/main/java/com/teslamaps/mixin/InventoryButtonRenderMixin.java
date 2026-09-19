package com.teslamaps.mixin;

import com.teslamaps.features.invbuttons.InventoryButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the inventory buttons. The tail of extractRenderState is after the slot and carried
 * item, so buttons are never hidden by an item, and before extractDeferredElements, so an
 * item tooltip still draws over them. Fabric's screen events fire after the tooltip pass.
 * Screens with a recipe book rewrite extractRenderState without calling super, so they need
 * the same inject.
 */
@Mixin({AbstractContainerScreen.class, AbstractRecipeBookScreen.class})
public class InventoryButtonRenderMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void teslamaps$renderInventoryButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo ci) {
        InventoryButtons.render((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }
}
