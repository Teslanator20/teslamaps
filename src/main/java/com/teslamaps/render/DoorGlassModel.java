package com.teslamaps.render;

import com.teslamaps.features.IHateDoors;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;

/** Door / Storm pillar block model: stained glass where a feature marked the block, the real block everywhere else. */
public final class DoorGlassModel implements BlockStateModel, FabricBlockStateModel {

    private final BlockStateModel original;

    public DoorGlassModel(BlockStateModel original) {
        this.original = original;
    }

    private static BlockState glassAt(BlockPos pos, BlockState state) {
        BlockState g = IHateDoors.glassAt(pos, state);
        return g != null ? g : com.teslamaps.features.FuckDiorite.glassAt(pos);
    }

    /** Blocks that must not count as occluding (door, pillar); walls are left out, they would un-cull every wall face. */
    public static boolean noOcclude(net.minecraft.world.level.block.Block b) {
        return (IHateDoors.active && IHateDoors.doorBlock(b))
                || (com.teslamaps.features.FuckDiorite.active && com.teslamaps.features.FuckDiorite.pillarBlock(b));
    }

    /** Blocks that get this wrapper right now. */
    public static boolean wraps(net.minecraft.world.level.block.Block b) {
        return (IHateDoors.active && (IHateDoors.doorBlock(b) || IHateDoors.wallBlock(b)))
                || (com.teslamaps.features.FuckDiorite.active && com.teslamaps.features.FuckDiorite.pillarBlock(b));
    }

    private static BlockStateModel model(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter view, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        BlockState glass = glassAt(pos, state);
        if (glass != null) ((FabricBlockStateModel) model(glass)).emitQuads(emitter, view, pos, glass, random, cullTest);
        else ((FabricBlockStateModel) original).emitQuads(emitter, view, pos, state, random, cullTest);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    @Override
    public int materialFlags(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
        BlockState glass = glassAt(pos, state);
        if (glass != null) return ((FabricBlockStateModel) model(glass)).materialFlags(view, pos, glass, random);
        return ((FabricBlockStateModel) original).materialFlags(view, pos, state, random);
    }

    @Override
    public boolean hasMaterialFlag(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random, int flag) {
        return (materialFlags(view, pos, state, random) & flag) != 0;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        original.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return original.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return original.materialFlags();
    }
}
