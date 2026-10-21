// Ported from ADM's p3sim/run/api/TrapApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;
import java.util.UUID;

/** The trap room and the rooms that disable abilities. Implemented by TrapsModule. */
public interface TrapService {
    /** True when player stands where abilities are disabled. */
    default boolean abilitiesDisabled(Run run, UUID player) { return false; }
}
