// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;

/** Extra map marks after rooms, doors and players. */
@FunctionalInterface
public interface MapContributor {
    void paint(Run run, MapCanvas canvas);
}
