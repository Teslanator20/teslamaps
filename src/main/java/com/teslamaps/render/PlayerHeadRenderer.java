package com.teslamaps.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Utility for rendering player heads on the dungeon map.
 * Uses caching for smooth, lag-free rendering.
 */
public class PlayerHeadRenderer {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    // Cache skins by UUID to avoid repeated lookups
    private static final Map<UUID, PlayerSkin> skinCache = new HashMap<>();
    private static long lastCacheCleanup = 0;
    private static final long CACHE_CLEANUP_INTERVAL = 30000; // 30 seconds

    /**
     * Draw a player head at the specified position.
     */
    public static void drawPlayerHead(GuiGraphicsExtractor context, int x, int y, int size, UUID uuid) {
        PlayerSkin skin = getSkinTextures(uuid);
        PlayerFaceExtractor.extractRenderState(context, skin, x, y, size);
    }

    /**
     * Draw a player head rotated by the given angle (degrees).
     */
    public static void drawPlayerHeadRotated(GuiGraphicsExtractor context, int x, int y, int size, UUID uuid, float rotationDegrees) {
        PlayerSkin skin = getSkinTextures(uuid);

        var matrices = context.pose();
        matrices.pushMatrix();

        // Translate to center of head, rotate around Z axis (in radians), translate back
        matrices.translate(x + size / 2f, y + size / 2f);
        matrices.rotate((float) Math.toRadians(rotationDegrees));
        matrices.translate(-size / 2f, -size / 2f);

        PlayerFaceExtractor.extractRenderState(context, skin, 0, 0, size);

        matrices.popMatrix();
    }

    /**
     * Get skin textures for a UUID with caching.
     */
    private static PlayerSkin getSkinTextures(UUID uuid) {
        if (uuid == null) {
            return DefaultPlayerSkin.get(UUID.randomUUID());
        }

        // Periodically clean old cache entries
        long now = System.currentTimeMillis();
        if (now - lastCacheCleanup > CACHE_CLEANUP_INTERVAL) {
            skinCache.clear();
            lastCacheCleanup = now;
        }

        // Check cache first
        PlayerSkin cached = skinCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Look up the skin
        PlayerSkin skin = lookupSkin(uuid);
        skinCache.put(uuid, skin);
        return skin;
    }

    /**
     * Look up skin textures for a UUID (no caching).
     */
    private static PlayerSkin lookupSkin(UUID uuid) {
        // Try to get from player list (works for local player and others)
        if (CLIENT.getConnection() != null) {
            PlayerInfo entry = CLIENT.getConnection().getPlayerInfo(uuid);
            if (entry != null) {
                return entry.getSkin();
            }

            // Search player list by UUID
            for (PlayerInfo e : CLIENT.getConnection().getOnlinePlayers()) {
                if (e.getProfile() != null && e.getProfile().id().equals(uuid)) {
                    return e.getSkin();
                }
            }
        }

        // Fallback to default skin based on UUID
        return DefaultPlayerSkin.get(uuid);
    }

    /**
     * Clear the skin cache (call on disconnect).
     */
    public static void clearCache() {
        skinCache.clear();
    }
}
