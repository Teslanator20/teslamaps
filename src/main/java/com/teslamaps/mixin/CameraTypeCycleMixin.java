package com.teslamaps.mixin;

import com.teslamaps.features.CameraTweaks;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CameraType.class)
public class CameraTypeCycleMixin {
    @Inject(method = "cycle", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noFrontView(CallbackInfoReturnable<CameraType> cir) {
        CameraType next = CameraTweaks.cycle((CameraType) (Object) this);
        if (next != null) cir.setReturnValue(next);
    }
}
