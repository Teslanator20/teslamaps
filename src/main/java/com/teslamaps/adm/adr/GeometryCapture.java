package com.teslamaps.adm.adr;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

// Ported from ADM's GeometryCapture.kt
/** Two blocks per door spot ("dslots"); the rooms themselves come from the room library. */
public class GeometryCapture {
    public static final int GRID_ORIGIN = -200;
    public static final int DOOR_ALONG = 3;
    public static final int DOOR_ACROSS = 2;

    private final Consumer<String> emit;
    private final List<int[]> doorSpots = new ArrayList<>();
    private final HashSet<Integer> doorsDone = new HashSet<>();

    public GeometryCapture(Consumer<String> emit) {
        this.emit = emit;
        int across = 2 * DOOR_ACROSS + 1, along = 2 * DOOR_ALONG + 1;
        for (int i = 1; i <= 5; i++) for (int j = 0; j <= 5; j++) {
            int gap = GRID_ORIGIN - 1 + 32 * i, mid = GRID_ORIGIN + 32 * j + 15;
            doorSpots.add(new int[]{gap - DOOR_ACROSS, mid - DOOR_ALONG, across, along});
            doorSpots.add(new int[]{mid - DOOR_ALONG, gap - DOOR_ACROSS, along, across});
        }
    }

    /** {"k":"dslots","t":t,"d":[[x, z, what, top], ...]}: what "-" none, "n" open, "w" wither, "b" blood, "e" entrance, "f" falling. */
    private void readDoors(ClientLevel level, int t) {
        List<String> read = new ArrayList<>();
        for (int i = 0; i < doorSpots.size(); i++) {
            if (doorsDone.contains(i)) continue;
            int[] spot = doorSpots.get(i);
            int x = spot[0] + spot[2] / 2, z = spot[1] + spot[3] / 2;
            if (!loaded(level, x, z, 1, 1)) continue;
            doorsDone.add(i);
            BlockState top = level.getBlockState(new BlockPos(x, 73, z));
            Block door = level.getBlockState(new BlockPos(x, 69, z)).getBlock();
            String what;
            if (top.isAir()) what = "-";
            else if (door == Blocks.COAL_BLOCK) what = "w";
            else if (door == Blocks.RED_TERRACOTTA) what = "b";
            else if (door instanceof InfestedBlock) what = "e";
            else if (door == Blocks.BARRIER) what = "f";
            else what = "n";
            String topName = top.isAir() ? "" : BuiltInRegistries.BLOCK.getKey(top.getBlock()).toString();
            read.add("[" + x + "," + z + ",\"" + what + "\"," + jsonString(topName) + "]");
        }
        if (!read.isEmpty()) emit.accept("{\"k\":\"dslots\",\"t\":" + t + ",\"d\":[" + String.join(",", read) + "]}");
    }

    public void tick(ClientLevel level, int t) {
        if (t % 10 == 0) readDoors(level, t);
    }

    private static boolean loaded(ClientLevel level, int x0, int z0, int w, int d) {
        for (int cx = x0 >> 4; cx <= (x0 + w - 1) >> 4; cx++)
            for (int cz = z0 >> 4; cz <= (z0 + d - 1) >> 4; cz++)
                if (level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) == null) return false;
        return true;
    }

    public static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') sb.append("\\\"");
            else if (ch == '\\') sb.append("\\\\");
            else if (ch < ' ') sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.append('"').toString();
    }
}
