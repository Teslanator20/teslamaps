package com.teslamaps.features;

// Skysoft's ScreenshotCapturePreview.kt
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.ScreenshotScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CompletableFuture;

/** After F2: the shot flies into the top right corner with Share / Delete / Close; click it to open the browser. */
public final class ScreenshotPreview {

    private static final int MAX_TEX_W = 640;
    private static final long HOLD_MS = 90, SHRINK_MS = 520, TRAVEL_MS = 980, SHOW_MS = 10_000;
    private static final int INSET = 8, PAD = 4, ACTION_H = 16, GAP = 4, CLOSE = 12;

    private static Identifier texture;
    private static int texW, texH, nextId;
    private static File file;
    private static long startedAt;
    private static boolean animate;
    private static long deleteArmedAt;
    private static int[] imgRect, shareRect, deleteRect, closeRect;

    private ScreenshotPreview() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ScreenshotPreview").when(() -> cfg().screenshotPreview)
                .hud("screenshotpreview", (ctx, delta) -> {
                    if (Minecraft.getInstance().screen == null) render(ctx, -1, -1, false);
                })
                .config("Screenshots", b -> {
                    b.toggle("Capture Preview", () -> cfg().screenshotPreview, v -> cfg().screenshotPreview = v)
                            .desc("After a screenshot a preview sits in the top right corner with Share, Delete and Close. Click the picture to open the browser (works while a screen, e.g. chat, is open)");
                    b.toggle("  Fly-In Animation", () -> cfg().screenshotPreviewAnimation, v -> cfg().screenshotPreviewAnimation = v);
                    b.toggle("  Shutter Sound", () -> cfg().screenshotPreviewSound, v -> cfg().screenshotPreviewSound = v);
                    b.toggle("Screenshot Editor", () -> cfg().screenshotEditor, v -> cfg().screenshotEditor = v)
                            .desc("Edit button in the screenshot browser: crop, draw, zoom, undo/redo, save as copy or replace");
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {
                if (texture != null && !(s instanceof ScreenshotScreen || s instanceof com.teslamaps.screen.ScreenshotEditorScreen)) render(ctx, mx, my, true);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    texture == null || s instanceof ScreenshotScreen || s instanceof com.teslamaps.screen.ScreenshotEditorScreen || !click(s, event.x(), event.y(), event.button()));
        });
    }

    /** Screenshot IO thread. */
    public static void present(File saved) {
        TeslaMapsConfig c = cfg();
        if (!c.screenshotPreview) return;
        Minecraft mc = Minecraft.getInstance();
        if (c.screenshotPreviewSound) {
            mc.execute(() -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PISTON_CONTRACT, 1.7f, 0.35f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.2f, 0.55f));
            });
        }
        CompletableFuture.supplyAsync(() -> load(saved), net.minecraft.util.Util.ioPool()).whenComplete((image, err) -> {
            if (err != null || image == null) {
                if (err != null) TeslaMaps.LOGGER.warn("Screenshot preview failed: {}", err.toString());
                return;
            }
            mc.execute(() -> show(saved, image));
        });
    }

    private static NativeImage load(File f) {
        try (FileInputStream in = new FileInputStream(f); NativeImage src = NativeImage.read(in)) {
            int w = src.getWidth(), h = src.getHeight();
            if (w <= MAX_TEX_W) {
                NativeImage copy = new NativeImage(w, h, false);
                copy.copyFrom(src);
                return copy;
            }
            int nh = Math.max(1, h * MAX_TEX_W / w);
            NativeImage small = new NativeImage(MAX_TEX_W, nh, false);
            src.resizeSubRectTo(0, 0, w, h, small);
            return small;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void show(File saved, NativeImage image) {
        clear();
        if (!cfg().screenshotPreview) {
            image.close();
            return;
        }
        texture = Identifier.fromNamespaceAndPath("teslamaps", "screenshot_preview/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "teslamaps screenshot preview", image));
        texW = image.getWidth();
        texH = image.getHeight();
        file = saved;
        startedAt = System.currentTimeMillis();
        animate = cfg().screenshotPreviewAnimation;
    }

    private static void clear() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
        texture = null;
        file = null;
        imgRect = shareRect = deleteRect = closeRect = null;
        deleteArmedAt = 0;
    }

    private static float easeOutCubic(float t) {
        float u = 1 - Math.clamp(t, 0f, 1f);
        return 1 - u * u * u;
    }

    private static float smoothStep(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static int[] lerp(int[] a, int[] b, float t) {
        return new int[]{Math.round(a[0] + (b[0] - a[0]) * t), Math.round(a[1] + (b[1] - a[1]) * t),
                Math.max(1, Math.round(a[2] + (b[2] - a[2]) * t)), Math.max(1, Math.round(a[3] + (b[3] - a[3]) * t))};
    }

    private static int[] fit(int sw, int sh, int maxW, int maxH, double aspect) {
        int w = Math.max(1, (int) Math.min(maxW, Math.round(maxH * aspect)));
        int h = Math.max(1, (int) Math.round(w / aspect));
        return new int[]{w, h};
    }

    private static void render(GuiGraphicsExtractor ctx, int mx, int my, boolean interactive) {
        if (texture == null) return;
        long age = System.currentTimeMillis() - startedAt;
        if (!cfg().screenshotPreview || age >= SHOW_MS) {
            clear();
            return;
        }
        int sw = ctx.guiWidth(), sh = ctx.guiHeight();
        double aspect = (double) texW / texH;
        int[] fin = fit(sw, sh, Math.min(220, (int) (sw * 0.23)), Math.min(180, (int) (sh * 0.35)), aspect);
        int[] settled = {sw - fin[0] - INSET - PAD, INSET + PAD, fin[0], fin[1]};
        long t = animate ? age : TRAVEL_MS;
        int[] r;
        if (t < HOLD_MS) {
            r = new int[]{0, 0, sw, sh};
        } else if (t < SHRINK_MS) {
            int[] c = fit(sw, sh, Math.min(520, (int) (sw * 0.58)), Math.min(292, (int) (sh * 0.62)), aspect);
            r = lerp(new int[]{0, 0, sw, sh}, new int[]{(sw - c[0]) / 2, (sh - c[1]) / 2, c[0], c[1]},
                    easeOutCubic((t - HOLD_MS) / (float) (SHRINK_MS - HOLD_MS)));
        } else if (t < TRAVEL_MS) {
            int[] c = fit(sw, sh, Math.min(520, (int) (sw * 0.58)), Math.min(292, (int) (sh * 0.62)), aspect);
            r = lerp(new int[]{(sw - c[0]) / 2, (sh - c[1]) / 2, c[0], c[1]}, settled,
                    smoothStep((t - SHRINK_MS) / (float) (TRAVEL_MS - SHRINK_MS)));
        } else {
            r = settled;
        }
        if (t < TRAVEL_MS) {
            ctx.blit(texture, r[0], r[1], r[0] + r[2], r[1] + r[3], 0f, 1f, 0f, 1f);
            imgRect = shareRect = deleteRect = closeRect = null;
            return;
        }
        int panelX = r[0] - PAD, panelY = r[1] - PAD, panelW = r[2] + PAD * 2, panelH = r[3] + PAD * 2 + ACTION_H + GAP;
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 4);
        ctx.blit(texture, r[0], r[1], r[0] + r[2], r[1] + r[3], 0f, 1f, 0f, 1f);

        int actionY = r[1] + r[3] + GAP;
        int delW = 40;
        imgRect = r;
        deleteRect = new int[]{panelX + panelW - PAD - delW, actionY, delW, ACTION_H};
        shareRect = new int[]{panelX + PAD, actionY, panelW - PAD * 2 - delW - GAP, ACTION_H};
        closeRect = new int[]{r[0] + r[2] - CLOSE, r[1], CLOSE, CLOSE};

        var font = Minecraft.getInstance().font;
        boolean armed = System.currentTimeMillis() - deleteArmedAt < 3000;
        button(ctx, font, shareRect, "Share", interactive, mx, my, 0xFFFFFFFF);
        button(ctx, font, deleteRect, armed ? "Sure?" : "Delete", interactive, mx, my, 0xFFFF5555);
        button(ctx, font, closeRect, "x", interactive, mx, my, 0xFFFFFFFF);
        if (!interactive) {
            ctx.centeredText(font, "§8open chat to use", r[0] + r[2] / 2, panelY + panelH + 2, 0xFF808080);
        }
    }

    private static void button(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font font, int[] b, String label,
                               boolean interactive, int mx, int my, int color) {
        boolean hover = interactive && inside(b, mx, my);
        HudStyle.screenButton(ctx, b[0], b[1], b[2], b[3], hover, interactive);
        ctx.centeredText(font, label, b[0] + b[2] / 2, b[1] + (b[3] - 8) / 2, color);
    }

    private static boolean inside(int[] r, double x, double y) {
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    /** True = consumed. */
    private static boolean click(Screen screen, double x, double y, int button) {
        if (button != 0 || imgRect == null || file == null) return false;
        File f = file;
        Minecraft mc = Minecraft.getInstance();
        if (inside(closeRect, x, y)) {
            clear();
            return true;
        }
        if (inside(deleteRect, x, y)) {
            long now = System.currentTimeMillis();
            if (now - deleteArmedAt >= 3000) {
                deleteArmedAt = now;
                startedAt = Math.max(startedAt, now - SHOW_MS + 4000);
                return true;
            }
            clear();
            if (!ScreenshotManager.delete(f)) ImageShare.feedback("§c[TeslaMaps] Delete failed.");
            return true;
        }
        if (inside(shareRect, x, y)) {
            clear();
            ScreenshotManager.shareToClipboard(f);
            return true;
        }
        if (inside(imgRect, x, y)) {
            clear();
            mc.schedule(() -> mc.setScreen(new ScreenshotScreen(screen, f)));
            return true;
        }
        return false;
    }
}
