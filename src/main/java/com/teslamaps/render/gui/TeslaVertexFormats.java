package com.teslamaps.render.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.joml.Matrix3x2fc;

public class TeslaVertexFormats {

    // Position (3f) + UV0 (2f, local coords from rect center) + UV3 (2f) + LineWidth (1f) + Color (4ub).
    // BufferBuilder only writes its fixed attribute names, so the four SdfParams floats
    // (halfWidth, halfHeight, radius, extra) ride in UV3.xy, LineWidth and Position.z;
    // the vertex shader rebuilds them and drops z. extra = stroke thickness for outlines,
    // blur radius for shadows.
    public static final VertexFormat SDF = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .build();

    public static void vertex(VertexConsumer consumer, Matrix3x2fc pose, float x, float y, float u, float v,
                              float p0, float p1, float p2, float p3, int color) {
        float px = pose.m00() * x + pose.m10() * y + pose.m20();
        float py = pose.m01() * x + pose.m11() * y + pose.m21();
        consumer.addVertex(px, py, p3).setUv(u, v).setUv3(p0, p1).setLineWidth(p2).setColor(color);
    }

    public static void init() {
    }
}
