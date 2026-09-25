package com.teslamaps.features;

// Ported from HypixelSimpleChat's rules/islands/Dungeons.kt, Foraging.kt, rules/lobby/*, rules/system/* and CompactRules.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.PlayerChat;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short line instead of the full one, original in the hover. "" = drop the line. */
public final class ChatCompactRulesIslands {

    private ChatCompactRulesIslands() {}

    private record R(Predicate<TeslaMapsConfig> on, String hint, Pattern p, BiFunction<Matcher, String, String> out) {}

    private static final List<R> RULES = new ArrayList<>();

    private static void dungeon(String hint, String regex, BiFunction<Matcher, String, String> out) {
        RULES.add(new R(c -> c.chatCompactDungeon && DungeonManager.isInDungeon(), hint, Pattern.compile(regex), out));
    }

    private static void lobby(String hint, String regex, BiFunction<Matcher, String, String> out) {
        RULES.add(new R(c -> c.chatCompactLobby, hint, Pattern.compile(regex), out));
    }

    private static void foraging(String hint, String regex, BiFunction<Matcher, String, String> out) {
        RULES.add(new R(c -> c.chatCompactForaging, hint, Pattern.compile(regex), out));
    }

    static {
        dungeon("Key was picked up", "^A (\\w+) Key was picked up!?$", (m, l) -> "§a+ " + keyColor(m.group(1)) + m.group(1) + " Key");
        dungeon("has obtained", "^(.+) has obtained (Wither|Blood) Key!$",
                (m, l) -> "§a+ " + keyColor(m.group(2)) + m.group(2) + " Key §7(" + span(l, m.group(1)) + "§7)");
        dungeon("has obtained", "^(.+) has obtained (Superboom TNT(?: x\\d)?|Revive Stone|Premium Flesh|Beating Heart)!$",
                (m, l) -> "§a+ " + color(l, m.group(2), "§9") + m.group(2) + " §7(" + span(l, m.group(1)) + "§7)");
        dungeon("has obtained Blessing", "^(.+) has obtained Blessing of (.+)!$",
                (m, l) -> "§d+ Blessing of " + m.group(2) + " §7(" + span(l, m.group(1)) + "§7)");
        dungeon("Blessing of", "^A Blessing of (.+) was picked up!$", (m, l) -> "§d+ Blessing of " + m.group(1));
        dungeon("DUNGEON BUFF!", "^DUNGEON BUFF! (.+)$", (m, l) -> "§dBuff §7· §f" + m.group(1));
        dungeon("ranted you", "^(?:Also )?[Gg]ranted you .+$", (m, l) -> "");
        dungeon("ranted you", "^[Gg]rants you .+$", (m, l) -> "");
        dungeon("door!", "^(.+) opened (?:a|an|the) (?:(\\w+) )?door!$",
                (m, l) -> "§7Door §8· " + span(l, m.group(1)) + (m.group(2) == null ? "" : " §8(" + color(l, m.group(2), "§7") + m.group(2) + "§8)"));
        dungeon("DOOR has been opened", "^The (.+) DOOR has been opened!$", (m, l) -> "§7Door §8· §c" + m.group(1));
        dungeon("PUZZLE SOLVED!", "^PUZZLE SOLVED! (.+)$", (m, l) -> "§aPuzzle ✔ §7" + m.group(1));
        dungeon("stats are doubled", "^Your (Healer|Mage|Berserk|Archer|Tank) stats are doubled because you are the only player using this class!$",
                (m, l) -> "§6" + m.group(1) + " stats doubled §7(solo)");

        lobby("joined the lobby!", "^(?:>+ )?(.+?) joined the lobby!.*$", (m, l) -> span(l, m.group(1)) + " §8joined");
        lobby("Activated your booster", "^Activated your booster\\..*?([0-9.]+)x coins",
                (m, l) -> "§eBooster §f" + m.group(1) + "x§e coins" + hours(m.group()));
        lobby("You tipped", "^You tipped (\\d+) players? in (\\d+)(?: different)? games?!$",
                (m, l) -> "§aTipped §f" + m.group(1) + " §7(" + m.group(2) + " games)");
        lobby("as interest", "^(?:You have just received|Since you've been away you earned) ([\\d,.]+) coins as interest in your (?:personal|co-op) bank account!$",
                (m, l) -> "§6+" + shortNum(m.group(1)) + " coins §7(interest)");
        lobby("Mystery Box!", "^(?:✦ )?.+ found an? (.+) in an? (?:Holiday )?Mystery Box!$", (m, l) -> "§7Mystery Box §8· " + span(l, m.group(1)));
        lobby("Mystery Box!", "^(?:✦ )?.+ found an? (.+) Mystery Box!$", (m, l) -> "§7Mystery Box §8· " + span(l, m.group(1)));
        lobby("Mystery Dust!", "^(?:✦ )?You earned ([\\d,]+) Mystery Dust!$", (m, l) -> "§7+" + m.group(1) + " Mystery Dust");
        lobby("Pet Consumables", "^(?:✦ )?You earned ([\\d,]+) Pet Consumables items!$", (m, l) -> "§7+" + m.group(1) + " Pet Consumables");
        lobby("Achievement Unlocked", "^Achievement Unlocked: (.+)$", (m, l) -> "§eAchievement §7· " + span(l, m.group(1)));
        lobby("Sending to server", "^Sending to server (.+?)\\.\\.\\.$", (m, l) -> "§8→ §7" + m.group(1));
        lobby("Warping you to your", "^Warping you to your SkyBlock island\\.\\.\\.$", (m, l) -> "§8→ §7Island");
        lobby("Queuing", "^Queuing\\.\\.\\. (.+)$", (m, l) -> "§8⌛ §7" + m.group(1));
        lobby("Watchdog has banned", "^Watchdog has banned ([\\d,.]+) players in the last 7 days\\.$", (m, l) -> "§cWatchdog §7· §f" + m.group(1) + " §7bans");
        lobby("Staff have banned", "^Staff have banned an additional ([\\d,.]+) players in the last 7 days\\.$", (m, l) -> "§cStaff §7· §f" + m.group(1) + " §7bans");
        lobby("WATCHDOG", "^\\[WATCHDOG ANNOUNCEMENT]$", (m, l) -> "");
        lobby("Blacklisted", "^Blacklisted modifications are a bannable offense!$", (m, l) -> "");
        lobby("SMP server", "^You can now create your own Hypixel SMP server!$", (m, l) -> "");
        lobby("tournament", "^HYPIXEL is hosting an? (.+) tournament!.*$", (m, l) -> "§6Tournament §7· §f" + m.group(1));
        lobby("playing on profile", "^You are playing on profile: (.+?)(?: \\(.+\\))?$", (m, l) -> "§7Profile §8· §a" + m.group(1));
        lobby("Profile ID", "^Profile ID: (.+)$", (m, l) -> "§8" + m.group(1));

        foraging("FLOOR DROP!", "^FLOOR DROP! You found (.+?)(?: x([\\d,]+))? on the ground!$",
                (m, l) -> "§6§lFLOOR DROP! §r" + span(l, m.group(1)) + (m.group(2) == null ? "" : " §7x" + m.group(2)));
        foraging("TIMBER!", "^TIMBER! .+ felled the entire .+!$", (m, l) -> color(l, "TIMBER!", "§c") + "§lTIMBER!");
        foraging("BEEHEEMOTH!", "^BEEHEEMOTH! A Beeheemoth has spawned at (.+)!$", (m, l) -> "§d§lBEEHEEMOTH! §a" + m.group(1));
        foraging("BEEHEEMOTH!", "^BEEHEEMOTH! The Beeheemoth is (starting to slow down|getting more fatigued|exhausted)",
                (m, l) -> "§d§lBEEHEEMOTH! " + switch (m.group(1)) {
                    case "starting to slow down" -> "§7slowing";
                    case "getting more fatigued" -> "§efatigued";
                    default -> "§aexhausted";
                });
        foraging("BEEHEEMOTH!", "^BEEHEEMOTH! The Beeheemoth got bored and flew away!$", (m, l) -> "§d§lBEEHEEMOTH! §7flew away");
        foraging("honeyhive", "^You stick your hand into the honeyhive and feel around", (m, l) -> "");
        foraging("HIVE!", "^HIVE! You found (.+?) x([\\d,]+) and (.+?)!*$",
                (m, l) -> "§6§lHIVE §r" + span(l, m.group(1)) + " §7x" + m.group(2) + " §8+ " + span(l, m.group(3)));
        foraging("stealing its honey", "^A (.+?) appeared, angry at you for stealing its honey!$", (m, l) -> span(l, m.group(1)) + " §7appeared");
        foraging("entered Critter Safari", "^-*\\s*(.+?) entered Critter Safari!$", (m, l) -> "§2Safari §8· " + span(l, m.group(1)) + " §aentered");
        foraging("joined the Critter Safari", "^(?:. )?(.+?) joined the Critter Safari\\.$", (m, l) -> "§2Safari §8· " + span(l, m.group(1)) + " §ajoined");
        foraging("You threw a", "^You threw an? .+? at the .+?!$", (m, l) -> "");
        foraging("CAPTURE!", "^CAPTURE! You caught an? (.+?) and gained (?:an?|([\\d,]+)x) ",
                (m, l) -> "§a§lCAPTURE! §r" + span(l, m.group(1)) + (m.group(2) == null ? "" : " §7x" + m.group(2)));
        foraging("Safari ", "^(?:\\[NPC] )?Safari (?:Manager|Receptionist): ", (m, l) -> "");
        foraging("escaped your Critter Capsule", "^The (.+?) escaped your Critter Capsule!$", (m, l) -> span(l, m.group(1)) + " §7escaped");
        foraging("escaped!", "^You (?:looked away|didn't reel|reeled too early)! (.+?) escaped!$", (m, l) -> span(l, m.group(1)) + " §cescaped");
        foraging("HONEY TREE!", "^HONEY TREE! (.+?) has appeared!$", (m, l) -> "§6§lHONEY TREE! §r" + span(l, m.group(1)));
    }

    public static void register() {
        Feature.of("ChatCompactRulesIslands")
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Compact Lines");
                    b.toggle("Compact Dungeon Lines", () -> c.chatCompactDungeon, v -> c.chatCompactDungeon = v)
                            .desc("\"+ Wither Key (name)\", \"Door · name\", \"Puzzle ✔ ...\", Superboom/Revive pickups, solo class. Full line on hover. Wins over the Hide filters for these lines.");
                    b.toggle("Compact Lobby & System Lines", () -> c.chatCompactLobby, v -> c.chatCompactLobby = v)
                            .desc("\"X joined\", boosters, tips, bank interest, Mystery Box, achievements, \"→ mini12\", Watchdog, profile lines");
                    b.toggle("Compact Foraging / Hunting / Safari", () -> c.chatCompactForaging, v -> c.chatCompactForaging = v)
                            .desc("Floor Drop, TIMBER!, Beeheemoth, Honeyhive, Safari entries and captures. Capsule throws and Safari NPC lines are dropped.");
                });
    }

    /** Short form of a server line, or null. {@code t} is the stripped text. */
    public static String shortForm(Component message, String t) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatCompactDungeon && !c.chatCompactLobby && !c.chatCompactForaging) return null;
        PlayerChat.Msg pm = PlayerChat.current();
        if (pm != null && pm.channel() != PlayerChat.Channel.NPC) return null;
        String s = t.trim();
        for (int i = 0, n = RULES.size(); i < n; i++) {
            R r = RULES.get(i);
            if (!s.contains(r.hint) || !r.on.test(c)) continue;
            Matcher m = r.p.matcher(s);
            if (!m.find()) continue;
            return r.out.apply(m, ChatText.formatted(message));
        }
        return null;
    }

    private static String keyColor(String key) {
        return key.equals("Blood") ? "§c" : "§8";
    }

    /** {@code text} out of the legacy line with its colour codes and the colour in front of it. */
    static String span(String legacy, String text) {
        StringBuilder plain = new StringBuilder(legacy.length());
        int[] at = new int[legacy.length() + 1];
        for (int i = 0; i < legacy.length(); i++) {
            char ch = legacy.charAt(i);
            if (ch == '§' && i + 1 < legacy.length()) { i++; continue; }
            at[plain.length()] = i;
            plain.append(ch);
        }
        int idx = plain.indexOf(text);
        if (idx < 0 || text.isEmpty()) return "§f" + text;
        int from = at[idx], to = at[idx + text.length() - 1] + 1;
        return lead(legacy, from, "§f") + legacy.substring(from, to) + "§r";
    }

    static String color(String legacy, String marker, String fallback) {
        int idx = legacy.indexOf(marker);
        if (idx < 0) {
            String s = span(legacy, marker);
            return s.length() >= 2 && s.charAt(0) == '§' ? s.substring(0, 2) : fallback;
        }
        return lead(legacy, idx, fallback);
    }

    private static String lead(String legacy, int before, String fallback) {
        for (int j = before - 2; j >= 0; j--) {
            if (legacy.charAt(j) != '§') continue;
            char code = Character.toLowerCase(legacy.charAt(j + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) return "§" + code;
        }
        return fallback;
    }

    private static String hours(String line) {
        Matcher m = HOURS.matcher(line);
        if (!m.find()) return "";
        String w = m.group(1).toLowerCase();
        String n = switch (w) {
            case "one", "an", "a" -> "1";
            case "two" -> "2";
            case "three" -> "3";
            case "four" -> "4";
            case "five" -> "5";
            case "six" -> "6";
            default -> w;
        };
        return " §7· " + n + "h";
    }

    private static final Pattern HOURS = Pattern.compile("have (\\w+) hours?");

    private static String shortNum(String raw) {
        try {
            double v = Double.parseDouble(raw.replace(",", ""));
            if (v >= 1_000_000_000) return trim(v / 1_000_000_000) + "B";
            if (v >= 1_000_000) return trim(v / 1_000_000) + "M";
            if (v >= 1_000) return trim(v / 1_000) + "k";
            return raw;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String trim(double v) {
        String s = String.valueOf(Math.round(v * 10) / 10.0);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
