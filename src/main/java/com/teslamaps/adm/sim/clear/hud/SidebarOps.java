// Ported from ADM's p3sim/clear/hud/HudModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

/** What changed between two sidebars, in the order the packets must go. */
public class SidebarOps {
    /** Scores whose team and score holder are gone. */
    public final List<Integer> remove;
    /** Scores that appear (team + holder + score) with their full line. */
    public final List<Pair<Integer, String>> add;
    /** Scores whose line text changed (a team update only). */
    public final List<Pair<Integer, String>> change;

    public SidebarOps(List<Integer> remove, List<Pair<Integer, String>> add, List<Pair<Integer, String>> change) {
        this.remove = remove;
        this.add = add;
        this.change = change;
    }

    public boolean isEmpty() { return remove.isEmpty() && add.isEmpty() && change.isEmpty(); }
}
