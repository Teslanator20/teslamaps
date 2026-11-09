package com.teslamaps.mixin;

import com.teslamaps.features.ItemAnimations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class SwingDurationMixin {
    @Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true)
    private void teslamaps$swingSpeed(SwingAnimation anim, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof LocalPlayer) || !ItemAnimations.on()) return;
        int base = anim.duration();
        cir.setReturnValue(ItemAnimations.swingDuration(cir.getReturnValueI(), base));
    }
}
