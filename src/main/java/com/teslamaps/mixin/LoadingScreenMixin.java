package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.MenuBackgroundRenderer;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the menu background behind the loading screens instead of the dirt texture. Only the
 * background is replaced, so the vanilla progress text and bars keep rendering on top.
 */
@Mixin(Screen.class)
public class LoadingScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void teslamaps$loadingBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().customLoadingScreen) return;

        Screen self = (Screen) (Object) this;
        // ConnectScreen and GenericMessageScreen also show up when hopping servers
        boolean loading = self instanceof LevelLoadingScreen
                || self instanceof ConnectScreen
                || self instanceof ProgressScreen
                || self instanceof GenericMessageScreen;
        if (!loading) return;

        AnimClock.update();
        MenuBackgroundRenderer.render(ctx);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void teslamaps$transferTick(CallbackInfo ci) {
        com.teslamaps.features.ServerTransfer.onScreenTick((Screen) (Object) this);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideReconfig(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (com.teslamaps.features.ServerTransfer.hideReconfig((Screen) (Object) this)) ci.cancel();
    }
}
