package com.teslamaps.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public class TeslaRenderLayers {

    public static final RenderType LINES_ESP = RenderType.create(
            "teslamaps_lines_esp",
            RenderSetup.builder(TeslaRenderPipelines.LINES_ESP)
                    .bufferSize(1536)
                    .createRenderSetup()
    );

    public static final RenderType FILLED_ESP = RenderType.create(
            "teslamaps_filled_esp",
            RenderSetup.builder(TeslaRenderPipelines.FILLED_ESP)
                    .bufferSize(786432)
                    .createRenderSetup()
    );

    public static final RenderType LINES_DEPTH = RenderType.create(
            "teslamaps_lines_depth",
            RenderSetup.builder(TeslaRenderPipelines.LINES_DEPTH)
                    .bufferSize(1536)
                    .createRenderSetup()
    );

    public static final RenderType FILLED_DEPTH = RenderType.create(
            "teslamaps_filled_depth",
            RenderSetup.builder(TeslaRenderPipelines.FILLED_DEPTH)
                    .bufferSize(786432)
                    .createRenderSetup()
    );

    public static void init() {
    }
}
