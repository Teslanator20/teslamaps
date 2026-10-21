// Ported from ADM's p3sim/clear/mobs/MobRolls.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.pack.mobs.HitQuantiles;
import com.teslamaps.adm.sim.pack.mobs.HpScaleTable;
import com.teslamaps.adm.sim.pack.mobs.MeleeTable;
import com.teslamaps.adm.sim.pack.mobs.MobCell;
import com.teslamaps.adm.sim.pack.mobs.MobLayout;
import com.teslamaps.adm.sim.pack.mobs.MobModifier;
import com.teslamaps.adm.sim.pack.mobs.MobSpawn;
import com.teslamaps.adm.sim.pack.mobs.RoomMobs;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Triple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

public final class MobRolls {
    private MobRolls() {}

    public static final double PARTIAL_LAYOUT_WEIGHT = 0.25;

    public static final int MIN_LAYOUTS = 3;

    public static List<RolledMob> rollRoom(RoomMobs room, String floor, KRandom rng) { return rollRoom(room, floor, rng, SpawnMode.LAYOUTS, PARTIAL_LAYOUT_WEIGHT); }

    public static List<RolledMob> rollRoom(RoomMobs room, String floor, KRandom rng, SpawnMode mode) { return rollRoom(room, floor, rng, mode, PARTIAL_LAYOUT_WEIGHT); }

    public static List<RolledMob> rollRoom(RoomMobs room, String floor, KRandom rng, SpawnMode mode, double partialWeight) {
        if (mode == SpawnMode.LAYOUTS) {
            List<RolledMob> l = rollLayout(room, floor, rng, partialWeight);
            if (l != null) return l;
        }
        return rollCells(room, rng);
    }

    public static List<RolledMob> rollLayout(RoomMobs room, String floor, KRandom rng) { return rollLayout(room, floor, rng, PARTIAL_LAYOUT_WEIGHT); }

    public static List<RolledMob> rollLayout(RoomMobs room, String floor, KRandom rng, double partialWeight) {
        List<MobLayout> pool = new ArrayList<>();
        for (MobLayout l : room.layouts) if (l.fl.equals(floor) || l.fl.equals("?")) pool.add(l);
        if (pool.size() < MIN_LAYOUTS) return null;
        double usual = (room.starred != null ? room.starred.p50 : 0.0) / 2.0;
        double[] weights = new double[pool.size()];
        double total = 0.0;
        for (int i = 0; i < pool.size(); i++) {
            int stars = 0;
            for (MobSpawn m : pool.get(i).mobs) if (m.star) stars++;
            weights[i] = stars < usual ? partialWeight : 1.0;
            total += weights[i];
        }
        if (total <= 0.0) return null;
        double pick = rng.nextDouble() * total;
        MobLayout chosen = pool.get(pool.size() - 1);
        for (int i = 0; i < weights.length; i++) { pick -= weights[i]; if (pick < 0.0) { chosen = pool.get(i); break; } }
        List<RolledMob> mobs = new ArrayList<>();
        for (MobSpawn it : chosen.mobs) mobs.add(new RolledMob(it.arch, it.star, it.x, it.y, it.z));
        List<MobLayout> full = completeUnstarred(pool);
        if (full.isEmpty() || full.contains(chosen)) return mobs;
        MobLayout donor = full.get(rng.nextInt(full.size()));
        List<RolledMob> starred = new ArrayList<>();
        for (RolledMob m : mobs) if (m.star) starred.add(m);
        Set<Triple<Integer, Integer, Integer>> taken = new HashSet<>();
        for (RolledMob m : starred) taken.add(Triple.of(m.x, m.y, m.z));
        List<RolledMob> out = new ArrayList<>(starred);
        for (MobSpawn it : donor.mobs) {
            if (!it.star && !taken.contains(Triple.of(it.x, it.y, it.z))) out.add(new RolledMob(it.arch, false, it.x, it.y, it.z));
        }
        return out;
    }

    public static final int MIN_FULL_UNSTARRED = 5;

    public static List<MobLayout> completeUnstarred(List<MobLayout> pool) {
        int[] counts = new int[pool.size()];
        int most = 0;
        for (int i = 0; i < pool.size(); i++) {
            int c = 0;
            for (MobSpawn m : pool.get(i).mobs) if (!m.star) c++;
            counts[i] = c;
            if (c > most) most = c;
        }
        if (most < MIN_FULL_UNSTARRED) return new ArrayList<>();
        List<MobLayout> out = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) if (counts[i] * 2 >= most) out.add(pool.get(i));
        return out;
    }

    public static List<RolledMob> rollCells(RoomMobs room, KRandom rng) {
        int n = Math.max(room.visits(), 1);
        List<RolledMob> out = new ArrayList<>();
        for (Map.Entry<CellKey, List<MobCell>> en : cellGroups(room.cells).entrySet()) {
            CellKey key = en.getKey();
            List<MobCell> group = en.getValue();
            int sum = 0;
            for (MobCell c : group) sum += c.occ;
            if (sum <= 0) continue;
            if (rng.nextDouble() >= Math.min(1.0, (double) sum / n)) continue;
            int pick = rng.nextInt(sum);
            MobCell chosen = group.get(group.size() - 1);
            for (MobCell c : group) { pick -= c.occ; if (pick < 0) { chosen = c; break; } }
            out.add(new RolledMob(chosen.arch, key.star, key.x, key.y, key.z));
        }
        return out;
    }

    private static final class CellKey {
        final int x, y, z;
        final boolean star;

        CellKey(int x, int y, int z, boolean star) { this.x = x; this.y = y; this.z = z; this.star = star; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CellKey k)) return false;
            return x == k.x && y == k.y && z == k.z && star == k.star;
        }

        @Override public int hashCode() { return Objects.hash(x, y, z, star); }
    }

    private static Map<CellKey, List<MobCell>> cellGroups(List<MobCell> cells) {
        Map<CellKey, List<MobCell>> out = new LinkedHashMap<>();
        for (MobCell c : cells) out.computeIfAbsent(new CellKey(c.x, c.y, c.z, c.star), k -> new ArrayList<>()).add(c);
        return out;
    }

    public static double expectedStarred(RoomMobs room) {
        int n = Math.max(room.visits(), 1);
        double total = 0.0;
        for (Map.Entry<CellKey, List<MobCell>> en : cellGroups(room.cells).entrySet()) {
            if (!en.getKey().star) continue;
            int sum = 0;
            for (MobCell c : en.getValue()) sum += c.occ;
            total += Math.min(1.0, (double) sum / n);
        }
        return total;
    }

    public static double drawHpScale(HpScaleTable table, KRandom rng) {
        int total = 0;
        for (int c : table.counts) total += c;
        if (total <= 0) return 1.0;
        int pick = rng.nextInt(total);
        for (int i = 0; i < table.values.length; i++) { pick -= table.counts[i]; if (pick < 0) return table.values[i]; }
        return table.values[table.values.length - 1];
    }

    public static MobModifier rollModifier(List<MobModifier> modifiers, String floor, KRandom rng) { return rollModifier(modifiers, floor, rng, null); }

    public static MobModifier rollModifier(List<MobModifier> modifiers, String floor, KRandom rng, Map<String, Double> override) {
        if (modifiers.isEmpty()) return null;
        double[] weights = new double[modifiers.size()];
        double total = 0.0;
        for (int i = 0; i < modifiers.size(); i++) {
            MobModifier m = modifiers.get(i);
            Double w;
            if (override != null) w = override.get(m.id);
            else {
                w = m.weight.get(floor);
                if (w == null) w = m.weight.get("F7");
            }
            weights[i] = w != null ? w : 0.0;
            total += weights[i];
        }
        if (total <= 0.0) return null;
        double pick = rng.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) { pick -= weights[i]; if (pick < 0.0) return modifiers.get(i); }
        return modifiers.get(modifiers.size() - 1);
    }

    public static double spawnHp(long base, double scale, MobModifier modifier) {
        return (double) Math.round(base * scale * (modifier != null ? modifier.hpFactor : 1.0));
    }

    public static double sampleMelee(MeleeTable table, KRandom rng) { return sampleMeleeAt(table, rng.nextDouble(), rng.nextDouble() * 100.0); }

    public static double sampleMeleeAt(MeleeTable table, double atomRoll, double u) {
        double edge = 0.0;
        for (MeleeTable.Atom a : table.atoms) { edge += a.p; if (atomRoll < edge) return a.value; }
        SortedMap<Double, Double> q = table.quantiles;
        if (q.isEmpty()) return 0.0;
        double uu = Math.clamp(u, q.firstKey(), q.lastKey());
        double lo = q.headMap(uu + 1e-12).lastKey();
        double hi = q.tailMap(uu).firstKey();
        double v0 = Math.max(q.get(lo), 1e-3), v1 = Math.max(q.get(hi), 1e-3);
        if (hi == lo) return q.get(lo);
        double t = (uu - lo) / (hi - lo);
        return Math.exp(Math.log(v0) + (Math.log(v1) - Math.log(v0)) * t);
    }

    public static double sampleHit(HitQuantiles h, KRandom rng) { return sampleHitAt(h, rng.nextDouble() * 100.0); }

    public static double sampleHitAt(HitQuantiles h, double u) {
        double[] us = {0.0, 10.0, 50.0, 90.0, 100.0};
        double[] vs = {Math.max(h.p10 / 3.0, 1e-3), Math.max(h.p10, 1e-3), Math.max(h.p50, 1e-3), Math.max(h.p90, 1e-3), Math.max(h.p90 * 1.3, 1e-3)};
        double uu = Math.clamp(u, 0.0, 100.0);
        for (int i = 1; i < us.length; i++) {
            double u1 = us[i], v1 = vs[i], u0 = us[i - 1], v0 = vs[i - 1];
            if (uu <= u1) { double t = u1 == u0 ? 0.0 : (uu - u0) / (u1 - u0); return Math.exp(Math.log(v0) + (Math.log(v1) - Math.log(v0)) * t); }
        }
        return vs[vs.length - 1];
    }

    public static UUID uuidV2(KRandom rng) {
        long msb = (rng.nextLong() & -0xF001L) | 0x2000L;
        long lsb = (rng.nextLong() & 0x3FFFFFFFFFFFFFFFL) | Long.MIN_VALUE;
        return new UUID(msb, lsb);
    }

    public static UUID uuidV4(KRandom rng) {
        long msb = (rng.nextLong() & -0xF001L) | 0x4000L;
        long lsb = (rng.nextLong() & 0x3FFFFFFFFFFFFFFFL) | Long.MIN_VALUE;
        return new UUID(msb, lsb);
    }

    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static String profileName(KRandom rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(NAME_CHARS.charAt(rng.nextInt(NAME_CHARS.length())));
        return sb.toString();
    }
}
