package com.teslamaps.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

/**
 * Custom render layers for ESP rendering through walls.
 * Custom render layers for ESP rendering.
 */
public class TeslaRenderLayers {

    /**
     * Lines that render through walls (ESP).
     */
    public static final RenderLayer LINES_ESP = RenderLayer.of(
            "teslamaps_lines_esp",
            RenderSetup.builder(TeslaRenderPipelines.LINES_ESP)
                    .expectedBufferSize(1536)
                    .build()
    );

    /**
     * Filled quads that render through walls (ESP).
     */
    public static final RenderLayer FILLED_ESP = RenderLayer.of(
            "teslamaps_filled_esp",
            RenderSetup.builder(TeslaRenderPipelines.FILLED_ESP)
                    .expectedBufferSize(786432)
                    .build()
    );

    /**
     * Initialize render layers.
     */
    public static void init() {
        // Just accessing the fields triggers static initialization
    }
}
