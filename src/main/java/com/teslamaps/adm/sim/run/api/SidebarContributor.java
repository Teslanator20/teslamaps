// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;
import java.util.List;

/** Extra sidebar lines below the HUD's own. */
@FunctionalInterface
public interface SidebarContributor {
    List<String> lines(Run run);
}
