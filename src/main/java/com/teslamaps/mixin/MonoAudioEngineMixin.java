package com.teslamaps.mixin;

import com.teslamaps.features.MonoAudio;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class MonoAudioEngineMixin {

    @Shadow @Final private ChannelAccess channelAccess;

    @Inject(method = "updateSource", at = @At("TAIL"))
    private void teslamaps$refreshMono(Camera camera, CallbackInfo ci) {
        if (!MonoAudio.enabled()) return;
        channelAccess.executeOnChannels(s -> s.forEach(ch -> ((MonoAudio.Channel) ch).teslamaps$refreshMono()));
    }
}
