package com.teslamaps.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ESPRenderer {

    private static boolean depthTested = false;

    /** Alpha multiplier for everything drawn here, world overlay toggle fade. */
    public static float fade = 1f;

    // Frame batching: inside the world pass the per-box endBatch is skipped, consecutive boxes of a
    // layer share one draw, and FeatureRegistry.world flushes at the end. Boxes outside the view
    // frustum are dropped. Submission order is unchanged, so the picture is the same.
    private static boolean frameBatch = false;
    private static net.minecraft.client.renderer.culling.Frustum frustum;
    private static SubmitNodeCollector collector;

    public static void beginFrame(net.minecraft.client.renderer.culling.Frustum cull, SubmitNodeCollector nodes) {
        frameBatch = true;
        frustum = cull;
        collector = nodes;
    }

    public static void endFrame() {
        frameBatch = false;
        frustum = null;
        collector = null;
    }

    private static void finish(MultiBufferSource.BufferSource src,
                               net.minecraft.client.renderer.rendertype.RenderType layer) {
        if (!frameBatch) src.endBatch(layer);
    }

    /** Bounding box at the entity's rendered (partial-tick) position. */
    public static AABB lerpBox(net.minecraft.world.entity.Entity e) {
        AABB b = e.getBoundingBox();
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double dx = net.minecraft.util.Mth.lerp(pt, e.xo, e.getX()) - e.getX();
        double dy = net.minecraft.util.Mth.lerp(pt, e.yo, e.getY()) - e.getY();
        double dz = net.minecraft.util.Mth.lerp(pt, e.zo, e.getZ()) - e.getZ();
        return dx == 0 && dy == 0 && dz == 0 ? b : b.move(dx, dy, dz);
    }

    private static boolean culled(AABB box) {
        return frustum != null && !frustum.isVisible(box);
    }

    // logistic falloff over distance, 0.5 at espFadeDistance
    private static float fade(AABB box, Vec3 cam) {
        float d = com.teslamaps.config.TeslaMapsConfig.get().espFadeDistance;
        if (d <= 0f || cam == null) return 1f;
        double dx = (box.minX + box.maxX) * 0.5 - cam.x;
        double dy = (box.minY + box.maxY) * 0.5 - cam.y;
        double dz = (box.minZ + box.maxZ) * 0.5 - cam.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (float) (1.0 / (1.0 + Math.exp(6.0 * (dist - d) / d)));
    }

    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos,
                                       MultiBufferSource.BufferSource bufferSource) {
        if (culled(box)) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 1.0f;
        a *= fade(box, cameraPos);
        if (a < 2f / 255f) return;

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

        finish(bufferSource, layer);

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
        drawTracer(matrices, from, to, color, cameraPos, bufferSource, 2.0f);
    }

    public static void drawTracer(PoseStack matrices, Vec3 from, Vec3 to, int color, Vec3 cameraPos,
                                   MultiBufferSource.BufferSource bufferSource, float lineWidth) {
        if (com.teslamaps.config.TeslaMapsConfig.get().glowRibbons) {
            int c = (color >>> 24) == 0 ? color | 0xFF000000 : color;
            drawRibbon(matrices, from, to, c, c, cameraPos, bufferSource);
            return;
        }
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 1.0f;

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = bufferSource.getBuffer(TeslaRenderLayers.LINES_ESP);

        float dx = (float)(to.x - from.x);
        float dy = (float)(to.y - from.y);
        float dz = (float)(to.z - from.z);

        buffer.addVertex(pose, (float)from.x, (float)from.y, (float)from.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        buffer.addVertex(pose, (float)to.x, (float)to.y, (float)to.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);

        finish(bufferSource, TeslaRenderLayers.LINES_ESP);

        matrices.popPose();
    }

    /**
     * Camera-facing band with a bright core and soft edges, colour blended from one end to the other.
     * Width follows the distance to the camera, so it keeps a constant size on screen like a line.
     */
    public static void drawRibbon(PoseStack matrices, Vec3 from, Vec3 to, int colorFrom, int colorTo, Vec3 cameraPos,
                                  MultiBufferSource.BufferSource bufferSource) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        if (dx * dx + dy * dy + dz * dz < 1.0E-8) return;
        float k = 0.004f * com.teslamaps.config.TeslaMapsConfig.get().glowRibbonWidth;
        double avx = from.x - cameraPos.x, avy = from.y - cameraPos.y, avz = from.z - cameraPos.z;
        double asx = dy * avz - dz * avy, asy = dz * avx - dx * avz, asz = dx * avy - dy * avx;
        double alen = Math.sqrt(asx * asx + asy * asy + asz * asz);
        double bvx = to.x - cameraPos.x, bvy = to.y - cameraPos.y, bvz = to.z - cameraPos.z;
        double bsx = dy * bvz - dz * bvy, bsy = dz * bvx - dx * bvz, bsz = dx * bvy - dy * bvx;
        double blen = Math.sqrt(bsx * bsx + bsy * bsy + bsz * bsz);
        if (alen < 1.0E-9 || blen < 1.0E-9) return;
        double aw = k * Math.max(0.05, Math.sqrt(avx * avx + avy * avy + avz * avz)) / alen;
        double bw = k * Math.max(0.05, Math.sqrt(bvx * bvx + bvy * bvy + bvz * bvz)) / blen;
        float sa0 = (float) (asx * aw), sa1 = (float) (asy * aw), sa2 = (float) (asz * aw);
        float sb0 = (float) (bsx * bw), sb1 = (float) (bsy * bw), sb2 = (float) (bsz * bw);

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buf = bufferSource.getBuffer(TeslaRenderLayers.FILLED_ESP);

        float ax = (float) from.x, ay = (float) from.y, az = (float) from.z;
        float bx = (float) to.x, by = (float) to.y, bz = (float) to.z;
        float car = ((colorFrom >> 16) & 0xFF) / 255f, cag = ((colorFrom >> 8) & 0xFF) / 255f,
                cab = (colorFrom & 0xFF) / 255f, caa = ((colorFrom >>> 24) & 0xFF) / 255f;
        float cbr = ((colorTo >> 16) & 0xFF) / 255f, cbg = ((colorTo >> 8) & 0xFF) / 255f,
                cbb = (colorTo & 0xFF) / 255f, cba = ((colorTo >>> 24) & 0xFF) / 255f;
        for (int i = 0; i < 3; i++) {
            float o0 = RIBBON_OFF[i], o1 = RIBBON_OFF[i + 1];
            float m0 = RIBBON_ALPHA[i], m1 = RIBBON_ALPHA[i + 1];
            // both windings: the pipeline culls back faces, exactly one of them survives
            for (int w = 0; w < 2; w++) {
                float p0 = w == 0 ? o0 : o1, p1 = w == 0 ? o1 : o0;
                float n0 = w == 0 ? m0 : m1, n1 = w == 0 ? m1 : m0;
                buf.addVertex(pose, ax + sa0 * p0, ay + sa1 * p0, az + sa2 * p0).setColor(car, cag, cab, caa * n0);
                buf.addVertex(pose, bx + sb0 * p0, by + sb1 * p0, bz + sb2 * p0).setColor(cbr, cbg, cbb, cba * n0);
                buf.addVertex(pose, bx + sb0 * p1, by + sb1 * p1, bz + sb2 * p1).setColor(cbr, cbg, cbb, cba * n1);
                buf.addVertex(pose, ax + sa0 * p1, ay + sa1 * p1, az + sa2 * p1).setColor(car, cag, cab, caa * n1);
            }
        }
        finish(bufferSource, TeslaRenderLayers.FILLED_ESP);
        matrices.popPose();
    }

    // across the band: soft edge, solid core, soft edge
    private static final float[] RIBBON_OFF = {-1f, -0.3f, 0.3f, 1f};
    private static final float[] RIBBON_ALPHA = {0f, 1f, 1f, 0f};

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos,
                                             MultiBufferSource.BufferSource bufferSource) {
        drawTracerFromCamera(matrices, target, color, cameraPos, bufferSource, 2.0f);
    }

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos,
                                             MultiBufferSource.BufferSource bufferSource, float lineWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;

        var camera = mc.gameRenderer.mainCamera();
        if (camera.isDetached()) {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 start = mc.player.getEyePosition(pt).add(mc.player.getViewVector(pt).scale(0.2));
            drawTracer(matrices, start, target, color, cameraPos, bufferSource, lineWidth);
            return;
        }
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

        drawTracer(matrices, tracerStart, target, color, cameraPos, bufferSource, lineWidth);
    }

    public static void drawBoxOutline(PoseStack matrices, AABB box, int color, float lineWidth, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = WorldBuffers.INSTANCE;
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

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos,
                                            boolean throughWalls) {
        depthTested = !throughWalls;
        try { drawTracerFromCamera(matrices, target, color, cameraPos); }
        finally { depthTested = false; }
    }

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos,
                                            boolean throughWalls, float lineWidth) {
        depthTested = !throughWalls;
        try { drawTracerFromCamera(matrices, target, color, cameraPos, lineWidth); }
        finally { depthTested = false; }
    }

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos, float lineWidth) {
        drawTracerFromCamera(matrices, target, color, cameraPos, WorldBuffers.INSTANCE, lineWidth);
    }

    public static void drawTracerFromCamera(PoseStack matrices, Vec3 target, int color, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = WorldBuffers.INSTANCE;
        drawTracerFromCamera(matrices, target, color, cameraPos, bufferSource);
    }

    public static void drawLine(PoseStack matrices, Vec3 from, Vec3 to, int color, float lineWidth, Vec3 cameraPos,
                                boolean throughWalls) {
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 1.0f;
        var layer = throughWalls ? TeslaRenderLayers.LINES_ESP : TeslaRenderLayers.LINES_DEPTH;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = src.getBuffer(layer);
        float dx = (float) (to.x - from.x), dy = (float) (to.y - from.y), dz = (float) (to.z - from.z);
        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        finish(src, layer);
        matrices.popPose();
    }

    /** Connected segments pts[i-1] -> pts[i], segment i drawn with widths[i]; widths[i] <= 0 skips it. */
    public static void drawPolyline(PoseStack matrices, java.util.List<Vec3> pts, float[] widths, int color, Vec3 cameraPos,
                                    boolean throughWalls) {
        if (pts.size() < 2) return;
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 1.0f;
        var layer = throughWalls ? TeslaRenderLayers.LINES_ESP : TeslaRenderLayers.LINES_DEPTH;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = src.getBuffer(layer);
        Vec3 from = pts.get(0);
        for (int i = 1, n = pts.size(); i < n; i++) {
            Vec3 to = pts.get(i);
            float w = widths[i];
            if (w > 0) {
                float dx = (float) (to.x - from.x), dy = (float) (to.y - from.y), dz = (float) (to.z - from.z);
                buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(w);
                buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(w);
            }
            from = to;
        }
        finish(src, layer);
        matrices.popPose();
    }

    /** Connected segments pts[i-1] -> pts[i], segment i drawn in colors[i] (ARGB). */
    public static void drawPolyline(PoseStack matrices, java.util.List<Vec3> pts, int[] colors, float width, Vec3 cameraPos,
                                    boolean throughWalls) {
        if (pts.size() < 2) return;
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        var layer = throughWalls ? TeslaRenderLayers.LINES_ESP : TeslaRenderLayers.LINES_DEPTH;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = src.getBuffer(layer);
        Vec3 from = pts.get(0);
        for (int i = 1, n = pts.size(); i < n; i++) {
            Vec3 to = pts.get(i);
            int col = colors[i];
            float dx = (float) (to.x - from.x), dy = (float) (to.y - from.y), dz = (float) (to.z - from.z);
            buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(col).setNormal(pose, dx, dy, dz).setLineWidth(width);
            buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(col).setNormal(pose, dx, dy, dz).setLineWidth(width);
            from = to;
        }
        finish(src, layer);
        matrices.popPose();
    }

    public static void drawLine(PoseStack matrices, Vec3 start, Vec3 end, int color, float lineWidth, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = WorldBuffers.INSTANCE;
        drawTracer(matrices, start, end, color, cameraPos, bufferSource);
    }

    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos,
                                       MultiBufferSource.BufferSource bufferSource) {
        if (culled(box)) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = com.teslamaps.config.TeslaMapsConfig.get().espAlpha; // Use config transparency
        a *= fade(box, cameraPos);
        if (a < 2f / 255f) return;
        if (frameBatch && com.teslamaps.config.TeslaMapsConfig.get().espInstancing && InstancedBoxes.usable()
                && InstancedBoxes.acceptsPose(matrices.last().pose())) {
            InstancedBoxes.add(depthTested, box, cameraPos, r, g, b, a);
            return;
        }

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

        finish(bufferSource, layer);
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

    /** Double-sided quad, corners in world coordinates, depth tested. */
    public static void drawQuad(PoseStack matrices, float[] c, int color, Vec3 cameraPos) {
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f * fade;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buf = src.getBuffer(TeslaRenderLayers.FILLED_DEPTH);
        for (int i = 0; i < 4; i++) buf.addVertex(pose, c[i * 3], c[i * 3 + 1], c[i * 3 + 2]).setColor(r, g, b, a);
        for (int i = 3; i >= 0; i--) buf.addVertex(pose, c[i * 3], c[i * 3 + 1], c[i * 3 + 2]).setColor(r, g, b, a);
        finish(src, TeslaRenderLayers.FILLED_DEPTH);
        matrices.popPose();
    }

    private static final int CIRCLE_SEGMENTS = 64;
    private static final float[] CIRCLE_COS = new float[CIRCLE_SEGMENTS + 1];
    private static final float[] CIRCLE_SIN = new float[CIRCLE_SEGMENTS + 1];
    static {
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            double a = Math.PI * 2 * i / CIRCLE_SEGMENTS;
            CIRCLE_COS[i] = (float) Math.cos(a);
            CIRCLE_SIN[i] = (float) Math.sin(a);
        }
    }

    /** Horizontal ring around a point, depth tested. */
    public static void drawCircle(PoseStack matrices, double cx, double cy, double cz, double radius, int color, float lineWidth, Vec3 cameraPos) {
        drawCircle(matrices, cx, cy, cz, radius, color, lineWidth, cameraPos, false);
    }

    public static void drawCircle(PoseStack matrices, double cx, double cy, double cz, double radius, int color, float lineWidth, Vec3 cameraPos,
                                  boolean throughWalls) {
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 1f;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        var layer = throughWalls ? TeslaRenderLayers.LINES_ESP : TeslaRenderLayers.LINES_DEPTH;
        VertexConsumer buf = src.getBuffer(layer);
        float y = (float) cy, rad = (float) radius;
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float x0 = (float) cx + CIRCLE_COS[i] * rad, z0 = (float) cz + CIRCLE_SIN[i] * rad;
            float x1 = (float) cx + CIRCLE_COS[i + 1] * rad, z1 = (float) cz + CIRCLE_SIN[i + 1] * rad;
            float dx = x1 - x0, dz = z1 - z0;
            buf.addVertex(pose, x0, y, z0).setColor(r, g, b, a).setNormal(pose, dx, 0, dz).setLineWidth(lineWidth);
            buf.addVertex(pose, x1, y, z1).setColor(r, g, b, a).setNormal(pose, dx, 0, dz).setLineWidth(lineWidth);
        }
        finish(src, layer);
        matrices.popPose();
    }

    /** Filled horizontal disc, double sided, depth tested. */
    public static void drawDisc(PoseStack matrices, double cx, double cy, double cz, double radius, int color, Vec3 cameraPos) {
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f * fade;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buf = src.getBuffer(TeslaRenderLayers.FILLED_DEPTH);
        float x = (float) cx, y = (float) cy, z = (float) cz, rad = (float) radius;
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float x0 = x + CIRCLE_COS[i] * rad, z0 = z + CIRCLE_SIN[i] * rad;
            float x1 = x + CIRCLE_COS[i + 1] * rad, z1 = z + CIRCLE_SIN[i + 1] * rad;
            buf.addVertex(pose, x, y, z).setColor(r, g, b, a);
            buf.addVertex(pose, x0, y, z0).setColor(r, g, b, a);
            buf.addVertex(pose, x1, y, z1).setColor(r, g, b, a);
            buf.addVertex(pose, x1, y, z1).setColor(r, g, b, a);
            buf.addVertex(pose, x1, y, z1).setColor(r, g, b, a);
            buf.addVertex(pose, x1, y, z1).setColor(r, g, b, a);
            buf.addVertex(pose, x0, y, z0).setColor(r, g, b, a);
            buf.addVertex(pose, x, y, z).setColor(r, g, b, a);
        }
        finish(src, TeslaRenderLayers.FILLED_DEPTH);
        matrices.popPose();
    }

    public static void drawFilledBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = WorldBuffers.INSTANCE;
        drawFilledBox(matrices, box, color, cameraPos, bufferSource);
    }

    public static void drawESPBox(PoseStack matrices, AABB box, int color, Vec3 cameraPos) {
        MultiBufferSource.BufferSource bufferSource = WorldBuffers.INSTANCE;
        drawESPBox(matrices, box, color, cameraPos, bufferSource);
    }

    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos) {
        drawText(matrices, text, pos, scale, cameraPos, true);
    }

    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos,
                                boolean scaleWithDistance) {
        drawText(matrices, text, pos, scale, cameraPos, scaleWithDistance, true);
    }

    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos,
                                boolean scaleWithDistance, boolean throughWalls) {
        drawText(matrices, text, pos, scale, cameraPos, scaleWithDistance, throughWalls, 0xFFFFFFFF, 0);
    }

    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos,
                                boolean scaleWithDistance, boolean throughWalls, int color) {
        drawText(matrices, text, pos, scale, cameraPos, scaleWithDistance, throughWalls, color, 0);
    }

    public static void drawText(PoseStack matrices, String text, Vec3 pos, float scale, Vec3 cameraPos,
                                boolean scaleWithDistance, boolean throughWalls, int color, int background) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;

        // grow with distance so text stays readable far away (constant apparent size beyond 12 blocks)
        float dist = (float) pos.distanceTo(cameraPos);
        float scaleFactor = scale * 0.025f * (scaleWithDistance ? Math.max(1f, dist / 12f) : 1f);
        float textWidth = mc.font.width(text);
        if (frustum != null) {
            double r = Math.max(textWidth, 9f) * scaleFactor;
            if (culled(new AABB(pos.x - r, pos.y - r, pos.z - r, pos.x + r, pos.y + r, pos.z + r))) return;
        }

        matrices.pushPose();

        var pose = matrices.last().pose();
        pose.translate((float) (pos.x - cameraPos.x), (float) (pos.y - cameraPos.y), (float) (pos.z - cameraPos.z));
        pose.rotate(mc.gameRenderer.mainCamera().rotation());
        pose.scale(scaleFactor, -scaleFactor, scaleFactor);

        TEXT.pose = pose;
        TEXT.mode = throughWalls ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
                                 : net.minecraft.client.gui.Font.DisplayMode.NORMAL;
        mc.font.prepareText(text, -textWidth / 2f, 0f, color, true, background).visit(TEXT);
        TEXT.pose = null;

        matrices.popPose();
    }

    private static final TextSink TEXT = new TextSink();

    private static final class TextSink implements net.minecraft.client.gui.Font.GlyphVisitor {
        org.joml.Matrix4f pose;
        net.minecraft.client.gui.Font.DisplayMode mode;

        @Override
        public void acceptRenderable(net.minecraft.client.gui.font.TextRenderable r) {
            r.render(pose, WorldBuffers.INSTANCE.getBuffer(r.renderType(mode)), 15728880, false);
        }
    }

    // one buffer + one endBatch for many boxes; only one layer may be open at a time
    private static PoseStack batchMatrices;
    private static PoseStack.Pose batchPose;
    private static VertexConsumer batchBuffer;
    private static MultiBufferSource.BufferSource batchSource;
    private static net.minecraft.client.renderer.rendertype.RenderType batchLayer;
    private static boolean batchFilled;
    private static Vec3 batchCam;

    public static void beginBoxBatch(PoseStack matrices, Vec3 cameraPos, boolean filled, boolean throughWalls) {
        endBoxBatch();
        batchFilled = filled;
        batchCam = cameraPos;
        batchSource = WorldBuffers.INSTANCE;
        batchMatrices = matrices;
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        batchPose = matrices.last();
        if (filled) {
            batchLayer = throughWalls ? TeslaRenderLayers.FILLED_ESP : TeslaRenderLayers.FILLED_DEPTH;
        } else {
            batchLayer = throughWalls ? TeslaRenderLayers.LINES_ESP : TeslaRenderLayers.LINES_DEPTH;
        }
        batchBuffer = batchSource.getBuffer(batchLayer);
    }

    public static void batchBox(AABB box, int color, float lineWidth) {
        if (batchBuffer == null || culled(box)) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 1.0f;
        a *= fade(box, batchCam);
        if (a < 2f / 255f) return;
        if (batchFilled) {
            renderFilledBox(batchPose, batchBuffer, (float) box.minX, (float) box.minY, (float) box.minZ,
                    (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        } else {
            renderLineBox(batchPose, batchBuffer, (float) box.minX, (float) box.minY, (float) box.minZ,
                    (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a, lineWidth);
        }
    }

    public static void endBoxBatch() {
        if (batchBuffer == null) return;
        batchSource.endBatch(batchLayer);
        batchMatrices.popPose();
        batchBuffer = null;
        batchPose = null;
        batchSource = null;
        batchMatrices = null;
        batchLayer = null;
    }

    public static void drawBeaconBeam(PoseStack matrices, net.minecraft.core.BlockPos pos, int color, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * fade;
        if (a == 0) a = 0.5f;

        if (com.teslamaps.config.TeslaMapsConfig.get().realBeaconBeam && collector != null) {
            double dx = cameraPos.x - (pos.getX() + 0.5);
            double dz = cameraPos.z - (pos.getZ() + 0.5);
            float scale = mc.player != null && mc.player.isScoping() ? 1f
                    : Math.max(1f, (float) Math.sqrt(dx * dx + dz * dz) / 96f);
            float time = Math.floorMod(mc.level.getGameTime(), 40L) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            matrices.pushPose();
            matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            net.minecraft.client.renderer.blockentity.BeaconRenderer.submitBeaconBeam(matrices,
                    collector,
                    net.minecraft.client.renderer.blockentity.BeaconRenderer.BEAM_LOCATION,
                    1f, time, 0, 319, color | 0xFF000000, 0.2f * scale, 0.25f * scale);
            matrices.popPose();
            return;
        }

        Vec3 bottom = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 256, pos.getZ() + 0.5);

        if (com.teslamaps.config.TeslaMapsConfig.get().glowRibbons) {
            int c = (color >>> 24) == 0 ? (color & 0xFFFFFF) | 0x80000000 : color;
            drawRibbon(matrices, bottom, top, c, c & 0xFFFFFF, cameraPos, WorldBuffers.INSTANCE);
            return;
        }
        drawLine(matrices, bottom, top, color, 2.0f, cameraPos);
    }
}
