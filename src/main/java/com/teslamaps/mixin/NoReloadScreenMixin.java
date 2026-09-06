package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

// Skips the resource/texture reload overlay (the dirt screen on F3+T / pack changes) when enabled.
// The reload runs async; we just suppress the overlay's drawing and drive completion off isDone()
// (the vanilla fade-out path can't run since we skip its rendering). Only runtime reloads (fadeIn=false)
// are skipped, never the initial Mojang-logo load.
@Mixin(LoadingOverlay.class)
public class NoReloadScreenMixin {
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private boolean fadeIn;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$skipReloadScreen(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().noReloadScreen || fadeIn) return;
        ci.cancel(); // draw nothing
        if (reload.isDone()) {
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                onFinish.accept(Optional.of(t));
            }
            minecraft.setOverlay(null);
        }
    }
}
