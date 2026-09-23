package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TerracottaTimer {

    private static final CopyOnWriteArrayList<TerracottaSpawn> spawns = new CopyOnWriteArrayList<>();
    private static final ServerTickTimer PHASE_END = new ServerTickTimer();

    // respawn as a target server tick, ported from NoammAddons' TerracottaTimer.kt
    private static final class TerracottaSpawn {
        final BlockPos pos;
        final Vec3 center;
        final long end;
        String text = "";

        TerracottaSpawn(BlockPos pos, long end) {
            this.pos = pos;
            this.center = Vec3.atCenterOf(pos);
            this.end = end;
        }
    }

    public static void register() {
        Feature.of("TerracottaTimer").section("Puzzles & Terminals").when(() -> TeslaMapsConfig.get().terracottaTimer)
                .serverChat(TerracottaTimer::onChat);
    }

    private static void onChat(String text) {
        if (spawns.isEmpty() || !text.contains("ENOUGH!")) return;
        if (!"[BOSS] Sadan: ENOUGH!".equals(StringUtil.stripTrim(text))) return;
        PHASE_END.start(10, spawns::clear);
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().terracottaTimer) {
            spawns.clear();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBossArea()) {
            spawns.clear();
            return;
        }

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F6") && !floor.contains("M6"))) {
            spawns.clear();
            return;
        }

        long now = ServerTickTimer.now();
        for (TerracottaSpawn spawn : spawns) {
            long left = spawn.end - now;
            if (left <= 0) {
                spawns.remove(spawn);
                continue;
            }
            float secs = left / 20f;
            spawn.text = String.format("§%s%.1fs", getColorCode(secs), secs);
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().terracottaTimer) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBossArea()) return;

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F6") && !floor.contains("M6"))) return;

        if (newState.getBlock() instanceof FlowerPotBlock) {
            for (TerracottaSpawn spawn : spawns) {
                if (spawn.pos.equals(pos)) return;
            }

            int ticks = floor.contains("M6") ? 240 : 300;
            spawns.add(new TerracottaSpawn(pos.immutable(), ServerTickTimer.now() + ticks));
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().terracottaTimer) return;
        if (spawns.isEmpty()) return;

        for (TerracottaSpawn spawn : spawns) {
            if (spawn.text.isEmpty()) continue;
            ESPRenderer.drawText(matrices, spawn.text, spawn.center, 2f, cameraPos);
        }
    }

    private static String getColorCode(float time) {
        if (time > 5f) return "a"; // Green
        if (time > 2f) return "6"; // Orange
        return "c"; // Red
    }

    public static void reset() {
        spawns.clear();
        PHASE_END.stop();
    }
}
