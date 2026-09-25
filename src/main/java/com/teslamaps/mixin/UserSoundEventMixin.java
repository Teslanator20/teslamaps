package com.teslamaps.mixin;

import com.teslamaps.utils.UserSounds;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class UserSoundEventMixin {
    @Inject(method = "getSoundEvent", at = @At("HEAD"), cancellable = true)
    private void teslamaps$userSound(Identifier id, CallbackInfoReturnable<WeighedSoundEvents> cir) {
        if (UserSounds.NAMESPACE.equals(id.getNamespace())) cir.setReturnValue(UserSounds.event(id));
    }
}
