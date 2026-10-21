// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One room's live state. check: doors/state module only. */
public class RoomRecord {
    public final PlacedRoom plan;

    public RoomRecord(PlacedRoom plan) { this.plan = plan; }

    public int index() { return plan.index; }

    public CheckMark check = CheckMark.UNDISCOVERED;
    /** Run ticks of the first time the room reached each mark. */
    public Integer tDiscovered = null;
    public Integer tWhite = null;
    public Integer tGreen = null;
    public Integer tRed = null;

    /** Secrets module. */
    public int secretsFound = 0;

    public int secretsMax() { return plan.secretsMax; }

    /** Mobs module. */
    public int mobsRequired = 0;
    public int mobsRequiredAlive = 0;

    /** Players inside now. */
    public final Set<UUID> occupants = new LinkedHashSet<>();
    /** Everybody who has ever been inside. */
    public final Set<UUID> visitors = new LinkedHashSet<>();

    /** Key: "<moduleId>.<name>". Saved by the owner's own snapshot. */
    public final Map<String, Object> extras = new HashMap<>();
}
