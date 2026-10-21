// Ported from ADM's p3sim/clear/secrets/CellGroups.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellGroups {
    public final List<CellGroup> groups;
    private final Map<Long, CellGroup> byCell = new HashMap<>();
    private final Map<String, CellGroup> byId = new LinkedHashMap<>();
    private final LinkedHashSet<String> gone = new LinkedHashSet<>();

    public CellGroups(List<CellGroup> groups) {
        this.groups = groups;
        for (CellGroup g : groups) for (Pos c : g.cells) byCell.put(key(c.x, c.y, c.z), g);
        for (CellGroup g : groups) byId.put(g.id, g);
    }

    public CellGroup standingAt(int x, int y, int z) {
        CellGroup g = byCell.get(key(x, y, z));
        return g != null && !gone.contains(g.id) ? g : null;
    }

    public List<CellGroup> hitBy(double cx, double cy, double cz, double r) {
        List<CellGroup> out = new ArrayList<>();
        for (CellGroup g : groups) if (!gone.contains(g.id) && g.within(cx, cy, cz, r)) out.add(g);
        return out;
    }

    public CellGroup group(String id) { return byId.get(id); }

    public boolean isDestroyed(CellGroup g) { return gone.contains(g.id); }

    public Set<String> destroyedIds() { return gone; }

    public boolean destroy(CellGroup g) { return gone.add(g.id); }

    public void setDestroyed(Collection<String> ids) {
        gone.clear();
        for (String id : ids) if (byId.containsKey(id)) gone.add(id);
    }

    public List<CellGroup> ofRoom(int room) { return ofRoom(room, null); }

    public List<CellGroup> ofRoom(int room, GroupKind kind) {
        List<CellGroup> out = new ArrayList<>();
        for (CellGroup g : groups) if (g.room == room && (kind == null || g.kind == kind)) out.add(g);
        return out;
    }

    public static long key(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    public static CellGroups build(DungeonPlan plan) {
        List<CellGroup> out = new ArrayList<>();
        for (PlacedRoom r : plan.rooms) {
            var design = r.design;
            if (design == null) continue;
            Placement placement = r.placement;
            if (placement == null) continue;
            for (int i = 0; i < design.crypts.size(); i++) {
                List<Pos> w = world(placement, design.crypts.get(i));
                if (!w.isEmpty()) out.add(new CellGroup(r.index + ":c" + i, r.index, GroupKind.CRYPT, w));
            }
            for (int i = 0; i < design.superboomWalls.size(); i++) {
                List<Pos> w = world(placement, design.superboomWalls.get(i));
                if (!w.isEmpty()) out.add(new CellGroup(r.index + ":w" + i, r.index, GroupKind.WALL, w));
            }
        }
        return new CellGroups(out);
    }

    private static List<Pos> world(Placement p, List<Pos> cells) {
        List<Pos> out = new ArrayList<>(cells.size());
        for (Pos c : cells) {
            var w = p.toWorld(c.x, c.y, c.z);
            out.add(new Pos(w.x, w.y, w.z));
        }
        return out;
    }
}
