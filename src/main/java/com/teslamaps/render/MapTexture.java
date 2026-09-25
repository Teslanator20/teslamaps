package com.teslamaps.render;

// Idea from Devonian's DungeonMap.redrawMap: paint the static map layer into a texture, draw one quad per frame
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/** Room and door rectangles of the dungeon map, rasterized at most every 250 ms (or on a move/resize). */
public final class MapTexture {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("teslamaps", "map_cache");
    private static final long REFRESH_MS = 250;

    private static DynamicTexture tex;
    private static Raster raster;
    private static int w, h, bx, by;
    private static float sc;
    private static long last;

    private MapTexture() {}

    public static void draw(GuiGraphicsExtractor g, int x, int y, int width, int height, float scale,
                            Consumer<GuiGraphicsExtractor> paint) {
        if (width <= 0 || height <= 0) return;
        long now = System.currentTimeMillis();
        boolean moved = tex == null || width != w || height != h || x != bx || y != by || scale != sc;
        if (moved || now - last >= REFRESH_MS) {
            last = now;
            rebuild(x, y, width, height, scale, paint);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, 0f, 0f, width, height, width, height);
    }

    public static void invalidate() {
        last = 0;
    }

    private static void rebuild(int x, int y, int width, int height, float scale, Consumer<GuiGraphicsExtractor> paint) {
        Minecraft mc = Minecraft.getInstance();
        if (tex == null || width != w || height != h) {
            tex = new DynamicTexture(() -> "TeslaMaps map cache", new NativeImage(width, height, true));
            mc.getTextureManager().register(ID, tex);
            w = width;
            h = height;
        }
        bx = x;
        by = y;
        sc = scale;
        NativeImage img = tex.getPixels();
        if (img == null) return;
        img.fillRect(0, 0, width, height, 0);
        if (raster == null) raster = new Raster(mc);
        raster.begin(img, x, y);
        paint.accept(raster);
        raster.end();
        tex.upload();
    }

    /** Takes plain fills into the image; anything else lands in a throwaway render state. */
    private static final class Raster extends GuiGraphicsExtractor {
        private final GuiRenderState sink;
        private NativeImage img;
        private int ox, oy;

        Raster(Minecraft mc) {
            this(mc, new GuiRenderState());
        }

        private Raster(Minecraft mc, GuiRenderState sink) {
            super(mc, sink, 0, 0);
            this.sink = sink;
        }

        void begin(NativeImage img, int ox, int oy) {
            this.img = img;
            this.ox = ox;
            this.oy = oy;
        }

        void end() {
            img = null;
            sink.reset();
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int color) {
            if (img == null) return;
            if (x0 > x1) { int t = x0; x0 = x1; x1 = t; }
            if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
            x0 = Math.max(0, x0 - ox);
            x1 = Math.min(img.getWidth(), x1 - ox);
            y0 = Math.max(0, y0 - oy);
            y1 = Math.min(img.getHeight(), y1 - oy);
            int sa = color >>> 24;
            if (sa == 0 || x0 >= x1 || y0 >= y1) return;
            if (sa == 255) {
                img.fillRect(x0, y0, x1 - x0, y1 - y0, color);
                return;
            }
            for (int py = y0; py < y1; py++) {
                for (int px = x0; px < x1; px++) img.setPixel(px, py, over(color, img.getPixel(px, py)));
            }
        }

        // non-premultiplied source-over, so blending the result later equals drawing both in order
        private static int over(int src, int dst) {
            float as = (src >>> 24) / 255f, ad = (dst >>> 24) / 255f;
            float ao = as + ad * (1 - as);
            if (ao <= 0f) return 0;
            int r = mix((src >> 16) & 0xFF, (dst >> 16) & 0xFF, as, ad, ao);
            int g = mix((src >> 8) & 0xFF, (dst >> 8) & 0xFF, as, ad, ao);
            int b = mix(src & 0xFF, dst & 0xFF, as, ad, ao);
            return Math.round(ao * 255f) << 24 | r << 16 | g << 8 | b;
        }

        private static int mix(int cs, int cd, float as, float ad, float ao) {
            return Math.min(255, Math.round((cs * as + cd * ad * (1 - as)) / ao));
        }
    }
}
