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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ESPRenderer {

    private static boolean depthTested = false;

    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos,
                                       MultiBufferSource.BufferSource bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        var layer = depthTested ? TeslaRenderLayers.LINES_DEPTH : TeslaRenderLayers.LINES_ESP;
        VertexConsumer buffer = bufferSource.getBuffer(layer);

        renderLineBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);

        bufferSource.endBatch(layer);

        matrices.popPose();
    }

    private static void renderLineBox(PoseStack.Pose pose, VertexConsumer buffer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float r, float g, float b, float a, float lineWidth) {
        int[] edges = {
            0, 1,  1, 5,  5, 4,  4, 0,
            3, 2,  2, 6,  6, 7,  7, 3,
            0, 3,  1, 2,  5, 6,  4, 7
        };

        float[] corners = {
            minX, minY, minZ,  // 0
            maxX, minY, minZ,  // 1
            maxX, maxY, minZ,  // 2
            minX, maxY, minZ,  // 3
            minX, minY, maxZ,  // 4
            maxX, minY, maxZ,  // 5
            maxX, maxY, maxZ,  // 6
            minX, maxY, maxZ   // 7
        };

        for (int i = 0; i < edges.length; i += 2) {
            int i0 = edges[i] * 3;
            int i1 = edges[i + 1] * 3;

            float x0 = corners[i0];
            float y0 = corners[i0 + 1];
            float z0 = corners[i0 + 2];
            float x1 = corners[i1];
            float y1 = corners[i1 + 1];
            float z1 = corners[i1 + 2];

            float dx = x1 - x0;
            float dy = y1 - y0;
            float dz = z1 - z0;

            buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
            buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        }
    }

    public static void drawTracer(PoseStack matrices, Vec3 from, Vec3 to, int color, Vec3 cameraPos,
                                   MultiBufferSource.BufferSource bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        float dx = (float)(to.x - from.x);
        float dy = (float)(to.y - from.y);
        float dz = (float)(to.z - from.z);

        buffer.addVertex(pose, (float)from.x, (float)from.y, (float)from.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(2.0f);
        buffer.addVertex(pose, (float)to.x, (float)to.y, (float)to.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(2.0f);

        bufferSource.endBatch(TeslaRenderLayers.LINES_ESP);

        matrices.popPose();
    }

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos,
                                             MultiBufferSource.BufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return;

        var camera = mc.gameRenderer.getMainCamera();
        float yaw = camera.yRot();
        float pitch = camera.xRot();

        double yawRad = Math.toRadians(-yaw);
        double pitchRad = Math.toRadians(-pitch);
        double cosP = Math.cos(pitchRad);
        Vec3 lookDir = new Vec3(
            Math.sin(yawRad) * cosP,
            Math.sin(pitchRad),
            Math.cos(yawRad) * cosP
        );

        Vec3 tracerStart = cameraPos.add(lookDir.scale(0.5));

        drawTracer(matrices, tracerStart, target, color, cameraPos, bufferSource);
    }

    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawBoxOutline(matrices, box, color, lineWidth, cameraPos, bufferSource);
    }

    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos, boolean throughWalls) {
        depthTested = !throughWalls;
        try { drawBoxOutline(matrices, box, color, lineWidth, cameraPos); }
        finally { depthTested = false; }
    }

    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos, boolean throughWalls) {
        depthTested = !throughWalls;
        try { drawFilledBox(matrices, box, color, cameraPos); }
        finally { depthTested = false; }
    }

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawTracerFromCamera(matrices, target, color, cameraPos, bufferSource);
    }

    public static void drawLine(PoseStack matrices, Vec3 start, Vec3 end, int color, float lineWidth, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawTracer(matrices, start, end, color, cameraPos, bufferSource);
    }

    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos,
                                       MultiBufferSource.BufferSource bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = com.teslamaps.config.TeslaMapsConfig.get().espAlpha; // Use config transparency

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        var layer = depthTested ? TeslaRenderLayers.FILLED_DEPTH : TeslaRenderLayers.FILLED_ESP;
        VertexConsumer buffer = bufferSource.getBuffer(layer);

        renderFilledBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);

        bufferSource.endBatch(layer);
        matrices.popPose();
    }

    private static void renderFilledBox(PoseStack.Pose pose, VertexConsumer buffer,
                                         float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ,
                                         float r, float g, float b, float a) {
        buffer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);

        buffer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
    }

    public static void drawESPBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos,
                                    MultiBufferSource.BufferSource bufferSource) {
        if (com.teslamaps.config.TeslaMapsConfig.get().filledESP) {
            drawFilledBox(matrices, box, color, cameraPos, bufferSource);
        } else {
            drawBoxOutline(matrices, box, color, 3.0f, cameraPos, bufferSource);
        }
    }

    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawFilledBox(matrices, box, color, cameraPos, bufferSource);
    }

    public static void drawESPBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawESPBox(matrices, box, color, cameraPos, bufferSource);
    }

    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return;

        MultiBufferSource.BufferSource immediate = mc.renderBuffers().bufferSource();

        matrices.pushPose();

        var pose = matrices.last().pose();
        float scaleFactor = scale * 0.025f;
        pose.translate((float) (pos.x - cameraPos.x), (float) (pos.y - cameraPos.y), (float) (pos.z - cameraPos.z));
        pose.rotate(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(scaleFactor, -scaleFactor, scaleFactor);

        float textWidth = mc.font.width(text);

        mc.font.drawInBatch(
            text,
            -textWidth / 2f,
            0f,
            0xFFFFFFFF, // base color (white) — § codes in the string override
            true,       // shadow
            pose,
            immediate,
            net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0,          // no background
            15728880    // full brightness
        );

        immediate.endBatch(); // flush so the glyphs actually render this frame
        matrices.popPose();
    }

    public static void drawBeaconBeam(PoseStack matrices, net.minecraft.core.BlockPos pos, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 0.5f;

        Vec3 bottom = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 256, pos.getZ() + 0.5);

        drawLine(matrices, bottom, top, color, 2.0f, cameraPos);
    }
}
