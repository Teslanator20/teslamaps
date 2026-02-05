package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TerminalGuiManager;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept mouse clicks in handled screens (1.21.10 version).
 * In 1.21.10, mouseClicked signature changed to use Click object.
 */
@Mixin(HandledScreen.class)
public class HandledScreenClickMixin {

    /**
     * Intercept mouse clicks using the new 1.21.10 signature: (Click, boolean).
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        // Extract mouse position from Click object
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Check terminal GUI first
        if (TerminalGuiManager.handleMouseClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        // Check leap overlay
        if (LeapOverlay.handleClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
