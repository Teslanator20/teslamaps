package com.teslamaps.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

import java.util.OptionalDouble;

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
            1536,
            TeslaRenderPipelines.LINES_ESP,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(5.0)))
                    .build(false)
    );

    /**
     * Filled quads that render through walls (ESP).
     */
    public static final RenderLayer FILLED_ESP = RenderLayer.of(
            "teslamaps_filled_esp",
            786432,
            TeslaRenderPipelines.FILLED_ESP,
            RenderLayer.MultiPhaseParameters.builder()
                    .build(false)
    );

    /**
     * Initialize render layers.
     */
    public static void init() {
        // Just accessing the fields triggers static initialization
    }
}
