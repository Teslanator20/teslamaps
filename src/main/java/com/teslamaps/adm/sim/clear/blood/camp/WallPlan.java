// Ported from ADM's p3sim/clear/blood/camp/WallPlan.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;

/** What the wall shows in one run, and the throw order. Pure. */
public final class WallPlan {
    private WallPlan() {}

    public static final List<String> WALL_BOSSES = List.of("Livid", "Spirit Bear", "Bonzo", "Scarf");

    public static final List<String> STANDARD = List.of(
            "Freak", "Reaper", "Tear", "Leech", "Mr. Dead", "Putrid", "Psycho", "Ooze", "Mute",
            "Vader", "Parasite", "Walker", "Revoker", "Cannibal", "Skull", "Frost", "Flamer"
    );

    public static final int DECOR_PER_30 = 7;

    public static final double WS_THROW_CHANCE = 0.033;

    public static final String WS_NAME = "Wandering Soul";

    public static List<WallStation> assign(List<Vec3> stands, KRandom rng) {
        int n = stands.size();
        if (n == 0) return new ArrayList<>();
        List<Integer> order = new ArrayList<>(n);
        for (int k = 0; k < n; k++) order.add(k);
        KRandom.shuffle(order, rng);
        int giants = 1;
        int bosses = Math.min(WALL_BOSSES.size(), n > 2 ? Math.max(1, n - 2) : 0);
        int ws = n >= giants + bosses + STANDARD.size() + 1 ? 1 : 0;
        int standard = Math.min(STANDARD.size(), Math.max(0, n - giants - bosses - ws));
        List<String> names = new ArrayList<>(STANDARD);
        KRandom.shuffle(names, rng);
        List<String> bossNames = new ArrayList<>(WALL_BOSSES);
        KRandom.shuffle(bossNames, rng);
        WallStation[] out = new WallStation[n];
        int i = 0;
        for (int k = 0; k < giants; k++) { int s = order.get(i++); out[s] = new WallStation(s, stands.get(s), WallClass.GIANT, "Giant"); }
        for (int k = 0; k < bosses; k++) { int s = order.get(i++); out[s] = new WallStation(s, stands.get(s), WallClass.BOSS, bossNames.get(k)); }
        for (int k = 0; k < standard; k++) { int s = order.get(i++); out[s] = new WallStation(s, stands.get(s), WallClass.STANDARD, names.get(k)); }
        for (int k = 0; k < ws; k++) { int s = order.get(i++); out[s] = new WallStation(s, stands.get(s), WallClass.WANDERING_SOUL, WS_NAME); }
        while (i < n) { int s = order.get(i++); out[s] = new WallStation(s, stands.get(s), WallClass.DECOR, ""); }
        List<WallStation> res = new ArrayList<>(n);
        for (WallStation st : out) {
            if (st == null) throw new NullPointerException();
            res.add(st);
        }
        return res;
    }

    public static List<WallStation> throwOrder(List<WallStation> wall, KRandom rng, int later) { return throwOrder(wall, rng, later, 0.0); }

    public static List<WallStation> throwOrder(List<WallStation> wall, KRandom rng, int later, double wsChance) {
        List<WallStation> left = new ArrayList<>(wall);
        List<WallStation> out = new ArrayList<>();
        WallStation t = take(left, rng, WallClass.GIANT, WallClass.BOSS, WallClass.STANDARD, WallClass.DECOR);
        if (t != null) out.add(t);
        t = take(left, rng, WallClass.BOSS, WallClass.STANDARD, WallClass.DECOR);
        if (t != null) out.add(t);
        for (int k = 0; k < 2; k++) {
            t = take(left, rng, WallClass.STANDARD, WallClass.BOSS, WallClass.DECOR);
            if (t != null) out.add(t);
        }
        for (int k = 0; k < later; k++) {
            t = take(left, rng, WallClass.STANDARD, WallClass.BOSS, WallClass.DECOR);
            if (t != null) out.add(t);
        }
        if (wsChance > 0.0 && rng.nextDouble() < wsChance) {
            WallStation soul = null;
            for (WallStation s : left) if (s.cls == WallClass.WANDERING_SOUL) { soul = s; break; }
            if (soul != null) out.add(rng.nextInt(Math.min(4, out.size()), out.size() + 1), soul);
        }
        return out;
    }

    private static WallStation take(List<WallStation> left, KRandom rng, WallClass... classes) {
        for (WallClass c : classes) {
            List<WallStation> pool = new ArrayList<>();
            for (WallStation s : left) if (s.cls == c) pool.add(s);
            if (!pool.isEmpty()) {
                WallStation s = pool.get(rng.nextInt(pool.size()));
                left.remove(s);
                return s;
            }
        }
        return null;
    }

    /** Where a thrown head ends: from stand toward target, length blocks long. */
    public static Vec3 headEnd(Vec3 stand, Vec3 target, double length) {
        Vec3 d = target.minus(stand);
        double l = d.length();
        if (l < 1e-6) return stand;
        return stand.plus(d.times(length / l));
    }
}
