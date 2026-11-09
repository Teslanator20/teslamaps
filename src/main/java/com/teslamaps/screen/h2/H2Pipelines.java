package com.teslamaps.screen.h2;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.teslamaps.render.gui.TeslaVertexFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

// Ported from h2team's H2Pipelines.java
final class H2Pipelines {
    static final RenderPipeline ROUNDED_RECT = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, TeslaVertexFormats.SDF)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withLocation("pipeline/teslamaps_h2_rounded_rect")
            .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/h2_rounded_rect"))
            .build();

    private H2Pipelines() {}
}
