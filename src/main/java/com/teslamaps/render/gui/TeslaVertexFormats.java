package com.teslamaps.render.gui;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;

public class TeslaVertexFormats {

    // Claim the lowest free slot above the vanilla elements. Do NOT hardcode a high
    // id: SkyHanni searches upward from its preferred id for a run of free slots and
    // hard-crashes if one is missing, so anything we take up there breaks it.
    public static final VertexFormatElement SDF_PARAMS =
            registerFirstFree(VertexFormatElement.Type.FLOAT, false, 4);

    private static VertexFormatElement registerFirstFree(VertexFormatElement.Type type,
                                                          boolean normalized, int count) {
        for (int id = 8; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(id, 0, type, normalized, count);
            }
        }
        throw new IllegalStateException("No free vertex format element slot left");
    }

    // Position (3f) + UV0 (2f, local coords from rect center) + SdfParams
    // (halfWidth, halfHeight, radius, extra) + Color (4ub) = 40 bytes/vertex.
    // extra = stroke thickness for outlines, blur radius for shadows.
    public static final VertexFormat SDF = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("SdfParams", SDF_PARAMS)
            .add("Color", VertexFormatElement.COLOR)
            .build();

    public static void init() {
    }
}
