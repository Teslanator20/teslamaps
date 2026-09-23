package com.teslamaps.registry;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * A movable panel in /tmap gui. Position and scale live in the config fields
 * {@code <prefix>X}, {@code <prefix>Y}, {@code <prefix>Scale}.
 */
public final class HudEditElement {

    public enum HudCtx {
        ANY, DUNGEON, GARDEN, SAFARI, FORAGING, MINING, THE_END;

        public boolean now() {
            return switch (this) {
                case ANY -> true;
                case DUNGEON -> com.teslamaps.dungeon.DungeonManager.isInDungeon();
                case GARDEN -> com.teslamaps.utils.SkyblockUtils.isInGarden();
                case SAFARI -> com.teslamaps.safari.SafariData.inSafari();
                case FORAGING -> com.teslamaps.features.foraging.Foraging.onForagingIsland();
                case MINING -> com.teslamaps.features.mining.Mining.onMiningIsland();
                case THE_END -> com.teslamaps.features.theend.EndProtector.inArea();
            };
        }
    }

    public interface Preview {
        void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale);
    }

    public interface ScaledSize {
        int of(float scale);
    }

    static final List<HudEditElement> ALL = new ArrayList<>();

    public static List<HudEditElement> all() {
        return ALL;
    }

    public final String label;
    private final Field fx, fy, fscale;
    private HudCtx ctx = HudCtx.ANY;
    private String section;
    private BooleanSupplier enabled = () -> true;
    private ScaledSize w, h;
    private Preview preview;
    private IntSupplier xOverride, yOverride;
    private int boxOffset;
    private int dragColor = 0xFFFF5555;
    private float min = 0.5f, max = 3.0f, step = 0.05f;
    private boolean plainLabel;

    private HudEditElement(String prefix, String label) {
        this.label = label;
        this.fx = field(prefix + "X");
        this.fy = field(prefix + "Y");
        this.fscale = field(prefix + "Scale");
    }

    public static HudEditElement of(String configPrefix, String label) {
        HudEditElement e = new HudEditElement(configPrefix, label);
        ALL.add(e);
        return e;
    }

    private static Field field(String name) {
        try {
            return TeslaMapsConfig.class.getField(name);
        } catch (NoSuchFieldException ex) {
            throw new IllegalStateException("HudEditElement: no config field " + name, ex);
        }
    }

    public HudEditElement ctx(HudCtx ctx) { this.ctx = ctx; return this; }
    public HudEditElement section(String section) { this.section = section; return this; }
    public HudEditElement enabled(BooleanSupplier enabled) { this.enabled = enabled; return this; }
    public HudEditElement preview(Preview preview) { this.preview = preview; return this; }
    public HudEditElement dragColor(int argb) { this.dragColor = argb; return this; }
    public HudEditElement scaleRange(float min, float max) { this.min = min; this.max = max; return this; }
    public HudEditElement scaleStep(float step) { this.step = step; return this; }
    /** Label without the "(1.0x)" suffix. */
    public HudEditElement plainLabel() { this.plainLabel = true; return this; }
    /** The drawn panel starts {@code offset * scale} px up-left of the stored position. */
    public HudEditElement boxOffset(int offset) { this.boxOffset = offset; return this; }
    /** For elements whose stored position is resolved (e.g. -1 = centered). */
    public HudEditElement position(IntSupplier x, IntSupplier y) { this.xOverride = x; this.yOverride = y; return this; }

    /** Unscaled width/height, multiplied by the scale. */
    public HudEditElement size(IntSupplier w, IntSupplier h) {
        this.w = s -> (int) (w.getAsInt() * s);
        this.h = s -> (int) (h.getAsInt() * s);
        return this;
    }

    /** Width/height that already take the scale. */
    public HudEditElement scaledSize(ScaledSize w, ScaledSize h) {
        this.w = w;
        this.h = h;
        return this;
    }

    public boolean shown(boolean showAll) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return enabled.getAsBoolean() && (section == null || c.section(section)) && (showAll || ctx.now());
    }

    public int x() { return xOverride != null ? xOverride.getAsInt() : getInt(fx); }
    public int y() { return yOverride != null ? yOverride.getAsInt() : getInt(fy); }
    public float scale() { return getFloat(fscale); }
    public void setX(int v) { setInt(fx, v); }
    public void setY(int v) { setInt(fy, v); }
    public int width() { return w.of(scale()); }
    public int height() { return h.of(scale()); }
    public int boxX() { return x() - (int) (boxOffset * scale()); }
    public int boxY() { return y() - (int) (boxOffset * scale()); }
    public int dragColor() { return dragColor; }

    public boolean hit(int mouseX, int mouseY) {
        int bx = boxX(), by = boxY();
        return mouseX >= bx && mouseX <= bx + width() && mouseY >= by && mouseY <= by + height();
    }

    public void scroll(double amount) {
        float s = scale();
        if (amount > 0) s = Math.min(max, s + step);
        else if (amount < 0) s = Math.max(min, s - step);
        setFloat(fscale, s);
    }

    public void drawPreview(GuiGraphicsExtractor g, Minecraft mc) {
        if (preview != null) preview.draw(g, mc, x(), y(), scale());
    }

    public String title() {
        return plainLabel ? label : String.format("%s (%.1fx)", label, scale());
    }

    private static int getInt(Field f) {
        try { return f.getInt(TeslaMapsConfig.get()); } catch (IllegalAccessException e) { throw fail(e); }
    }

    private static float getFloat(Field f) {
        try { return f.getFloat(TeslaMapsConfig.get()); } catch (IllegalAccessException e) { throw fail(e); }
    }

    private static void setInt(Field f, int v) {
        try { f.setInt(TeslaMapsConfig.get(), v); } catch (IllegalAccessException e) { throw fail(e); }
    }

    private static void setFloat(Field f, float v) {
        try { f.setFloat(TeslaMapsConfig.get(), v); } catch (IllegalAccessException e) { throw fail(e); }
    }

    private static RuntimeException fail(Exception e) {
        TeslaMaps.LOGGER.error("HudEditElement field access", e);
        return new IllegalStateException(e);
    }
}
