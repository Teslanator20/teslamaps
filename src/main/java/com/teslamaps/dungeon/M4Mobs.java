package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

// Tracks the M4 (Thorn) boss-arena entities each tick for the highlight/trajectory features.
public class M4Mobs {
    private static final AABB SEARCH = new AABB(-36.0, 68.0, -36.0, 47.0, 110.0, 47.0); // m4 arena

    public static final Set<Entity> cows = new HashSet<>();
    public static final Set<Entity> sheep = new HashSet<>();
    public static final Set<Entity> chickens = new HashSet<>();
    public static final Set<Entity> rabbits = new HashSet<>();
    public static final Set<Entity> wolves = new HashSet<>();
    public static final Set<Entity> bats = new HashSet<>();
    public static Entity thorn = null;
    public static Entity bear = null;

    public static boolean inM4Boss() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f == null || f.getLevel() != 4 || !DungeonManager.isInDungeon()) return false;
        if (DungeonManager.isInBoss()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null; // boss arena
    }

    private static long lastDbg = 0;

    public static void tick() {
        cows.clear(); sheep.clear(); chickens.clear(); rabbits.clear(); wolves.clear(); bats.clear();
        thorn = null; bear = null;

        if (!inM4Boss()) { dbg(false, 0); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (Entity e : mc.level.getEntities((Entity) null, SEARCH)) {
            if (!e.isAlive()) continue;
            if (e instanceof Ghast) thorn = e;
            else if (e instanceof Cow) cows.add(e);
            else if (e instanceof Sheep) sheep.add(e);
            else if (e instanceof Chicken) chickens.add(e);
            else if (e instanceof Rabbit) rabbits.add(e);
            else if (e instanceof Wolf) wolves.add(e);
            else if (e instanceof Bat b) { if (!b.isInvisible()) bats.add(e); }
            else if (e instanceof Player p && "Spirit Bear".equals(p.getGameProfile().name())) bear = e;
        }
        dbg(true, mc.level.getEntities((Entity) null, SEARCH).size());
    }

    /** /tmap debug: says whether the arena gate opened and what was found, once a second. */
    private static void dbg(boolean inArena, int scanned) {
        if (!TeslaMapsConfig.get().debugMode) return;
        long now = System.currentTimeMillis();
        if (now - lastDbg < 1000) return;
        lastDbg = now;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        com.teslamaps.TeslaMaps.LOGGER.info(
                "[M4Dbg] floor={} inDungeon={} isInBoss={} inM4Boss={} scanned={} thorn={} bear={} cows={} sheep={} chickens={}",
                f, DungeonManager.isInDungeon(), DungeonManager.isInBoss(), inArena,
                scanned, thorn != null, bear != null, cows.size(), sheep.size(), chickens.size());
    }

    public static void reset() {
        cows.clear(); sheep.clear(); chickens.clear(); rabbits.clear(); wolves.clear(); bats.clear();
        thorn = null; bear = null;
    }
}
