package com.teslamaps.mixin;

import com.teslamaps.features.SoundVolumes;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public class SoundVolumeMixin {
    @Shadow @Final protected Identifier identifier;

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void teslamaps$volume(CallbackInfoReturnable<Float> cir) {
        float m = SoundVolumes.multiplier(identifier);
        if (m != 1f) cir.setReturnValue(cir.getReturnValueF() * m);
    }
}
