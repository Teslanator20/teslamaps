package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/ClassMilestones.kt

import java.util.Locale;

public final class ClassMilestones {
    private ClassMilestones() {}

    private static final long[] STANDARD = {750_000L, 3_750_000L, 15_000_000L, 37_500_000L, 75_000_000L, 112_500_000L, 150_000_000L, 187_500_000L, 225_000_000L};
    private static final long[] TANK = {450_000L, 2_250_000L, 9_000_000L, 22_500_000L, 45_000_000L, 67_500_000L, 90_000_000L, 112_500_000L, 135_000_000L};

    private static final String NUMERALS = "❶❷❸❹❺❻❼❽❾";

    public static long[] thresholds(String dungeonClass) {
        if (dungeonClass == null) return null;
        return switch (dungeonClass.toLowerCase(Locale.ROOT)) {
            case "mage", "berserk", "berserker", "archer" -> STANDARD;
            case "tank" -> TANK;
            default -> null;
        };
    }

    public static int reached(long[] table, double total) {
        int n = 0;
        for (long v : table) if (total >= v) n++;
        return n;
    }

    public static String clock(int seconds) {
        return seconds < 60 ? String.format(Locale.ROOT, "%02ds", seconds) : String.format(Locale.ROOT, "%02dm %02ds", seconds / 60, seconds % 60);
    }

    public static String line(String dungeonClass, int index, long[] table, int seconds) {
        String cls = dungeonClass.toLowerCase(Locale.ROOT);
        String name = cls.equals("berserker") ? "Berserk" : (cls.isEmpty() ? cls : cls.substring(0, 1).toUpperCase(Locale.ROOT) + cls.substring(1));
        String what = cls.equals("tank") ? "You have tanked and dealt" : "You have dealt";
        String kind = cls.equals("archer") ? "Ranged Damage" : "Total Damage";
        String amount = String.format(Locale.ROOT, "%,d", table[index]);
        return "§e§l" + name + " Milestone §r§e" + NUMERALS.charAt(index) + "§r§7: " + what + " §r§c" + amount + "§r§7 " + kind + " so far! §r§a" + clock(seconds);
    }
}
