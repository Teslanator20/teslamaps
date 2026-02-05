package com.teslamaps.dungeon.puzzle;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * Spirit Bear Timer - Tracks F4/M4 boss spirit bear spawn progress.
 * Monitors sea lantern/coal block changes to track kills and spawn timer.
 */
public class SpiritBearTimer {

    // F4 lantern positions (25 positions)
    private static final Set<BlockPos> F4_POSITIONS = Set.of(
        new BlockPos(-3, 77, 33), new BlockPos(-9, 77, 31), new BlockPos(-16, 77, 26),
        new BlockPos(-20, 77, 20), new BlockPos(-23, 77, 13), new BlockPos(-24, 77, 6),
        new BlockPos(-24, 77, 0), new BlockPos(-22, 77, -7), new BlockPos(-18, 77, -13),
        new BlockPos(-12, 77, -19), new BlockPos(-5, 77, -22), new BlockPos(1, 77, -24),
        new BlockPos(8, 77, -24), new BlockPos(14, 77, -23), new BlockPos(21, 77, -19),
        new BlockPos(27, 77, -14), new BlockPos(31, 77, -8), new BlockPos(33, 77, -1),
        new BlockPos(34, 77, 5), new BlockPos(33, 77, 12), new BlockPos(31, 77, 19),
        new BlockPos(27, 77, 25), new BlockPos(20, 77, 30), new BlockPos(14, 77, 33),
        new BlockPos(7, 77, 34)
    );

    // M4 lantern positions (30 positions)
    private static final Set<BlockPos> M4_POSITIONS = Set.of(
        new BlockPos(-2, 77, 33), new BlockPos(-7, 77, 32), new BlockPos(-13, 77, 28),
        new BlockPos(-17, 77, 24), new BlockPos(-21, 77, 18), new BlockPos(-23, 77, 13),
        new BlockPos(-24, 77, 7), new BlockPos(-24, 77, 2), new BlockPos(-23, 77, -4),
        new BlockPos(-21, 77, -9), new BlockPos(-17, 77, -14), new BlockPos(-12, 77, -19),
        new BlockPos(-6, 77, -22), new BlockPos(-1, 77, -23), new BlockPos(5, 77, -24),
        new BlockPos(10, 77, -24), new BlockPos(16, 77, -22), new BlockPos(21, 77, -19),
        new BlockPos(27, 77, -15), new BlockPos(30, 77, -10), new BlockPos(32, 77, -5),
        new BlockPos(34, 77, 1), new BlockPos(34, 77, 7), new BlockPos(33, 77, 12),
        new BlockPos(31, 77, 18), new BlockPos(28, 77, 23), new BlockPos(23, 77, 28),
        new BlockPos(18, 77, 31), new BlockPos(12, 77, 33), new BlockPos(7, 77, 34)
    );

    private static final BlockPos LAST_POSITION = new BlockPos(7, 77, 34);

    private static int kills = 0;
    private static int timer = -1; // -1 = not spawning, 0 = alive, >0 = ticks until spawn
    private static String lastFloor = "";

    public static void tick() {
        if (!TeslaMapsConfig.get().spiritBearTimer) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) {
            reset();
            return;
        }

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F4") && !floor.contains("M4"))) {
            reset();
            return;
        }

        lastFloor = floor;

        // Tick down timer
        if (timer > 0) {
            timer--;
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().spiritBearTimer) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) return;

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F4") && !floor.contains("M4"))) return;

        Set<BlockPos> positions = floor.contains("M4") ? M4_POSITIONS : F4_POSITIONS;
        if (!positions.contains(pos)) return;

        // Coal -> Sea Lantern = kill
        if (oldState.getBlock() == Blocks.COAL_BLOCK && newState.getBlock() == Blocks.SEA_LANTERN) {
            int maxKills = floor.contains("M4") ? 30 : 25;
            if (kills < maxKills) {
                kills++;
            }
            // Last position triggers spawn timer
            if (pos.equals(LAST_POSITION)) {
                timer = 68; // 3.4 seconds
            }
        }
        // Sea Lantern -> Coal = unkill (reset)
        else if (oldState.getBlock() == Blocks.SEA_LANTERN && newState.getBlock() == Blocks.COAL_BLOCK) {
            if (kills > 0) {
                kills--;
            }
            if (pos.equals(LAST_POSITION)) {
                timer = -1;
            }
        }
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!TeslaMapsConfig.get().spiritBearTimer) return;
        if (lastFloor.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int maxKills = lastFloor.contains("M4") ? 30 : 25;

        String text;
        if (timer < 0) {
            // Not spawning - show kill count
            text = String.format("§6Spirit Bear: §d%d/%d", kills, maxKills);
        } else if (timer > 0) {
            // Spawning countdown
            text = String.format("§6Spirit Bear: §e%.2fs", timer / 20f);
        } else {
            // Alive
            text = "§6Spirit Bear: §aAlive!";
        }

        // Render at bottom center of screen
        int screenWidth = mc.getWindow().getScaledWidth();
        int x = (screenWidth - mc.textRenderer.getWidth(text)) / 2;
        int y = mc.getWindow().getScaledHeight() - 50;

        context.drawTextWithShadow(mc.textRenderer, text, x, y, 0xFFFFFFFF);
    }

    public static void reset() {
        kills = 0;
        timer = -1;
        lastFloor = "";
    }

    public static boolean isActive() {
        return !lastFloor.isEmpty() && TeslaMapsConfig.get().spiritBearTimer;
    }
}
