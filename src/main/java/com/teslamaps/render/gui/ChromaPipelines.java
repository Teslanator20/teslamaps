package com.teslamaps.render.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** The two chroma pipelines: plain geometry and textured text. */
public class ChromaPipelines {

    public static final String UNIFORM = "TeslaChromaUniforms";

    static final BindGroupLayout UNIFORM_LAYOUT =
            BindGroupLayout.builder().withUniform(UNIFORM, UniformType.UNIFORM_BUFFER).build();

    static final ColorTargetState COLOR_ONLY = new ColorTargetState(
            Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR);

    static RenderPipeline.Builder base() {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(UNIFORM_LAYOUT)
                .withColorTargetState(COLOR_ONLY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    private static Identifier shader(String name) {
        return Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_" + name);
    }

    public static final RenderPipeline CHROMA_STANDARD = base()
            .withLocation("pipeline/teslamaps_chroma_standard")
            .withVertexShader(shader("standard_chroma"))
            .withFragmentShader(shader("standard_chroma"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .build();

    /** Text goes through this one: same vertex format vanilla uses for glyphs. */
    public static final RenderPipeline CHROMA_TEXT = base()
            .withLocation("pipeline/teslamaps_chroma_text")
            .withVertexShader(shader("textured_chroma"))
            .withFragmentShader(shader("textured_chroma"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .build();

    /** Two-colour fade for text, same uniform block and movement as the chroma. */
    public static final RenderPipeline FADE_TEXT = base()
            .withLocation("pipeline/teslamaps_fade_text")
            .withVertexShader(shader("textured_chroma"))
            .withFragmentShader(shader("textured_fade"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .build();

    public static void init() {
    }
}
