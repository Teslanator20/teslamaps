package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/ClassKit.kt

import java.util.List;
import java.util.Locale;

public final class ClassKit {
    private ClassKit() {}

    public static class Kit {
        public final String cls;
        public final String ability;
        public final int abilityCd;
        public final String ultimate;
        public final int ultimateCd;

        public Kit(String cls, String ability, int abilityCd, String ultimate, int ultimateCd) {
            this.cls = cls;
            this.ability = ability;
            this.abilityCd = abilityCd;
            this.ultimate = ultimate;
            this.ultimateCd = ultimateCd;
        }
    }

    public static final int ULT_FIRST_READY = 430;
    public static final int ABILITY_FIRST_READY = 411;
    public static final int ULT_REMINDER = 607;

    private static final List<Kit> KITS = List.of(
            new Kit("Healer", "Healing Circle", 40, "Wish", 2400),
            new Kit("Mage", "Guided Sheep", 320, "Thunderstorm", 2600),
            new Kit("Berserk", "Throwing Axe", 214, "Ragnarok", 1200),
            new Kit("Archer", "Explosive Shot", 446, "Rapid Fire", 2000),
            new Kit("Tank", "Seismic Wave", 300, "Castle of Stone", 3000)
    );

    public static Kit of(String dungeonClass) {
        if (dungeonClass == null) return null;
        String c = dungeonClass.toLowerCase(Locale.ROOT);
        if (c.equals("berserker")) c = "berserk";
        for (Kit k : KITS) if (k.cls.toLowerCase(Locale.ROOT).equals(c)) return k;
        return null;
    }

    public static int ultimateCd(Kit kit, boolean doubled) {
        return kit.ultimate.equals("Thunderstorm") ? (doubled ? 2600 : 5100) : kit.ultimateCd;
    }

    public static String used(String name) { return "§aUsed §r§6" + name + "§r§a!"; }

    public static String abilityAvailable(String name) { return "§6" + name + " §r§ais now available!"; }

    public static String ultimateReady(String name) { return "§6" + name + "§r§a is ready to use! Press §r§6§lDROP§r§a to activate it!"; }

    public static String ultimateRecharged(String cls, String name) { return "§aYour " + cls + " §r§e§lULTIMATE§r§a §r§6" + name + " §r§ais now available!"; }

    public static String abilityCooling(int ticksLeft) { return "§cYour Regular Ability is currently on cooldown for " + secondsLeft(ticksLeft) + " more seconds."; }

    public static String ultimateCooling(int ticksLeft) { return "§cYour Ultimate is currently on cooldown for " + secondsLeft(ticksLeft) + " more seconds."; }

    public static String hitLine(String name, int n, double damage) {
        String d = String.format(Locale.ROOT, "%,.1f", damage);
        if (d.endsWith(".0")) d = d.substring(0, d.length() - 2);
        return "§7Your " + name + " hit §r§c" + n + " §r§7" + (n == 1 ? "enemy" : "enemies") + " for §r§c" + d + " §r§7damage.";
    }

    public static String wishSelf(int healed, int shield) {
        return "§eYour §r§6Wish §r§ehealed your entire team for §r§a" + String.format(Locale.ROOT, "%,d", healed)
                + " §r§ehealth and shielded them for §r§a" + String.format(Locale.ROOT, "%,d", shield) + "§r§e!";
    }

    private static int secondsLeft(int ticks) { return Math.max(1, (ticks + 19) / 20); }
}
