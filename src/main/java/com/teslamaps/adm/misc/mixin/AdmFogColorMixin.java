package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class AdmFogColorMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void teslamaps$admBlackSky(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenAmount, Vector4f dest, CallbackInfo ci) {
        if (!RandomStuff.blackSkyActive() || camera.getFluidInCamera() != FogType.NONE) return;
        dest.set(0f, 0f, 0f, 1f);
    }
}
