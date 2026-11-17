package com.teslamaps.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * World geometry collected during the frame and drawn in one render pass at the end of the
 * level's main pass. One persistent buffer per RenderType, in first-use order.
 */
public final class WorldBuffers implements MultiBufferSource.BufferSource {

    public static final WorldBuffers INSTANCE = new WorldBuffers();

    private static final class Slot {
        final RenderType type;
        final ByteBufferBuilder bytes = new ByteBufferBuilder(1536);
        BufferBuilder builder;
        GpuBuffer gpu;
        MeshData mesh;
        PreparedRenderType prepared;
        StagedVertexBuffer.ExecuteInfo info;

        Slot(RenderType type) {
            this.type = type;
        }
    }

    private final List<Slot> slots = new ArrayList<>();
    private Slot last;
    private boolean any;
    private boolean broken;
    private long brokenAt;
    private int reserved;

    private WorldBuffers() {}

    /** Fixes the draw order: reserved types first, instanced boxes, then everything else. */
    public void reserve(RenderType type) {
        slot(type);
        reserved = slots.size();
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        Slot s = last != null && last.type == type ? last : slot(type);
        last = s;
        if (s.builder == null) s.builder = new BufferBuilder(s.bytes, type.primitiveTopology(), type.format());
        any = true;
        return s.builder;
    }

    private Slot slot(RenderType type) {
        for (int i = 0, n = slots.size(); i < n; i++) {
            Slot s = slots.get(i);
            if (s.type == type) return s;
        }
        Slot s = new Slot(type);
        slots.add(s);
        return s;
    }

    @Override
    public void endBatch(RenderType type) {
    }

    @Override
    public void endBatch() {
    }

    /** Drops everything recorded so far without drawing it. */
    public void discard() {
        for (int i = 0, n = slots.size(); i < n; i++) {
            Slot s = slots.get(i);
            if (s.builder == null) continue;
            MeshData mesh = s.builder.build();
            if (mesh != null) mesh.close();
            s.builder = null;
        }
        any = false;
        last = null;
    }

    public void draw() {
        if (!any && !InstancedBoxes.pending()) return;
        any = false;
        last = null;
        // retry now and then so one bad frame does not kill every overlay for the session
        if (broken && System.currentTimeMillis() - brokenAt < 10_000) {
            discard();
            InstancedBoxes.clear();
            return;
        }
        try {
            drawAll();
        } catch (Throwable t) {
            broken = true;
            brokenAt = System.currentTimeMillis();
            TeslaMaps.LOGGER.warn("World overlay rendering failed, retrying in 10s", t);
        } finally {
            for (int i = 0, n = slots.size(); i < n; i++) {
                Slot s = slots.get(i);
                if (s.mesh != null) s.mesh.close();
                s.mesh = null;
                s.prepared = null;
                s.info = null;
                s.builder = null;
            }
        }
    }

    private void drawAll() {
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder enc = device.createCommandEncoder();
        boolean drawn = false;
        for (int i = 0, n = slots.size(); i < n; i++) {
            Slot s = slots.get(i);
            if (s.builder == null) continue;
            MeshData mesh = s.builder.build();
            s.builder = null;
            if (mesh == null) continue;
            s.mesh = mesh;
            ByteBuffer vertices = mesh.vertexBuffer();
            int bytes = vertices.remaining();
            if (s.gpu == null || s.gpu.size() < bytes) {
                if (s.gpu != null) s.gpu.close();
                long size = Math.max(bytes, s.gpu == null ? 4096L : s.gpu.size() * 2);
                s.gpu = device.createBuffer(() -> "teslamaps world " + s.type, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
            }
            enc.writeToBuffer(s.gpu.slice(0, bytes), vertices);
            MeshData.DrawState ds = mesh.drawState();
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(ds.primitiveTopology());
            seq.getBuffer(ds.indexCount());
            s.info = new StagedVertexBuffer.ExecuteInfo(s.gpu, null, seq.type(), 0, 0, ds.indexCount(), ds.primitiveTopology());
            s.prepared = s.type.prepare();
            drawn = true;
        }
        InstancedBoxes.upload(enc);
        if (!drawn && !InstancedBoxes.pending()) return;

        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = enc.createRenderPass(() -> "teslamaps world", target.getColorTextureView(), Optional.empty(),
                target.hasDepth() ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            boolean boxes = false;
            for (int i = 0, n = slots.size(); i < n; i++) {
                if (i == reserved) {
                    InstancedBoxes.draw(pass);
                    boxes = true;
                }
                Slot s = slots.get(i);
                if (s.info != null) s.prepared.drawFromBuffer(s.info, pass);
            }
            if (!boxes) InstancedBoxes.draw(pass);
        }
    }
}
