package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.WitherDragons.Dragon;
import com.teslamaps.player.PlayerTracker;

import java.util.List;

// Ported from NoammAddons' DragonCheck.handleSpawnPacket (M7 update: dragons spawn one after another)
public class DragonPriority {

    public static final String[] SOLO_CLASSES = {"Healer", "Tank"};

    /** spawnOrder = alive/spawning dragons, earliest first. */
    public static Dragon findPriority(List<Dragon> spawnOrder) {
        if (spawnOrder.isEmpty()) return null;
        Dragon best = spawnOrder.get(0);
        Dragon worse = spawnOrder.get(spawnOrder.size() - 1);
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.witherDragonPriority) return best;
        if (c.witherDragonFirstOnly && WitherDragons.aliveCount >= 2) return best;
        String playerClass = PlayerTracker.getLocalClass();
        if (playerClass != null && playerClass.equals(c.witherDragonSoloClass)) return worse;
        return best;
    }
}
