/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
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

public class PlayerHeadRenderer {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    private static final Map<UUID, PlayerSkin> skinCache = new HashMap<>();
    private static long lastCacheCleanup = 0;
    private static final long CACHE_CLEANUP_INTERVAL = 30000; // 30 seconds

    public static void drawPlayerHead(GuiGraphicsExtractor context, int x, int y, int size, UUID uuid) {
        PlayerSkin skin = getSkinTextures(uuid);
        PlayerFaceExtractor.extractRenderState(context, skin, x, y, size);
    }

    public static void drawPlayerHeadRotated(GuiGraphicsExtractor context, int x, int y, int size, UUID uuid, float rotationDegrees) {
        PlayerSkin skin = getSkinTextures(uuid);

        var matrices = context.pose();
        matrices.pushMatrix();

        matrices.translate(x + size / 2f, y + size / 2f);
        matrices.rotate((float) Math.toRadians(rotationDegrees));
        matrices.translate(-size / 2f, -size / 2f);

        PlayerFaceExtractor.extractRenderState(context, skin, 0, 0, size);

        matrices.popMatrix();
    }

    private static PlayerSkin getSkinTextures(UUID uuid) {
        if (uuid == null) {
            return DefaultPlayerSkin.get(UUID.randomUUID());
        }

        long now = System.currentTimeMillis();
        if (now - lastCacheCleanup > CACHE_CLEANUP_INTERVAL) {
            skinCache.clear();
            lastCacheCleanup = now;
        }

        PlayerSkin cached = skinCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        PlayerSkin skin = lookupSkin(uuid);
        skinCache.put(uuid, skin);
        return skin;
    }

    private static PlayerSkin lookupSkin(UUID uuid) {
        if (CLIENT.getConnection() != null) {
            PlayerInfo entry = CLIENT.getConnection().getPlayerInfo(uuid);
            if (entry != null) {
                return entry.getSkin();
            }

            for (PlayerInfo e : CLIENT.getConnection().getOnlinePlayers()) {
                if (e.getProfile() != null && e.getProfile().id().equals(uuid)) {
                    return e.getSkin();
                }
            }
        }

        return DefaultPlayerSkin.get(uuid);
    }

    public static void clearCache() {
        skinCache.clear();
    }
}
