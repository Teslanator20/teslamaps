package com.teslamaps.dungeon.puzzle;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PuzzleTimers {

    private static String current = null;
    private static long startMs = 0L;
    private static boolean reported = false;

    public static void tick() {
        if (!TeslaMapsConfig.get().puzzleTimers || !DungeonManager.isInDungeon()) { current = null; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getType() != RoomType.PUZZLE || room.getName() == null) { current = null; return; }

        String name = room.getName();
        if (!name.equals(current)) {
            current = name;
            startMs = System.currentTimeMillis();
            // already solved/failed when we walk in (teammate did it, or re-entering) -> nothing to time
            CheckmarkState entry = room.getCheckmarkState();
            reported = entry == CheckmarkState.WHITE || entry == CheckmarkState.GREEN || entry == CheckmarkState.FAILED;
        }
        if (reported) return;

        CheckmarkState st = room.getCheckmarkState();
        if (st == CheckmarkState.WHITE || st == CheckmarkState.GREEN) {
            reported = true;
            double secs = (System.currentTimeMillis() - startMs) / 1000.0;
            if (secs < 0.5) return; // checkmark state raced in right after entering - not our solve
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
        } else if (st == CheckmarkState.FAILED) {
            reported = true;
        }
    }
}
