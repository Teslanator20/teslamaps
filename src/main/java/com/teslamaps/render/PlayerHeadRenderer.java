package com.teslamaps.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility for rendering player heads on the dungeon map.
 * Uses caching for smooth, lag-free rendering.
 */
public class PlayerHeadRenderer {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    // Cache skins by UUID to avoid repeated lookups
    private static final Map<UUID, SkinTextures> skinCache = new HashMap<>();
    private static long lastCacheCleanup = 0;
    private static final long CACHE_CLEANUP_INTERVAL = 30000; // 30 seconds

    /**
     * Draw a player head at the specified position.
     */
    public static void drawPlayerHead(DrawContext context, int x, int y, int size, UUID uuid) {
        SkinTextures skin = getSkinTextures(uuid);
        PlayerSkinDrawer.draw(context, skin, x, y, size);
    }

    /**
     * Draw a player head rotated by the given angle (degrees).
     */
    public static void drawPlayerHeadRotated(DrawContext context, int x, int y, int size, UUID uuid, float rotationDegrees) {
        SkinTextures skin = getSkinTextures(uuid);

        var matrices = context.getMatrices();
        matrices.pushMatrix();

        // Translate to center of head, rotate around Z axis (in radians), translate back
        matrices.translate(x + size / 2f, y + size / 2f);
        matrices.rotate((float) Math.toRadians(rotationDegrees));
        matrices.translate(-size / 2f, -size / 2f);

        PlayerSkinDrawer.draw(context, skin, 0, 0, size);

        matrices.popMatrix();
    }

    /**
     * Get skin textures for a UUID with caching.
     */
    private static SkinTextures getSkinTextures(UUID uuid) {
        if (uuid == null) {
            return DefaultSkinHelper.getSkinTextures(UUID.randomUUID());
        }

        // Periodically clean old cache entries
        long now = System.currentTimeMillis();
        if (now - lastCacheCleanup > CACHE_CLEANUP_INTERVAL) {
            skinCache.clear();
            lastCacheCleanup = now;
        }

        // Check cache first
        SkinTextures cached = skinCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Look up the skin
        SkinTextures skin = lookupSkin(uuid);
        skinCache.put(uuid, skin);
        return skin;
    }

    /**
     * Look up skin textures for a UUID (no caching).
     */
    private static SkinTextures lookupSkin(UUID uuid) {
        // Try to get from player list (works for local player and others)
        if (CLIENT.getNetworkHandler() != null) {
            PlayerListEntry entry = CLIENT.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                return entry.getSkinTextures();
            }

            // Search player list by UUID
            for (PlayerListEntry e : CLIENT.getNetworkHandler().getPlayerList()) {
                if (e.getProfile() != null && e.getProfile().id().equals(uuid)) {
                    return e.getSkinTextures();
                }
            }
        }

        // Fallback to default skin based on UUID
        return DefaultSkinHelper.getSkinTextures(uuid);
    }

    /**
     * Clear the skin cache (call on disconnect).
     */
    public static void clearCache() {
        skinCache.clear();
    }
}
