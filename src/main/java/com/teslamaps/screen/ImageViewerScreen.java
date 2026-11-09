package com.teslamaps.screen;

import com.teslamaps.features.SharedImageLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Full screen view of a picture. Click anywhere, escape or backspace leaves; arrows browse when a gallery opened it. */
public class ImageViewerScreen extends Screen {

    private final Screen parent;
    private final String caption;
    private final String url;

    private Identifier texture;
    private int imageWidth, imageHeight;
    private String error;
    private boolean loading;

    // opening animation: grows out of the thumbnail, or slides in when browsing with the arrows
    private static final long GROW_MS = 220, SLIDE_MS = 180;
    private int[] fromRect;
    private int slideDir;
    private long openedAt = System.currentTimeMillis();
    private java.util.function.IntConsumer navigate;

    private ImageViewerScreen(Screen parent, String caption, String url,
                              Identifier texture, int width, int height) {
        super(Component.literal("Image"));
        this.parent = parent;
        this.caption = caption;
        this.url = url;
        this.texture = texture;
        this.imageWidth = width;
        this.imageHeight = height;
        this.loading = texture == null;
    }

    /** A picture already on the GPU, for example a screenshot thumbnail. */
    public static ImageViewerScreen forTexture(Screen parent, String caption,
                                               Identifier texture, int width, int height) {
        return new ImageViewerScreen(parent, caption, null, texture, width, height);
    }

    /** Gallery view: arrows call {@code navigate(-1/+1)}; {@code from} = thumbnail rect x,y,w,h or null. */
    public ImageViewerScreen gallery(java.util.function.IntConsumer navigate, int[] from, int slideDir) {
        this.navigate = navigate;
        this.fromRect = from;
        this.slideDir = slideDir;
        this.openedAt = System.currentTimeMillis();
        return this;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k == org.lwjgl.glfw.com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) { onClose(); return true; }
        if (navigate != null && (k == org.lwjgl.glfw.com.mojang.blaze3d.platform.InputConstants.KEY_LEFT || k == org.lwjgl.glfw.com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT)) {
            navigate.accept(k == org.lwjgl.glfw.com.mojang.blaze3d.platform.InputConstants.KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    private static float easeOut(float t) {
        float u = 1 - Math.clamp(t, 0f, 1f);
        return 1 - u * u * u;
    }

    /** A shared picture, downloaded on demand. */
    public static ImageViewerScreen forUrl(Screen parent, String caption, String url) {
        SharedImageLoader.Loaded known = SharedImageLoader.cached(url);
        if (known != null) {
            return new ImageViewerScreen(parent, caption, url, known.id(), known.width(), known.height());
        }
        return new ImageViewerScreen(parent, caption, url, null, 0, 0);
    }

    @Override
    protected void init() {
        if (texture == null && url != null) {
            SharedImageLoader.load(url,
                    loaded -> {
                        texture = loaded.id();
                        imageWidth = loaded.width();
                        imageHeight = loaded.height();
                        loading = false;
                    },
                    message -> {
                        error = message;
                        loading = false;
                    });
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        onClose();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE6000000);

        if (loading) {
            ctx.centeredText(this.font, "§7Loading...", width / 2, height / 2, 0xFFFFFFFF);
        } else if (error != null) {
            ctx.centeredText(this.font, "§c" + error, width / 2, height / 2, 0xFFFF5555);
        } else if (texture != null && imageWidth > 0 && imageHeight > 0) {
            int maxW = width - 24, maxH = height - 48;
            float fit = Math.min((float) maxW / imageWidth, (float) maxH / imageHeight);
            int w = Math.max(1, (int) (imageWidth * fit));
            int h = Math.max(1, (int) (imageHeight * fit));
            int x = (width - w) / 2, y = (height - h) / 2;
            long age = System.currentTimeMillis() - openedAt;
            if (fromRect != null && age < GROW_MS) {
                float t = easeOut(age / (float) GROW_MS);
                int x0 = Math.round(fromRect[0] + (x - fromRect[0]) * t), y0 = Math.round(fromRect[1] + (y - fromRect[1]) * t);
                int x1 = Math.round(fromRect[0] + fromRect[2] + (x + w - fromRect[0] - fromRect[2]) * t);
                int y1 = Math.round(fromRect[1] + fromRect[3] + (y + h - fromRect[1] - fromRect[3]) * t);
                x = x0; y = y0; w = Math.max(1, x1 - x0); h = Math.max(1, y1 - y0);
            } else if (slideDir != 0 && age < SLIDE_MS) {
                x += Math.round(Math.clamp(width * 0.08f, 24f, 96f) * slideDir * (1 - easeOut(age / (float) SLIDE_MS)));
            }
            ctx.blit(texture, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
        }

        if (caption != null) ctx.text(this.font, "§f" + caption, 8, 8, 0xFFFFFFFF);
        ctx.centeredText(this.font, navigate != null ? "§8← → browse · click / esc to close" : "§8click anywhere to close",
                width / 2, height - 14, 0xFF808080);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
