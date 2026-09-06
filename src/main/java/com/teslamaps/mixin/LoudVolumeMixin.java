package com.teslamaps.mixin;

import com.teslamaps.utils.LoudSound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Vanilla clamps the computed volume to 1.0. ONLY our own LoudSoundInstance sounds should exceed it.
// calculateVolume(float, SoundSource) can't see the instance, so a thread-local flag set during play()
// gates the amplification — otherwise vanilla explosions (played at volume 4.0, e.g. Necron / wither
// dragons) would get ~4x louder.
@Mixin(SoundEngine.class)
public class LoudVolumeMixin {

    @Unique
    private static final ThreadLocal<Boolean> teslamaps$loud = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "play", at = @At("HEAD"))
    private void teslamaps$markLoud(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        teslamaps$loud.set(instance instanceof LoudSound.LoudSoundInstance);
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void teslamaps$clearLoud(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        teslamaps$loud.set(Boolean.FALSE);
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"), cancellable = true)
    private void teslamaps$unclampLoud(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (teslamaps$loud.get() && volume > 1.0f) cir.setReturnValue(cir.getReturnValueF() * volume);
    }
}
