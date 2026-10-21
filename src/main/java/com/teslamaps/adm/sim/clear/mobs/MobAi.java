// Ported from ADM's p3sim/clear/mobs/MobAi.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.util.KRandom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MobAi {
    private MobAi() {}

    public static final int SEE_BEFORE_STOP = 20;

    public static AiAction step(AiKind kind, boolean mover, double x, double z, double speed, AiTarget target, AiState state, List<double[]> loop, AiParams p) {
        return step(kind, mover, x, z, speed, target, state, loop, p, true, null);
    }

    public static AiAction step(AiKind kind, boolean mover, double x, double z, double speed, AiTarget target, AiState state, List<double[]> loop, AiParams p, boolean search) {
        return step(kind, mover, x, z, speed, target, state, loop, p, search, null);
    }

    public static AiAction step(AiKind kind, boolean mover, double x, double z, double speed, AiTarget target, AiState state, List<double[]> loop, AiParams p, boolean search, KRandom rng) {
        if (state.cooldown > 0) state.cooldown--;
        state.sinceTeleport++;
        if (target == null) {
            state.chasing = false; state.drawn = false; state.seeTime = 0;
            AiAction it = patrol(x, z, state, loop, p, mover);
            if (it == AiAction.Idle.INSTANCE && (loop == null || loop.isEmpty())) return stroll(x, z, speed, null, state, p, rng);
            return it;
        }
        if (!mover && kind != AiKind.TELEPORT) {
            if (kind == AiKind.RANGED && target.seen && target.dist <= p.rangedRange) {
                AiAction act = shoot(target, state, p);
                if (act != AiAction.Idle.INSTANCE) { state.strollLeft = 0; return act; }
            } else if (kind == AiKind.RANGED) state.drawn = false;
            if (kind != AiKind.RANGED && p.staticReach > 0.0 && target.dist <= p.staticReach) return attack(kind, target, state, p);
            if (loop != null && !loop.isEmpty()) return patrol(x, z, state, loop, p, mover);
            return stroll(x, z, speed, target, state, p, rng);
        }
        if (state.chasing) {
            state.unseen = target.seen ? 0 : state.unseen + 1;
            if (target.dist > p.loseRadius || state.unseen > p.unseenMemory) { state.chasing = false; state.unseen = 0; state.drawn = false; state.seeTime = 0; }
        } else if (search && target.seen && target.dist <= p.aggroRadius) state.chasing = true;
        if (!state.chasing) return patrol(x, z, state, loop, p, mover);
        switch (kind) {
            case TELEPORT -> {
                if (p.assassinTeleportEvery > 0 && state.sinceTeleport >= p.assassinTeleportEvery && target.dist > p.meleeReach) {
                    state.sinceTeleport = 0;
                    double dx = target.x - x, dz = target.z - z;
                    double len = Math.max(Math.sqrt(dx * dx + dz * dz), 1e-6);
                    return new AiAction.Teleport(target.x + dx / len * 1.5, target.z + dz / len * 1.5);
                } else if (target.dist <= p.meleeReach) return attack(kind, target, state, p);
                else return new AiAction.MoveTo(target.x, target.z, speed);
            }
            case RANGED -> {
                state.seeTime = target.seen ? state.seeTime + 1 : 0;
                if (target.dist <= p.rangedRange && target.seen) {
                    AiAction act = shoot(target, state, p);
                    if (state.seeTime < SEE_BEFORE_STOP && act != AiAction.Shoot.INSTANCE) return new AiAction.MoveTo(target.x, target.z, speed);
                    return act;
                } else {
                    state.drawn = false;
                    return new AiAction.MoveTo(target.x, target.z, speed);
                }
            }
            default -> {
                if (target.dist <= p.meleeReach) return attack(kind, target, state, p);
                return new AiAction.MoveTo(target.x, target.z, speed);
            }
        }
    }

    public static int strollBand(double dist) {
        if (dist < 6) return 0;
        if (dist < 12) return 1;
        if (dist < 20) return 2;
        if (dist < 32) return 3;
        return 4;
    }

    private static AiAction stroll(double x, double z, double speed, AiTarget target, AiState state, AiParams p, KRandom rng) {
        double pace = speed * p.strollPace;
        if (state.strollLeft > 0) {
            state.strollLeft--;
            double dx = state.strollX - x, dz = state.strollZ - z;
            if (dx * dx + dz * dz > 0.25 && state.strollLeft > 0) return new AiAction.MoveTo(state.strollX, state.strollZ, pace);
            state.strollLeft = 0;
            return AiAction.Idle.INSTANCE;
        }
        if (!p.strolls || rng == null || pace <= 0.0) return AiAction.Idle.INSTANCE;
        int band = strollBand(target != null ? target.dist : Double.MAX_VALUE);
        if (rng.nextDouble() >= p.strollRates[band] / 1200.0) return AiAction.Idle.INSTANCE;
        double len = Math.min(25.0, 3.0 - 4.0 * Math.log(1.0 - rng.nextDouble()));
        double heading;
        if (target != null && target.seen && rng.nextDouble() < p.strollAimed[band]) {
            heading = Math.atan2(target.z - z, target.x - x);
            len = Math.min(len, target.dist - 1.0);
            if (len < 1.0) return AiAction.Idle.INSTANCE;
        } else heading = rng.nextDouble() * 2 * Math.PI;
        state.strollX = x + Math.cos(heading) * len;
        state.strollZ = z + Math.sin(heading) * len;
        state.strollLeft = (int) (len / pace) + 40;
        return new AiAction.MoveTo(state.strollX, state.strollZ, pace);
    }

    public static int shotInterval(double dist, AiParams p) {
        double f = Math.clamp(dist / p.rangedRange, 0.1, 1.0);
        return (int) (p.rangedMinCadence + (p.rangedCadence - p.rangedMinCadence) * f);
    }

    private static AiAction shoot(AiTarget target, AiState state, AiParams p) {
        if (!state.drawn) { state.drawn = true; state.cooldown = shotInterval(target.dist, p); return AiAction.Idle.INSTANCE; }
        if (state.cooldown > 0) return AiAction.Idle.INSTANCE;
        state.cooldown = shotInterval(target.dist, p);
        return AiAction.Shoot.INSTANCE;
    }

    private static AiAction attack(AiKind kind, AiTarget target, AiState state, AiParams p) {
        if (state.cooldown > 0) return AiAction.Idle.INSTANCE;
        state.cooldown = p.meleeCadence;
        return AiAction.Melee.INSTANCE;
    }

    private static AiAction patrol(double x, double z, AiState state, List<double[]> loop, AiParams p, boolean mover) {
        if (loop == null || loop.isEmpty()) return AiAction.Idle.INSTANCE;
        int i = Math.clamp(state.loopIndex, 0, loop.size() - 1);
        double dx = loop.get(i)[0] - x, dz = loop.get(i)[1] - z;
        if (dx * dx + dz * dz <= p.patrolArrive * p.patrolArrive) { i = (i + 1) % loop.size(); state.loopIndex = i; }
        return new AiAction.MoveTo(loop.get(i)[0], loop.get(i)[1], p.patrolSpeed);
    }

    public static AiKind kindOf(String archId, String entity, String movement) { return kindOf(archId, entity, movement, false); }

    public static AiKind kindOf(String archId, String entity, String movement, boolean teleportOn) {
        if (movement.equals("teleport") && teleportOn) return AiKind.TELEPORT;
        if (RANGED_IDS.contains(archId) || entity.equals("minecraft:skeleton") || entity.equals("minecraft:wither_skeleton")) return AiKind.RANGED;
        return AiKind.MELEE;
    }

    private static final Set<String> RANGED_IDS = Set.of("withermancer", "super_archer", "sniper", "skeleton_master", "skeleton_soldier", "skeleton_lord");

    public static final Map<String, Double> ARCHETYPE_SPEEDS = new LinkedHashMap<>();

    static {
        ARCHETYPE_SPEEDS.put("skeleton_soldier", 0.137); ARCHETYPE_SPEEDS.put("skeleton_master", 0.137); ARCHETYPE_SPEEDS.put("skeleton_lord", 0.137); ARCHETYPE_SPEEDS.put("super_archer", 0.137);
        ARCHETYPE_SPEEDS.put("crypt_lurker", 0.183); ARCHETYPE_SPEEDS.put("super_tank_zombie", 0.168);
        ARCHETYPE_SPEEDS.put("zombie_soldier", 0.278); ARCHETYPE_SPEEDS.put("zombie_knight", 0.28); ARCHETYPE_SPEEDS.put("zombie_commander", 0.19);
        ARCHETYPE_SPEEDS.put("crypt_dreadlord", 0.172); ARCHETYPE_SPEEDS.put("crypt_souleater", 0.123); ARCHETYPE_SPEEDS.put("withermancer", 0.27);
        ARCHETYPE_SPEEDS.put("lost_adventurer", 0.286); ARCHETYPE_SPEEDS.put("angry_archaeologist", 0.30); ARCHETYPE_SPEEDS.put("frozen_adventurer", 0.30); ARCHETYPE_SPEEDS.put("fels", 0.32);
    }

    public static double speedFor(String archId, String entity, boolean speedy, AiParams p) {
        Double base = ARCHETYPE_SPEEDS.get(archId);
        if (base == null && archId.startsWith(BloodLooks.PREFIX)) base = com.teslamaps.adm.sim.clear.blood.camp.CampMobs.SPEED;
        if (base == null) base = speedOf(entity, p);
        return base * (speedy ? p.speedyFactor : 1.0);
    }

    public static double speedOf(String entity, AiParams p) {
        return switch (entity) {
            case "minecraft:zombie" -> p.zombieSpeed;
            case "minecraft:skeleton" -> p.skeletonSpeed;
            case "minecraft:wither_skeleton" -> p.witherSkeletonSpeed;
            default -> p.otherSpeed;
        };
    }
}
