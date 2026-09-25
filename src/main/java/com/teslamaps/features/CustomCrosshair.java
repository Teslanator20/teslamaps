package com.teslamaps.features;

// Shape after FloydAddons' FloydCrosshairChanger.kt, third person/centering after Devonian's Misc.kt and Skysoft's CenteredCrosshairFix.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CustomCrosshair {

    public static final String[] SHAPES = {"Vanilla", "Cross", "Dot", "X", "Circle", "Star"};
    private static Feature feature;

    private CustomCrosshair() {}

    public static void register() {
        feature = Feature.of("CustomCrosshair").section("Render").when(() -> TeslaMapsConfig.get().customCrosshair)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crosshair");
                    b.toggle("Crosshair Tweaks", () -> c.customCrosshair, v -> c.customCrosshair = v)
                            .desc("Own crosshair shape and color, crosshair in third person, exact centering.");
                    b.dropdown("  Shape", SHAPES, () -> c.crosshairShape, v -> c.crosshairShape = v);
                    b.color("  Color", () -> c.crosshairColor, v -> c.crosshairColor = v);
                    b.toggle("  Chroma", () -> c.crosshairChroma, v -> c.crosshairChroma = v);
                    b.slider("  Size", 2f, 24f, () -> (float) c.crosshairSize, v -> c.crosshairSize = v.intValue());
                    b.slider("  Thickness", 1f, 5f, () -> (float) c.crosshairThickness, v -> c.crosshairThickness = v.intValue());
                    b.slider("  Center Gap", 0f, 8f, () -> (float) c.crosshairGap, v -> c.crosshairGap = v.intValue());
                    b.toggle("  Show In Third Person", () -> c.crosshairThirdPerson, v -> c.crosshairThirdPerson = v);
                    b.toggle("  Exact Centering", () -> c.crosshairCentered, v -> c.crosshairCentered = v)
                            .desc("Fixes the one-pixel offset of the vanilla crosshair at odd window sizes.");
                });
    }

    private static boolean on() {
        return feature != null && TeslaMapsConfig.get().customCrosshair && feature.active();
    }

    /** CameraType.isFirstPerson inside Gui.extractCrosshair. */
    public static boolean firstPerson(boolean original) {
        return original || on() && TeslaMapsConfig.get().crosshairThirdPerson;
    }

    /** 0 = vanilla, 1 = vanilla moved to the exact center, 2 = own shape. */
    public static int mode() {
        if (!on()) return 0;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.crosshairShape == null || c.crosshairShape.equals("Vanilla")) return c.crosshairCentered ? 1 : 0;
        return 2;
    }

    /** Pushes a pose shift onto the exact screen center. False = nothing pushed. */
    public static boolean pushCenter(GuiGraphicsExtractor g, float cx, float cy) {
        var win = Minecraft.getInstance().getWindow();
        double scale = win.getGuiScale();
        float ox = (float) (win.getWidth() / scale / 2.0 - cx), oy = (float) (win.getHeight() / scale / 2.0 - cy);
        if (ox == 0 && oy == 0) return false;
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        return true;
    }

    public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String shape = c.crosshairShape;
        int color = TeslaMapsConfig.parseColor(c.crosshairColor);
        if (c.crosshairChroma) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f;
            color = (color & 0xFF000000) | (java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) & 0xFFFFFF);
        }
        float cx = x + w / 2, cy = y + h / 2;
        boolean shift = c.crosshairCentered && pushCenter(g, cx + 0.5f, cy + 0.5f);
        int size = Math.max(1, c.crosshairSize), t = Math.max(1, c.crosshairThickness), gap = Math.max(0, c.crosshairGap);
        switch (shape) {
            case "Cross" -> {
                line(g, cx, cy - size, cx, cy - gap, t, color);
                line(g, cx, cy + gap, cx, cy + size, t, color);
                line(g, cx - size, cy, cx - gap, cy, t, color);
                line(g, cx + gap, cy, cx + size, cy, t, color);
            }
            case "Dot" -> line(g, cx, cy, cx, cy, Math.max(t, size / 3), color);
            case "X" -> {
                float s = size * 0.7071f, q = gap * 0.7071f;
                line(g, cx - s, cy - s, cx - q, cy - q, t, color);
                line(g, cx + q, cy + q, cx + s, cy + s, t, color);
                line(g, cx + s, cy - s, cx + q, cy - q, t, color);
                line(g, cx - q, cy + q, cx - s, cy + s, t, color);
            }
            case "Circle" -> {
                int n = Math.max(12, size * 3);
                float r = Math.max(size, gap + 1);
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                    line(g, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                            cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, t, color);
                }
            }
            default -> {
                float dx = size * 0.8660f, half = size * 0.5f;
                tri(g, cx, cy - size, cx - dx, cy + half, cx + dx, cy + half, t, color, gap);
                tri(g, cx, cy + size, cx - dx, cy - half, cx + dx, cy - half, t, color, gap);
            }
        }
        if (shift) g.pose().popMatrix();
    }

    private static void tri(GuiGraphicsExtractor g, float ax, float ay, float bx, float by, float cx, float cy,
                            int t, int color, int gap) {
        seg(g, ax, ay, bx, by, t, color, gap);
        seg(g, bx, by, cx, cy, t, color, gap);
        seg(g, cx, cy, ax, ay, t, color, gap);
    }

    private static void seg(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int t, int color, int gap) {
        if (gap <= 0) { line(g, x0, y0, x1, y1, t, color); return; }
        float vx = x1 - x0, vy = y1 - y0;
        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len <= gap * 2f) return;
        float mx = (x0 + x1) / 2f, my = (y0 + y1) / 2f, ux = vx / len * gap, uy = vy / len * gap;
        line(g, x0, y0, mx - ux, my - uy, t, color);
        line(g, mx + ux, my + uy, x1, y1, t, color);
    }

    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int t, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(1, Math.round(Math.max(Math.abs(dx), Math.abs(dy))));
        float r = (t - 1) / 2f;
        if (dx == 0 || dy == 0) {
            int l = Math.round(Math.min(x0, x1) - r), tp = Math.round(Math.min(y0, y1) - r);
            int rt = Math.round(Math.max(x0, x1) + r + 1), bt = Math.round(Math.max(y0, y1) + r + 1);
            g.fill(l, tp, rt, bt, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            float p = i / (float) steps;
            float x = x0 + dx * p, y = y0 + dy * p;
            g.fill(Math.round(x - r), Math.round(y - r), Math.round(x + r + 1), Math.round(y + r + 1), color);
        }
    }
}
