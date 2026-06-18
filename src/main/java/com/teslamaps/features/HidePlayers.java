package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Hides nearby players (ported 1:1 from Odin's Hide Players).
 * Hides real players within a distance (or all), optionally only in dungeons.
 */
public class HidePlayers {

    /** @return true if this entity should NOT be rendered. */
    public static boolean shouldHide(Entity entity) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.hidePlayers) return false;
        if (!(entity instanceof Player)) return false;
        if (entity.getUUID().version() != 4) return false; // skip NPCs / fake players

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == mc.player) return false;
        if (config.hidePlayersOnlyDungeon && !DungeonManager.isInDungeon()) return false;
        if (config.hidePlayersAll) return true;

        double d = config.hidePlayersDistance;
        return entity.distanceToSqr(mc.player) <= d * d; // hide players within range
    }
}
