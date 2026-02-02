package com.teslamaps.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

import java.util.OptionalDouble;

/**
 * Custom render layers for ESP rendering through walls.
 * Based on Odin's CustomRenderLayer.
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
     * Initialize render layers.
     */
    public static void init() {
        // Just accessing the fields triggers static initialization
    }
}
