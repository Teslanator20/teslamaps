// Ported from ADM's p3sim/clear/mobs/MobPlanner.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.mobs.Miniboss;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobModifier;
import com.teslamaps.adm.sim.pack.mobs.MobPack;
import com.teslamaps.adm.sim.pack.mobs.RoomMobs;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.util.KRandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class MobPlanner {
    private MobPlanner() {}

    public static final double MINIBOSS_CHANCE = 0.65;

    public static final double YELLOW_CHANCE = 1.0;

    public static final Map<String, Double> MINIBOSS_WEIGHTS = new LinkedHashMap<>();

    static {
        MINIBOSS_WEIGHTS.put("lost_adventurer", 0.55);
        MINIBOSS_WEIGHTS.put("angry_archaeologist", 0.23);
        MINIBOSS_WEIGHTS.put("frozen_adventurer", 0.16);
        MINIBOSS_WEIGHTS.put("shadow_assassin", 0.05);
    }

    public static final Map<String, int[]> EXTRA_MINIBOSS_CELLS = new LinkedHashMap<>();

    static {
        EXTRA_MINIBOSS_CELLS.put("Waterfall", new int[]{106, 39, 20});
        EXTRA_MINIBOSS_CELLS.put("Pit", new int[]{14, 90, 9});
        EXTRA_MINIBOSS_CELLS.put("Wizard", new int[]{46, 52, 16});
        EXTRA_MINIBOSS_CELLS.put("Atlas", new int[]{31, 66, 31});
        EXTRA_MINIBOSS_CELLS.put("Perch", new int[]{13, 60, 13});
        EXTRA_MINIBOSS_CELLS.put("Duncan", new int[]{23, 84, 20});
        EXTRA_MINIBOSS_CELLS.put("Chambers", new int[]{19, 90, 8});
        EXTRA_MINIBOSS_CELLS.put("Layers", new int[]{23, 79, 39});
        EXTRA_MINIBOSS_CELLS.put("Balcony", new int[]{42, 51, 15});
    }

    public static final long CLEAR_ASSASSIN_HP_F7 = 4_200_000L;

    public static List<PlannedMob> planRoom(MobPack pack, String roomName, Function<String, String> aliasOf, Placement placement, PlanParams p, KRandom rng) {
        RoomMobs rm = pack.room(roomName, aliasOf);
        if (rm == null) return Collections.emptyList();
        return planFrom(pack, rm, placement, p, rng);
    }

    private static boolean isDeferred(Set<String> deferred, MobPack pack, int index) {
        MobArchetype a = pack.archetype(index);
        return a != null && a.id != null && deferred.contains(a.id);
    }

    public static List<PlannedMob> planFrom(MobPack pack, RoomMobs rm, Placement placement, PlanParams p, KRandom rng) {
        List<RolledMob> rolled = new ArrayList<>();
        for (RolledMob it : MobRolls.rollRoom(rm, p.floor, rng, p.mode, p.partialWeight)) if (!isDeferred(p.deferred, pack, it.arch)) rolled.add(it);
        List<Miniboss> rows = pack.minibossesOf(rm.name);
        List<double[]> bossLoop = null;
        Predicate<RolledMob> isBoss = r -> {
            MobArchetype a = pack.archetype(r.arch);
            return a != null && Objects.equals(a.cls, "miniboss");
        };
        int[] extra = EXTRA_MINIBOSS_CELLS.get(rm.name);
        boolean anyBoss = false;
        for (RolledMob r : rolled) if (isBoss.test(r)) { anyBoss = true; break; }
        if (!rows.isEmpty() || Objects.equals(rm.clear, "miniboss") || extra != null || anyBoss) {
            Miniboss row = rows.isEmpty() ? null : rows.get(0);
            RolledMob firstBoss = null;
            for (RolledMob r : rolled) if (isBoss.test(r)) { firstBoss = r; break; }
            boolean yellow = Objects.equals(rm.type, "yellow");
            if (rng.nextDouble() >= (yellow ? YELLOW_CHANCE : p.minibossChance)) {
                List<RolledMob> kept = new ArrayList<>();
                for (RolledMob r : rolled) if (!isBoss.test(r)) kept.add(r);
                rolled = kept;
            } else {
                List<RolledMob> rest = new ArrayList<>();
                for (RolledMob r : rolled) if (!isBoss.test(r)) rest.add(r);
                Integer own = null;
                if (yellow && !rm.name.equals("Default")) {
                    if (row != null) own = row.arch;
                    else if (firstBoss != null) own = firstBoss.arch;
                }
                Integer bossArch = own;
                if (bossArch == null) bossArch = drawMinibossArch(pack, rng, p.deferred);
                if (bossArch == null && row != null) bossArch = row.arch;
                if (bossArch == null && firstBoss != null) bossArch = firstBoss.arch;
                if (bossArch != null && !isDeferred(p.deferred, pack, bossArch)) {
                    Pos cell = row != null ? row.cell : null;
                    RolledMob boss;
                    if (cell != null) boss = new RolledMob(bossArch, true, cell.x, cell.y, cell.z);
                    else if (extra != null) boss = new RolledMob(bossArch, true, extra[0], extra[1], extra[2]);
                    else boss = firstBoss != null ? new RolledMob(bossArch, firstBoss.star, firstBoss.x, firstBoss.y, firstBoss.z) : null;
                    if (boss != null) {
                        List<RolledMob> next = new ArrayList<>(rest);
                        next.add(boss);
                        rolled = next;
                    }
                    if (row != null && row.loop != null && row.loop.size() >= 2) {
                        List<double[]> l = new ArrayList<>();
                        for (Pos pt : row.loop) {
                            WorldPos w = placement.toWorld(pt.x, pt.y, pt.z);
                            l.add(new double[]{w.x + 0.5, w.z + 0.5});
                        }
                        bossLoop = l;
                    } else bossLoop = null;
                }
            }
        }
        List<PlannedMob> out = new ArrayList<>(rolled.size());
        for (RolledMob r : rolled) {
            MobArchetype arch = pack.archetype(r.arch);
            if (arch == null) continue;
            Long base = clearHp(arch, p.floor);
            if (base == null) continue;
            MobModifier mod = null;
            if (Objects.equals(arch.cls, "clear")) {
                MobModifier m = MobRolls.rollModifier(pack.modifiers, p.floor, rng, arch.modifierWeights);
                mod = m != null && !m.id.isEmpty() ? m : null;
            }
            WorldPos w = placement.toWorld(r.x, r.y, r.z);
            boolean isBossArch = Objects.equals(arch.cls, "miniboss");
            List<double[]> loop = isBossArch ? bossLoop : null;
            boolean mover = loop != null || Objects.equals(arch.movement, "teleport") || (!Objects.equals(arch.movement, "patrol") && rng.nextDouble() < p.moverShare);
            UUID id = arch.disguise ? MobRolls.uuidV2(rng) : MobRolls.uuidV4(rng);
            out.add(new PlannedMob(id, arch.index, r.star, r.star || isBossArch, mod != null ? mod.id : "", MobRolls.spawnHp(base, p.hpScale, mod), w.x + 0.5, (double) w.y, w.z + 0.5, SPAWN_YAW, mover, loop));
        }
        return out;
    }

    public static final float SPAWN_YAW = 0f;

    public static Long clearHp(MobArchetype arch, String floor) {
        if (Objects.equals(arch.movement, "teleport") && floor.equals("F7")) return CLEAR_ASSASSIN_HP_F7;
        return arch.hpFor(floor);
    }

    public static Integer drawMinibossArch(MobPack pack, KRandom rng, Set<String> deferred) {
        Map<String, Double> have = new LinkedHashMap<>();
        for (Map.Entry<String, Double> en : MINIBOSS_WEIGHTS.entrySet()) {
            if (!deferred.contains(en.getKey()) && pack.archetypeById(en.getKey()) != null) have.put(en.getKey(), en.getValue());
        }
        if (have.isEmpty()) return null;
        double sum = 0.0;
        for (double w : have.values()) sum += w;
        double pick = rng.nextDouble() * sum;
        String last = null;
        for (Map.Entry<String, Double> en : have.entrySet()) {
            last = en.getKey();
            pick -= en.getValue();
            if (pick < 0.0) return pack.archetypeById(en.getKey()).index;
        }
        return pack.archetypeById(last).index;
    }

    public static AiKind aiKind(MobArchetype arch) { return aiKind(arch, false); }

    public static AiKind aiKind(MobArchetype arch, boolean teleportOn) { return MobAi.kindOf(arch.id, arch.entity, arch.movement, teleportOn); }
}
