package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for LivingEntity to handle blindness, darkness, and nausea effect removal.
 */
@Mixin(LivingEntity.class)
public class BlindnessMixin {
    /**
     * Override hasStatusEffect to hide blindness/darkness from render checks.
     */
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void onHasStatusEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        // Only apply to the local player
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || self != mc.player) return;

        // Check if this is blindness or darkness and noBlind is enabled
        if (TeslaMapsConfig.get().noBlind) {
            if (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS) {
                cir.setReturnValue(false);
            }
        }

        // Check if this is nausea and noNausea is enabled
        if (TeslaMapsConfig.get().noNausea) {
            if (effect == MobEffects.NAUSEA) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Override getEffectFadeFactor to return 0 for nausea when noNausea is enabled.
     */
    @Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
    private void onGetEffectFadeFactor(Holder<MobEffect> effect, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!TeslaMapsConfig.get().noNausea) return;

        // Only apply to the local player
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || self != mc.player) return;

        // Return 0 for nausea effect
        if (effect == MobEffects.NAUSEA) {
            cir.setReturnValue(0.0f);
        }
    }
}
