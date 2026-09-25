package com.teslamaps.mixin;

import com.teslamaps.render.gui.PanelBlur;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// world is on the target, hud not yet
@Mixin(value = GameRenderer.class, priority = 1100)
public class PanelBlurMixin {

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void teslamaps$panelBlur(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        com.teslamaps.debug.Perf.begin("PanelBlur");
        try {
            PanelBlur.capture();
        } finally {
            com.teslamaps.debug.Perf.end();
        }
    }
}
