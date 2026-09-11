package com.teslamaps.features.customize;

import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.RegistryUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.HashMap;
import java.util.Map;

/** Every material x pattern combination, cached once so the item mixin can hand one out per frame. */
public class CustomArmorTrims {
    public static final Map<ArmorTrimId, ArmorTrim> TRIMS_CACHE = new HashMap<>();
    private static boolean initialized = false;

    /** Lazily built: the trim registries only exist once a connection carries them. */
    public static ArmorTrim get(ArmorTrimId id, ArmorTrim fallback) {
        if (!initialized) init();
        return TRIMS_CACHE.getOrDefault(id, fallback);
    }

    public static void init() {
        if (initialized) return;
        try {
            HolderLookup.Provider lookup = RegistryUtils.lookup();
            TRIMS_CACHE.clear();
            for (Holder.Reference<TrimMaterial> material : lookup.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().toList()) {
                for (Holder.Reference<TrimPattern> pattern : lookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements().toList()) {
                    TRIMS_CACHE.put(new ArmorTrimId(material.key().identifier(), pattern.key().identifier()),
                            new ArmorTrim(material, pattern));
                }
            }
            initialized = true;
            TeslaMaps.LOGGER.info("[Customize] cached {} armor trims", TRIMS_CACHE.size());
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[Customize] failed to cache armor trims", e);
        }
    }
}
