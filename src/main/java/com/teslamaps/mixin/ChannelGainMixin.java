package com.teslamaps.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// OpenAL clamps source gain to AL_MAX_GAIN (default 1.0); lift it so amplified (>1.0) volumes actually play louder.
@Mixin(Channel.class)
public class ChannelGainMixin {

    @Shadow @Final private int source;

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void teslamaps$liftMaxGain(float volume, CallbackInfo ci) {
        AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, Math.max(1.0f, volume));
    }
}
