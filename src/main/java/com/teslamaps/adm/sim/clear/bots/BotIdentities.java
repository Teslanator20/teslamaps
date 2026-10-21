// Ported from ADM's p3sim/clear/bots/BotIdentity.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Draws teammate identities from the recorded spread. The names are invented from two word lists. */
public final class BotIdentities {
    private BotIdentities() {}

    private static final List<Pair<Double, Double>> LEVEL = PlanMath.pts(0.0, 40.0, 0.05, 93.0, 0.25, 255.0, 0.5, 326.0, 0.75, 502.0, 0.95, 540.0, 1.0, 571.0);
    private static final List<Pair<Double, Double>> CLASS_LEVEL = PlanMath.pts(0.0, 30.0, 0.05, 34.0, 0.25, 41.0, 0.5, 48.0, 0.6, 50.0, 1.0, 50.0);

    /** Glyphs after a name and how often they occur (the rest of the share is none). */
    private static final List<Pair<String, Double>> GLYPHS = List.of(
            Pair.of("§6➶", 0.19), Pair.of("§7✿", 0.06), Pair.of("§7ᛃ", 0.04), Pair.of("§b§l⚛", 0.04), Pair.of("§6§l⚛", 0.03), Pair.of("§7❤", 0.02),
            Pair.of("§6⚔", 0.02), Pair.of("§7✨", 0.02), Pair.of("§7☠", 0.02));

    /** The level colour: one band per 40 levels. */
    public static String levelColour(int level) {
        if (level < 40) return "§7";
        if (level < 80) return "§f";
        if (level < 120) return "§e";
        if (level < 160) return "§a";
        if (level < 200) return "§2";
        if (level < 240) return "§b";
        if (level < 280) return "§3";
        if (level < 320) return "§9";
        if (level < 360) return "§d";
        if (level < 400) return "§5";
        if (level < 440) return "§6";
        if (level < 480) return "§c";
        return "§4";
    }

    public static final List<String> PREFIXES = List.of(
            "Amber", "Ash", "Birch", "Bramble", "Cinder", "Clover", "Copper", "Dawn", "Drift", "Dusk", "Ember", "Fern", "Flint", "Frost", "Gale", "Glimmer",
            "Hazel", "Indigo", "Juniper", "Larch", "Lumen", "Marsh", "Mistral", "Moss", "Nimbus", "Onyx", "Pebble", "Quartz", "Russet", "Saffron", "Sable",
            "Thistle", "Tundra", "Umber", "Velvet", "Willow", "Wisp", "Yarrow", "Zephyr", "Maple", "Coral", "Granite", "Harbor", "Lantern", "Meadow", "Prairie"
    );
    public static final List<String> SUFFIXES = List.of(
            "fox", "wolf", "hawk", "otter", "moth", "finch", "stag", "badger", "lynx", "wren", "newt", "hare", "crane", "viper", "bison", "gecko", "mantis",
            "puma", "seal", "swan", "toad", "vole", "lark", "ibis", "kite", "mole", "owl", "pike", "shrew", "tern", "wasp", "drake", "heron", "marten", "pika",
            "raven", "quail", "stoat", "tapir", "egret", "falcon", "gull", "lemur", "magpie", "osprey", "plover"
    );

    private static String cap(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1); }

    /** One name that is 3 to 16 letters, digits and underscores. */
    public static String name(KRandom random) {
        String a = PREFIXES.get(random.nextInt(PREFIXES.size()));
        String b = SUFFIXES.get(random.nextInt(SUFFIXES.size()));
        double pick = random.nextDouble();
        String raw;
        if (pick < 0.40) raw = a + cap(b);
        else if (pick < 0.60) raw = (a + b).toLowerCase(Locale.ROOT) + random.nextInt(2, 100);
        else if (pick < 0.75) raw = a + cap(b) + random.nextInt(1, 100);
        else if (pick < 0.85) raw = a + "_" + cap(b);
        else raw = a + random.nextInt(2, 1000);
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    /** A version 4 (random) profile id drawn from [random]. */
    public static UUID uuid(KRandom random) {
        long msb = (random.nextLong() & -0xf001L) | 0x4000L;
        long lsb = (random.nextLong() & 0x3fffffffffffffffL) | Long.MIN_VALUE;
        return new UUID(msb, lsb);
    }

    /** One identity; [taken] are lower-case names already in the party. */
    public static BotIdentity draw(KRandom random, Set<String> taken) {
        String name = name(random);
        int guard = 0;
        while (taken.contains(name.toLowerCase(Locale.ROOT)) && guard++ < 50) name = name(random);
        BotRank rank = pickRank(random.nextDouble());
        int level = Math.clamp((long) (int) PlanMath.curve(LEVEL, random.nextDouble()), 40, 571);
        int cls = Math.clamp((long) (int) PlanMath.curve(CLASS_LEVEL, random.nextDouble()), 30, 50);
        return new BotIdentity(name, rank, level, cls, pickGlyph(random.nextDouble()), uuid(random));
    }

    public static BotRank pickRank(double u) {
        double x = u;
        for (BotRank r : BotRank.values()) { if (x < r.share) return r; x -= r.share; }
        return BotRank.MVP_PLUS;
    }

    public static String pickGlyph(double u) {
        double x = u;
        for (Pair<String, Double> g : GLYPHS) { if (x < g.second) return g.first; x -= g.second; }
        return null;
    }

    /** The old look: the class word as the name, with the rank and levels of the commonest account. */
    public static BotIdentity classWord(String cls, UUID uuid, int sbLevel, int classLevel) {
        return new BotIdentity(BotRoster.label(cls), BotRank.MVP_PLUS, sbLevel, classLevel, null, uuid);
    }
}
