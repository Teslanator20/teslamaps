package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class WaterFovMixin {
    @ModifyExpressionValue(method = "modifyFovBasedOnDeathOrFluid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private FogType teslamaps$noWaterFov(FogType type) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.moreOverlayHiders && c.noWaterFov && (type == FogType.WATER || type == FogType.LAVA) ? FogType.NONE : type;
    }
}
