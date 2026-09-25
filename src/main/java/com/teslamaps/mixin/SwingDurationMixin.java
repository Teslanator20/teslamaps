package com.teslamaps.mixin;

import com.teslamaps.features.ItemAnimations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class SwingDurationMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void teslamaps$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof LocalPlayer p) || !ItemAnimations.on()) return;
        InteractionHand hand = p.swingingArm != null ? p.swingingArm : InteractionHand.MAIN_HAND;
        int base = p.getItemInHand(hand).getSwingAnimation().duration();
        cir.setReturnValue(ItemAnimations.swingDuration(cir.getReturnValueI(), base));
    }
}
