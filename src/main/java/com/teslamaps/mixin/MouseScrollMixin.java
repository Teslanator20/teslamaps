package com.teslamaps.mixin;

import com.teslamaps.features.Zoom;
import net.minecraft.client.MouseHandler;
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
}
