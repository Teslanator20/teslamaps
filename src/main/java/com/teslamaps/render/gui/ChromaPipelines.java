package com.teslamaps.render.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** The two chroma pipelines: plain geometry and textured text. */
public class ChromaPipelines {

    public static final String UNIFORM = "TeslaChromaUniforms";

    private static Identifier shader(String name) {
        return Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_" + name);
    }

    public static final RenderPipeline CHROMA_STANDARD = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation("pipeline/teslamaps_chroma_standard")
                    .withVertexShader(shader("standard_chroma"))
                    .withFragmentShader(shader("standard_chroma"))
                    .withUniform(UNIFORM, UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(
                            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .build()
    );

    /** Text goes through this one: same vertex format vanilla uses for glyphs. */
    public static final RenderPipeline CHROMA_TEXT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation("pipeline/teslamaps_chroma_text")
                    .withVertexShader(shader("textured_chroma"))
                    .withFragmentShader(shader("textured_chroma"))
                    .withSampler("Sampler0")
                    .withUniform(UNIFORM, UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(
                            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .build()
    );

    /** Two-colour fade for text, same uniform block and movement as the chroma. */
    public static final RenderPipeline FADE_TEXT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation("pipeline/teslamaps_fade_text")
                    .withVertexShader(shader("textured_chroma"))
                    .withFragmentShader(shader("textured_fade"))
                    .withSampler("Sampler0")
                    .withUniform(UNIFORM, UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(
                            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .build()
    );

    public static void init() {
    }
}
