package com.teslamaps.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.lwjgl.system.MemoryUtil;

/**
 * A quad that hands four free floats plus a uv pair to its fragment shader.
 * Used for the procedural menu backgrounds, which need resolution/time/mouse
 * rather than the rounded-rect parameters {@link SdfRectRenderState} supplies.
 */
public class ShaderQuadRenderState implements GuiElementRenderState {

    private final RenderPipeline pipeline;
    private final Matrix3x2f pose;
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final float p0;
    private final float p1;
    private final float p2;
    private final float p3;
    private final float u;
    private final float v;
    private final int color;
    private final ScreenRectangle bounds;

    public ShaderQuadRenderState(RenderPipeline pipeline, Matrix3x2fc pose,
                                 float x, float y, float width, float height,
                                 float p0, float p1, float p2, float p3,
                                 float u, float v, int color) {
        this.pipeline = pipeline;
        this.pose = new Matrix3x2f(pose);
        this.x0 = x;
        this.y0 = y;
        this.x1 = x + width;
        this.y1 = y + height;
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.u = u;
        this.v = v;
        this.color = color;
        this.bounds = new ScreenRectangle((int) x, (int) y,
                (int) Math.ceil(width), (int) Math.ceil(height)).transformMaxBounds(this.pose);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        vertex(consumer, x0, y0);
        vertex(consumer, x0, y1);
        vertex(consumer, x1, y1);
        vertex(consumer, x1, y0);
    }

    private void vertex(VertexConsumer consumer, float x, float y) {
        consumer.addVertexWith2DPose(pose, x, y).setUv(u, v);
        if (consumer instanceof BufferBuilder builder) {
            long ptr = builder.beginElement(TeslaVertexFormats.SDF_PARAMS);
            if (ptr != -1L) {
                MemoryUtil.memPutFloat(ptr, p0);
                MemoryUtil.memPutFloat(ptr + 4L, p1);
                MemoryUtil.memPutFloat(ptr + 8L, p2);
                MemoryUtil.memPutFloat(ptr + 12L, p3);
            }
        }
        consumer.setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
