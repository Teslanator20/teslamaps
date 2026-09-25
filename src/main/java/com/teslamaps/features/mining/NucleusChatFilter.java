package com.teslamaps.features.mining;

// Ported from SkyHanni's features/chat/CrystalNucleusChatFilter.kt (patterns rewritten for stripped text)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NucleusChatFilter {

    private static final String BAR = "▬".repeat(64);
    private static final Pattern CRYSTAL_COUNT = Pattern.compile("✦ CRYSTAL FOUND \\((\\d)/5\\)");
    private static final Pattern CRYSTAL_NAME = Pattern.compile("(\\w+ Crystal)");
    private static final Pattern PLACED = Pattern.compile("✦ You placed the (\\w+ Crystal)!");
    private static final Pattern SCAVENGE = Pattern.compile("You found (.+) with your Metal Detector!");
    private static final Pattern KEEPER = Pattern.compile("\\[NPC] Keeper of (\\w+): .*");
    private static final Pattern COMPONENT = Pattern.compile("me the (.+)! Bring me (\\d|one) more");

    private static boolean inCrystal, inRun;
    private static int count;
    private static String lastKeeper = "";
    private static long componentListUntil, blockSince;

    private NucleusChatFilter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NucleusChatFilter").section("Mining").config("Mining", b -> {
            TeslaMapsConfig c = cfg();
            b.label("Crystal Nucleus Chat");
            b.toggle("Nucleus Chat Filter", () -> c.nucleusChatFilter, v -> c.nucleusChatFilter = v)
                    .desc("Hides or shortens the chat of a Crystal Nucleus run in the Crystal Hollows. Needs the Chat section on.")
                    .tags("crystal hollows", "chat", "filter", "compact");
            b.toggle("  Crystal Found", () -> c.nucleusFilterCrystalFound, v -> c.nucleusFilterCrystalFound = v);
            b.toggle("  Crystal Placed", () -> c.nucleusFilterCrystalPlaced, v -> c.nucleusFilterCrystalPlaced = v);
            b.toggle("  Run Completed", () -> c.nucleusFilterRunDone, v -> c.nucleusFilterRunDone = v);
            b.toggle("  Non-Tool Scavenge Loot", () -> c.nucleusFilterScavenge, v -> c.nucleusFilterScavenge = v);
            b.toggle("  Goblin Guards", () -> c.nucleusFilterGuards, v -> c.nucleusFilterGuards = v);
            b.toggle("  Professor Robot", () -> c.nucleusFilterRobot, v -> c.nucleusFilterRobot = v);
            b.toggle("  King Yolkar", () -> c.nucleusFilterYolkar, v -> c.nucleusFilterYolkar = v);
            b.toggle("  Divan Keepers", () -> c.nucleusFilterKeepers, v -> c.nucleusFilterKeepers = v);
        });
    }

    /** From ChatFilter.process: null = untouched, "" = hide, else the replacement line. */
    public static String handle(Component message, String raw, String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.nucleusChatFilter || !c.section("Mining") || !Mining.inHollows()) return null;
        String t = stripped.trim();
        if ((inCrystal || inRun) && System.currentTimeMillis() - blockSince > 3000) inCrystal = inRun = false;
        if (t.isEmpty() && !inCrystal && !inRun) return null;

        if (t.equals(BAR)) {
            int col = barColor(message, raw);
            if (col == 0xAA00AA && c.nucleusFilterCrystalFound) {
                inCrystal = !inCrystal;
                blockSince = System.currentTimeMillis();
                return "";
            }
            if (col == 0x00AAAA && c.nucleusFilterRunDone && Mining.inNucleus()) {
                inRun = !inRun;
                blockSince = System.currentTimeMillis();
                return "";
            }
            return null;
        }
        if (inCrystal && c.nucleusFilterCrystalFound) {
            Matcher m = CRYSTAL_COUNT.matcher(t);
            if (m.matches()) {
                count = Integer.parseInt(m.group(1));
                return "";
            }
            m = CRYSTAL_NAME.matcher(t);
            if (m.matches()) return "§5§l✦ " + crystal(m.group(1)) + " §5found§d! §7(§a" + count + "§7/§a5§7)";
            return "";
        }
        if (c.nucleusFilterCrystalPlaced && Mining.inNucleus()) {
            if (t.equals("Keep exploring the Crystal Hollows to find the rest!")) return "";
            Matcher m = PLACED.matcher(t);
            if (m.matches()) return "§5§l✦ " + crystal(m.group(1)) + " §5placed§d!";
        }
        if (c.nucleusFilterRunDone && Mining.inNucleus()) {
            if (t.equals("Pick it up near the Nucleus Vault!")) return "§5Crystal Nucleus Run complete§d!";
            if (inRun) return "";
        }
        if (c.nucleusFilterScavenge) {
            Matcher m = SCAVENGE.matcher(t);
            if (m.matches() && !m.group(1).startsWith("Scavenged")) return "";
            if (t.equals("PICK IT UP!")) return "";
        }
        if (c.nucleusFilterGuards && (t.startsWith("[GUARD] ")
                || t.equals("Whew! That was a close one, better get out of here...")
                || t.equals("The Goblin King's foul stench has dissipated!"))) {
            return "";
        }
        if (c.nucleusFilterRobot) {
            if (System.currentTimeMillis() < componentListUntil && stripped.startsWith("  ")) return "";
            if (t.startsWith("[NPC] Professor Robot") || t.startsWith("That's not one of the components I need!")) {
                if (t.contains("That's not one of the components I need!")) {
                    componentListUntil = System.currentTimeMillis() + 2000;
                    return "";
                }
                if (t.contains("brought me all") || t.contains("This will work just fine.")) {
                    return "§e[NPC] Professor Robot§f: §rAll components submitted.";
                }
                Matcher m = COMPONENT.matcher(t);
                if (m.find()) {
                    return "§e[NPC] Professor Robot§f: §9" + m.group(1) + " §rsubmitted. " + m.group(2) + " components left.";
                }
                return "";
            }
        }
        if (c.nucleusFilterYolkar && t.startsWith("[NPC] King Yolkar")) {
            if (t.contains("*rumble* *rumble*")) return "§e[NPC] §6King Yolkar§f: ...";
            if (t.contains("Bring me back a Goblin Egg")) return "§e[NPC] §6King Yolkar§f: §rBring me a §9Goblin Egg §rof any type.";
            if (t.contains("This egg will help me stomach my pain.")) return "§e[NPC] §6King Yolkar§f: §2King's Scent§r applied.";
            return "";
        }
        if (c.nucleusFilterKeepers && t.startsWith("[NPC] Keeper of ")) {
            Matcher m = KEEPER.matcher(t);
            if (m.matches()) lastKeeper = m.group(1);
            if (t.contains("You found all of the items!")) return "§e[NPC] §6Keeper of " + lastKeeper + "§f: §rAll tools submitted.";
            return "";
        }
        return null;
    }

    private static String crystal(String name) {
        String col = switch (name.split(" ")[0]) {
            case "Amethyst" -> "§5";
            case "Sapphire" -> "§b";
            case "Amber" -> "§6";
            case "Topaz" -> "§e";
            case "Jade" -> "§a";
            case "Ruby" -> "§c";
            case "Jasper" -> "§d";
            default -> "§f";
        };
        return col + name;
    }

    private static int barColor(Component message, String raw) {
        if (raw.contains("§5§l▬")) return 0xAA00AA;
        if (raw.contains("§3§l▬")) return 0x00AAAA;
        Optional<Integer> col = message.visit((Style style, String s) -> {
            if (s.isBlank()) return Optional.empty();
            TextColor tc = style.getColor();
            return Optional.of(tc == null ? -1 : tc.getValue());
        }, Style.EMPTY);
        return col.orElse(-1);
    }
}
