package com.teslamaps.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.teslamaps.features.MonoAudio;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class MonoAudioChannelMixin implements MonoAudio.Channel {

    @Shadow @Final private int source;
    @Unique private Vec3 teslamaps$monoPos = Vec3.ZERO;
    @Unique private boolean teslamaps$monoRelative;

    @Inject(method = "setSelfPosition", at = @At("HEAD"), cancellable = true)
    private void teslamaps$monoPosition(Vec3 pos, CallbackInfo ci) {
        if (!MonoAudio.enabled()) return;
        teslamaps$monoPos = pos;
        teslamaps$refreshMono();
        ci.cancel();
    }

    @Inject(method = "setRelative", at = @At("HEAD"), cancellable = true)
    private void teslamaps$monoRelative(boolean relative, CallbackInfo ci) {
        if (!MonoAudio.enabled()) return;
        teslamaps$monoRelative = relative;
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        teslamaps$refreshMono();
        ci.cancel();
    }

    @Override
    public void teslamaps$refreshMono() {
        if (!MonoAudio.enabled()) return;
        MonoAudio.center(source, teslamaps$monoRelative ? teslamaps$monoPos.length() : MonoAudio.distanceToListener(teslamaps$monoPos));
    }
}
