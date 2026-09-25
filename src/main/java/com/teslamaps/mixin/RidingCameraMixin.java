package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class RidingCameraMixin {
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float teslamaps$ridingX(Entity e, float pt, Operation<Float> original) {
        if (!TeslaMapsConfig.get().fixRidingCamera || !(e instanceof LocalPlayer) || !e.isPassenger()) return original.call(e, pt);
        return e.getXRot();
    }

    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float teslamaps$ridingY(Entity e, float pt, Operation<Float> original) {
        if (!TeslaMapsConfig.get().fixRidingCamera || !(e instanceof LocalPlayer) || !e.isPassenger()) return original.call(e, pt);
        return e.getYRot();
    }
}
