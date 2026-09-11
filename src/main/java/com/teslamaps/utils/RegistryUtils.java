package com.teslamaps.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;

/** Ported from Skyblocker's RegistryUtils. */
public final class RegistryUtils {
    private static HolderLookup.Provider fallback;

    private RegistryUtils() {}

    /** The connection's registries if there is one, else a vanilla lookup. */
    public static HolderLookup.Provider lookup() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getConnection() != null && client.getConnection().registryAccess() != null) {
            return client.getConnection().registryAccess();
        }
        if (fallback == null) fallback = VanillaRegistries.createLookup();
        return fallback;
    }
}
