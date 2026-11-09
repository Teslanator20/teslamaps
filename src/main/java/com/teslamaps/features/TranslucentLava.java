package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class TranslucentLava {
    private static boolean currentState = false;
    private static int lastTintColor = -1;
    private static FluidModel.Unbaked waterUnbaked;
    private static FluidModel.Unbaked lavaUnbaked;
    private static FluidModel waterBaked;
    private static FluidModel lavaBaked;

    public static boolean isEnabled() {
        return currentState;
    }

    public static FluidModel.Unbaked getWaterUnbaked() {
        return waterUnbaked;
    }

    public static FluidModel.Unbaked getLavaUnbaked() {
        return lavaUnbaked;
    }

    public static void setBakedModels(FluidModel water, FluidModel vanilla) {
        waterBaked = water;
        lavaBaked = vanilla;
    }

    public static FluidModel getCurrentModel() {
        return currentState ? waterBaked : lavaBaked;
    }

    private static void update(boolean enabled) {
        if (currentState != enabled) {
            currentState = enabled;
            FluidRenderingRegistry.setBlockTransparency(Blocks.LAVA, enabled);
            reloadWorld();
        }
    }

    public static void registerFluidModel() {
        waterUnbaked = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                new TintSource()
        );
        FluidRenderingRegistry.register(Fluids.LAVA, Fluids.FLOWING_LAVA, waterUnbaked);
        lavaUnbaked = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/lava_still")),
                new Material(Identifier.withDefaultNamespace("block/lava_flow")),
                null, null
        );
    }

    private static int tint() {
        return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTranslucentLava) | 0xFF000000;
    }

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean desired = cfg.section("Fishing") && cfg.translucentLava
                && (!cfg.translucentLavaCrimsonOnly || com.teslamaps.utils.SkyblockUtils.isInArea("Crimson Isle"));
        int desiredTint = tint();
        if (desired != currentState) {
            lastTintColor = desiredTint;
            update(desired);
        } else if (currentState && desiredTint != lastTintColor) {
            lastTintColor = desiredTint;
            reloadWorld();
        }
    }

    private static void reloadWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
        }
    }

    private static class TintSource implements BlockTintSource {
        public int color(BlockState state) {
            return tint();
        }

        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return tint();
        }
    }
}
