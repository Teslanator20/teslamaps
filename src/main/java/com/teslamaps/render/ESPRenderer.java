package com.teslamaps.render;

import com.teslamaps.TeslaMaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * ESP Renderer.
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

        // Push and translate by -camera 
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MatrixStack.Entry pose = matrices.peek();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        // Draw 12 edges
        renderLineBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);

        // End batch for this render layer 
        bufferSource.draw(TeslaRenderLayers.LINES_ESP);

        matrices.pop();
    }

    /**
     * Render a line box
     */
    private static void renderLineBox(MatrixStack.Entry pose, VertexConsumer buffer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float r, float g, float b, float a) {
        // Edge indices
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
     * Draw a tracer from camera position to target.
     * Uses the actual camera position for perfect center alignment.
     */
    public static void drawTracerFromCamera(MatrixStack matrices, Vec3d target, int color, Vec3d cameraPos,
                                             VertexConsumerProvider.Immediate bufferSource) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        // Get camera look direction from yaw/pitch
        var camera = mc.gameRenderer.getCamera();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        // Convert to direction vector
        double yawRad = Math.toRadians(-yaw);
        double pitchRad = Math.toRadians(-pitch);
        double cosP = Math.cos(pitchRad);
        Vec3d lookDir = new Vec3d(
            Math.sin(yawRad) * cosP,
            Math.sin(pitchRad),
            Math.cos(yawRad) * cosP
        );

        // Start 0.5 blocks in front of camera to avoid z-fighting
        Vec3d tracerStart = cameraPos.add(lookDir.multiply(0.5));

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

    /**
     * Draw a filled box that renders through walls.
     * Uses WORLD coordinates - camera offset is handled by matrix translation.
     */
    public static void drawFilledBox(MatrixStack matrices, Box box, int color, Vec3d cameraPos,
                                       VertexConsumerProvider.Immediate bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = com.teslamaps.config.TeslaMapsConfig.get().espAlpha; // Use config transparency

        // World coordinates
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MatrixStack.Entry pose = matrices.peek();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.FILLED_ESP);

        // Draw 6 faces as quads
        renderFilledBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);

        bufferSource.draw(TeslaRenderLayers.FILLED_ESP);
        matrices.pop();
    }

    /**
     * Render a filled box (6 quads).
     */
    private static void renderFilledBox(MatrixStack.Entry pose, VertexConsumer buffer,
                                         float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ,
                                         float r, float g, float b, float a) {
        // Bottom face (Y-)
        buffer.vertex(pose, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, minX, minY, maxZ).color(r, g, b, a);

        // Top face (Y+)
        buffer.vertex(pose, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(pose, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, maxY, minZ).color(r, g, b, a);

        // North face (Z-)
        buffer.vertex(pose, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(pose, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, minY, minZ).color(r, g, b, a);

        // South face (Z+)
        buffer.vertex(pose, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, minX, maxY, maxZ).color(r, g, b, a);

        // West face (X-)
        buffer.vertex(pose, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(pose, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, minX, maxY, minZ).color(r, g, b, a);

        // East face (X+)
        buffer.vertex(pose, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(pose, maxX, minY, maxZ).color(r, g, b, a);
    }

    /**
     * Draw ESP box (filled or outline based on config).
     * Automatically chooses between filled and outline mode.
     */
    public static void drawESPBox(MatrixStack matrices, Box box, int color, Vec3d cameraPos,
                                    VertexConsumerProvider.Immediate bufferSource) {
        if (com.teslamaps.config.TeslaMapsConfig.get().filledESP) {
            drawFilledBox(matrices, box, color, cameraPos, bufferSource);
        } else {
            drawBoxOutline(matrices, box, color, 3.0f, cameraPos, bufferSource);
        }
    }

    // Legacy wrapper
    public static void drawFilledBox(MatrixStack matrices, Box box, int color, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getBufferBuilders() == null) return;
        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
        drawFilledBox(matrices, box, color, cameraPos, bufferSource);
    }

    /**
     * Draw ESP box with automatic config check (legacy wrapper).
     */
    public static void drawESPBox(MatrixStack matrices, Box box, int color, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getBufferBuilders() == null) return;
        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
        drawESPBox(matrices, box, color, cameraPos, bufferSource);
    }

    /**
     * Draw text in the world at a specific position.
     */
    public static void drawText(MatrixStack matrices, String text, Vec3d pos, float scale, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        matrices.push();

        // Translate to world position relative to camera
        matrices.translate(
            pos.x - cameraPos.x,
            pos.y - cameraPos.y,
            pos.z - cameraPos.z
        );

        // Rotate to face camera (billboard)
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());

        // Scale (negative to flip text right-side up when facing camera)
        float scaleFactor = scale * 0.025f;
        matrices.scale(-scaleFactor, -scaleFactor, scaleFactor);

        float textWidth = mc.textRenderer.getWidth(text);

        // Draw text using the standard draw method with SEE_THROUGH layer
        mc.textRenderer.draw(
            text,
            -textWidth / 2f,
            0f,
            0xFFFFFFFF, // white color
            true, // shadow
            matrices.peek().getPositionMatrix(),
            immediate,
            net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH,
            0, // no background
            15728880 // full brightness
        );

        // Flush to render the text
        immediate.draw();

        matrices.pop();
    }

    /**
     * Draw a beacon beam at a specific position.
     */
    public static void drawBeaconBeam(MatrixStack matrices, net.minecraft.util.math.BlockPos pos, int color, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 0.5f;

        // Draw a simple vertical line as beacon beam
        Vec3d bottom = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Vec3d top = new Vec3d(pos.getX() + 0.5, pos.getY() + 256, pos.getZ() + 0.5);

        drawLine(matrices, bottom, top, color, 2.0f, cameraPos);
    }
}
