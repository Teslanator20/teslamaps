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
    public final String prefix;
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
        this.prefix = prefix;
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

    public String section() { return section; }

    /** Toggle and section both on - what {@link #shown} checks besides the situation. */
    public boolean enabledNow() {
        return enabled.getAsBoolean() && (section == null || TeslaMapsConfig.get().section(section));
    }

    public boolean shownDisabled(boolean showAll) {
        return !enabledNow() && (showAll || ctx.now());
    }

    private Field toggleField;
    private boolean toggleLooked;

    /** The boolean config field that switches this element's feature, found by name, or null. */
    public Field toggleField() {
        if (toggleLooked) return toggleField;
        toggleLooked = true;
        String cap = Character.toUpperCase(prefix.charAt(0)) + prefix.substring(1);
        for (String n : new String[]{prefix, prefix + "Enabled", "show" + cap, prefix + "Hud", prefix + "Display",
                prefix + "Tracker", prefix + "Timer", prefix + "Overlay"}) {
            try {
                Field f = TeslaMapsConfig.class.getField(n);
                if (f.getType() == boolean.class) { toggleField = f; break; }
            } catch (NoSuchFieldException ignored) {}
        }
        return toggleField;
    }

    /** @return the new value, or null when there is no toggle field. */
    public Boolean toggle() {
        Field f = toggleField();
        if (f == null) return null;
        try {
            boolean v = !f.getBoolean(TeslaMapsConfig.get());
            f.setBoolean(TeslaMapsConfig.get(), v);
            return v;
        } catch (IllegalAccessException e) {
            throw fail(e);
        }
    }

    public void resetToDefault(TeslaMapsConfig defaults) {
        try {
            fx.setInt(TeslaMapsConfig.get(), fx.getInt(defaults));
            fy.setInt(TeslaMapsConfig.get(), fy.getInt(defaults));
            fscale.setFloat(TeslaMapsConfig.get(), fscale.getFloat(defaults));
        } catch (IllegalAccessException e) {
            throw fail(e);
        }
        TeslaMapsConfig.get().hudAnchorRefs.remove(prefix);
        cachedAt = 0;
    }

    public void nudgeScale(float d) {
        float s = Math.round((scale() + d) * 100f) / 100f;
        setFloat(fscale, Math.max(min, Math.min(max, s)));
        cachedAt = 0;
    }

    public int x() { return xOverride != null ? xOverride.getAsInt() : getInt(fx); }
    public int y() { return yOverride != null ? yOverride.getAsInt() : getInt(fy); }
    public float scale() { return getFloat(fscale); }
    public void setX(int v) { setInt(fx, v); }
    public void setY(int v) { setInt(fy, v); }
    public int width() { float g = globalScale(); return g == 1f ? w.of(scale()) : (int) (w.of(scale()) * g); }
    public int height() { float g = globalScale(); return g == 1f ? h.of(scale()) : (int) (h.of(scale()) * g); }

    /** GUI-015: multiplier on top of every element's own scale. */
    public static float globalScale() {
        float g = TeslaMapsConfig.get().hudGlobalScale;
        return g > 0.09f && g < 10.01f ? g : 1f;
    }

    /** Pivot of the global scale: the unshifted top-left of the drawn box. */
    public int pivotX() { return x() - (int) (boxOffset * scale()); }
    public int pivotY() { return y() - (int) (boxOffset * scale()); }
    public int boxX() { return x() - (int) (boxOffset * scale()) + shiftX(); }
    public int boxY() { return y() - (int) (boxOffset * scale()) + shiftY(); }
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

    public void setScale(float s) {
        setFloat(fscale, Math.max(min, Math.min(max, Math.round(s / step) * step)));
        cachedAt = 0;
    }

    public void drawPreview(GuiGraphicsExtractor g, Minecraft mc) {
        if (preview == null) return;
        float gs = globalScale();
        if (gs == 1f) { preview.draw(g, mc, x() + shiftX(), y() + shiftY(), scale()); return; }
        int px = boxX(), py = boxY();
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(gs, gs);
        g.pose().translate(-px, -py);
        preview.draw(g, mc, x() + shiftX(), y() + shiftY(), scale());
        g.pose().popMatrix();
    }

    public String title() {
        return plainLabel ? label : String.format("%s (%.1fx)", label, scale());
    }

    // GUI-005: edge anchors + render clamp, applied as an offset; the stored position never changes
    private int cachedDx, cachedDy;
    private long cachedAt;

    public int shiftX() { updateShift(); return cachedDx; }
    public int shiftY() { updateShift(); return cachedDy; }

    private void updateShift() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hudClampToScreen && !c.hudEdgeAnchors) { cachedDx = cachedDy = 0; return; }
        long now = System.currentTimeMillis();
        if (now - cachedAt < 200 && cachedAt != 0) return;
        cachedAt = now;
        var win = Minecraft.getInstance().getWindow();
        int sw = win.getGuiScaledWidth(), sh = win.getGuiScaledHeight();
        int bw = width(), bh = height();
        int bx = x() - (int) (boxOffset * scale()), by = y() - (int) (boxOffset * scale());
        int dx = 0, dy = 0;
        int[] ref = c.hudAnchorRefs.get(prefix);
        if (c.hudEdgeAnchors && ref != null && ref.length == 2 && xOverride == null && yOverride == null) {
            if (bx + bw / 2 > ref[0] / 2) dx = sw - ref[0];
            if (by + bh / 2 > ref[1] / 2) dy = sh - ref[1];
        }
        if (c.hudClampToScreen) {
            int nx = bx + dx, ny = by + dy;
            dx += Math.max(0, Math.min(sw - bw, nx)) - nx;
            dy += Math.max(0, Math.min(sh - bh, ny)) - ny;
        }
        cachedDx = dx;
        cachedDy = dy;
    }

    /** Before a drag: store where the panel is actually drawn, and anchor it to this screen size. */
    public void bakeShift() {
        cachedAt = 0;
        int dx = shiftX(), dy = shiftY();
        if (dx != 0) setX(x() + dx);
        if (dy != 0) setY(y() + dy);
        recordAnchor();
    }

    public void recordAnchor() {
        var win = Minecraft.getInstance().getWindow();
        TeslaMapsConfig.get().hudAnchorRefs.put(prefix, new int[]{win.getGuiScaledWidth(), win.getGuiScaledHeight()});
        cachedAt = 0;
    }

    /** When anchors get switched on: every element not placed yet counts as placed on this screen. */
    public static void anchorUnplaced() {
        for (HudEditElement e : ALL) if (!TeslaMapsConfig.get().hudAnchorRefs.containsKey(e.prefix)) e.recordAnchor();
    }

    private static final java.util.Map<String, HudEditElement> BY_HUD = new java.util.HashMap<>();
    // clamp themselves or draw several elements
    private static final java.util.Set<String> UNLINKED = java.util.Set.of("farmprofit", "dungeontimers");
    private static final java.util.Map<String, String> HUD_ALIAS = java.util.Map.ofEntries(
            java.util.Map.entry("skilltracker", "skillTracker"), java.util.Map.entry("skillexp", "skillExpDisplay"),
            java.util.Map.entry("petoverlay", "petOverlay"), java.util.Map.entry("bestiary", "bestiaryTracker"),
            java.util.Map.entry("charmedvisitors", "charmedVisitor"), java.util.Map.entry("feastseason", "feastSeason"),
            java.util.Map.entry("safari", "safariHud"), java.util.Map.entry("hotfperks", "hotfPerkDisplay"),
            java.util.Map.entry("lasso", "lassoDisplay"), java.util.Map.entry("lather", "latherOverlay"),
            java.util.Map.entry("contesttimers", "contestTimer"), java.util.Map.entry("deployables", "deployable"),
            java.util.Map.entry("dragontimer", "witherDragonHud"),
            java.util.Map.entry("perfhud", "perfHud"));

    /** The editor element of a registered HUD id (same name ignoring case, or an alias), or null. */
    private static final java.util.Map<String, Integer> MISSED_AT = new java.util.HashMap<>();

    public static HudEditElement forHud(String hudId) {
        HudEditElement cached = BY_HUD.get(hudId);
        if (cached != null) return cached;
        if (UNLINKED.contains(hudId)) return null;
        // a miss only holds until more elements register
        Integer missed = MISSED_AT.get(hudId);
        if (missed != null && missed == ALL.size()) return null;
        HudEditElement hit = null;
        String want = HUD_ALIAS.getOrDefault(hudId, hudId);
        for (HudEditElement e : ALL) if (e.prefix.equalsIgnoreCase(want)) { hit = e; break; }
        if (hit != null) BY_HUD.put(hudId, hit);
        else MISSED_AT.put(hudId, ALL.size());
        return hit;
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
