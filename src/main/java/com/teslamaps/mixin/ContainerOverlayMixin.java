package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TerminalGuiManager;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draw the custom terminal GUI / leap overlay on top of container screens.
 *
 * 26.1.2: split out of GenericContainerScreenMixin because the top-level render method
 * (Screen#render -> extractRenderState) is only declared on AbstractContainerScreen, not on
 * the concrete ContainerScreen.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TerminalGuiManager.shouldRenderCustomGui()) {
            TerminalGuiManager.render(context);
        }

        // Render leap overlay
        if (LeapOverlay.shouldRender()) {
            LeapOverlay.render(context, mouseX, mouseY);
        }
    }
}
