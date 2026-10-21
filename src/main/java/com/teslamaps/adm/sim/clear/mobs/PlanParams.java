// Ported from ADM's p3sim/clear/mobs/MobPlanner.kt
package com.teslamaps.adm.sim.clear.mobs;

import java.util.Set;

public class PlanParams {
    public final String floor;
    public final double hpScale;
    public final SpawnMode mode;
    public final double partialWeight;
    public final double moverShare;
    public final Set<String> deferred;
    public final double minibossChance;

    public PlanParams(String floor, double hpScale, SpawnMode mode, double partialWeight, double moverShare, Set<String> deferred, double minibossChance) {
        this.floor = floor; this.hpScale = hpScale; this.mode = mode; this.partialWeight = partialWeight; this.moverShare = moverShare;
        this.deferred = deferred; this.minibossChance = minibossChance;
    }

    public PlanParams(String floor, double hpScale, SpawnMode mode, double partialWeight, double moverShare, Set<String> deferred) {
        this(floor, hpScale, mode, partialWeight, moverShare, deferred, MobPlanner.MINIBOSS_CHANCE);
    }

    public PlanParams(String floor, double hpScale, SpawnMode mode, double partialWeight, double moverShare) {
        this(floor, hpScale, mode, partialWeight, moverShare, defaultDeferred());
    }

    public PlanParams(String floor, double hpScale, SpawnMode mode, double partialWeight) {
        this(floor, hpScale, mode, partialWeight, new AiParams().moverShare);
    }

    public PlanParams(String floor, double hpScale, SpawnMode mode) { this(floor, hpScale, mode, MobRolls.PARTIAL_LAYOUT_WEIGHT); }

    public PlanParams(String floor, double hpScale) { this(floor, hpScale, SpawnMode.LAYOUTS); }

    public PlanParams(String floor) { this(floor, 1.0); }

    public PlanParams() { this("F7"); }

    public static Set<String> defaultDeferred() { return Set.of("king_midas"); }
}
