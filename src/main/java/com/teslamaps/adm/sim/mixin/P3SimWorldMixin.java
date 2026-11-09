package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.Arena;
import com.teslamaps.adm.sim.SimServer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlatLevelSource.class)
public class P3SimWorldMixin {
    @Inject(method = "buildTerrain", at = @At("HEAD"))
    private void teslamaps$adsArena(ChunkAccess chunk, Blender blender, RandomState random, StructureManager structures,
                                    BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> biomeSet,
                                    CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (SimServer.server != null) Arena.fill(chunk);
    }
}
