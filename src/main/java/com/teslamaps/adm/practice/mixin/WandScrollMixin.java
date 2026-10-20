package com.teslamaps.adm.practice.mixin;

import com.teslamaps.adm.practice.BrBoxes;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class WandScrollMixin {
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wandScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (BrBoxes.onScroll(yOffset)) ci.cancel();
    }
}
