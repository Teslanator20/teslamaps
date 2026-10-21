// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Objects;

/** Equipment worn by an archetype (Shadow Assassin). */
public final class MobEquipment {
    public final String mainhand;
    public final String mainhandName;
    public final String feet;

    public MobEquipment(String mainhand, String mainhandName, String feet) { this.mainhand = mainhand; this.mainhandName = mainhandName; this.feet = feet; }

    @Override public boolean equals(Object o) {
        return o instanceof MobEquipment e && Objects.equals(mainhand, e.mainhand) && Objects.equals(mainhandName, e.mainhandName) && Objects.equals(feet, e.feet);
    }
    @Override public int hashCode() { return Objects.hash(mainhand, mainhandName, feet); }
    @Override public String toString() { return "MobEquipment(mainhand=" + mainhand + ", mainhandName=" + mainhandName + ", feet=" + feet + ")"; }
}
