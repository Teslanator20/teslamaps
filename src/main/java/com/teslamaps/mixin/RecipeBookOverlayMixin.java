package com.teslamaps.mixin;

import com.teslamaps.render.ContainerOverlays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Recipe-book screens (player inventory, crafting table, furnace, ...) override
// extractRenderState and never call AbstractContainerScreen's version, so the
// ContainerOverlayMixin inject doesn't fire for them. Hook this path too.
@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerOverlays.renderAll(context, mouseX, mouseY);
    }
}
