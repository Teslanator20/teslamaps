// Ported from ADM's p3sim/clear/state/KeyPlans.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.KeyState;
import com.teslamaps.adm.sim.run.api.KeyPlan;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class KeyLedger {
    private final KeyState keys;
    public final KeyPlan plan;
    private final LinkedHashSet<Integer> dropped = new LinkedHashSet<>();

    public KeyLedger(KeyState keys, KeyPlan plan) {
        this.keys = keys;
        this.plan = plan;
    }

    public KeyKind pending(int room) { return dropped.contains(room) ? null : plan.keyFor(room); }

    public boolean markDropped(int room) {
        if (pending(room) == null) return false;
        dropped.add(room);
        return true;
    }

    public Set<Integer> droppedRooms() { return dropped; }

    public void restoreDropped(Collection<Integer> rooms) { dropped.clear(); dropped.addAll(rooms); }

    public void pick(KeyKind kind) {
        switch (kind) {
            case WITHER -> { keys.witherHeld++; keys.witherObtained++; }
            case BLOOD -> { keys.bloodHeld = true; keys.bloodObtained = true; }
        }
    }

    public boolean has(KeyKind kind) {
        return switch (kind) {
            case WITHER -> keys.witherHeld > 0;
            case BLOOD -> keys.bloodHeld;
        };
    }

    public boolean consume(KeyKind kind) {
        if (!has(kind)) return false;
        switch (kind) {
            case WITHER -> { keys.witherHeld--; keys.witherUsed++; }
            case BLOOD -> { keys.bloodHeld = false; keys.bloodUsed = true; }
        }
        return true;
    }

    public boolean consistent() {
        return keys.witherObtained - keys.witherUsed == keys.witherHeld && keys.witherHeld >= 0
                && (!keys.bloodUsed || keys.bloodObtained) && keys.bloodHeld == (keys.bloodObtained && !keys.bloodUsed);
    }
}
