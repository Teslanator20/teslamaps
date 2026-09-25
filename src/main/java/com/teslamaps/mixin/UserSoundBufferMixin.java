package com.teslamaps.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.teslamaps.utils.UserSounds;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public class UserSoundBufferMixin {
    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void teslamaps$userSound(Identifier path, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if (UserSounds.NAMESPACE.equals(path.getNamespace())) cir.setReturnValue(UserSounds.buffer(path));
    }
}
