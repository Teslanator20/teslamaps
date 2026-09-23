package com.teslamaps.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * One entity sweep per tick instead of one per feature per frame.
 * Built lazily on first access, dropped again by invalidate() at tick end,
 * so a tick nobody asks costs nothing.
 * Ported from bax's utils/entity/EntityLookupCache.
 */
public final class EntityCache {
    private static List<Entity> all;
    private static List<ArmorStand> armorStands;
    private static List<ArmorStand> namedArmorStands;
    private static Set<UUID> uuids;
    private static final Map<Class<?>, List<?>> typed = new HashMap<>();

    private EntityCache() {}

    public static void invalidate() {
        all = null;
        armorStands = null;
        namedArmorStands = null;
        uuids = null;
        typed.clear();
    }

    private static void build() {
        if (all != null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            all = Collections.emptyList();
            armorStands = Collections.emptyList();
            namedArmorStands = Collections.emptyList();
            uuids = Collections.emptySet();
            return;
        }
        List<Entity> a = new ArrayList<>();
        List<ArmorStand> stands = new ArrayList<>();
        List<ArmorStand> named = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            a.add(e);
            ids.add(e.getUUID());
            if (e instanceof ArmorStand as) {
                stands.add(as);
                if (as.hasCustomName() && as.getCustomName() != null) named.add(as);
            }
        }
        all = a;
        armorStands = stands;
        namedArmorStands = named;
        uuids = ids;
    }

    public static List<Entity> all() {
        build();
        return all;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> List<T> byType(Class<T> type) {
        build();
        List<?> list = typed.get(type);
        if (list == null) {
            List<T> out = new ArrayList<>();
            for (Entity e : all) if (type.isInstance(e)) out.add(type.cast(e));
            typed.put(type, out);
            list = out;
        }
        return (List<T>) list;
    }

    public static List<ArmorStand> armorStands() {
        build();
        return armorStands;
    }

    /** Armor stands with a custom name — the ones Hypixel uses as mob/HP labels. */
    public static List<ArmorStand> namedArmorStands() {
        build();
        return namedArmorStands;
    }

    public static Set<UUID> renderedUuids() {
        build();
        return uuids;
    }
}
