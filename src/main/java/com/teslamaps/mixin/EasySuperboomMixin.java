package com.teslamaps.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class EasySuperboomMixin {

    @Shadow
    public HitResult hitResult;

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void teslamaps$easySuperboom(CallbackInfo ci) {
        if (com.teslamaps.features.EasySuperboom.onPickBlock(hitResult)) ci.cancel();
    }
}
