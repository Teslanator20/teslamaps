package com.teslamaps.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class TeslaRenderPipelines {

    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    // reversed depth since 26.3: nearer = greater
    private static final DepthStencilState DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    private static final ColorTargetState TRANSLUCENT = new ColorTargetState(BlendFunction.TRANSLUCENT);

    private static RenderPipeline.Builder lines() {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withColorTargetState(TRANSLUCENT)
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                .withPrimitiveTopology(PrimitiveTopology.LINES);
    }

    private static RenderPipeline.Builder filled() {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(TRANSLUCENT)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    public static final RenderPipeline LINES_ESP = lines()
            .withLocation("pipeline/teslamaps_lines_esp")
            .withDepthStencilState(NO_DEPTH)
            .build();

    public static final RenderPipeline FILLED_ESP = filled()
            .withLocation("pipeline/teslamaps_filled_esp")
            .withDepthStencilState(NO_DEPTH)
            .build();

    public static final RenderPipeline LINES_DEPTH = lines()
            .withLocation("pipeline/teslamaps_lines_depth")
            .withDepthStencilState(DEPTH)
            .build();

    public static final RenderPipeline FILLED_DEPTH = filled()
            .withLocation("pipeline/teslamaps_filled_depth")
            .withDepthStencilState(DEPTH)
            .build();

    // instanced filled boxes: unit cube + per-box data in a uniform block (InstancedBoxes)
    public static final RenderPipeline FILLED_ESP_INSTANCED = instanced("teslamaps_filled_esp_instanced", NO_DEPTH);
    public static final RenderPipeline FILLED_DEPTH_INSTANCED = instanced("teslamaps_filled_depth_instanced", DEPTH);

    private static RenderPipeline instanced(String name, DepthStencilState depth) {
        return filled()
                .withLocation("pipeline/" + name)
                .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_esp_box"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("BoxData", UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withDepthStencilState(depth)
                .build();
    }

    // Fullscreen blit of the accumulation texture, alpha taken from ColorModulator so
    // the blend amount can change every frame. Same setup as vanilla's entity outline
    // blit, minus the alpha write — the frame's own alpha channel must stay intact.
    public static final RenderPipeline MOTION_BLUR = RenderPipeline.builder()
            .withLocation("pipeline/teslamaps_motion_blur")
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_motion_blur"))
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withColorTargetState(new ColorTargetState(
                    Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
            .withDepthStencilState(Optional.empty())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    public static void init() {
    }
}
