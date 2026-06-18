package com.teslamaps.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Custom render layers for ESP rendering through walls.
 * Custom render layers for ESP rendering.
 */
public class TeslaRenderLayers {

    /**
     * Lines that render through walls (ESP).
     */
    public static final RenderType LINES_ESP = RenderType.create(
            "teslamaps_lines_esp",
            RenderSetup.builder(TeslaRenderPipelines.LINES_ESP)
                    .bufferSize(1536)
                    .createRenderSetup()
    );

    /**
     * Filled quads that render through walls (ESP).
     */
    public static final RenderType FILLED_ESP = RenderType.create(
            "teslamaps_filled_esp",
            RenderSetup.builder(TeslaRenderPipelines.FILLED_ESP)
                    .bufferSize(786432)
                    .createRenderSetup()
    );

    /**
     * Initialize render layers.
     */
    public static void init() {
        // Just accessing the fields triggers static initialization
    }
}
