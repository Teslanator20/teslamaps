// Ported from ADM's p3sim/clear/hud/ActionBarModel.kt
package com.teslamaps.adm.sim.clear.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The live extra segments of the action bar. Server thread only. */
public class BarSegments {
    private final LinkedHashMap<String, BarSegment> map = new LinkedHashMap<>();

    /** to null: every viewer sees it. */
    public void set(String key, String text, int priority, int tick, int ttlTicks, UUID to) {
        map.put(key + "\u0000" + (to != null ? to.toString() : ""), new BarSegment(key, text, priority, tick + Math.max(1, ttlTicks), to, tick));
    }

    public void clear(String key) {
        map.keySet().removeIf(it -> {
            int i = it.indexOf('\u0000');
            return (i < 0 ? it : it.substring(0, i)).equals(key);
        });
    }

    public void clearAll() { map.clear(); }

    /** Drops what has expired and returns what viewer sees, strongest first (priority, then the newest). */
    public List<BarSegment> live(int tick, UUID viewer) {
        map.values().removeIf(it -> it.expiresTick <= tick);
        List<BarSegment> out = new ArrayList<>();
        for (BarSegment s : map.values()) if (s.audience == null || Objects.equals(s.audience, viewer)) out.add(s);
        out.sort(Comparator.<BarSegment>comparingInt(s -> s.priority).reversed().thenComparing(Comparator.<BarSegment>comparingInt(s -> s.setTick).reversed()));
        return out;
    }
}
