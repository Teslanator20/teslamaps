package com.teslamaps.mixin;

import com.teslamaps.features.KeepTerrainLoaded;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkCache.class)
public class KeepTerrainChunkCacheMixin {
    @ModifyVariable(method = "updateViewRadius", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int teslamaps$expandTerrainCache(int viewDistance) {
        return KeepTerrainLoaded.storageViewDistance(viewDistance);
    }
}
