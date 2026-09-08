package com.teslamaps.screen;

import com.teslamaps.features.SharedImageLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Full screen view of a picture. Click anywhere or press escape to leave. */
public class ImageViewerScreen extends Screen {

    private final Screen parent;
    private final String caption;
    private final String url;

    private Identifier texture;
    private int imageWidth, imageHeight;
    private String error;
    private boolean loading;

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
            ctx.blit(texture, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
        }

        if (caption != null) ctx.text(this.font, "§f" + caption, 8, 8, 0xFFFFFFFF);
        ctx.centeredText(this.font, "§8click anywhere to close", width / 2, height - 14, 0xFF808080);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
