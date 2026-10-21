// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.concurrent.CopyOnWriteArrayList;

/** Where modules register extra HUD content; the HUD module reads these. */
public final class HudRegistry {
    private HudRegistry() {}

    public static final CopyOnWriteArrayList<SidebarContributor> sidebar = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<TabContributor> tab = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<MapContributor> map = new CopyOnWriteArrayList<>();
}
