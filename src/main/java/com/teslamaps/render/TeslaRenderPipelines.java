/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;

public class TeslaRenderPipelines {

    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, true);

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

    public static void init() {
    }
}
