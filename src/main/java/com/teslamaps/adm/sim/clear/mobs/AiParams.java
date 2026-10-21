// Ported from ADM's p3sim/clear/mobs/MobAi.kt
package com.teslamaps.adm.sim.clear.mobs;

import java.util.Objects;

public final class AiParams {
    public final double moverShare;
    public final boolean strolls;
    public final double[] strollRates;
    public final double[] strollAimed;
    public final double strollPace;
    public final double aggroRadius;
    public final double loseRadius;
    public final double meleeReach;
    public final int meleeCadence;
    public final double rangedRange;
    public final int rangedCadence;
    public final int rangedMinCadence;
    public final double staticReach;
    public final double searchChance;
    public final int unseenMemory;
    public final double zombieSpeed;
    public final double skeletonSpeed;
    public final double witherSkeletonSpeed;
    public final double otherSpeed;
    public final int assassinTeleportEvery;
    public final double patrolSpeed;
    public final double speedyFactor;
    public final double patrolArrive;

    public AiParams(double moverShare, boolean strolls, double[] strollRates, double[] strollAimed, double strollPace, double aggroRadius, double loseRadius,
                    double meleeReach, int meleeCadence, double rangedRange, int rangedCadence, int rangedMinCadence, double staticReach, double searchChance,
                    int unseenMemory, double zombieSpeed, double skeletonSpeed, double witherSkeletonSpeed, double otherSpeed, int assassinTeleportEvery,
                    double patrolSpeed, double speedyFactor, double patrolArrive) {
        this.moverShare = moverShare;
        this.strolls = strolls;
        this.strollRates = strollRates;
        this.strollAimed = strollAimed;
        this.strollPace = strollPace;
        this.aggroRadius = aggroRadius;
        this.loseRadius = loseRadius;
        this.meleeReach = meleeReach;
        this.meleeCadence = meleeCadence;
        this.rangedRange = rangedRange;
        this.rangedCadence = rangedCadence;
        this.rangedMinCadence = rangedMinCadence;
        this.staticReach = staticReach;
        this.searchChance = searchChance;
        this.unseenMemory = unseenMemory;
        this.zombieSpeed = zombieSpeed;
        this.skeletonSpeed = skeletonSpeed;
        this.witherSkeletonSpeed = witherSkeletonSpeed;
        this.otherSpeed = otherSpeed;
        this.assassinTeleportEvery = assassinTeleportEvery;
        this.patrolSpeed = patrolSpeed;
        this.speedyFactor = speedyFactor;
        this.patrolArrive = patrolArrive;
    }

    public static final double DEF_MOVER_SHARE = 0.0;
    public static final boolean DEF_STROLLS = true;
    public static double[] defStrollRates() { return new double[]{7.0, 5.9, 3.9, 2.1, 1.35}; }
    public static double[] defStrollAimed() { return new double[]{0.31, 0.41, 0.35, 0.2, 0.11}; }
    public static final double DEF_STROLL_PACE = 0.85;
    public static final double DEF_AGGRO_RADIUS = 35.0;
    public static final double DEF_LOSE_RADIUS = 35.0;
    public static final double DEF_MELEE_REACH = 1.43;
    public static final int DEF_MELEE_CADENCE = 20;
    public static final double DEF_RANGED_RANGE = 15.0;
    public static final int DEF_RANGED_CADENCE = 60;
    public static final int DEF_RANGED_MIN_CADENCE = 20;
    public static final double DEF_STATIC_REACH = 1.43;
    public static final double DEF_SEARCH_CHANCE = 0.1;
    public static final int DEF_UNSEEN_MEMORY = 60;
    public static final double DEF_ZOMBIE_SPEED = 0.28;
    public static final double DEF_SKELETON_SPEED = 0.137;
    public static final double DEF_WITHER_SKELETON_SPEED = 0.27;
    public static final double DEF_OTHER_SPEED = 0.32;
    public static final int DEF_ASSASSIN_TELEPORT_EVERY = 0;
    public static final double DEF_PATROL_SPEED = 0.25;
    public static final double DEF_SPEEDY_FACTOR = 1.5;
    public static final double DEF_PATROL_ARRIVE = 1.0;

    public AiParams() {
        this(DEF_MOVER_SHARE, DEF_STROLLS, defStrollRates(), defStrollAimed(), DEF_STROLL_PACE, DEF_AGGRO_RADIUS, DEF_LOSE_RADIUS,
                DEF_MELEE_REACH, DEF_MELEE_CADENCE, DEF_RANGED_RANGE, DEF_RANGED_CADENCE, DEF_RANGED_MIN_CADENCE, DEF_STATIC_REACH, DEF_SEARCH_CHANCE,
                DEF_UNSEEN_MEMORY, DEF_ZOMBIE_SPEED, DEF_SKELETON_SPEED, DEF_WITHER_SKELETON_SPEED, DEF_OTHER_SPEED, DEF_ASSASSIN_TELEPORT_EVERY,
                DEF_PATROL_SPEED, DEF_SPEEDY_FACTOR, DEF_PATROL_ARRIVE);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiParams p)) return false;
        return Double.compare(moverShare, p.moverShare) == 0 && strolls == p.strolls && strollRates == p.strollRates && strollAimed == p.strollAimed
                && Double.compare(strollPace, p.strollPace) == 0 && Double.compare(aggroRadius, p.aggroRadius) == 0 && Double.compare(loseRadius, p.loseRadius) == 0
                && Double.compare(meleeReach, p.meleeReach) == 0 && meleeCadence == p.meleeCadence && Double.compare(rangedRange, p.rangedRange) == 0
                && rangedCadence == p.rangedCadence && rangedMinCadence == p.rangedMinCadence && Double.compare(staticReach, p.staticReach) == 0
                && Double.compare(searchChance, p.searchChance) == 0 && unseenMemory == p.unseenMemory && Double.compare(zombieSpeed, p.zombieSpeed) == 0
                && Double.compare(skeletonSpeed, p.skeletonSpeed) == 0 && Double.compare(witherSkeletonSpeed, p.witherSkeletonSpeed) == 0
                && Double.compare(otherSpeed, p.otherSpeed) == 0 && assassinTeleportEvery == p.assassinTeleportEvery && Double.compare(patrolSpeed, p.patrolSpeed) == 0
                && Double.compare(speedyFactor, p.speedyFactor) == 0 && Double.compare(patrolArrive, p.patrolArrive) == 0;
    }

    @Override public int hashCode() {
        return Objects.hash(moverShare, strolls, System.identityHashCode(strollRates), System.identityHashCode(strollAimed), strollPace, aggroRadius, loseRadius,
                meleeReach, meleeCadence, rangedRange, rangedCadence, rangedMinCadence, staticReach, searchChance, unseenMemory, zombieSpeed, skeletonSpeed,
                witherSkeletonSpeed, otherSpeed, assassinTeleportEvery, patrolSpeed, speedyFactor, patrolArrive);
    }

    @Override public String toString() {
        return "AiParams(moverShare=" + moverShare + ", strolls=" + strolls + ", aggroRadius=" + aggroRadius + ", loseRadius=" + loseRadius
                + ", meleeReach=" + meleeReach + ", rangedRange=" + rangedRange + ", patrolSpeed=" + patrolSpeed + ")";
    }
}
