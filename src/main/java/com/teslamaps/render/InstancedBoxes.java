package com.teslamaps.render;

// GPU instancing for filled ESP boxes, idea from Skyblocker's FilledBoxInstancedFeatureRenderer:
// one unit cube, every box is an instance, one draw per 341 boxes instead of 24 vertices each.
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.teslamaps.TeslaMaps;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class InstancedBoxes {

    private static final int PER_DRAW = 341;
    private static final int CHUNK_BYTES = 16384;
    private static final int FLOATS = 12;

    private static final float[][] data = {new float[FLOATS * 64], new float[FLOATS * 64]};
    private static final int[] count = new int[2];
    private static final Matrix4f pose = new Matrix4f();
    private static boolean poseSet;

    private static GpuBuffer cube, ubo;
    private static ByteBuffer staging;
    private static boolean broken;
    private static GpuBufferSlice transforms;
    private static final Matrix4f mv = new Matrix4f();
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private InstancedBoxes() {}

    public static boolean usable() {
        return !broken;
    }

    /** First box of the frame fixes the pose; a box with a different pose goes the normal way. */
    public static boolean acceptsPose(Matrix4f m) {
        if (!poseSet) {
            pose.set(m);
            poseSet = true;
            return true;
        }
        return pose.equals(m);
    }

    public static void add(boolean depth, AABB box, Vec3 cam, float r, float g, float b, float a) {
        int list = depth ? 1 : 0;
        float[] d = data[list];
        int o = count[list] * FLOATS;
        if (o + FLOATS > d.length) data[list] = d = java.util.Arrays.copyOf(d, d.length * 2);
        d[o] = (float) (box.minX - cam.x);
        d[o + 1] = (float) (box.minY - cam.y);
        d[o + 2] = (float) (box.minZ - cam.z);
        d[o + 4] = (float) (box.maxX - cam.x);
        d[o + 5] = (float) (box.maxY - cam.y);
        d[o + 6] = (float) (box.maxZ - cam.z);
        d[o + 8] = r;
        d[o + 9] = g;
        d[o + 10] = b;
        d[o + 11] = a;
        count[list]++;
    }

    public static boolean pending() {
        return count[0] + count[1] > 0;
    }

    public static void clear() {
        count[0] = count[1] = 0;
        poseSet = false;
        transforms = null;
    }

    private static int chunks(int n) {
        return (n + PER_DRAW - 1) / PER_DRAW;
    }

    /** Before the render pass: box data into the uniform buffer. */
    public static void upload(CommandEncoder enc) {
        if (!pending()) return;
        if (broken) {
            clear();
            return;
        }
        try {
            GpuDevice device = RenderSystem.getDevice();
            if (cube == null) cube = createCube(device);
            int total = chunks(count[0]) + chunks(count[1]);
            long bytes = (long) total * CHUNK_BYTES;
            if (ubo == null || ubo.size() < bytes) {
                if (ubo != null) ubo.close();
                ubo = device.createBuffer(() -> "teslamaps esp boxes", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, bytes);
            }
            if (staging == null || staging.capacity() < bytes) {
                if (staging != null) MemoryUtil.memFree(staging);
                staging = MemoryUtil.memAlloc((int) bytes).order(java.nio.ByteOrder.nativeOrder());
            }
            staging.clear();
            for (int list = 0; list < 2; list++) {
                float[] d = data[list];
                int n = count[list];
                for (int c = 0; c < chunks(n); c++) {
                    int start = staging.position();
                    int from = c * PER_DRAW, to = Math.min(n, from + PER_DRAW);
                    for (int i = from * FLOATS; i < to * FLOATS; i++) staging.putFloat(d[i]);
                    staging.position(start + CHUNK_BYTES);
                }
            }
            staging.flip();
            enc.writeToBuffer(ubo.slice(0, bytes), staging);
            mv.set(RenderSystem.getModelViewStack()).mul(pose);
            transforms = RenderSystem.getDynamicUniforms().writeTransform(mv, WHITE, NO_OFFSET, IDENTITY);
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(36);
        } catch (Throwable t) {
            broken = true;
            TeslaMaps.LOGGER.warn("Instanced ESP boxes disabled", t);
            clear();
        }
    }

    /** Inside the world render pass. */
    public static void draw(RenderPass pass) {
        if (transforms == null || !pending()) {
            clear();
            return;
        }
        try {
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            int chunk = 0;
            for (int list = 0; list < 2; list++) {
                int n = count[list];
                if (n == 0) continue;
                RenderPipeline pipeline = list == 1 ? TeslaRenderPipelines.FILLED_DEPTH_INSTANCED : TeslaRenderPipelines.FILLED_ESP_INSTANCED;
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, cube.slice());
                pass.setIndexBuffer(seq.getBuffer(), seq.type());
                for (int c = 0; c < chunks(n); c++, chunk++) {
                    int boxes = Math.min(PER_DRAW, n - c * PER_DRAW);
                    pass.setUniform("BoxData", ubo.slice((long) chunk * CHUNK_BYTES, CHUNK_BYTES));
                    pass.drawIndexed(36, boxes, 0, 0, 0);
                }
            }
        } catch (Throwable t) {
            broken = true;
            TeslaMaps.LOGGER.warn("Instanced ESP boxes disabled", t);
        } finally {
            clear();
        }
    }

    // same face order as ESPRenderer.renderFilledBox
    private static final float[] CUBE = {
            0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1,
            0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0,
            0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0,
            0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1,
            0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0,
            1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1,
    };

    private static GpuBuffer createCube(GpuDevice device) {
        ByteBuffer buf = MemoryUtil.memAlloc(CUBE.length * 4).order(java.nio.ByteOrder.nativeOrder());
        try {
            for (float f : CUBE) buf.putFloat(f);
            buf.flip();
            return device.createBuffer(() -> "teslamaps unit cube", GpuBuffer.USAGE_VERTEX, buf);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }
}
