package com.teslamaps.adm.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Ported from ADM's misc/Witherborn.kt: a wither first seen within 4.5 blocks of a player is a Storm-armor minion
public final class Witherborn {
    private static final double NEAR = 4.5;
    private static final Set<Integer> minions = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> seen = ConcurrentHashMap.newKeySet();

    private Witherborn() {}

    static void register() {
        AdmCore.onWorldLoad(() -> { minions.clear(); seen.clear(); });
    }

    /** A wither spawning, main thread. */
    public static void onSpawn(int id, double x, double y, double z) {
        if (!seen.add(id)) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Player p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= NEAR * NEAR) {
                minions.add(id);
                return;
            }
        }
    }

    public static boolean isMinion(Entity e) {
        return minions.contains(e.getId()) || (e instanceof WitherBoss w && w.getScale() < 0.9f);
    }

    public static boolean isBoss(Entity e) {
        return e instanceof WitherBoss && !isMinion(e);
    }
}
