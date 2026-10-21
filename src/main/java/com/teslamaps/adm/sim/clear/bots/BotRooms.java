// Ported from ADM's p3sim/clear/bots/BotWorldImpl.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import java.util.HashMap;
import java.util.List;

/** The room facts the world needs, from the plan. */
public class BotRooms {
    private final DungeonPlan plan;
    private final HashMap<Integer, List<SecretSpot>> cache = new HashMap<>();

    public BotRooms(DungeonPlan plan) { this.plan = plan; }

    public List<SecretSpot> secretSpots(int room) {
        return cache.computeIfAbsent(room, k -> {
            for (PlacedRoom r : plan.rooms) if (r.index == room) return RoomRoutes.secretSpots(r);
            return List.of();
        });
    }
}
