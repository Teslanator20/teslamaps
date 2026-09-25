package com.teslamaps.render;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.resources.Identifier;

/** Swaps vanilla's entity outline post chain for one with a dilate pass (post_effect/entity_outline_wN). */
public final class GlowOutlineWidth {

    public static final int MAX = 8;
    private static final Identifier VANILLA = Identifier.withDefaultNamespace("entity_outline");
    private static final Identifier[] WIDE = new Identifier[MAX + 1];

    static {
        for (int w = 2; w <= MAX; w++) WIDE[w] = Identifier.fromNamespaceAndPath("teslamaps", "entity_outline_w" + w);
    }

    private GlowOutlineWidth() {}

    public static Identifier swap(Identifier id) {
        int w = TeslaMapsConfig.get().glowOutlineWidth;
        if (w <= 1 || !VANILLA.equals(id)) return id;
        return WIDE[Math.min(w, MAX)];
    }
}
