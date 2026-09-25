package com.teslamaps.render.gui;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Look of the mod's own screens. Separate from {@link HudStyle}, which only covers the
 * in-game HUD panels — a player can run a glassy menu with plain vanilla HUD text or the
 * other way around.
 *
 * <p>A screen never picks a colour itself, it asks for a role (card, border, accent) and
 * draws its panels through {@link #panel}, which is where Glass swaps the flat fill for
 * the blurred glass stack.
 */
public final class GuiStyle {

    public static final String MODERN = "Modern";
    public static final String PURPLE = "Purple";
    public static final String GLASS = "Glass";

    public static final String[] NAMES = {MODERN, PURPLE, GLASS};

    private record Palette(int background, int sidebar, int card, int cardHover, int input,
                           int border, int borderHover, int accent, int textDim, int textFaint,
                           int rowHover, int rowSelected, int scrollTrack, int scrollThumb,
                           int toggleOff, int knob) {}

    // flat and dark, thin light hairlines instead of grey borders, blue accent
    private static final Palette MODERN_P = new Palette(
            0xFF0E0F12, 0xFF14161A, 0xFF1A1C21, 0xFF23262D, 0xFF101216,
            0x1AFFFFFF, 0x40FFFFFF, 0xFF5B8CFF, 0xFF9AA3AF, 0xFF636B76,
            0x14FFFFFF, 0x2E5B8CFF, 0xFF14161A, 0xFF3A4048, 0xFF23262D, 0xFFFFFFFF);

    private static final Palette PURPLE_P = new Palette(
            0xF0100010, 0xF0170022, 0xF01D0030, 0xF02A0048, 0xF0140018,
            0xFF5000FF, 0xFF7A3CFF, 0xFFC58AFF, 0xFFA98FD0, 0xFF7A5FA0,
            0x309060FF, 0x509060FF, 0xFF24003A, 0xFF5000FF, 0xFF2A0A40, 0xFFE8D8FF);

    // the blurred backdrop carries the look; the veil only has to buy back text contrast
    private static final Palette GLASS_P = new Palette(
            0xA6070A0F, 0x2EFFFFFF, 0x00000000, 0x00000000, 0x00000000,
            0x59FFFFFF, 0x8CFFFFFF, 0xFF7FE9FF, 0xFFCEDAE6, 0xFF9AA8B6,
            0x22FFFFFF, 0x3CFFFFFF, 0x1AFFFFFF, 0x80FFFFFF, 0x59202832, 0xFFFFFFFF);

    private GuiStyle() {}

    public static String current() {
        String style = TeslaMapsConfig.get().guiStyle;
        if (style == null) return MODERN;
        if (style.equals("Tooltip")) return PURPLE;      // renamed
        for (String name : NAMES) if (name.equals(style)) return name;
        return MODERN;
    }

    private static Palette palette() {
        return switch (current()) {
            case PURPLE -> PURPLE_P;
            case GLASS -> GLASS_P;
            default -> MODERN_P;
        };
    }

    public static boolean isGlass() {
        return current().equals(GLASS);
    }

    /** Whether the screen wants the world behind it blurred before anything is drawn. */
    public static boolean blurBackground() {
        return isGlass();
    }

    /**
     * Panel behind a button, dropdown or input. Glass draws the frosted stack (veil,
     * top highlight, hairline, soft shadow), every other style a flat card fill.
     */
    public static void panel(GuiGraphicsExtractor ctx, float x, float y, float width, float height,
                             boolean hovered) {
        panel(ctx, x, y, width, height, hovered, 4f);
    }

    public static void panel(GuiGraphicsExtractor ctx, float x, float y, float width, float height,
                             boolean hovered, float radius) {
        if (width <= 0 || height <= 0) return;
        if (isGlass()) {
            TeslaGui.glassPanel(ctx, x, y, width, height, radius,
                    hovered ? 38 : 24, hovered ? 58 : 40, 20, hovered ? 26 : 16);
            return;
        }
        TeslaGui.roundedRect(ctx, x, y, width, height, radius, hovered ? cardHover() : card());
    }

    /** Sidebar strip. Glass frosts it instead of painting a solid column. */
    public static void sidebarPanel(GuiGraphicsExtractor ctx, float x, float y, float width, float height) {
        if (isGlass()) {
            ctx.fill((int) x, (int) y, (int) (x + width), (int) (y + height), 0x59000000);
            TeslaGui.glassPanel(ctx, x - 8, y - 8, width + 8, height + 16, 12, 22, 40, 24, 14);
            return;
        }
        ctx.fill((int) x, (int) y, (int) (x + width), (int) (y + height), sidebar());
    }

    public static int background() { return palette().background(); }

    public static int sidebar() { return palette().sidebar(); }

    /** Buttons, dropdowns, panels sitting on the background. */
    public static int card() { return palette().card(); }

    public static int cardHover() { return palette().cardHover(); }

    /** Text input background (search bar). */
    public static int input() { return palette().input(); }

    public static int border() { return palette().border(); }

    /** Border of an element that is hovered but not active. */
    public static int borderHover() { return palette().borderHover(); }

    /** Selection, active headers, toggle-on track. */
    public static int accent() {
        int own = customAccent();
        return own != 0 ? own : palette().accent();
    }

    private static String accentHex;
    private static int accentArgb;

    /** User accent from the GUI Style option, 0 when off. */
    private static int customAccent() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.guiCustomAccent || c.guiAccentColor == null) return 0;
        if (!c.guiAccentColor.equals(accentHex)) {
            accentHex = c.guiAccentColor;
            accentArgb = 0xFF000000 | TeslaMapsConfig.parseColor(accentHex);
        }
        return accentArgb;
    }

    public static int text() { return 0xFFFFFFFF; }

    public static int textDim() { return palette().textDim(); }

    /** Section headers and other barely-there labels. */
    public static int textFaint() { return palette().textFaint(); }

    public static int rowHover() { return palette().rowHover(); }

    public static int rowSelected() {
        int sel = palette().rowSelected(), own = customAccent();
        return own != 0 ? (sel & 0xFF000000) | (own & 0xFFFFFF) : sel;
    }

    public static int scrollTrack() { return palette().scrollTrack(); }

    public static int scrollThumb() { return palette().scrollThumb(); }

    public static int toggleOff() { return palette().toggleOff(); }

    public static int knob() { return palette().knob(); }
}
