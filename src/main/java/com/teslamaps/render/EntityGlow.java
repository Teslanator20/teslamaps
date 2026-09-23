package com.teslamaps.render;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Outline glow for registry features. Marks live for one tick: a feature re-marks its
 * entities every tick, and the set is cleared at the start of the next one.
 */
public final class EntityGlow {

    public static final Map<Entity, Integer> COLORS = new HashMap<>();

    private EntityGlow() {}

    public static void mark(Entity e, int rgb) {
        COLORS.put(e, rgb);
    }

    public static void clear() {
        COLORS.clear();
    }
}
