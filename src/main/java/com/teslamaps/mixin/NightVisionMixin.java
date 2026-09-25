package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class NightVisionMixin {
    @Inject(method = "getNightVisionScale", at = @At("RETURN"), cancellable = true)
    private static void teslamaps$nightVision(LivingEntity entity, float delta, CallbackInfoReturnable<Float> cir) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.nightVisionControl || entity != Minecraft.getInstance().player) return;
        float v = c.nightVisionNoBlink ? 1f : cir.getReturnValueF();
        cir.setReturnValue(v * Math.max(0f, Math.min(100f, c.nightVisionStrength)) / 100f);
    }
}
