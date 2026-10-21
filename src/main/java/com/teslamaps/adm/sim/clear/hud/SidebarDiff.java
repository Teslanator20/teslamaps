// Ported from ADM's p3sim/clear/hud/HudModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SidebarDiff {
    private SidebarDiff() {}

    /** The map the sidebar currently shows for lines (top to bottom): the top line has the highest score. */
    public static Map<Integer, String> scored(List<String> lines) {
        LinkedHashMap<Integer, String> m = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) m.put(lines.size() - i, lines.get(i));
        return m;
    }

    public static SidebarOps diff(Map<Integer, String> previous, Map<Integer, String> next) {
        List<Integer> remove = new ArrayList<>();
        for (Integer k : previous.keySet()) if (!next.containsKey(k)) remove.add(k);
        remove.sort(Comparator.reverseOrder());
        List<Pair<Integer, String>> add = new ArrayList<>();
        for (Map.Entry<Integer, String> e : next.entrySet()) if (!previous.containsKey(e.getKey())) add.add(Pair.of(e.getKey(), e.getValue()));
        add.sort(Comparator.<Pair<Integer, String>>comparingInt(p -> p.first).reversed());
        List<Pair<Integer, String>> change = new ArrayList<>();
        for (Map.Entry<Integer, String> e : next.entrySet()) {
            String prev = previous.get(e.getKey());
            if (prev != null && !prev.equals(e.getValue())) change.add(Pair.of(e.getKey(), e.getValue()));
        }
        change.sort(Comparator.<Pair<Integer, String>>comparingInt(p -> p.first).reversed());
        return new SidebarOps(remove, add, change);
    }
}
