package com.teslamaps.screen;

// Skysoft's ScreenshotEditSession / ScreenshotEditorController / ScreenshotEditedImage, lean
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.features.ImageShare;
import com.teslamaps.features.ScreenshotManager;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Crop, draw (8 colours, 3 sizes), zoom 0.25-8x with pan, 32 undo steps; save as copy or replace. */
public class ScreenshotEditorScreen extends Screen {

    private enum Tool { VIEW, CROP, DRAW }

    private static final int[] COLORS = {0xFFFFFFFF, 0xFF111111, 0xFFFF4D57, 0xFFFF963D,
            0xFFFFD84A, 0xFF55D879, 0xFF4AA8FF, 0xFFB778FF};
    private static final double[] SIZES = {0.003, 0.006, 0.012};
    private static final String[] SIZE_NAMES = {"S", "M", "L"};
    private static final double MIN_ZOOM = 0.25, MAX_ZOOM = 8, ZOOM_STEP = 1.2, MIN_CROP = 0.02;
    private static final int HISTORY = 32, TOP = 30, BOTTOM = 32, SWATCH = 12;
    private static int nextId;

    private record Stroke(int color, float radius, List<float[]> points) {}
    private record Snap(double[] crop, List<Stroke> strokes) {}

    private final Screen parent;
    private final File file;
    private NativeImage original;
    private DynamicTexture texture;
    private Identifier textureId;
    private int imgW, imgH;
    private String error;

    private Tool tool = Tool.VIEW;
    private int color = 2, size = 1;
    private double zoom = 1, panX, panY;
    private double[] crop = {0, 0, 1, 1};
    private List<Stroke> strokes = new ArrayList<>();
    private final Deque<Snap> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean unsaved, dirty;

    private int dragButton = -1;
    private double dragX, dragY;
    private Snap before;
    private Stroke current;
    private double[] cropStart;

    private String status = "";
    private String armed;
    private long armedAt;

    public ScreenshotEditorScreen(Screen parent, File file) {
        super(Component.literal("Screenshot Editor"));
        this.parent = parent;
        this.file = file;
    }

    @Override
    protected void init() {
        if (original == null && error == null) load();
        int bh = 18, y = 6, x = 8;
        x = toolButton(x, y, bh, "View", Tool.VIEW);
        x = toolButton(x, y, bh, "Crop", Tool.CROP);
        x = toolButton(x, y, bh, "Draw", Tool.DRAW);
        x += 8 + COLORS.length * (SWATCH + 3) + 6;
        for (int i = 0; i < SIZES.length; i++) {
            int idx = i;
            addRenderableWidget(new TeslaButton(x, y, 18, bh, (size == i ? "§a" : "") + SIZE_NAMES[i], () -> {
                size = idx;
                rebuildWidgets();
            }));
            x += 20;
        }
        x += 8;
        addRenderableWidget(new TeslaButton(x, y, 36, bh, "Undo", this::undo)).active = !undo.isEmpty();
        x += 38;
        addRenderableWidget(new TeslaButton(x, y, 36, bh, "Redo", this::redo)).active = !redo.isEmpty();
        x += 38;
        addRenderableWidget(new TeslaButton(x, y, 30, bh, "Fit", this::fit));
        x += 32;
        addRenderableWidget(new TeslaButton(x, y, 60, bh, "Reset Crop", this::resetCrop)).active = !isFull(crop);

        String[] labels = {"§aSave Copy", "§eReplace", "§dShare", "§cDelete", "Close"};
        Runnable[] actions = {() -> save(false), () -> { if (confirm("replace", "§eClick §eReplace§e again to overwrite the original")) save(true); },
                this::share, this::delete, this::onClose};
        int bw = Math.max(48, Math.min(90, (width - 16 - (labels.length - 1) * 6) / labels.length));
        int bx = (width - (labels.length * bw + (labels.length - 1) * 6)) / 2;
        for (int i = 0; i < labels.length; i++) {
            addRenderableWidget(new TeslaButton(bx + i * (bw + 6), height - BOTTOM + 7, bw, bh, labels[i], actions[i]));
        }
        ScreenFit.clamp(this);
    }

    private int toolButton(int x, int y, int h, String label, Tool t) {
        addRenderableWidget(new TeslaButton(x, y, 34, h, (tool == t ? "§a" : "") + label, () -> {
            tool = t;
            rebuildWidgets();
        }));
        return x + 36;
    }

    private int swatchX() {
        return 8 + 3 * 36 + 8;
    }

    private void load() {
        try (FileInputStream in = new FileInputStream(file)) {
            original = NativeImage.read(in);
            imgW = original.getWidth();
            imgH = original.getHeight();
            NativeImage work = new NativeImage(imgW, imgH, false);
            work.copyFrom(original);
            textureId = Identifier.fromNamespaceAndPath("teslamaps", "screenshot_editor/" + nextId++);
            texture = new DynamicTexture(() -> "teslamaps screenshot editor", work);
            minecraft.getTextureManager().register(textureId, texture);
        } catch (Exception e) {
            error = "Cannot open " + file.getName() + ": " + e.getMessage();
            TeslaMaps.LOGGER.warn("Screenshot editor: {}", error);
        }
    }

    @Override
    public void removed() {
        if (textureId != null) minecraft.getTextureManager().release(textureId);
        textureId = null;
        texture = null;
        if (original != null) original.close();
        original = null;
    }

    // --- geometry ---

    private int canvasX() { return 8; }
    private int canvasY() { return TOP; }
    private int canvasW() { return width - 16; }
    private int canvasH() { return height - TOP - BOTTOM; }

    private double scale() {
        return Math.min((double) canvasW() / imgW, (double) canvasH() / imgH) * zoom;
    }

    private double imageX() { return canvasX() + (canvasW() - imgW * scale()) / 2 + panX; }
    private double imageY() { return canvasY() + (canvasH() - imgH * scale()) / 2 + panY; }

    private double toImgX(double mx) { return (mx - imageX()) / scale(); }
    private double toImgY(double my) { return (my - imageY()) / scale(); }

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasX() && mx < canvasX() + canvasW() && my >= canvasY() && my < canvasY() + canvasH();
    }

    // --- history ---

    private Snap snap() {
        return new Snap(crop.clone(), new ArrayList<>(strokes));
    }

    private void commit(Snap previous) {
        undo.push(previous);
        while (undo.size() > HISTORY) undo.removeLast();
        redo.clear();
        unsaved = true;
        rebuildWidgets();
    }

    private void restore(Snap s) {
        crop = s.crop.clone();
        strokes = new ArrayList<>(s.strokes);
        redraw();
        unsaved = true;
        rebuildWidgets();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(snap());
        restore(undo.pop());
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(snap());
        restore(redo.pop());
    }

    private void fit() {
        zoom = 1;
        panX = panY = 0;
    }

    private void resetCrop() {
        if (isFull(crop)) return;
        Snap prev = snap();
        crop = new double[]{0, 0, 1, 1};
        commit(prev);
    }

    private static boolean isFull(double[] c) {
        return c[0] <= 0 && c[1] <= 0 && c[2] >= 1 && c[3] >= 1;
    }

    // --- painting ---

    private void redraw() {
        if (texture == null) return;
        NativeImage work = texture.getPixels();
        work.copyFrom(original);
        for (Stroke s : strokes) {
            float[] prev = null;
            for (float[] p : s.points) {
                segment(work, prev == null ? p : prev, p, s.radius, s.color);
                prev = p;
            }
        }
        dirty = true;
    }

    private void segment(NativeImage img, float[] a, float[] b, float r, int argb) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len = Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) Math.ceil(len / Math.max(0.5, r / 2)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            stamp(img, a[0] + dx * t, a[1] + dy * t, r, argb);
        }
    }

    private void stamp(NativeImage img, double cx, double cy, float r, int argb) {
        int x0 = Math.max(0, (int) Math.floor(cx - r)), x1 = Math.min(imgW - 1, (int) Math.ceil(cx + r));
        int y0 = Math.max(0, (int) Math.floor(cy - r)), y1 = Math.min(imgH - 1, (int) Math.ceil(cy + r));
        double rr = r * r;
        for (int y = y0; y <= y1; y++) {
            double ddy = y + 0.5 - cy;
            for (int x = x0; x <= x1; x++) {
                double ddx = x + 0.5 - cx;
                if (ddx * ddx + ddy * ddy <= rr) img.setPixel(x, y, argb);
            }
        }
    }

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        int sx = swatchX();
        if (my >= 9 && my < 9 + SWATCH && mx >= sx && mx < sx + COLORS.length * (SWATCH + 3)) {
            int i = (int) ((mx - sx) / (SWATCH + 3));
            if (i >= 0 && i < COLORS.length) {
                color = i;
                if (tool != Tool.DRAW) {
                    tool = Tool.DRAW;
                    rebuildWidgets();
                }
            }
            return true;
        }
        if (texture == null || !inCanvas(mx, my)) return false;
        dragButton = event.button();
        dragX = mx;
        dragY = my;
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return true;
        if (tool == Tool.DRAW) {
            float ix = (float) toImgX(mx), iy = (float) toImgY(my);
            before = snap();
            float r = (float) Math.max(1, SIZES[size] * Math.min(imgW, imgH) / 2);
            current = new Stroke(COLORS[color], r, new ArrayList<>());
            current.points.add(new float[]{ix, iy});
            strokes.add(current);
            stamp(texture.getPixels(), ix, iy, r, current.color);
            dirty = true;
        } else if (tool == Tool.CROP) {
            before = snap();
            cropStart = new double[]{clamp01(toImgX(mx) / imgW), clamp01(toImgY(my) / imgH)};
        }
        return true;
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragButton < 0) return super.mouseDragged(event, dx, dy);
        double mx = event.x(), my = event.y();
        if (dragButton != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || tool == Tool.VIEW) {
            panX += mx - dragX;
            panY += my - dragY;
        } else if (tool == Tool.DRAW && current != null) {
            float[] last = current.points.get(current.points.size() - 1);
            float ix = (float) toImgX(mx), iy = (float) toImgY(my);
            if (Math.abs(ix - last[0]) + Math.abs(iy - last[1]) >= 0.5f) {
                float[] p = {ix, iy};
                current.points.add(p);
                segment(texture.getPixels(), last, p, current.radius, current.color);
                dirty = true;
            }
        } else if (tool == Tool.CROP && cropStart != null) {
            double x = clamp01(toImgX(mx) / imgW), y = clamp01(toImgY(my) / imgH);
            crop = new double[]{Math.min(cropStart[0], x), Math.min(cropStart[1], y),
                    Math.max(cropStart[0], x), Math.max(cropStart[1], y)};
        }
        dragX = mx;
        dragY = my;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragButton >= 0 && event.button() == dragButton) {
            if (dragButton == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && tool == Tool.DRAW && current != null) {
                current = null;
                commit(before);
            } else if (dragButton == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && tool == Tool.CROP && cropStart != null) {
                cropStart = null;
                if (crop[2] - crop[0] < MIN_CROP || crop[3] - crop[1] < MIN_CROP) crop = before.crop.clone();
                else if (!java.util.Arrays.equals(crop, before.crop)) commit(before);
            }
            dragButton = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (texture == null || sy == 0 || !inCanvas(mx, my)) return false;
        double ix = toImgX(mx), iy = toImgY(my);
        zoom = Math.clamp(zoom * Math.pow(ZOOM_STEP, Math.signum(sy)), MIN_ZOOM, MAX_ZOOM);
        // keep the pixel under the cursor in place
        panX += mx - (imageX() + ix * scale());
        panY += my - (imageY() + iy * scale());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean ctrl = (event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL) != 0;
        boolean shift = (event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT) != 0;
        int k = event.key();
        if (ctrl && k == com.mojang.blaze3d.platform.InputConstants.KEY_Z) { if (shift) redo(); else undo(); return true; }
        if (ctrl && k == com.mojang.blaze3d.platform.InputConstants.KEY_Y) { redo(); return true; }
        return super.keyPressed(event);
    }

    // --- actions ---

    private boolean confirm(String action, String prompt) {
        long now = System.currentTimeMillis();
        if (action.equals(armed) && now - armedAt < 4000) {
            armed = null;
            return true;
        }
        armed = action;
        armedAt = now;
        status = prompt;
        return false;
    }

    @Override
    public void onClose() {
        if (unsaved && !confirm("discard", "§eUnsaved edits - press again to discard them")) return;
        minecraft.gui.setScreen(parent);
    }

    private void delete() {
        if (!confirm("delete", "§eClick §cDelete§e again to delete " + file.getName())) return;
        if (ScreenshotManager.delete(file)) {
            ScreenshotScreen.forget(file.getName());
            unsaved = false;
            minecraft.gui.setScreen(parent);
        } else {
            status = "§cDelete failed";
        }
    }

    private void share() {
        if (texture == null || !confirm("share", "§eClick §dShare§e again to upload" + (hasEdits() ? " the edited picture" : ""))) return;
        File upload = file;
        if (hasEdits()) {
            try {
                Path tmp = Files.createTempFile("teslamaps-edit-", ".png");
                tmp.toFile().deleteOnExit();
                try (NativeImage out = render()) {
                    out.writeToFile(tmp);
                }
                upload = tmp.toFile();
            } catch (Exception e) {
                status = "§cCould not prepare the upload: " + e.getMessage();
                return;
            }
        }
        status = "§7Uploading...";
        ImageShare.upload(upload,
                url -> {
                    String token = ImageShare.toToken(url);
                    if (token == null) {
                        status = "§cCannot build a token for " + url;
                        return;
                    }
                    minecraft.keyboardHandler.setClipboard(ImageShare.TAG + " " + token);
                    status = "§aCopied - paste it into any chat";
                },
                err -> status = "§cUpload failed: " + err);
    }

    private boolean hasEdits() {
        return !strokes.isEmpty() || !isFull(crop);
    }

    private int[] cropPixels() {
        int l = Math.clamp((int) Math.floor(crop[0] * imgW), 0, imgW - 1);
        int t = Math.clamp((int) Math.floor(crop[1] * imgH), 0, imgH - 1);
        int r = Math.clamp((int) Math.ceil(crop[2] * imgW), l + 1, imgW);
        int b = Math.clamp((int) Math.ceil(crop[3] * imgH), t + 1, imgH);
        return new int[]{l, t, r, b};
    }

    private NativeImage render() {
        int[] c = cropPixels();
        NativeImage work = texture.getPixels();
        NativeImage out = new NativeImage(c[2] - c[0], c[3] - c[1], false);
        for (int y = c[1]; y < c[3]; y++) {
            for (int x = c[0]; x < c[2]; x++) out.setPixel(x - c[0], y - c[1], work.getPixel(x, y));
        }
        return out;
    }

    private void save(boolean replace) {
        if (texture == null) return;
        File dest = replace ? file : copyName();
        try {
            // a temp file outside screenshots/, so the capture hooks do not fire for it
            Path tmp = Files.createTempFile("teslamaps-edit-", ".png");
            try (NativeImage out = render()) {
                out.writeToFile(tmp);
            }
            Files.move(tmp, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ScreenshotScreen.forget(dest.getName());
            unsaved = false;
            status = "§aSaved " + dest.getName();
            if (replace) minecraft.gui.setScreen(new ScreenshotEditorScreen(parent, file));
        } catch (Exception e) {
            status = "§cSave failed: " + e.getMessage();
            TeslaMaps.LOGGER.warn("Screenshot editor save failed", e);
        }
    }

    private File copyName() {
        String base = file.getName().replaceFirst("\\.png$", "");
        File dir = file.getParentFile();
        for (int i = 1; ; i++) {
            File f = new File(dir, base + "_edited" + (i == 1 ? "" : "_" + i) + ".png");
            if (!f.exists()) return f;
        }
    }

    // --- render ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xF0101012);
        HudStyle.screenPanel(ctx, 4, 2, width - 8, TOP - 4, 4);
        if (dirty && texture != null) {
            texture.upload();
            dirty = false;
        }

        if (error != null) {
            ctx.centeredText(font, "§c" + error, width / 2, height / 2, 0xFFFF5555);
        } else if (textureId != null) {
            int cx = canvasX(), cy = canvasY(), cw = canvasW(), ch = canvasH();
            ctx.enableScissor(cx, cy, cx + cw, cy + ch);
            double s = scale();
            int x0 = (int) Math.round(imageX()), y0 = (int) Math.round(imageY());
            int x1 = (int) Math.round(imageX() + imgW * s), y1 = (int) Math.round(imageY() + imgH * s);
            ctx.blit(textureId, x0, y0, x1, y1, 0f, 1f, 0f, 1f);
            if (!isFull(crop) || cropStart != null) {
                int l = (int) Math.round(imageX() + crop[0] * imgW * s), t = (int) Math.round(imageY() + crop[1] * imgH * s);
                int r = (int) Math.round(imageX() + crop[2] * imgW * s), b = (int) Math.round(imageY() + crop[3] * imgH * s);
                int dim = 0xA0000000;
                ctx.fill(x0, y0, x1, t, dim);
                ctx.fill(x0, b, x1, y1, dim);
                ctx.fill(x0, t, l, b, dim);
                ctx.fill(r, t, x1, b, dim);
                int line = 0xFFFFFFFF;
                ctx.fill(l, t, r, t + 1, line);
                ctx.fill(l, b - 1, r, b, line);
                ctx.fill(l, t, l + 1, b, line);
                ctx.fill(r - 1, t, r, b, line);
            }
            if (tool == Tool.DRAW && inCanvas(mouseX, mouseY)) {
                int rad = (int) Math.max(1, Math.round(SIZES[size] * Math.min(imgW, imgH) / 2 * s));
                int c = COLORS[color];
                ctx.fill(mouseX - rad, mouseY, mouseX + rad + 1, mouseY + 1, c);
                ctx.fill(mouseX, mouseY - rad, mouseX + 1, mouseY + rad + 1, c);
            }
            ctx.disableScissor();
        }

        int sx = swatchX();
        for (int i = 0; i < COLORS.length; i++) {
            int x = sx + i * (SWATCH + 3), y = 9;
            if (i == color) ctx.fill(x - 2, y - 2, x + SWATCH + 2, y + SWATCH + 2, 0xFFFFFFFF);
            ctx.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, 0xFF000000);
            ctx.fill(x, y, x + SWATCH, y + SWATCH, COLORS[i]);
        }

        String info = file.getName() + " §8· §7" + Math.round(zoom * 100) + "%"
                + (hasEdits() ? " §8· §7" + strokes.size() + " strokes" : "");
        ctx.text(font, "§7" + info, 10, height - BOTTOM - 10, 0xFFAAAAAA);
        String right = !status.isEmpty() ? status : switch (tool) {
            case VIEW -> "§8drag to pan · scroll to zoom";
            case CROP -> "§8drag a rectangle to crop · right-drag pans";
            case DRAW -> "§8drag to draw · right-drag pans · ctrl+z / ctrl+y";
        };
        ctx.text(font, right, width - 10 - font.width(right), height - BOTTOM - 10, 0xFFFFFFFF);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
