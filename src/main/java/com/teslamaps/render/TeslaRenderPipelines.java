package com.teslamaps.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Custom render pipelines for ESP rendering through walls.
 *
 * 26.1.2: the pipeline builder no longer exposes withBlend/withDepthWrite/withDepthTestFunction,
 * and the vanilla snippets are private. We build straight from the vanilla LINES / DEBUG_FILLED
 * snippets (exposed via the access widener) so the shaders, uniforms and vertex formats stay
 * correct, and only override the depth state to disable the depth test (ALWAYS_PASS, no write),
 * which is what makes the ESP render through walls.
 */
public class TeslaRenderPipelines {

    // ALWAYS_PASS = depth test always passes (renders through walls); false = don't write depth.
    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    /**
     * Lines with depth test disabled - renders through walls.
     */
    public static final RenderPipeline LINES_ESP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation("pipeline/teslamaps_lines_esp")
                    .withDepthStencilState(NO_DEPTH)
                    .build()
    );

    /**
     * Filled boxes with depth test disabled - renders through walls.
     */
    public static final RenderPipeline FILLED_ESP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/teslamaps_filled_esp")
                    .withDepthStencilState(NO_DEPTH)
                    .build()
    );

    /**
     * Initialize pipelines (called during mod init to ensure they're registered).
     */
    public static void init() {
        // Just accessing the fields is enough to trigger static initialization
    }
}
