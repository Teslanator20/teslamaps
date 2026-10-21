package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorFrames.kt

import com.teslamaps.adm.sim.pack.DoorModel;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class DoorFrames {
    private DoorFrames() {}

    public static final int CORE_PLANES = 1;
    public static final int FLOOR_PLANES = 3;
    public static final int BEDROCK_Y_MIN = 66;
    public static final int BEDROCK_Y_MAX = 67;
    private static final String BEDROCK = "minecraft:bedrock";
    private static final String FLOOR_FALLBACK = "minecraft:stone";

    public static DoorStyle styleFor(DoorKind kind, Slot slot, long seed) {
        if (kind == DoorKind.ENTRANCE) return DoorStyle.COBBLESTONE_ENTRANCE;
        if (kind == DoorKind.BLOOD) return DoorStyle.BLOOD;
        List<DoorStyle> pool = new ArrayList<>();
        for (DoorStyle s : DoorStyle.values()) if (s.weight > 0) pool.add(s);
        long key = (long) ((int) slot.axis * 36 + slot.tx * 6 + slot.tz);
        int sum = 0;
        for (DoorStyle s : pool) sum += s.weight;
        int pick = new KRandom(seed ^ (key * 0x9E3779B97F4A7C15L)).nextInt(sum);
        for (DoorStyle s : pool) {
            pick -= s.weight;
            if (pick < 0) return s;
        }
        return pool.get(pool.size() - 1);
    }

    public static Pair<Integer, Integer> planeCell(Slot slot, int a, int dd) {
        Pair<Integer, Integer> c = DoorGeometry.gapCell(slot, a);
        return slot.axis == 'E' ? Pair.of(c.first + dd, c.second) : Pair.of(c.first, c.second + dd);
    }

    public static List<Edit> frame(Slot slot, DoorStyle style, BiFunction<Integer, Integer, String> floorAt) {
        List<Edit> out = new ArrayList<>();
        for (int a = DoorGeometry.FRAME_MIN; a <= DoorGeometry.FRAME_MAX; a++) {
            for (int dd = -CORE_PLANES; dd <= CORE_PLANES; dd++) {
                Pair<Integer, Integer> c = planeCell(slot, a, dd);
                out.add(new Edit(c.first, DoorModel.LINTEL_Y, c.second, style.lintel));
                if (a == DoorGeometry.FRAME_MIN || a == DoorGeometry.FRAME_MAX) {
                    int i = 0;
                    for (int y = DoorModel.BODY_Y_MIN; y <= DoorModel.BODY_Y_MAX; y++, i++) out.add(new Edit(c.first, y, c.second, style.pillars.get(i)));
                }
            }
            Pair<Integer, Integer> g = planeCell(slot, a, 0);
            Pair<Integer, Integer> n = planeCell(slot, a, -1);
            String floor = floorAt.apply(n.first, n.second);
            out.add(new Edit(g.first, DoorModel.THRESHOLD_Y, g.second, floor != null ? floor : FLOOR_FALLBACK));
            for (int dd = -FLOOR_PLANES; dd <= FLOOR_PLANES; dd++) {
                Pair<Integer, Integer> c = planeCell(slot, a, dd);
                for (int y = BEDROCK_Y_MIN; y <= BEDROCK_Y_MAX; y++) out.add(new Edit(c.first, y, c.second, BEDROCK));
            }
        }
        return out;
    }
}
