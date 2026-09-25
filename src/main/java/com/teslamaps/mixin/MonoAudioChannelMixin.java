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
    @Unique private boolean teslamaps$monoApplied;

    @Inject(method = "setSelfPosition", at = @At("HEAD"), cancellable = true)
    private void teslamaps$monoPosition(Vec3 pos, CallbackInfo ci) {
        teslamaps$monoPos = pos;
        if (!MonoAudio.enabled()) return;
        teslamaps$refreshMono();
        ci.cancel();
    }

    @Inject(method = "setRelative", at = @At("HEAD"), cancellable = true)
    private void teslamaps$monoRelative(boolean relative, CallbackInfo ci) {
        teslamaps$monoRelative = relative;
        if (!MonoAudio.enabled()) return;
        teslamaps$refreshMono();
        ci.cancel();
    }

    @Override
    public void teslamaps$refreshMono() {
        if (!MonoAudio.enabled()) {
            if (!teslamaps$monoApplied) return;
            teslamaps$monoApplied = false;
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, teslamaps$monoRelative ? AL10.AL_TRUE : AL10.AL_FALSE);
            AL10.alSource3f(source, AL10.AL_POSITION, (float) teslamaps$monoPos.x, (float) teslamaps$monoPos.y, (float) teslamaps$monoPos.z);
            return;
        }
        teslamaps$monoApplied = true;
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        MonoAudio.center(source, teslamaps$monoRelative ? teslamaps$monoPos.length() : MonoAudio.distanceToListener(teslamaps$monoPos));
    }
}
