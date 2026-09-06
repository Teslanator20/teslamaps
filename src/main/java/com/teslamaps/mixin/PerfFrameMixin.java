package com.teslamaps.mixin;

import com.teslamaps.debug.Perf;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Frame boundary for the perf counters: closes out the per-frame totals and
// gives the spike logger the frame time to compare against.
@Mixin(Minecraft.class)
public class PerfFrameMixin {

    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void teslamaps$perfFrame(boolean tick, CallbackInfo ci) {
        if (Perf.enabled) Perf.frame(Minecraft.getInstance().getFrameTimeNs());
    }
}
