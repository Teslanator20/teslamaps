package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.MenuBackgroundRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same animated background as the title screen, so the two match.
 * <p>
 * Targets Screen rather than JoinMultiplayerScreen: that screen inherits
 * extractBackground instead of overriding it, and mixin cannot inject into an
 * inherited method.
 */
@Mixin(Screen.class)
public abstract class JoinMultiplayerScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void teslamaps$shaderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().customTitleScreen) return;
        if (!((Object) this instanceof JoinMultiplayerScreen)) return;

        MenuBackgroundRenderer.render(ctx);
        ci.cancel();
    }
}
