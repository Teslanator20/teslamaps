// Ported from ADM's p3sim/clear/blood/camp/CampMobs.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CampMobs {
    private CampMobs() {}

    public static final List<Pair<String, Integer>> MODIFIER_WEIGHTS = List.of(
            Pair.of("Speedy", 316), Pair.of("Healthy", 306), Pair.of("Stormy", 201), Pair.of("Stealth", 156), Pair.of("Golden", 150), Pair.of("Boomer", 141));

    public static final double STANDARD_HP_LOW = 248_000.0;
    public static final double STANDARD_HP_HIGH = 256_000.0;

    public static final double HEALTHY = 1.3;
    public static final double OOZE = 1.25;
    public static final double LADDER = 1.3;
    public static final double GIANT_HP = 15_000_000.0;
    public static final double BOSS_HP = 10_000_000.0;

    /** UNMEASURED ladder rung weights. */
    public static final double[] LADDER_WEIGHTS = {0.30, 0.30, 0.20, 0.12, 0.08};

    /** Tag icon prefixes (private use glyphs). */
    public static final String ICON_STANDARD = "";
    public static final String ICON_GIANT = "";
    public static final String ICON_BONZO_SCARF = "";
    public static final String ICON_SPIRIT_BEAR = "";

    public static final String PROFILE = "Undead";
    public static final double SPEED = 0.31;

    public static final String EQUIPMENT = "MAINHAND:stone_sword,FEET:chainmail_boots,LEGS:chainmail_leggings,CHEST:chainmail_chestplate";

    public static final int DEATH_DELAY_TICKS = 21;

    public static final double SPAWN_RISE_LOW = 5.5;
    public static final double SPAWN_RISE_HIGH = 7.6;

    public static String modifier(KRandom rng) {
        int total = 0;
        for (Pair<String, Integer> p : MODIFIER_WEIGHTS) total += p.second;
        int x = rng.nextInt(total);
        for (Pair<String, Integer> p : MODIFIER_WEIGHTS) {
            if (x < p.second) return p.first;
            x -= p.second;
        }
        return MODIFIER_WEIGHTS.get(MODIFIER_WEIGHTS.size() - 1).first;
    }

    private static int rung(KRandom rng) {
        double sum = 0.0;
        for (double w : LADDER_WEIGHTS) sum += w;
        double x = rng.nextDouble() * sum;
        for (int k = 0; k < LADDER_WEIGHTS.length; k++) {
            if (x < LADDER_WEIGHTS[k]) return k;
            x -= LADDER_WEIGHTS[k];
        }
        return LADDER_WEIGHTS.length - 1;
    }

    private static double pow(int k) {
        double v = 1.0;
        for (int i = 0; i < k; i++) v *= LADDER;
        return v;
    }

    /** Rolls the mob a wall head makes. */
    public static CampMob roll(WallStation station, KRandom rng) {
        String name = station.cls == WallClass.GIANT ? "Giant" : station.mobName;
        String mod = station.cls == WallClass.WANDERING_SOUL ? "Boomer" : modifier(rng);
        double healthy = mod.equals("Healthy") ? HEALTHY : 1.0;
        double hp;
        String head;
        switch (station.cls) {
            case GIANT -> { hp = GIANT_HP * pow(rung(rng)) * healthy; head = ICON_GIANT; }
            case BOSS -> {
                hp = BOSS_HP * pow(rung(rng)) * healthy;
                head = switch (name) {
                    case "Bonzo", "Scarf" -> ICON_BONZO_SCARF;
                    case "Spirit Bear" -> ICON_SPIRIT_BEAR;
                    default -> ICON_STANDARD;
                };
            }
            default -> {
                double base = 1000.0 * (STANDARD_HP_LOW / 1000 + rng.nextInt((int) ((STANDARD_HP_HIGH - STANDARD_HP_LOW) / 1000) + 1));
                hp = Math.round(base * healthy * (name.equals("Ooze") ? OOZE : 1.0) / 1000.0) * 1000.0;
                head = ICON_STANDARD;
            }
        }
        return new CampMob(
                station.cls == WallClass.GIANT ? "giant" : "player",
                name, mod, (double) Math.round(hp), head,
                station.cls != WallClass.WANDERING_SOUL);
    }

    /** The extras handed to the mob service with the spawn. */
    public static Map<String, String> extras(CampMob m, String landing) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("camp", "blood"); out.put("landing", landing);
        out.put("name", m.name);
        out.put("entity", m.entity);
        if (m.modifier != null) out.put("modifier", m.modifier);
        out.put("displayName", m.name);
        out.put("tagHead", m.tagHead); out.put("tagNameColor", "red");
        out.put("speed", Double.toString(SPEED));
        out.put("equipment", EQUIPMENT);
        out.put("deathDelay", Integer.toString(DEATH_DELAY_TICKS));
        out.put("targetable", "true");
        out.put("counted", Boolean.toString(m.counted));
        if (m.entity.equals("player")) { out.put("disguise", "true"); out.put("uuidVersion", "2"); out.put("profile", PROFILE); }
        return out;
    }
}
