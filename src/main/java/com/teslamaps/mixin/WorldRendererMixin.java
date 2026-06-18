package com.teslamaps.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder mixin for WorldRenderer.
 * Entity glowing is handled via EntityMixin.isGlowing() override instead.
 * 3D tracers/beacons require correct method signature - TODO for later.
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    // Entity outline/glow is handled via EntityMixin
}
