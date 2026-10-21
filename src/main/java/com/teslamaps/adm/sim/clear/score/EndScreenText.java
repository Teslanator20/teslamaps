// Ported from ADM's p3sim/clear/score/EndScreenText.kt
package com.teslamaps.adm.sim.clear.score;

import com.teslamaps.adm.sim.run.api.EndScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EndScreenText {
    private EndScreenText() {}

    public static final String RULE = "§a§l" + "▬".repeat(64);

    public static String centre(String text) {
        int w = 0; boolean bold = false; int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char f = text.charAt(i + 1);
                if (f == 'l') bold = true; else if ("0123456789abcdefr".indexOf(f) >= 0) bold = false;
                i += 2;
                continue;
            }
            int cw = switch (c) {
                case '!', ',', '.', ':', ';', '\'', '|' -> 2;
                case 'i' -> 2;
                case 'l', '`' -> 3;
                case ' ', 'I', '[', ']', '"' -> 4;
                case 't', 'f', 'k', '(', ')', '<', '>', '*', '{', '}', '☠' -> 5;
                case '@' -> 7;
                default -> 6;
            };
            w += cw + (bold ? 1 : 0);
            i++;
        }
        return "§f" + " ".repeat(Math.max((int) Math.ceil((160 - w / 2.0) / 4), 0)) + text;
    }

    public static String time(int ticks) {
        int s = Math.max(ticks / 20, 0);
        return s < 60 ? String.format(Locale.ROOT, "%02ds", s) : String.format(Locale.ROOT, "%02dm %02ds", s / 60, s % 60);
    }

    public static String letterColour(String letter) {
        return switch (letter) {
            case "S+" -> "§b§l";
            case "S" -> "§b";
            case "A" -> "§a";
            case "B" -> "§e";
            case "C" -> "§6";
            default -> "§c";
        };
    }

    private static String comma(long v) { return String.format(Locale.US, "%,d", v); }

    public static String xp(double v) {
        String s = String.format(Locale.US, "%,.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static final List<String> CLASSES = List.of("Healer", "Berserk", "Archer", "Tank", "Mage");

    public static final String DEFAULT_BOSSES = "Maxor, Storm, Goldor, and Necron";
    public static final String DEFAULT_FLOOR_NAME = "The Catacombs - Floor VII";

    public static List<String> lines(EndScreen e) { return lines(e, DEFAULT_BOSSES, 0, DEFAULT_FLOOR_NAME, null); }

    public static List<String> lines(EndScreen e, String bosses) { return lines(e, bosses, 0, DEFAULT_FLOOR_NAME, null); }

    public static List<String> lines(EndScreen e, String bosses, int killed) { return lines(e, bosses, killed, DEFAULT_FLOOR_NAME, null); }

    public static List<String> lines(EndScreen e, String bosses, int killed, String floorName) { return lines(e, bosses, killed, floorName, null); }

    public static List<String> lines(EndScreen e, String bosses, int killed, String floorName, EndScreenPlayer me) {
        int sep = floorName.indexOf(" - ");
        String dungeon = sep >= 0 ? floorName.substring(0, sep) : floorName;
        String floor = sep >= 0 ? floorName.substring(sep + 3) : "";
        String scoreLine = "Team Score: §r§a" + e.score + " §r§f(§r" + letterColour(e.letter) + e.letter + "§r§f)";
        String defeated = "§r§c☠ §r§eDefeated §r§c" + bosses + " §r§ein §r§a" + time(e.ticks);
        ArrayList<String> out = new ArrayList<>();
        out.add(RULE);
        out.add(centre(title(dungeon, floor, ""))); out.add(""); out.add(centre(scoreLine)); out.add(centre(defeated));
        out.add("§f" + " ".repeat(29) + "§6> §e§lEXTRA STATS §6<");
        if (me != null) {
            if (me.bits > 0) out.add(centre("§r§8+§r§b" + me.bits + " Bits"));
            out.add(centre("§r§8+§r§3" + xp(me.cataXp) + " Catacombs Experience"));
            out.add(centre("§r§8+§r§3" + xp(me.cataXp * CLASS_SHARE) + " " + me.playerClass + " Experience"));
            for (String c : CLASSES) if (!c.equals(me.playerClass)) out.add(centre("§r§8+§r§3" + xp(me.cataXp * TEAM_BONUS_SHARE) + " " + c + " Experience §r§b(Team Bonus)"));
        }
        out.add(RULE);
        out.add(RULE);
        out.add(centre(title(dungeon, floor, " Stats"))); out.add(""); out.add(centre(scoreLine)); out.add(centre(defeated)); out.add("");
        if (me != null) {
            out.add(centre("Total Damage as " + me.playerClass + ": §r§a" + comma(me.damage)));
            out.add(centre("Ally Healing: §r§a" + comma(me.healing)));
        }
        out.add(centre("Enemies Killed: §r§a" + (me != null ? me.killed : killed)));
        out.add(centre("Deaths: §r§c" + e.deaths));
        out.add(centre("Secrets Found: §r§b" + (me != null ? me.secretsFound : e.secretsFound)));
        out.add("");
        out.add(RULE);
        return out;
    }

    private static String title(String dungeon, String floor, String suffix) { return "§r§c" + dungeon + " §r§8- §r§e" + floor + suffix; }

    public static final double CLASS_SHARE = 0.76;
    public static final double TEAM_BONUS_SHARE = 0.19;
}
