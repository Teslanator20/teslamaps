package com.teslamaps.features;

// Ported from Skysoft's RarityHighlightRenderer.kt (contour type)
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.TeslaGuiPipelines;
import com.teslamaps.render.gui.TeslaVertexFormats;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.system.MemoryUtil;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Rarity outline around the item's own pixels. The item is already baked into the gui item
 * atlas, so a second quad samples that atlas cell and colours every empty pixel next to a filled one.
 */
public final class RarityContour {

    public static final String SHAPE = "Silhouette";

    /** Colour for the next item drawn, used once, 0 = none. */
    public static int pending;
    private static final Map<GuiItemRenderState, Integer> colors = new IdentityHashMap<>();

    private RarityContour() {}

    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.rarityBackgrounds && SHAPE.equals(c.rarityBgShape);
    }

    public static int colorFor(ItemStack stack) {
        if (!enabled() || stack.isEmpty()) return 0;
        int rgb = RarityBackground.rarityColor(stack);
        if (rgb == -1) return 0;
        int alpha = (int) (Math.max(0f, Math.min(1f, TeslaMapsConfig.get().rarityBgOpacity)) * 255);
        if (alpha <= 0) return 0;
        return (alpha << 24) | RarityBackground.palette(rgb & 0xFFFFFF);
    }

    public static void attach(GuiItemRenderState item) {
        if (pending == 0) return;
        if (colors.size() > 1024) colors.clear();
        colors.put(item, pending);
        pending = 0;
    }

    public static void submit(GuiRenderState state, GuiItemRenderState item, GuiItemAtlas.SlotView view) {
        if (colors.isEmpty()) return;
        Integer c = colors.remove(item);
        if (c == null) return;
        state.addGlyphToCurrentLayer(new ContourState(item, view, c));
    }

    private record ContourState(GuiItemRenderState item, GuiItemAtlas.SlotView view, int color) implements GuiElementRenderState {
        private static final float SIZE = 16f;

        @Override
        public RenderPipeline pipeline() {
            return TeslaGuiPipelines.RARITY_CONTOUR;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.singleTexture(view.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        }

        @Override
        public ScreenRectangle scissorArea() {
            return item.scissorArea();
        }

        @Override
        public ScreenRectangle bounds() {
            ScreenRectangle r = new ScreenRectangle(item.x() - 1, item.y() - 1, 18, 18).transformMaxBounds(item.pose());
            return item.scissorArea() != null ? item.scissorArea().intersection(r) : r;
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            float us = (view.u1() - view.u0()) / SIZE, vs = (view.v1() - view.v0()) / SIZE;
            float x0 = item.x() - 1, y0 = item.y() - 1, x1 = item.x() + SIZE + 1, y1 = item.y() + SIZE + 1;
            vertex(consumer, x0, y0, view.u0() - us, view.v0() - vs);
            vertex(consumer, x0, y1, view.u0() - us, view.v1() + vs);
            vertex(consumer, x1, y1, view.u1() + us, view.v1() + vs);
            vertex(consumer, x1, y0, view.u1() + us, view.v0() - vs);
        }

        private void vertex(VertexConsumer consumer, float x, float y, float u, float v) {
            consumer.addVertexWith2DPose(item.pose(), x, y).setUv(u, v);
            if (consumer instanceof BufferBuilder builder) {
                long ptr = builder.beginElement(TeslaVertexFormats.SDF_PARAMS);
                if (ptr != -1L) {
                    MemoryUtil.memPutFloat(ptr, view.u0());
                    MemoryUtil.memPutFloat(ptr + 4L, view.v0());
                    MemoryUtil.memPutFloat(ptr + 8L, view.u1());
                    MemoryUtil.memPutFloat(ptr + 12L, view.v1());
                }
            }
            consumer.setColor(color);
        }
    }
}
