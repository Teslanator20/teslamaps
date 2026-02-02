package com.teslamaps.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;

/**
 * Custom render pipelines for ESP rendering through walls.
 * Based on Odin's CustomRenderPipelines.
 */
public class TeslaRenderPipelines {

    /**
     * Lines with depth test disabled - renders through walls.
     */
    public static final RenderPipeline LINES_ESP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/teslamaps_lines_esp")
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
                    .withCull(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    /**
     * Filled boxes with depth test disabled - renders through walls.
     */
    public static final RenderPipeline FILLED_ESP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/teslamaps_filled_esp")
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
                    .withCull(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    /**
     * Initialize pipelines (called during mod init to ensure they're registered).
     */
    public static void init() {
        // Just accessing the fields is enough to trigger static initialization
    }
}
