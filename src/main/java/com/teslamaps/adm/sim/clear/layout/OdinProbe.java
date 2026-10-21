package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/OdinProbe.kt (reads TeslaMaps' own room scanner instead of Odin's)

import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
import com.teslamaps.scanner.RoomScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class OdinProbe {
    private OdinProbe() {}

    private static Collection<DungeonRoom> scanned() {
        ComponentGrid grid = DungeonManager.getGrid();
        if (grid == null) return List.of();
        return new LinkedHashSet<>(grid.getAllRooms());
    }

    /** TeslaMaps' rotation in degrees (clay corner 0 = NW) as Odin's rotation word. */
    private static String rotationWord(DungeonRoom r) {
        if (!r.hasRotation()) return null;
        int q = Math.floorMod(r.getRotation() / 90, 4);
        for (Rot rot : Rot.values()) if (rot.q == q) return rot.name();
        return null;
    }

    public static List<SeenRoom> rooms() {
        try {
            List<SeenRoom> out = new ArrayList<>();
            for (DungeonRoom r : scanned()) {
                Set<Pair<Integer, Integer>> tiles = new LinkedHashSet<>();
                for (int[] c : r.getComponents()) tiles.add(Pair.of(c[0], c[1]));
                out.add(new SeenRoom(r.getName(), rotationWord(r), tiles, r.getType() != null ? r.getType().name() : null, r.getShape()));
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    public static int retryRotations() {
        try {
            int n = 0;
            for (DungeonRoom r : scanned()) {
                if (!"1x1".equals(r.getShape()) || r.hasRotation()) continue;
                RoomScanner.detectAndSetRotation(r);
                n++;
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int rescanUnnamed() {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return 0;
            ComponentGrid grid = DungeonManager.getGrid();
            int n = 0;
            for (int tz = 0; tz < 6; tz++) for (int tx = 0; tx < 6; tx++) {
                DungeonRoom room = grid != null ? grid.getRoom(tx, tz) : null;
                if (room != null && room.getName() != null) continue;
                if (!CoreHasher.isPositionLoaded(level, -185 + 32 * tx, -185 + 32 * tz)) continue;
                n++;
            }
            if (n > 0) RoomScanner.forceScan();
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String currentRoom() {
        try {
            DungeonRoom r = DungeonManager.getCurrentRoom();
            return r != null ? r.getName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean inDungeons() {
        try {
            return DungeonManager.isInDungeon();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean inBoss() {
        try {
            return DungeonManager.isInBossArea();
        } catch (Throwable t) {
            return true;
        }
    }
}
