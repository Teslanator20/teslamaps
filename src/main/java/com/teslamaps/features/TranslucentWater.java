package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class TranslucentWater {
    private static boolean currentState = false;
    private static int lastTintColor = -1;
    private static FluidModel.Unbaked waterUnbaked;
    private static FluidModel.Unbaked vanillaWaterUnbaked;
    private static FluidModel tintedBaked;
    private static FluidModel vanillaBaked;

    public static boolean isEnabled() {
        return currentState;
    }

    public static FluidModel.Unbaked getWaterUnbaked() {
        return waterUnbaked;
    }

    public static FluidModel.Unbaked getVanillaWaterUnbaked() {
        return vanillaWaterUnbaked;
    }

    public static void setBakedModels(FluidModel tinted, FluidModel vanilla) {
        tintedBaked = tinted;
        vanillaBaked = vanilla;
    }

    public static FluidModel getCurrentModel() {
        return currentState ? tintedBaked : vanillaBaked;
    }

    private static void update(boolean enabled) {
        if (currentState != enabled) {
            currentState = enabled;
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
        vanillaWaterUnbaked = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                BlockTintSources.water()
        );
        FluidRenderingRegistry.register(Fluids.WATER, Fluids.FLOWING_WATER, waterUnbaked);
    }

    private static int tint() {
        return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTintedWater) | 0xFF000000;
    }

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean desired = cfg.section("Fishing") && cfg.tintedWater;
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
            mc.levelRenderer.allChanged();
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
