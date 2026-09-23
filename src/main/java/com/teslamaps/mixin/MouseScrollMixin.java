package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.Zoom;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void teslamaps$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (Zoom.onScroll(yOffset)) ci.cancel();
    }

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void teslamaps$zoomSensitivity(LocalPlayer player, double x, double y, Operation<Void> original) {
        original.call(player, Zoom.mouse(x), Zoom.mouse(y));
    }
}
