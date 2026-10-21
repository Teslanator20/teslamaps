// Ported from ADM's p3sim/clear/secrets/Blessings.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.clear.damage.ClassMilestones;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;
import java.util.Locale;

public final class Blessings {
    private Blessings() {}

    public static final int OWN_LEVEL = 5;
    public static final List<Pair<Integer, Integer>> CHEST_LEVEL_WEIGHTS = List.of(Pair.of(1, 46), Pair.of(2, 30), Pair.of(5, 24));
    private static final List<Pair<Blessing, Integer>> CHEST_WEIGHTS = List.of(Pair.of(Blessing.LIFE, 355), Pair.of(Blessing.POWER, 277),
            Pair.of(Blessing.STONE, 204), Pair.of(Blessing.WISDOM, 145), Pair.of(Blessing.TIME, 19));
    public static final List<Pair<Integer, Integer>> OBSERVER_LEVEL_WEIGHTS = List.of(Pair.of(1, 375), Pair.of(2, 271), Pair.of(5, 354));
    public static final double OBSERVER_TIMED_SHARE_OF_V = 0.525;
    public static final double BOOST = 1.25;
    public static final double FLAT_PER_LEVEL = 5.8;
    public static final double MULT_PER_LEVEL = 0.03;
    public static final double BOOSTED_MULT_PER_LEVEL = 0.036;
    public static final double LIFE_MULT_PER_LEVEL = 0.0436;
    public static final double WISDOM_SPEED_PER_LEVEL = 4.8;
    public static final double STONE_DAMAGE_PER_LEVEL = 7.2;

    // Hypixel's private-use stat glyphs, as recorded.
    private static final String G_STRENGTH = "";
    private static final String G_CRIT_DAMAGE = "";
    private static final String G_INTELLIGENCE = "";
    private static final String G_SPEED = "";
    private static final String G_REGEN = "";
    private static final String G_DEFENSE = "";
    private static final String G_DAMAGE = "";

    private static final List<Pair<Blessing, Integer>> WEIGHTS = List.of(Pair.of(Blessing.POWER, 31), Pair.of(Blessing.WISDOM, 27),
            Pair.of(Blessing.LIFE, 26), Pair.of(Blessing.STONE, 16));

    public static Blessing pick(KRandom random) { return draw(WEIGHTS, random, Blessing.POWER); }

    public static Blessing pickChest(KRandom random) { return draw(CHEST_WEIGHTS, random, Blessing.LIFE); }

    private static final List<Pair<Blessing, Integer>> KILL_WEIGHTS = List.of(Pair.of(Blessing.POWER, 402), Pair.of(Blessing.LIFE, 370),
            Pair.of(Blessing.WISDOM, 252), Pair.of(Blessing.STONE, 154));
    public static final double KILL_PICKED_UP_SHARE = 0.61;

    public static Blessing pickKill(KRandom random) { return draw(KILL_WEIGHTS, random, Blessing.POWER); }

    private static <T> int sum(List<Pair<T, Integer>> weights) {
        int s = 0;
        for (Pair<T, Integer> p : weights) s += p.second;
        return s;
    }

    public static int chestLevel(Blessing b, KRandom random) {
        int n = random.nextInt(sum(CHEST_LEVEL_WEIGHTS));
        for (Pair<Integer, Integer> p : CHEST_LEVEL_WEIGHTS) { n -= p.second; if (n < 0) return levelFor(b, p.first); }
        return levelFor(b, 5);
    }

    private static <T> T draw(List<Pair<T, Integer>> weights, KRandom random, T fallback) {
        int n = random.nextInt(sum(weights));
        for (Pair<T, Integer> p : weights) { n -= p.second; if (n < 0) return p.first; }
        return fallback;
    }

    public static int levelFor(Blessing b, int wanted) { return b == Blessing.TIME ? 5 : Math.clamp(wanted, 1, 5); }

    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    public static String clock(int ticks) { return ClassMilestones.clock(seconds(ticks)); }

    public static int seconds(int ticks) { return Math.max((ticks + CLOCK_LEAD) / 20, 0); }
    public static final int CLOCK_LEAD = 2;

    public static int observerLevel(KRandom random) {
        int n = random.nextInt(sum(OBSERVER_LEVEL_WEIGHTS));
        for (Pair<Integer, Integer> p : OBSERVER_LEVEL_WEIGHTS) { n -= p.second; if (n < 0) return p.first; }
        return 5;
    }

    public static boolean observerTimed(KRandom random, int level) { return level == 5 && random.nextDouble() < OBSERVER_TIMED_SHARE_OF_V; }

    private static String label(Blessing b, int level) { return "§r§dBlessing of " + b.displayName + " " + roman(level) + "§r§f"; }

    public static String pickedUp(Blessing b) { return "§eA §r§dBlessing of " + b.displayName + "§r§e was picked up!"; }

    public static String obtained(String who, Blessing b) { return who + " §r§ehas obtained §r§dBlessing of " + b.displayName + "§r§e!"; }

    public static String announceOwn(Blessing b, int level, int ticks) {
        return "§6§lDUNGEON BUFF! §r§fA " + label(b, level) + " was found! (§r§a" + clock(ticks) + "§r§f)";
    }

    public static String announceYouFound(Blessing b, int level, Integer ticks) {
        return "§6§lDUNGEON BUFF! §r§fYou found a " + label(b, level) + "!" + (ticks != null ? " (§r§a" + clock(ticks) + "§r§f)" : "");
    }

    public static String announceOther(String name, Blessing b, int level) { return announceOther(name, b, level, "§b", null); }

    public static String announceOther(String name, Blessing b, int level, String rankColour) { return announceOther(name, b, level, rankColour, null); }

    public static String announceOther(String name, Blessing b, int level, String rankColour, Integer ticks) {
        return "§6§lDUNGEON BUFF! §r" + rankColour + name + " §r§ffound a " + label(b, level) + "!" + (ticks != null ? " (§r§a" + clock(ticks) + "§r§f)" : "");
    }

    private static String fmt(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static String mult(double m) { return String.format(Locale.ROOT, "%.2f", m); }

    private static String term(int level, String tail, double boost) {
        return "§r§a+" + fmt(FLAT_PER_LEVEL * level * boost) + "§r§7 & §r§a+" + mult(1.0 + (boost != 1.0 ? BOOSTED_MULT_PER_LEVEL : MULT_PER_LEVEL) * level) + "x " + tail;
    }

    public static List<String> grantLines(Blessing b, int level) { return grantLines(b, level, 1.0); }

    public static List<String> grantLines(Blessing b, int level, double boost) {
        String pre = "     §7Granted you ";
        return switch (b) {
            case POWER -> List.of(
                    pre + term(level, "§r§c" + G_STRENGTH + " Strength", boost) + "§r§7.",
                    "     §7Also granted you " + term(level, "§r§9" + G_CRIT_DAMAGE + " Crit Damage", boost) + "§r§7.");
            case LIFE -> {
                String m = mult(1.0 + LIFE_MULT_PER_LEVEL * level * boost);
                yield List.of(pre + "§r§a+" + m + "x HP §r§7and §r§a+" + m + "x §r§c" + G_REGEN + " Health Regen§r§7.");
            }
            case WISDOM -> List.of(pre + term(level, "§r§b" + G_INTELLIGENCE + " Intelligence", boost) + " §r§7and §r§a+" + fmt(WISDOM_SPEED_PER_LEVEL * level) + " §r§f" + G_SPEED + " Speed§r§7.");
            case STONE -> List.of(pre + term(level, G_DEFENSE + " Defense", boost) + " §r§7and §r§a+" + fmt(STONE_DAMAGE_PER_LEVEL * level) + " §r§c" + G_DAMAGE + " Damage§r§7.");
            case TIME -> List.of(
                    pre + term(level, "HP", boost) + "§r§7, " + term(level, G_DEFENSE + " Defense", boost) + "§r§7, " +
                            term(level, "§r§b" + G_INTELLIGENCE + " Intelligence", boost) + "§r§7, and " + term(level, "§r§c" + G_STRENGTH + " Strength", boost) + "§r§7.");
        };
    }

    public static double multiplier(Blessing b, int level) {
        return b == Blessing.LIFE ? 1.0 + LIFE_MULT_PER_LEVEL * level : 1.0 + MULT_PER_LEVEL * level;
    }
}
