// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import java.util.UUID;

public class DoorRecord {
    public final PlacedDoor plan;

    public DoorRecord(PlacedDoor plan) {
        this.plan = plan;
        this.phase = plan.kind == DoorKind.NORMAL ? DoorPhase.OPEN : DoorPhase.CLOSED;
    }

    public int index() { return plan.index; }

    /** Plain doors start OPEN, every other kind CLOSED. */
    public DoorPhase phase;
    /** Run ticks the door started to fall. */
    public Integer openedAt = null;
    public UUID openedBy = null;
}
