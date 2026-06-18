package com.teslamaps.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos,
                                       MultiBufferSource.BufferSource bufferSource) {
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
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        // Draw 12 edges
        renderLineBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);

        // End batch for this render layer 
        bufferSource.endBatch(TeslaRenderLayers.LINES_ESP);

        matrices.popPose();
    }

    /**
     * Render a line box
     */
    private static void renderLineBox(PoseStack.Pose pose, VertexConsumer buffer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float r, float g, float b, float a, float lineWidth) {
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

            buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
            buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        }
    }

    /**
     * Draw a tracer from player eye to target.
     * Uses WORLD coordinates.
     */
    public static void drawTracer(PoseStack matrices, Vec3 from, Vec3 to, int color, Vec3 cameraPos,
                                   MultiBufferSource.BufferSource bufferSource) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        // Push and translate by -camera
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        // Direction vector
        float dx = (float)(to.x - from.x);
        float dy = (float)(to.y - from.y);
        float dz = (float)(to.z - from.z);

        // Render line from player to target in WORLD coordinates
        buffer.addVertex(pose, (float)from.x, (float)from.y, (float)from.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(2.0f);
        buffer.addVertex(pose, (float)to.x, (float)to.y, (float)to.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(2.0f);

        bufferSource.endBatch(TeslaRenderLayers.LINES_ESP);

        matrices.popPose();
    }

    /**
     * Draw a tracer from camera position to target.
     * Uses the actual camera position for perfect center alignment.
     */
    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos,
                                             MultiBufferSource.BufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return;

        // Get camera look direction from yaw/pitch
        var camera = mc.gameRenderer.getMainCamera();
        float yaw = camera.yRot();
        float pitch = camera.xRot();

        // Convert to direction vector
        double yawRad = Math.toRadians(-yaw);
        double pitchRad = Math.toRadians(-pitch);
        double cosP = Math.cos(pitchRad);
        Vec3 lookDir = new Vec3(
            Math.sin(yawRad) * cosP,
            Math.sin(pitchRad),
            Math.cos(yawRad) * cosP
        );

        // Start 0.5 blocks in front of camera to avoid z-fighting
        Vec3 tracerStart = cameraPos.add(lookDir.scale(0.5));

        drawTracer(matrices, tracerStart, target, color, cameraPos, bufferSource);
    }

    // Legacy methods that need BufferSource - for compatibility
    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawBoxOutline(matrices, box, color, lineWidth, cameraPos, bufferSource);
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

    /**
     * Draw a filled box that renders through walls.
     * Uses WORLD coordinates - camera offset is handled by matrix translation.
     */
    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos,
                                       MultiBufferSource.BufferSource bufferSource) {
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

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.FILLED_ESP);

        // Draw 6 faces as quads
        renderFilledBox(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);

        bufferSource.endBatch(TeslaRenderLayers.FILLED_ESP);
        matrices.popPose();
    }

    /**
     * Render a filled box (6 quads).
     */
    private static void renderFilledBox(PoseStack.Pose pose, VertexConsumer buffer,
                                         float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ,
                                         float r, float g, float b, float a) {
        // Bottom face (Y-)
        buffer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);

        // Top face (Y+)
        buffer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);

        // North face (Z-)
        buffer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);

        // South face (Z+)
        buffer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);

        // West face (X-)
        buffer.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);

        // East face (X+)
        buffer.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
    }

    /**
     * Draw ESP box (filled or outline based on config).
     * Automatically chooses between filled and outline mode.
     */
    public static void drawESPBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos,
                                    MultiBufferSource.BufferSource bufferSource) {
        if (com.teslamaps.config.TeslaMapsConfig.get().filledESP) {
            drawFilledBox(matrices, box, color, cameraPos, bufferSource);
        } else {
            drawBoxOutline(matrices, box, color, 3.0f, cameraPos, bufferSource);
        }
    }

    // Legacy wrapper
    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawFilledBox(matrices, box, color, cameraPos, bufferSource);
    }

    /**
     * Draw ESP box with automatic config check (legacy wrapper).
     */
    public static void drawESPBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        drawESPBox(matrices, box, color, cameraPos, bufferSource);
    }

    /**
     * Draw text in the world at a specific position.
     */
    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return;

        MultiBufferSource.BufferSource immediate = mc.renderBuffers().bufferSource();

        matrices.pushPose();

        // Translate to world position relative to camera
        matrices.translate(
            pos.x - cameraPos.x,
            pos.y - cameraPos.y,
            pos.z - cameraPos.z
        );

        // Rotate to face camera (billboard)
        matrices.mulPose(mc.gameRenderer.getMainCamera().rotation());

        // Scale (negative to flip text right-side up when facing camera)
        float scaleFactor = scale * 0.025f;
        matrices.scale(-scaleFactor, -scaleFactor, scaleFactor);

        float textWidth = mc.font.width(text);

        // Draw text using the standard draw method with SEE_THROUGH layer
        mc.font.drawInBatch(
            text,
            -textWidth / 2f,
            0f,
            0xFFFFFFFF, // white color
            true, // shadow
            matrices.last().pose(),
            immediate,
            net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0, // no background
            15728880 // full brightness
        );

        // Flush to render the text
        immediate.endBatch();

        matrices.popPose();
    }

    /**
     * Draw a beacon beam at a specific position.
     */
    public static void drawBeaconBeam(PoseStack matrices, net.minecraft.core.BlockPos pos, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 0.5f;

        // Draw a simple vertical line as beacon beam
        Vec3 bottom = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 256, pos.getZ() + 0.5);

        drawLine(matrices, bottom, top, color, 2.0f, cameraPos);
    }
}
