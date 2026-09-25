package com.teslamaps.features;

// Idea from Skyblocker's WidgetManager / CopyToPopup: one HUD layout per location
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps x/y/scale of every /tmap gui element per location. A location without its own layout
 * uses "Default"; it gets its own the first time something is moved while there.
 */
public final class HudLayouts {

    private HudLayouts() {}

    public static final String DEFAULT = "Default";
    private static final HudCtx[] CTXS = {HudCtx.DUNGEON, HudCtx.GARDEN, HudCtx.SAFARI, HudCtx.FORAGING, HudCtx.MINING, HudCtx.THE_END};
    public static final String[] NAMES = {DEFAULT, "Dungeons", "Garden", "Safari", "Foraging", "Mining", "The End"};

    private static String current;
    private static Map<String, float[]> loaded;
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HudLayouts").tickAlways(HudLayouts::tick)
                .config("Interface", b -> b.toggle("HUD Layout Per Location", () -> cfg().hudLayoutsPerLocation, v -> cfg().hudLayoutsPerLocation = v)
                        .desc("Every /tmap gui element keeps its own position and scale in Dungeons, Garden, Safari, Foraging, Mining and The End. /tmap gui gets Copy To / Reset buttons.")
                        .tags("hud", "layout", "location", "position", "copy"));
    }

    public static boolean enabled() {
        return cfg().hudLayoutsPerLocation;
    }

    public static String location() {
        for (int i = 0; i < CTXS.length; i++) if (CTXS[i].now()) return NAMES[i + 1];
        return DEFAULT;
    }

    public static String current() {
        return current == null ? location() : current;
    }

    private static void tick() {
        if (!enabled()) {
            current = null;
            return;
        }
        if (current != null && ++ticks % 20 != 0) return;
        String now = location();
        if (current == null) {
            cfg().hudLayouts.computeIfAbsent(DEFAULT, k -> snapshot());
            current = now;
            apply(now);
            return;
        }
        if (now.equals(current)) return;
        store(current);
        current = now;
        apply(now);
    }

    private static Map<String, float[]> snapshot() {
        Map<String, float[]> m = new HashMap<>();
        for (HudEditElement e : HudEditElement.all()) m.put(e.prefix, new float[]{e.storedX(), e.storedY(), e.scale()});
        return m;
    }

    private static boolean same(Map<String, float[]> a, Map<String, float[]> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (Map.Entry<String, float[]> en : a.entrySet()) if (!Arrays.equals(en.getValue(), b.get(en.getKey()))) return false;
        return true;
    }

    private static void store(String loc) {
        Map<String, float[]> snap = snapshot();
        if (cfg().hudLayouts.containsKey(loc) || !same(snap, loaded)) {
            cfg().hudLayouts.put(loc, snap);
            TeslaMapsConfig.save();
        }
        loaded = snap;
    }

    private static void apply(String loc) {
        Map<String, float[]> l = cfg().hudLayouts.get(loc);
        if (l == null) l = cfg().hudLayouts.get(DEFAULT);
        if (l != null) {
            for (HudEditElement e : HudEditElement.all()) {
                float[] v = l.get(e.prefix);
                if (v == null || v.length < 3) continue;
                e.setX(Math.round(v[0]));
                e.setY(Math.round(v[1]));
                e.setScale(v[2]);
            }
        }
        loaded = snapshot();
    }

    public static boolean hasOwn(String loc) {
        return cfg().hudLayouts.containsKey(loc);
    }

    /** From /tmap gui once it closes. */
    public static void editorClosed() {
        if (!enabled()) return;
        if (current == null) current = location();
        cfg().hudLayouts.computeIfAbsent(DEFAULT, k -> snapshot());
        store(current);
    }

    public static void copyTo(String target) {
        if (current == null) current = location();
        store(current);
        cfg().hudLayouts.put(target, snapshot());
        TeslaMapsConfig.save();
    }

    /** Current location falls back to Default again. */
    public static void resetCurrent() {
        if (current == null) current = location();
        if (DEFAULT.equals(current)) return;
        cfg().hudLayouts.remove(current);
        apply(current);
        TeslaMapsConfig.save();
    }
}
