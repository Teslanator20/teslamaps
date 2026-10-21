// Ported from ADM's p3sim/run/api/DamageApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.Run;
import java.util.UUID;

/** The damage hub and the player's percent-of-max-HP ledger. Implemented by DamageModule. Server thread only. */
public interface DamageService {
    /** Damages a mob by amount HP (SkyBlock scale) through MobService.setHp. False if unknown or dead. */
    default boolean hurtMob(Run run, UUID mob, double amount, DamageSource source, UUID by) { return false; }

    /** An Implosion on every alive mob in box [x0,y0,z0,x1,y1,z1]; damage overrides the profile. Returns mobs hit. */
    default int implode(Run run, UUID caster, double[] box, Double damage) { return 0; }

    default int implode(Run run, UUID caster, double[] box) { return implode(run, caster, box, null); }

    /** A mob's melee hit; size rolled from the pack's melee table. */
    default HurtResult meleeHit(Run run, UUID mob, UUID player) { return new HurtResult(0.0, false, true); }

    /** Hurts a member by percent of max HP (a trap: 15.0, true damage). Posts PlayerDamaged, PlayerDied. */
    default HurtResult hurtPlayer(Run run, UUID player, double percent, DamageSource source, UUID attacker, boolean trueDamage) {
        return new HurtResult(0.0, false, true);
    }

    default HurtResult hurtPlayer(Run run, UUID player, double percent, DamageSource source, UUID attacker) {
        return hurtPlayer(run, player, percent, source, attacker, false);
    }

    /** 0..100; 100 when unknown. */
    default double hpPercent(Run run, UUID player) { return 100.0; }

    /** Kills a member now (ghost ~6 s, Team Deaths +1). */
    default void kill(Run run, UUID player, DamageSource source, UUID attacker) {}

    default void kill(Run run, UUID player, DamageSource source) { kill(run, player, source, null); }

    /** Revives a ghost (by null: Revive Stone or the timer). */
    default void revive(Run run, UUID player, UUID by) {}

    default void revive(Run run, UUID player) { revive(run, player, null); }
}
