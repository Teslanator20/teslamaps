package com.teslamaps.render;

import com.teslamaps.TeslaMaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * ESP Renderer - copied exactly from Odin's approach.
 * Key: push matrix, translate by -camera, use WORLD coordinates, endBatch.
 */
public class ESPRenderer {

    private static int frameCounter = 0;

    /**
     * Draw a box outline that renders through walls.
     * Uses WORLD coordinates - camera offset is handled by matrix translation.
     */
    public static void drawBoxOutline(MatrixStack matrices, Box box, int color, float lineWidth, Vec3d cameraPos,
                                       VertexConsumerProvider.Immediate bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        // World coordinates - NOT camera relative
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        frameCounter++;
        if (frameCounter % 200 == 0) {
            TeslaMaps.LOGGER.info("[ESPRenderer] Drawing box at world ({},{},{}) to ({},{},{})",
                minX, minY, minZ, maxX, maxY, maxZ);
        }

        // Push and translate by -camera (like Odin does)
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MatrixStack.Entry pose = matrices.peek();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        // Draw 12 edges using Odin's renderLineBox pattern
        renderLineBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);

        // End batch for this render layer (like Odin does)
        bufferSource.draw(TeslaRenderLayers.LINES_ESP);

        matrices.pop();
    }

    /**
     * Render a line box - copied from Odin's PrimitiveRenderer.renderLineBox
     */
    private static void renderLineBox(MatrixStack.Entry pose, VertexConsumer buffer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float r, float g, float b, float a) {
        // Edge indices from Odin
        int[] edges = {
            0, 1,  1, 5,  5, 4,  4, 0,
            3, 2,  2, 6,  6, 7,  7, 3,
            0, 3,  1, 2,  5, 6,  4, 7
        };

        // Corner positions
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

            buffer.vertex(pose, x0, y0, z0).color(r, g, b, a).normal(pose, dx, dy, dz);
            buffer.vertex(pose, x1, y1, z1).color(r, g, b, a).normal(pose, dx, dy, dz);
        }
    }

    /**
     * Draw a tracer from player eye to target.
     * Uses WORLD coordinates.
     */
    public static void drawTracer(MatrixStack matrices, Vec3d from, Vec3d to, int color, Vec3d cameraPos,
                                   VertexConsumerProvider.Immediate bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        // Push and translate by -camera
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MatrixStack.Entry pose = matrices.peek();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        // Direction vector
        float dx = (float)(to.x - from.x);
        float dy = (float)(to.y - from.y);
        float dz = (float)(to.z - from.z);

        // Render line from player to target in WORLD coordinates
        buffer.vertex(pose, (float)from.x, (float)from.y, (float)from.z).color(r, g, b, a).normal(pose, dx, dy, dz);
        buffer.vertex(pose, (float)to.x, (float)to.y, (float)to.z).color(r, g, b, a).normal(pose, dx, dy, dz);

        bufferSource.draw(TeslaRenderLayers.LINES_ESP);

        matrices.pop();
    }

    /**
     * Draw a tracer from player eye position to target.
     * Uses Apollo's approach: start 2 blocks in front of the player's eyes
     * to prevent first-person z-fighting/glitching.
     */
    public static void drawTracerFromCamera(MatrixStack matrices, Vec3d target, int color, Vec3d cameraPos,
                                             VertexConsumerProvider.Immediate bufferSource) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Get player eye position
        Vec3d playerEye = mc.player.getEyePos();

        // Get rotation vector and offset start position 2 blocks in front of eyes (Apollo's approach)
        float partialTicks = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d rotationVec = mc.player.getRotationVec(partialTicks);
        Vec3d tracerStart = playerEye.add(rotationVec.multiply(2.0));

        drawTracer(matrices, tracerStart, target, color, cameraPos, bufferSource);
    }

    // Legacy methods that need BufferSource - for compatibility
    public static void drawBoxOutline(MatrixStack matrices, Box box, int color, float lineWidth, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getBufferBuilders() == null) return;
        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
        drawBoxOutline(matrices, box, color, lineWidth, cameraPos, bufferSource);
    }

    public static void drawTracerFromCamera(MatrixStack matrices, Vec3d target, int color, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getBufferBuilders() == null) return;
        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
        drawTracerFromCamera(matrices, target, color, cameraPos, bufferSource);
    }

    public static void drawLine(MatrixStack matrices, Vec3d start, Vec3d end, int color, float lineWidth, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getBufferBuilders() == null) return;
        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
        drawTracer(matrices, start, end, color, cameraPos, bufferSource);
    }

    public static void drawFilledBox(MatrixStack matrices, Box box, int color, Vec3d cameraPos) {
        drawBoxOutline(matrices, box, color, 2.0f, cameraPos);
    }
}
