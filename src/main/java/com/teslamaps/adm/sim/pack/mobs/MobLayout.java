// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.List;

/** One recorded visit: the full list of mobs seen in a room. {@code fl} is {@code F7}, {@code M7} or {@code ?} (matches both). */
public class MobLayout {
    public final String src;
    public final String fl;
    public final List<MobSpawn> mobs;

    public MobLayout(String src, String fl, List<MobSpawn> mobs) { this.src = src; this.fl = fl; this.mobs = mobs; }
}
