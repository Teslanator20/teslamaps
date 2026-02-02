package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
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
    @Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
    private void onHasStatusEffect(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        // Only apply to the local player
        LivingEntity self = (LivingEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || self != mc.player) return;

        // Check if this is blindness or darkness and noBlind is enabled
        if (TeslaMapsConfig.get().noBlind) {
            if (effect == StatusEffects.BLINDNESS || effect == StatusEffects.DARKNESS) {
                cir.setReturnValue(false);
            }
        }

        // Check if this is nausea and noNausea is enabled
        if (TeslaMapsConfig.get().noNausea) {
            if (effect == StatusEffects.NAUSEA) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Override getEffectFadeFactor to return 0 for nausea when noNausea is enabled.
     */
    @Inject(method = "getEffectFadeFactor", at = @At("HEAD"), cancellable = true)
    private void onGetEffectFadeFactor(RegistryEntry<StatusEffect> effect, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!TeslaMapsConfig.get().noNausea) return;

        // Only apply to the local player
        LivingEntity self = (LivingEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || self != mc.player) return;

        // Return 0 for nausea effect
        if (effect == StatusEffects.NAUSEA) {
            cir.setReturnValue(0.0f);
        }
    }
}
