package com.teslamaps.dungeon.puzzle;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Terracotta Timer - Shows spawn timers for F6/M6 boss terracotta.
 * Detects flower pot placement (marker for terracotta spawn).
 */
public class TerracottaTimer {

    private static final CopyOnWriteArrayList<TerracottaSpawn> spawns = new CopyOnWriteArrayList<>();

    private record TerracottaSpawn(BlockPos pos, float timeRemaining) {}

    public static void tick() {
        if (!TeslaMapsConfig.get().terracottaTimer) {
            spawns.clear();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) {
            spawns.clear();
            return;
        }

        // Only active in F6/M6
        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F6") && !floor.contains("M6"))) {
            spawns.clear();
            return;
        }

        // Tick down timers
        Iterator<TerracottaSpawn> iter = spawns.iterator();
        while (iter.hasNext()) {
            TerracottaSpawn spawn = iter.next();
            float newTime = spawn.timeRemaining - 0.05f;
            if (newTime <= 0) {
                spawns.remove(spawn);
            } else {
                spawns.set(spawns.indexOf(spawn), new TerracottaSpawn(spawn.pos, newTime));
            }
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().terracottaTimer) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) return;

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F6") && !floor.contains("M6"))) return;

        // Flower pot placed = terracotta spawn marker
        if (newState.getBlock() instanceof FlowerPotBlock) {
            // Check if already tracking this position
            for (TerracottaSpawn spawn : spawns) {
                if (spawn.pos.equals(pos)) return;
            }

            // M6 = 12 seconds, F6 = 15 seconds
            float time = floor.contains("M6") ? 12f : 15f;
            spawns.add(new TerracottaSpawn(pos.toImmutable(), time));
        }
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().terracottaTimer) return;
        if (spawns.isEmpty()) return;

        for (TerracottaSpawn spawn : spawns) {
            String colorCode = getColorCode(spawn.timeRemaining);
            String text = String.format("§%s%.1fs", colorCode, spawn.timeRemaining);
            Vec3d pos = Vec3d.ofCenter(spawn.pos);
            ESPRenderer.drawText(matrices, text, pos, 2f, cameraPos);
        }
    }

    private static String getColorCode(float time) {
        if (time > 5f) return "a"; // Green
        if (time > 2f) return "6"; // Orange
        return "c"; // Red
    }

    public static void reset() {
        spawns.clear();
    }
}
