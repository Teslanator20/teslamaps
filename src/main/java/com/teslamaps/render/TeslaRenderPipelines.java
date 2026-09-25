package com.teslamaps.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class TeslaRenderPipelines {

    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    public static final RenderPipeline LINES_ESP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation("pipeline/teslamaps_lines_esp")
                    .withDepthStencilState(NO_DEPTH)
                    .build()
    );

    public static final RenderPipeline FILLED_ESP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/teslamaps_filled_esp")
                    .withDepthStencilState(NO_DEPTH)
                    .build()
    );

    private static final DepthStencilState DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);

    public static final RenderPipeline LINES_DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation("pipeline/teslamaps_lines_depth")
                    .withDepthStencilState(DEPTH)
                    .build()
    );

    public static final RenderPipeline FILLED_DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/teslamaps_filled_depth")
                    .withDepthStencilState(DEPTH)
                    .build()
    );

    // instanced filled boxes: unit cube + per-box data in a uniform block (InstancedBoxes)
    public static final RenderPipeline FILLED_ESP_INSTANCED = instanced("teslamaps_filled_esp_instanced", NO_DEPTH);
    public static final RenderPipeline FILLED_DEPTH_INSTANCED = instanced("teslamaps_filled_depth_instanced", DEPTH);

    private static RenderPipeline instanced(String name, DepthStencilState depth) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation("pipeline/" + name)
                        .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_esp_box"))
                        .withUniform("BoxData", UniformType.UNIFORM_BUFFER)
                        .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                        .withDepthStencilState(depth)
                        .build());
    }

    // Fullscreen blit of the accumulation texture, alpha taken from ColorModulator so
    // the blend amount can change every frame. Same setup as vanilla's entity outline
    // blit, minus the alpha write — the frame's own alpha channel must stay intact.
    public static final RenderPipeline MOTION_BLUR = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation("pipeline/teslamaps_motion_blur")
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_motion_blur"))
                    .withSampler("InSampler")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(
                            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR))
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .build()
    );

    public static void init() {
    }
}
