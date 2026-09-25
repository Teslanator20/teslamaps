package com.teslamaps.render;

import java.util.IdentityHashMap;
import java.util.Map;

/** See-through entities beyond the body: armor and held items. Ported from Bax's GhostPlayerState. */
public final class GhostRender {

    /** Alpha of the living entity being submitted right now, 255 = none. */
    public static int submitting = 255;

    private static final Map<int[], Integer> ITEMS = new IdentityHashMap<>();

    private GhostRender() {}

    public static int[] trackItem(int[] tints) {
        int[] copy = tints.clone();
        ITEMS.put(copy, submitting);
        return copy;
    }

    public static int itemAlpha(int[] tints) {
        if (ITEMS.isEmpty()) return 255;
        Integer a = ITEMS.get(tints);
        return a == null ? 255 : a;
    }

    public static void clearItems() {
        if (!ITEMS.isEmpty()) ITEMS.clear();
    }

    public static int withAlpha(int argb, int a) {
        return (argb & 0xFFFFFF) | (Math.min((argb >>> 24) & 0xFF, a) << 24);
    }
}
