// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;
import java.util.List;

/** Extra §-coded tab rows in the dungeon-stats column. */
@FunctionalInterface
public interface TabContributor {
    List<String> rows(Run run);
}
