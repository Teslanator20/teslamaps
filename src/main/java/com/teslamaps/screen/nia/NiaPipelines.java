package com.teslamaps.screen.nia;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.teslamaps.render.gui.TeslaVertexFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

// Ported from NiaMod's gui/render/RenderPipelines.java (GUI_ROUNDED_RECT only)
final class NiaPipelines {

    static final RenderPipeline ROUNDED_RECT = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, TeslaVertexFormats.SDF)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withLocation("pipeline/nia_rounded_rect")
            .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/nia_rounded_rect"))
            .build();

    private NiaPipelines() {}
}
