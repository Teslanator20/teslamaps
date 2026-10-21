// Ported from ADM's p3sim/clear/state/StateText.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StateText {
    private StateText() {}

    public static final Cue COUNTDOWN_TICK = new Cue("block.lever.click", 0.55f, 2.0f);
    public static final Cue MORT_LINE = new Cue("entity.villager.trade", 1.0f, 0.698f);
    public static final Cue DOOR_OPENED = new Cue("block.note_block.pling", 8.0f, 4.048f, CueSource.BLOCKS);
    public static final Cue KEY_OBTAINED = new Cue("entity.experience_orb.pickup", 1.0f, 1.492f, CueSource.PLAYERS);
    public static final Cue KEY_SHUTTER = new Cue("block.vault.open_shutter", 2.0f, 1.0f, CueSource.PLAYERS);
    public static final Cue BLOOD_DOOR = new Cue("entity.ghast.hurt", 2.0f, 0.492f, CueSource.HOSTILE);
    public static final Cue NO_KEY = new Cue("entity.enderman.teleport", 8.0f, 0.0f, CueSource.HOSTILE);

    public static String ready(String name, String colour) { return colour + name + "§r§a is now ready!"; }

    public static String ready(String name) { return ready(name, "§b"); }

    public static String notReady(String name, String colour) { return colour + name + "§r§c is no longer ready!"; }

    public static String notReady(String name) { return notReady(name, "§b"); }

    public static String selectedClass(String name, String colour, String cls) { return colour + name + "§r§a selected the " + cls + " Class!"; }

    public static String startingIn(int seconds) { return "§e§lStarting in " + seconds + " " + (seconds == 1 ? "second" : "seconds") + "."; }

    public static final List<String> mortLines = List.of(
            "Here, I found this map when I first entered the dungeon.",
            "You should find it useful if you get lost.",
            "Good luck."
    );

    public static List<String> classDoubled(String dungeonClass) {
        String cls = null;
        for (String c : CLASS_NAMES) if (c.equalsIgnoreCase(dungeonClass)) { cls = c; break; }
        if (cls == null) return null;
        List<Triple<String, String, String>> stats = DOUBLED_STATS.get(cls);
        if (stats == null) return null;
        List<String> out = new ArrayList<>();
        out.add("§6Your §r§a" + cls + " §r§6stats are doubled because you are the only player using this class!");
        for (Triple<String, String, String> s : stats) out.add("§a[" + cls + "] §r§f" + s.first + " §r§c" + s.second + "§r§f -> §r§a" + s.third);
        return out;
    }

    private static final List<String> CLASS_NAMES = List.of("Healer", "Berserk", "Archer", "Tank", "Mage");

    private static final Map<String, List<Triple<String, String, String>>> DOUBLED_STATS = new LinkedHashMap<>();

    static {
        DOUBLED_STATS.put("Mage", List.of(Triple.of("Intelligence", "490", "740"), Triple.of("Ability Damage", "14", "19"), Triple.of("Cooldown Reduction", "49%", "74%")));
        DOUBLED_STATS.put("Archer", List.of(Triple.of("Extra Arrow Chance", "97%", "147%"), Triple.of("Arrow Damage", "225.2%", "275.2%")));
        DOUBLED_STATS.put("Healer", List.of(
                Triple.of("Healing Aura Healing", "1.8%", "2.8%"), Triple.of("Ghost Healing Aura Healing", "20.5%", "21%"), Triple.of("Revive Cooldown", "59", "49"),
                Triple.of("Overheal Maximum Shield", "36.4%", "56.4%"), Triple.of("Vitality", "18.2", "28.2"), Triple.of("Mending", "30.5", "40.5"), Triple.of("Healing Aura Range", "8", "16")
        ));
        DOUBLED_STATS.put("Berserk", List.of(
                Triple.of("Melee Damage", "76%", "116%"), Triple.of("Walk Speed", "49.2", "79.2"), Triple.of("Bloodlust Damage", "56%", "76%"),
                Triple.of("Lust For Blood Damage Increase Cap", "880%", "1,130%"), Triple.of("Lust For Blood Damage Increase Per Hit", "159%", "174%"),
                Triple.of("Indomitable Strength to Defense", "9.8%", "14.8%"), Triple.of("Weapon Master Swing Range Increase", "4.9", "5.4"),
                Triple.of("Bloodlust Heal Percent", "3%", "6%"), Triple.of("Bloodlust Duration", "5", "10")
        ));
        DOUBLED_STATS.put("Tank", List.of(
                Triple.of("Defense", "101", "151"), Triple.of("Total Defense", "35%", "60%"), Triple.of("Absorption Health", "700", "900"), Triple.of("Vitality", "15.1", "25.1"),
                Triple.of("Absorption Shield Health Required", "50%", "100%"), Triple.of("Absorption Shield Percentage", "10%", "20%"), Triple.of("Barrier Cooldown", "90", "45")
        ));
    }

    public static String mort(int index) { return "§e[NPC] §bMort§f: §r" + mortLines.get(index); }

    public static String keyName(KeyKind kind) { return kind == KeyKind.WITHER ? "Wither Key" : "Blood Key"; }

    public static final String KEY_RANK_PREFIX = "§b[MVP§c+§b]";

    public static String keyObtained(KeyKind kind, String name, String bracket) {
        return bracket + " " + name + "§f §ehas obtained " + (kind == KeyKind.WITHER ? "§8" : "§c") + keyName(kind) + "§e!";
    }

    public static String keyObtained(KeyKind kind, String name) { return keyObtained(kind, name, KEY_RANK_PREFIX); }

    public static String rightClick(KeyKind kind) {
        return kind == KeyKind.WITHER
                ? "§e§lRIGHT CLICK §7on a §8WITHER §7door to open it. This key can only be used to open §a1§7 door!"
                : "§e§lRIGHT CLICK §7on the §cBLOOD DOOR§7 to open it. This key can only be used to open §a1§7 door!";
    }

    public static String witherOpened(String name, String colour) { return colour + name + " §aopened a §8§lWITHER §r§adoor!"; }

    public static String witherOpened(String name) { return witherOpened(name, "§b"); }

    public static final String BLOOD_OPENED = "§cThe §c§lBLOOD DOOR§r§c has been opened!";
    public static final String SHIVER = "§5A shiver runs down your spine...";
    public static final String NO_KEY_LINE = "§cYou do not have the key for this door!";

    public static String plain(String s) { return s.replaceAll("§.", ""); }
}
