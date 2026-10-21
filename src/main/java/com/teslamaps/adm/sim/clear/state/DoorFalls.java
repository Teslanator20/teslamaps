// Ported from ADM's p3sim/clear/state/DoorFalls.kt
package com.teslamaps.adm.sim.clear.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class DoorFalls {
    private static final class Fall {
        final int door;
        final int startT;
        final int restT;
        final int airT;

        Fall(int door, int startT, int restT, int airT) { this.door = door; this.startT = startT; this.restT = restT; this.airT = airT; }
    }

    private final LinkedHashMap<Integer, Fall> falling = new LinkedHashMap<>();

    public List<FallStep> begin(int door, int t, int barrierTicks, int firstBlockToRestTicks, int barrierDelay) {
        if (falling.containsKey(door)) return Collections.emptyList();
        int rest = t + Math.max(Math.max(firstBlockToRestTicks, barrierDelay), 0);
        falling.put(door, new Fall(door, t, rest, rest + barrierTicks));
        if (barrierDelay > 0) return Collections.emptyList();
        return firstBlockToRestTicks > 0 ? List.of(new FallStep(door, BodyBlocks.BARRIER, true)) : List.of(new FallStep(door, BodyBlocks.BARRIER));
    }

    public List<FallStep> begin(int door, int t, int barrierTicks, int firstBlockToRestTicks) { return begin(door, t, barrierTicks, firstBlockToRestTicks, 0); }

    public List<FallStep> begin(int door, int t, int barrierTicks) { return begin(door, t, barrierTicks, 0, 0); }

    public void resume(int door, int openedT, int barrierTicks) { falling.put(door, new Fall(door, openedT, openedT, openedT + barrierTicks)); }

    public List<FallStep> due(int t) {
        ArrayList<FallStep> out = new ArrayList<>();
        ArrayList<Integer> done = new ArrayList<>();
        for (Fall f : falling.values()) {
            if (t >= f.airT) { out.add(new FallStep(f.door, BodyBlocks.AIR)); done.add(f.door); }
        }
        for (Integer d : done) falling.remove(d);
        return out;
    }

    public List<FallStep> restDue(int t, int previous) {
        ArrayList<FallStep> out = new ArrayList<>();
        for (Fall f : falling.values()) {
            if (f.restT > f.startT && f.restT > previous && f.restT <= t) out.add(new FallStep(f.door, BodyBlocks.BARRIER));
        }
        return out;
    }

    public boolean isFalling(int door) { return falling.containsKey(door); }

    public Integer airAt(int door) {
        Fall f = falling.get(door);
        return f == null ? null : f.airT;
    }

    public void clear() { falling.clear(); }
}
