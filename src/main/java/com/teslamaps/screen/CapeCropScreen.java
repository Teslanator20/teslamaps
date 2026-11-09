package com.teslamaps.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.features.capes.CapeCompose;
import com.teslamaps.features.capes.CapeImport;
import com.teslamaps.features.capes.GifCape;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Turns any picture into a cape: drag the frame over the part that should be on the
 * back, scroll to zoom, save. The sheet is composed client-side - frame on the front
 * and (mirrored) inner face, a heavily downscaled blur of the source everywhere else.
 */
public class CapeCropScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int SHEET_SCALE = 32;                       // 64x32 grid -> 2048x1024

    private final Screen parent;
    private final String name;
    private final String subfolder;
    private final byte[] data;
    private final boolean gif;

    private NativeImage source;
    private Identifier texture;

    private int panelX, panelY, panelW, panelH;
    private int viewX, viewY, viewW, viewH;      // where the source picture sits on screen
    private double cropCx = 0.5, cropCy = 0.5;   // crop centre in source fractions
    private double zoom = 1.0;                   // 1 = biggest 10:16 window that fits
    private boolean dragging;
    private String status = "";

    private CapeCropScreen(Screen parent, String name, byte[] data, String subfolder) {
        super(Component.literal("Crop cape"));
        this.parent = parent;
        this.name = name;
        this.data = data;
        this.subfolder = subfolder;
        this.gif = com.teslamaps.features.capes.GifCape.isGif(data);
    }

    public static void open(String name, byte[] data, String subfolder) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new CapeCropScreen(mc.gui.screen(), name, data, subfolder)));
    }

    @Override
    protected void init() {
        if (source == null) {
            try {
                source = still();
                texture = Identifier.fromNamespaceAndPath("teslamaps", "cape/crop");
                // the texture owns a copy so the compose step keeps its own pixels
                minecraft.getTextureManager().register(texture,
                        new DynamicTexture(() -> "TeslaMaps crop", still()));
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Crop decode failed", t);
                onClose();
                return;
            }
        }

        panelW = Math.min(width - MARGIN * 2, 480);
        panelH = Math.min(height - MARGIN * 2, 320);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        // picture view on the left, live cape preview on the right
        int innerH = panelH - 36 - 56;
        int maxW = panelW - 24 - 110;
        float aspect = (float) source.getWidth() / source.getHeight();
        viewW = maxW;
        viewH = (int) (viewW / aspect);
        if (viewH > innerH) {
            viewH = innerH;
            viewW = (int) (viewH * aspect);
        }
        viewX = panelX + 12 + (maxW - viewW) / 2;
        viewY = panelY + 36 + (innerH - viewH) / 2;

        int by = panelY + panelH - 24;
        int bw = (panelW - 24 - 6) / 2;
        addRenderableWidget(new TeslaButton(panelX + 12, by, bw, 18, "§aSave & wear", this::save));
        addRenderableWidget(new TeslaButton(panelX + 12 + bw + 6, by, bw, 18, "§7Cancel", this::onClose));

        ScreenFit.clamp(this);
    }

    /** The picture the crop frame is aimed at - for a gif that is its first frame. */
    private NativeImage still() throws Exception {
        return gif ? GifCape.firstFrame(data) : CapeImport.decode(data);
    }

    private CapeCompose.Crop crop() {
        return new CapeCompose.Crop(cropCx, cropCy, zoom);
    }

    /** Crop rect in source pixels, clamped inside the picture. */
    private int[] cropRect() {
        return CapeCompose.rect(source.getWidth(), source.getHeight(), crop());
    }

    private void save() {
        try {
            if (gif) {
                // the gif is kept as it came in; the crop travels with it and is
                // applied to every frame when the cape is worn
                CapeImport.saveGif(name, subfolder, data, crop());
                onClose();
                return;
            }
            try (NativeImage sheet = CapeCompose.sheet(source, crop(), SHEET_SCALE)) {
                CapeImport.saveComposed(name, subfolder, sheet);
            }
            onClose();
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Cape compose failed", t);
            status = "§cCould not save: " + t.getClass().getSimpleName();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= viewX && event.x() < viewX + viewW
                && event.y() >= viewY && event.y() < viewY + viewH) {
            dragging = true;
            moveCrop(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            moveCrop(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private void moveCrop(double mx, double my) {
        cropCx = (mx - viewX) / viewW;
        cropCy = (my - viewY) / viewH;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        zoom = Math.max(1.0, Math.min(8.0, zoom * (sy > 0 ? 1.1 : 1 / 1.1)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);
        ctx.text(font, "§f§lCrop \"" + name + "\"", panelX + 12, panelY + 12, 0xFFFFFFFF);
        ctx.text(font, gif ? "§8drag to aim, scroll to zoom - applies to every frame"
                : "§8drag to aim, scroll to zoom", panelX + 12, panelY + panelH - 52, 0xFF808080);
        if (!status.isEmpty()) ctx.text(font, status, panelX + 12, panelY + panelH - 40, 0xFFFFFFFF);

        // source picture, dimmed, with the crop frame on top
        ctx.blit(texture, viewX, viewY, viewX + viewW, viewY + viewH, 0f, 1f, 0f, 1f);
        ctx.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x70000000);

        int[] crop = cropRect();
        int fx = viewX + crop[0] * viewW / source.getWidth();
        int fy = viewY + crop[1] * viewH / source.getHeight();
        int fw = Math.max(1, crop[2] * viewW / source.getWidth());
        int fh = Math.max(1, crop[3] * viewH / source.getHeight());
        float u0 = crop[0] / (float) source.getWidth(), u1 = (crop[0] + crop[2]) / (float) source.getWidth();
        float v0 = crop[1] / (float) source.getHeight(), v1 = (crop[1] + crop[3]) / (float) source.getHeight();
        ctx.blit(texture, fx, fy, fx + fw, fy + fh, u0, u1, v0, v1);
        border(ctx, fx, fy, fw, fh, 0xFF30D158);

        // live cape preview on the right
        int pw = 90, ph = pw * 16 / 10;
        int px = panelX + panelW - 12 - pw;
        int py = panelY + 36 + (panelH - 36 - 56 - ph) / 2;
        ctx.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF1C1C1E);
        ctx.blit(texture, px, py, px + pw, py + ph, u0, u1, v0, v1);
        border(ctx, px - 2, py - 2, pw + 4, ph + 4, 0xFF3A3A3C);
        ctx.centeredText(font, "§8on the cape", px + pw / 2, py + ph + 6, 0xFF808080);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void border(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        if (texture != null) minecraft.getTextureManager().release(texture);
        if (source != null) source.close();
        texture = null;
        source = null;
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
