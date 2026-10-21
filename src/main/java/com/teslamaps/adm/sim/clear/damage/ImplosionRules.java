package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/ImplosionRules.kt

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ImplosionRules {
    private ImplosionRules() {}

    public static final double REACH = 6.0;
    public static final double REACH_UP = 7.0;
    public static final double REACH_DOWN = 6.0;

    public static class Profile {
        public final String floor;
        public final double median;
        public final boolean byKind;

        public Profile(String floor, double median, boolean byKind) {
            this.floor = floor;
            this.median = median;
            this.byKind = byKind;
        }

        public Profile(String floor, double median) { this(floor, median, false); }

        public double roll(KRandom random, double multiplier) {
            return median * multiplier * (0.9 + random.nextDouble() * 0.25);
        }

        public double roll(KRandom random) { return roll(random, 1.0); }

        public double castBase(KRandom random, double multiplier) {
            return byKind ? median * multiplier * (0.7 + random.nextDouble() * 0.6) : roll(random, multiplier);
        }

        public double castBase(KRandom random) { return castBase(random, 1.0); }

        public double perMob(String kind, boolean miniboss, double maxHp, double hpScale, double base, KRandom random) {
            if (!byKind) return base;
            double raw = base * factor(kind) * (0.97 + random.nextDouble() * 0.06);
            if (!miniboss && !kind.equals("shadow_assassin")) return raw;
            double unscaled = hpScale > 0.0 ? maxHp / hpScale : maxHp;
            return Math.min(raw, 0.75 * unscaled * (0.95 + random.nextDouble() * 0.10));
        }
    }

    private static final Profile F7 = new Profile("F7", 36_000_000.0, true);
    private static final Profile M7 = new Profile("M7", 56_600_000.0);

    private static final Map<String, Double> FACTORS = new HashMap<>();

    static {
        FACTORS.put("super_tank_zombie", 0.06);
        FACTORS.put("angry_archaeologist", 0.015);
        FACTORS.put("lost_adventurer", 0.09);
        FACTORS.put("frozen_adventurer", 0.18);
        FACTORS.put("withermancer", 0.37);
        FACTORS.put("crypt_undead", 0.54);
        FACTORS.put("mimic", 0.62);
        FACTORS.put("prince", 0.6);
        FACTORS.put("crypt_souleater", 1.14);
        FACTORS.put("wither_miner", 1.4);
        FACTORS.put("skeleton_master", 1.9);
        FACTORS.put("crypt_lurker", 2.6);
        FACTORS.put("zombie_knight", 2.8);
        FACTORS.put("zombie_soldier", 1.45);
        FACTORS.put("sniper", 1.3);
    }

    public static double factor(String kind) {
        Double f = FACTORS.get(kind);
        return f != null ? f : 1.0;
    }

    public static Profile profile(String floor) { return floor.startsWith("M") ? M7 : F7; }

    public static int castsNeeded(double hp, double perCast) {
        if (hp <= 0.0) return 0;
        if (perCast <= 0.0) return Integer.MAX_VALUE;
        return (int) Math.ceil(hp / perCast);
    }

    public static class Hitbox {
        public final double x;
        public final double y;
        public final double z;
        public final double width;
        public final double height;

        public Hitbox(double x, double y, double z, double width, double height) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
        }

        public double minX() { return x - width / 2; }
        public double maxX() { return x + width / 2; }
        public double minZ() { return z - width / 2; }
        public double maxZ() { return z + width / 2; }
        public double minY() { return y; }
        public double maxY() { return y + height; }
    }

    public static Pair<Double, Double> size(String kind) {
        if (kind.equals("fels")) return Pair.of(0.6, 2.9);
        if (kind.startsWith("withermancer") || kind.contains("wither_skeleton")) return Pair.of(0.7, 2.4);
        if (kind.contains("skeleton") || kind.equals("super_archer") || kind.equals("sniper")) return Pair.of(0.6, 1.99);
        return Pair.of(0.6, 1.95);
    }

    public static double[] castBox(double ex, double ey, double ez) {
        return new double[]{ex - REACH, ey - REACH_DOWN, ez - REACH, ex + REACH, ey + REACH_UP, ez + REACH};
    }

    public static boolean touches(Hitbox h, double[] box) {
        return h.maxX() >= box[0] && h.minX() <= box[3] && h.maxY() >= box[1] && h.minY() <= box[4] && h.maxZ() >= box[2] && h.minZ() <= box[5];
    }

    public static class Candidate {
        public final UUID id;
        public final Hitbox hitbox;
        public final boolean alive;
        public final boolean untargetable;

        public Candidate(UUID id, Hitbox hitbox, boolean alive, boolean untargetable) {
            this.id = id;
            this.hitbox = hitbox;
            this.alive = alive;
            this.untargetable = untargetable;
        }
    }

    public static List<UUID> select(double[] box, Collection<Candidate> mobs) {
        List<UUID> out = new ArrayList<>();
        for (Candidate c : mobs) if (c.alive && !c.untargetable && touches(c.hitbox, box)) out.add(c.id);
        return out;
    }

    public static String line(int count, double total) {
        String shown = String.format(Locale.ROOT, "%,.1f", total);
        if (shown.endsWith(".0")) shown = shown.substring(0, shown.length() - 2);
        return "§7Your Implosion hit §r§c" + count + " §r§7" + (count == 1 ? "enemy" : "enemies") + " for §r§c" + shown + " §r§7damage.";
    }
}
