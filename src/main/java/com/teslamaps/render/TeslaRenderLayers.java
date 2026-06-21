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
