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
 * A single SDF quad. The rect is expanded on all sides by {@code pad} so the
 * fragment shader has room to draw the soft parts (shadow falloff, antialiased
 * edge); the local coordinates handed to the shader stay centred on the
 * original rect, so the SDF math is unaffected by the padding.
 */
public class SdfRectRenderState implements GuiElementRenderState {

    private final RenderPipeline pipeline;
    private final Matrix3x2f pose;
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final float halfWidth;
    private final float halfHeight;
    private final float radius;
    private final float extra;
    private final int colorTop;
    private final int colorBottom;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    public SdfRectRenderState(RenderPipeline pipeline, Matrix3x2fc pose,
                              float x, float y, float width, float height,
                              float radius, float extra, float pad,
                              int colorTop, int colorBottom,
                              ScreenRectangle scissorArea) {
        this.pipeline = pipeline;
        this.pose = new Matrix3x2f(pose);
        this.x0 = x - pad;
        this.y0 = y - pad;
        this.x1 = x + width + pad;
        this.y1 = y + height + pad;
        this.halfWidth = width * 0.5f;
        this.halfHeight = height * 0.5f;
        this.radius = Math.min(radius, Math.min(this.halfWidth, this.halfHeight));
        this.extra = extra;
        this.colorTop = colorTop;
        this.colorBottom = colorBottom;
        this.scissorArea = scissorArea;
        this.bounds = computeBounds();
    }

    private ScreenRectangle computeBounds() {
        int left = (int) Math.floor(x0);
        int top = (int) Math.floor(y0);
        ScreenRectangle rect = new ScreenRectangle(left, top,
                (int) Math.ceil(x1) - left, (int) Math.ceil(y1) - top)
                .transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rect) : rect;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        // Local coords are relative to the unpadded rect's centre.
        float centerX = (x0 + x1) * 0.5f;
        float centerY = (y0 + y1) * 0.5f;
        float localLeft = x0 - centerX;
        float localTop = y0 - centerY;
        float localRight = x1 - centerX;
        float localBottom = y1 - centerY;

        vertex(consumer, x0, y0, localLeft, localTop, colorTop);
        vertex(consumer, x0, y1, localLeft, localBottom, colorBottom);
        vertex(consumer, x1, y1, localRight, localBottom, colorBottom);
        vertex(consumer, x1, y0, localRight, localTop, colorTop);
    }

    private void vertex(VertexConsumer consumer, float x, float y,
                        float localX, float localY, int color) {
        consumer.addVertexWith2DPose(pose, x, y).setUv(localX, localY);
        if (consumer instanceof BufferBuilder builder) {
            long ptr = builder.beginElement(TeslaVertexFormats.SDF_PARAMS);
            if (ptr != -1L) {
                MemoryUtil.memPutFloat(ptr, halfWidth);
                MemoryUtil.memPutFloat(ptr + 4L, halfHeight);
                MemoryUtil.memPutFloat(ptr + 8L, radius);
                MemoryUtil.memPutFloat(ptr + 12L, extra);
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
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
