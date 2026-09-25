package com.teslamaps.features;

// Ported from SkyHanni's DungeonBossMessages.kt + DungeonChatFilter.kt (patterns rewritten for stripped text)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class DungeonChatFilter {

    private DungeonChatFilter() {}

    private static final String[] BOSSES = {"The Watcher", "Bonzo", "Scarf", "The Professor", "Professor", "Livid",
            "Enderman", "Thorn", "Sadan", "Maxor", "Storm", "Goldor", "Necron", "Wither King"};
    private static final Set<String> BOSS_LINES = Set.of(
            "The Crystal withers your soul as you hold it in your hands!",
            "It doesn't seem like that is supposed to go there.");
    private static final String KEEP = "[BOSS] The Watcher: You have proven yourself. You may pass.";

    private static final List<Pattern> PREPARE = ps(
            ".+ has started the dungeon countdown\\. The dungeon will begin in 1 minute\\.",
            "(?:\\[[^]]+] )?\\w{1,16} is now ready!",
            "Dungeon starts in \\d+ seconds?\\.",
            "\\[(?:Healer|Mage|Berserk|Archer|Tank)] .+ -> .+");
    private static final Set<String> PREPARE_LINES = Set.of(
            "[NPC] Mort: Talk to me to change your class and ready up.",
            "[NPC] Mort: Here, I found this map when I first entered the dungeon.",
            "[NPC] Mort: You should find it useful if you get lost.",
            "[NPC] Mort: Good luck.",
            "You can no longer consume or splash any potions during the remainder of this Dungeon run!");

    private static final Set<String> NOT_POSSIBLE = Set.of(
            "You cannot hit the silverfish while it's moving!",
            "You cannot move the silverfish in that direction!",
            "There are blocks in the way!",
            "This chest has already been searched!",
            "This lever has already been used.",
            "You cannot do that in this room!",
            "You do not have the key for this door!",
            "You have already opened this dungeon chest!",
            "You cannot use abilities in this room!",
            "A mystical force in this room prevents you from using that ability!");

    private static final List<Pattern> DAMAGE = ps(
            ".+ used .+ on you!",
            "The .+ struck you for .+ damage!",
            "The .+ hit you for .+ damage!",
            ".+ struck you for .+ damage\\.",
            ".+ hit you for [\\d,.]+ (?:true )?damage\\.",
            ".+ exploded, hitting you for .+ damage\\.",
            ".+ hit you with .+ for .+ damage!",
            ".+ struck you for .+!",
            "The Mage's Magma burnt you for .+ true damage\\.",
            "Mute silenced you!");

    private static final List<Pattern> ABILITY = ps(
            "Your Guided Sheep hit .+ enem(?:y|ies) for .+ damage\\.",
            "BUFF! You were splashed by .+ with Healing VIII!",
            "You were healed for .+ health by .+!",
            "You gained .+ HP worth of absorption for 3s from .+!",
            ".+ picked up your .+ Orb!",
            "This ability is on cooldown for .+s\\.",
            ".+ healed you for .+ health!",
            ".+ formed a tether with you!",
            "Your tether with .+ healed you for .+ health\\.",
            "Your Implosion hit .+ enem(?:y|ies) for .+ damage\\.",
            "Your Spirit Pet healed .+ for .+ health!",
            "Your Spirit Pet hit .+ enem(?:y|ies) for .+ damage\\.",
            "You need at least .+ mana to activate this!",
            ".+ granted you .+ strength for 20 seconds!",
            "Your fairy healed .+ for .+ health!",
            ".+ fairy healed you for .+ health!",
            "Guided Sheep is now available!",
            "Creeper Veil (?:Activated|De-activated)!",
            "(?:Rapid Fire|Castle of Stone|Ragnarok|Thunderstorm) is ready to use! Press DROP to activate it!");

    private static final List<Pattern> END = ps(
            ".+ unlocked .+ Essence x\\d+!",
            "[A-Z][a-z]+ Essence x\\d+");

    public static void register() {
        rule("dungeonFilterBoss", c -> c.dungeonFilterBoss, DungeonChatFilter::boss);
        rule("dungeonFilterPrepare", c -> c.dungeonFilterPrepare,
                t -> PREPARE_LINES.contains(t) || t.startsWith("Your active Potion Effects have been paused") || any(PREPARE, t));
        rule("dungeonFilterAmbience", c -> c.dungeonFilterAmbience, t -> t.equals("A shiver runs down your spine..."));
        rule("dungeonFilterReminder", c -> c.dungeonFilterReminder,
                t -> t.startsWith("RIGHT CLICK on ") && t.endsWith("This key can only be used to open 1 door!"));
        rule("dungeonFilterBuff", c -> c.dungeonFilterBuff, t -> t.startsWith("BUFF! You have gained ")
                || t.startsWith("DUNGEON BUFF! ") || t.startsWith("Granted you ") || t.startsWith("Also granted you ")
                || t.startsWith("Grants you "));
        rule("dungeonFilterNotPossible", c -> c.dungeonFilterNotPossible, NOT_POSSIBLE::contains);
        rule("dungeonFilterDamage", c -> c.dungeonFilterDamage, t -> t.contains("you") && any(DAMAGE, t)
                || t.equals("Mute silenced you!"));
        rule("dungeonFilterAbility", c -> c.dungeonFilterAbility, t -> any(ABILITY, t));
        rule("dungeonFilterPuzzle", c -> c.dungeonFilterPuzzle, t -> t.startsWith("PUZZLE SOLVED! ")
                || t.startsWith("[STATUE] Oruo the Omniscient: "));
        rule("dungeonFilterEnd", c -> c.dungeonFilterEnd, t -> t.endsWith(" Experience (Team Bonus)")
                || (t.contains("Essence x") && any(END, t)));

        Feature.of("DungeonChatFilter")
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Dungeon Filter (Full)");
                    b.toggle("Full Dungeon Chat Filter", () -> c.dungeonFilterFull, v -> c.dungeonFilterFull = v)
                            .desc("Extra dungeon categories below, only inside dungeons. Uses the Dungeon filter action; needs the chat filter on.");
                    b.toggle("  Boss Dialog", () -> c.dungeonFilterBoss, v -> c.dungeonFilterBoss = v)
                            .desc("Every floor boss line (Watcher to Wither King) and the M7 crystal lines. \"You may pass\" stays.");
                    b.toggle("  Prepare / Start", () -> c.dungeonFilterPrepare, v -> c.dungeonFilterPrepare = v)
                            .desc("Countdown, ready up, Mort, potion pause, class stat lines");
                    b.toggle("  Reminders", () -> c.dungeonFilterReminder, v -> c.dungeonFilterReminder = v)
                            .desc("\"RIGHT CLICK on the door to open it\" key hints");
                    b.toggle("  Buffs", () -> c.dungeonFilterBuff, v -> c.dungeonFilterBuff = v)
                            .desc("DUNGEON BUFF, Healing V and the \"Granted you\" lines");
                    b.toggle("  Not Possible", () -> c.dungeonFilterNotPossible, v -> c.dungeonFilterNotPossible = v)
                            .desc("Already searched, lever used, cannot use abilities in this room, ...");
                    b.toggle("  Damage Taken", () -> c.dungeonFilterDamage, v -> c.dungeonFilterDamage = v)
                            .desc("\"X hit you for N damage\" and similar");
                    b.toggle("  Abilities", () -> c.dungeonFilterAbility, v -> c.dungeonFilterAbility = v)
                            .desc("Heals, tether, Spirit Pet, orbs, cooldown and mana lines, ult ready");
                    b.toggle("  Puzzles", () -> c.dungeonFilterPuzzle, v -> c.dungeonFilterPuzzle = v)
                            .desc("PUZZLE SOLVED and Oruo lines");
                    b.toggle("  Run End", () -> c.dungeonFilterEnd, v -> c.dungeonFilterEnd = v)
                            .desc("Essence unlocked and Team Bonus XP lines");
                    b.toggle("  Ambience", () -> c.dungeonFilterAmbience, v -> c.dungeonFilterAmbience = v)
                            .desc("\"A shiver runs down your spine...\"");
                });
    }

    private static void rule(String id, Predicate<TeslaMapsConfig> on, Predicate<String> match) {
        ChatFilter.RULES.add(new ChatFilter.Rule(id, ChatFilter.G_DUNGEON, true, false,
                c -> c.dungeonFilterFull && on.test(c), t -> DungeonManager.isInDungeon() && match.test(t.trim())));
    }

    private static boolean boss(String t) {
        if (BOSS_LINES.contains(t)) return true;
        if (!t.startsWith("[BOSS] ") || t.equals(KEEP)) return false;
        int colon = t.indexOf(": ");
        if (colon < 0) return false;
        String name = t.substring(7, colon);
        for (String b : BOSSES) if (name.endsWith(b)) return true;
        return false;
    }

    private static boolean any(List<Pattern> ps, String t) {
        for (Pattern p : ps) if (p.matcher(t).matches()) return true;
        return false;
    }

    private static List<Pattern> ps(String... regex) {
        Pattern[] out = new Pattern[regex.length];
        for (int i = 0; i < regex.length; i++) out[i] = Pattern.compile(regex[i]);
        return List.of(out);
    }
}
