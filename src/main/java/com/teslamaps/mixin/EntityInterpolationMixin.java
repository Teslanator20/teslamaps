package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel only sends entity positions a few times a second, so vanilla's 3-tick
 * interpolation still leaves other players stepping. Stretching the window makes
 * them glide, at the cost of showing them slightly further in the past — hence
 * off by default, and never applied to whatever we are riding.
 */
@Mixin(InterpolationHandler.class)
public class EntityInterpolationMixin {

    @Shadow @Final private Entity entity;

    @Inject(method = "interpolateTo", at = @At("HEAD"))
    private void teslamaps$smoothSteps(Vec3 pos, float yRot, float xRot, CallbackInfo ci) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.section("Render") || !cfg.smoothEntities) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || entity.level() != mc.level) return;
        if (entity == mc.player || entity == mc.getCameraEntity()) return;
        if (mc.player != null && entity == mc.player.getVehicle()) return;

        ((InterpolationHandler) (Object) this)
                .setInterpolationLength(Math.max(3, Math.min(12, cfg.smoothEntitySteps)));
    }
}
