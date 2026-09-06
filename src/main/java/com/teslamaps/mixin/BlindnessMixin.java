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

@Mixin(LivingEntity.class)
public class BlindnessMixin {
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void onHasStatusEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || self != mc.player) return;

        if (TeslaMapsConfig.get().noBlind) {
            if (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS) {
                cir.setReturnValue(false);
            }
        }

        if (TeslaMapsConfig.get().noNausea) {
            if (effect == MobEffects.NAUSEA) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
    private void onGetEffectFadeFactor(Holder<MobEffect> effect, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!TeslaMapsConfig.get().noNausea) return;

        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || self != mc.player) return;

        if (effect == MobEffects.NAUSEA) {
            cir.setReturnValue(0.0f);
        }
    }
}
