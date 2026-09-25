package com.teslamaps.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class NoCloudsMixin {
    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noClouds(FrameGraphBuilder builder, CloudStatus status, Vec3 pos,
                                     long ticks, float partialTick, int color, float height,
                                     int fogColor, CallbackInfo ci) {
        if (com.teslamaps.features.IslandClouds.hide()) ci.cancel();
    }
}
