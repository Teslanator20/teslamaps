// Ported from ADM's p3sim/clear/state/KeyPlans.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.api.KeyPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KeyPlans {
    private KeyPlans() {}

    public static KeyPlan of(DungeonPlan plan) {
        List<Integer> path = plan.path;
        if (path.size() < 3) return new KeyPlan(Collections.emptyList(), null);
        int bloodRoom = path.get(path.size() - 2);
        RoomType bt = typeOf(plan, bloodRoom);
        if (bt == RoomType.FAIRY || bt == RoomType.ENTRANCE) return new KeyPlan(Collections.emptyList(), null);
        List<Integer> wither = new ArrayList<>();
        for (Integer i : path.subList(1, path.size() - 2)) if (typeOf(plan, i) != RoomType.FAIRY) wither.add(i);
        return new KeyPlan(wither, bloodRoom);
    }

    private static RoomType typeOf(DungeonPlan plan, int i) {
        if (i < 0 || i >= plan.rooms.size()) return null;
        PlacedRoom r = plan.rooms.get(i);
        return r == null ? null : r.type;
    }
}
