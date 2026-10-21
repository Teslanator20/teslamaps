// Ported from ADM's p3sim/clear/secrets/Levers.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomLever;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Levers {
    private Levers() {}

    public static Pair<Map<Long, LeverInfo>, Set<String>> build(DungeonPlan plan, SecretPlan secrets) {
        HashMap<Long, LeverInfo> levers = new HashMap<>();
        LinkedHashSet<String> unlocked = new LinkedHashSet<>();
        for (PlacedRoom r : plan.rooms) {
            var design = r.design;
            if (design == null) continue;
            Placement placement = r.placement;
            if (placement == null) continue;
            if (r.type != RoomType.NORMAL && r.type != RoomType.RARE && r.type != RoomType.YELLOW) continue;
            // A key room has no lever: the redstone block the pack lists as one is the key's socket.
            if (RedstoneKeys.forDesign(design.id) != null) continue;
            HashMap<Long, List<String>> chestsByLever = new HashMap<>();
            RoomSecrets rs = secrets.room(r.index);
            if (rs != null) for (SecretSite s : rs.sites) {
                if (s.kind != SecretKind.LOCKED_CHEST) continue;
                Pos l = s.lever;
                if (l == null) { unlocked.add(s.key()); continue; }
                chestsByLever.computeIfAbsent(CellGroups.key(l.x, l.y, l.z), k -> new ArrayList<>()).add(s.key());
            }
            for (RoomLever lv : design.levers) {
                var w = placement.toWorld(lv.pos.x, lv.pos.y, lv.pos.z);
                long k = CellGroups.key(w.x, w.y, w.z);
                List<Pos> opens = new ArrayList<>();
                for (Pos c : lv.opens) {
                    var cw = placement.toWorld(c.x, c.y, c.z);
                    opens.add(new Pos(cw.x, cw.y, cw.z));
                }
                List<String> ch = chestsByLever.get(k);
                levers.put(k, new LeverInfo(r.index, new Pos(w.x, w.y, w.z), opens, ch != null ? ch : List.of()));
            }
            for (Map.Entry<Long, List<String>> e : chestsByLever.entrySet()) {
                if (!levers.containsKey(e.getKey())) levers.put(e.getKey(), new LeverInfo(r.index, secrets.site(e.getValue().get(0)).lever, List.of(), e.getValue()));
            }
        }
        return Pair.of(levers, unlocked);
    }
}
