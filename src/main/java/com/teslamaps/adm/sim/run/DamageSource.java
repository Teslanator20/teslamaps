// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

public enum DamageSource {
    MELEE, PROJECTILE, TRAP, IMPLOSION, BOW, MAGE_BEAM, FALL, OTHER,

    /** "was crushed" */
    CRUSH,

    /** "disconnected and became a ghost" (bots only) */
    DISCONNECT,
}
