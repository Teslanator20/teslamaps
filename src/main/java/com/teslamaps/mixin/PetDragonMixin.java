package com.teslamaps.mixin;

import com.teslamaps.features.PetDragon;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class PetDragonMixin {

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void teslamaps$petDragon(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {
        PetDragon.extract(state, delta.getGameTimeDeltaPartialTick(true));
        com.teslamaps.features.rift.CraftRoomHolograms.extract(state, delta.getGameTimeDeltaPartialTick(true));
    }
}
