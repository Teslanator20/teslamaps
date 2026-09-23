package com.teslamaps.dungeon.puzzle;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonWaypoints;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class PuzzleTimers {

    // Odin's timeToBeat: anything faster was started before we walked in
    private static final Map<String, Double> TIME_TO_BEAT = Map.of(
            "Blaze", 6.0, "Higher Blaze", 6.0, "Lower Blaze", 6.0, "Creeper Beams", 6.0,
            "Three Weirdos", 2.0, "Water Board", 10.0, "Teleport Maze", 6.0, "Boulder", 6.0,
            "Ice Fill", 10.0, "Quiz", 10.0);

    // reward chest block event positions, clay-relative (Odin PuzzleSolvers)
    private static final Map<String, int[][]> CHEST_POS = Map.of(
            "Three Weirdos", new int[][]{{18, 69, 24}, {16, 69, 25}, {14, 69, 24}},
            "Ice Fill", new int[][]{{14, 75, 29}, {16, 75, 29}},
            "Teleport Maze", new int[][]{{15, 70, 20}},
            "Water Board", new int[][]{{15, 56, 22}},
            "Boulder", new int[][]{{15, 66, 29}});

    private static String current = null;
    private static DungeonRoom currentRoom = null;
    private static int[] clay = null;
    private static long startMs = 0L;
    private static boolean reported = false;

    public static void register() {
        Feature.of("PuzzleTimers.chat").section("Puzzles & Terminals")
                .when(() -> TeslaMapsConfig.get().puzzleTimers)
                .serverChat(PuzzleTimers::onChat);
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().puzzleTimers || !DungeonManager.isInDungeon()) { current = null; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getType() != RoomType.PUZZLE || room.getName() == null) { current = null; return; }

        String name = room.getName();
        if (!name.equals(current)) {
            current = name;
            currentRoom = room;
            clay = null;
            startMs = System.currentTimeMillis();
            // already solved/failed when we walk in (teammate did it, or re-entering) -> nothing to time
            CheckmarkState entry = room.getCheckmarkState();
            reported = entry == CheckmarkState.WHITE || entry == CheckmarkState.GREEN || entry == CheckmarkState.FAILED;
        }
        if (reported) return;

        CheckmarkState st = room.getCheckmarkState();
        if (st == CheckmarkState.WHITE || st == CheckmarkState.GREEN) {
            complete(name);
        } else if (st == CheckmarkState.FAILED) {
            reported = true;
        }
    }

    /** Main thread. Reward chest opening, tick-exact unlike the map checkmark. */
    public static void onBlockEvent(BlockPos pos, Block block) {
        if (block != Blocks.CHERRY_LOG || current == null || reported || currentRoom == null) return;
        if (!TeslaMapsConfig.get().puzzleTimers) return;
        int[][] targets = CHEST_POS.get(current);
        if (targets == null) return;
        if (clay == null) clay = DungeonWaypoints.scanClayPos(currentRoom);
        if (clay == null) return;
        for (int[] t : targets) {
            int[] xz = DungeonWaypoints.relativeToWorld(clay, t[0], t[2]);
            if (pos.getX() == xz[0] && pos.getY() == t[1] && pos.getZ() == xz[1]) {
                complete(current);
                return;
            }
        }
    }

    private static void onChat(String raw) {
        if (current == null || !current.equals("Quiz")) return;
        String msg = StringUtil.strip(raw);
        if (msg.startsWith("[STATUE] Oruo the Omniscient:") && msg.contains("answered the final question")) complete("Quiz");
    }

    /** Called by solvers that know the exact solve moment (last blaze dead, …). */
    public static void onPuzzleComplete(String name) {
        if (current != null && current.equals(name)) complete(name);
    }

    private static void complete(String name) {
        if (reported) return;
        reported = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double secs = (System.currentTimeMillis() - startMs) / 1000.0;
        if (secs < Math.max(0.5, TIME_TO_BEAT.getOrDefault(name, 0.0))) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Double pb = c.puzzlePbs.get(name);
        if (pb != null && pb < 0.5) { c.puzzlePbs.remove(name); pb = null; } // heal broken 0.00s PBs
        boolean newPb = pb == null || secs < pb;
        String suffix;
        if (newPb) {
            suffix = pb == null ? " §6§l(NEW PB!)" : String.format(" §6§l(NEW PB!§r §7old: %.2fs§6§l)", pb);
            c.puzzlePbs.put(name, secs);
            TeslaMapsConfig.save();
        } else {
            suffix = String.format(" §7(PB: %.2fs)", pb);
        }
        mc.player.sendSystemMessage(Component.literal(String.format("§b[TeslaMaps] §f%s solved in §a%.2fs%s", name, secs, suffix)));
    }
}
