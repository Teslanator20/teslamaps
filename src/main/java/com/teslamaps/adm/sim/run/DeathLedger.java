// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Team deaths. Damage module. */
public class DeathLedger {
    public int teamDeaths = 0;
    public final List<Pair<Integer, UUID>> log = new ArrayList<>();
}
