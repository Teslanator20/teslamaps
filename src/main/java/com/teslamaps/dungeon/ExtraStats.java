package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Runs /showextrastats at the end of a run so the extra stats land in chat by themselves.
// Compact summary ported from Odin's ExtraStats.kt
public class ExtraStats {

    private static final Pattern EXTRA_STATS = Pattern.compile("^\\s*> EXTRA STATS <$");
    private static final Pattern DEFEATED = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");
    private static final Pattern TEAM_SCORE = Pattern.compile("^\\s*Team Score: (\\d+) \\((.{1,2})\\)\\s?(\\(NEW RECORD!\\))?$");
    private static final Pattern EXP = Pattern.compile("^\\s*(\\+[\\d,.]+\\s?\\w+ Experience)\\s?(?:\\(.+\\))?$");
    private static final Pattern BITS = Pattern.compile("^\\s*(\\+\\d+ Bits)$");
    private static final Pattern DAMAGE = Pattern.compile("^\\s*(Total Damage as .+: ([\\d,.]+)\\s?(\\(NEW RECORD!\\))?)$");
    private static final Pattern HEAL = Pattern.compile("^\\s*(Ally Healing: ([\\d,.]+)\\s?(\\(NEW RECORD!\\))?)$");
    private static final Pattern KILLS = Pattern.compile("^\\s*(Enemies Killed: (\\d+)\\s?(\\(NEW RECORD!\\))?)$");
    private static final Pattern DEATHS = Pattern.compile("^\\s*(Deaths: (\\d+))$");
    private static final Pattern SECRETS = Pattern.compile("^\\s*(Secrets Found: (\\d+))$");
    private static final Pattern SEPARATOR = Pattern.compile("^▬+$");
    private static final Pattern[] HIDE = {
            Pattern.compile("^\\s*(Master Mode)? ?(?:The)? Catacombs - (Entrance|Floor .{1,3})$"),
            Pattern.compile("^\\s*\\+0 Experience \\(No Class Milestone Reached\\)$"),
            Pattern.compile("^\\s*The Catacombs - .+ Stats$"),
            Pattern.compile("^\\s*Master Mode Catacombs - .+ Stats$"),
            Pattern.compile("^\\s*Master Mode The Catacombs - .+ Stats$"),
            EXTRA_STATS, DEFEATED, TEAM_SCORE, EXP, BITS, DAMAGE, HEAL, KILLS, DEATHS, SECRETS
    };

    private static String boss, time, scoreLetter, bits;
    private static int score, deaths, secrets;
    private static boolean timePb, scorePb;
    private static String damage = "0", kills = "0", heal = "0";
    private static final List<String> xp = new ArrayList<>();
    private static final List<String> combat = new ArrayList<>();
    private static long fallbackAt;
    private static boolean seenEnd;

    public static void register() {
        Feature.of("ExtraStatsCompact").section("Score & Splits")
                .when(() -> TeslaMapsConfig.get().extraStatsCompact && fallbackAt > 0)
                .tick(() -> {
                    if (System.currentTimeMillis() < fallbackAt) return;
                    fallbackAt = 0;
                    print();
                });
    }

    public static void onChatMessage(String msg) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.extraStatsCompact && DungeonManager.isInDungeon()) collect(StringUtil.strip(msg));
        if (!c.autoExtraStats) return;
        if (!msg.contains("> EXTRA STATS <")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        // fires before AutoRequeue's delayed requeue, so the stats still arrive
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand("showextrastats");
        });
    }

    /** True = drop this line from chat (compact mode). */
    public static boolean hide(String msg) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.extraStatsCompact || !DungeonManager.isInDungeon()) return false;
        String s = StringUtil.strip(msg);
        if (SEPARATOR.matcher(s).matches()) return seenEnd || DungeonManager.isInBossArea();
        // without auto extra stats the line stays clickable
        if (!c.autoExtraStats && EXTRA_STATS.matcher(s).matches()) return false;
        for (Pattern p : HIDE) if (p.matcher(s).matches()) return true;
        return false;
    }

    private static void collect(String s) {
        Matcher m;
        if (EXTRA_STATS.matcher(s).matches()) {
            if (!seenEnd) reset();
            seenEnd = true;
            fallbackAt = System.currentTimeMillis() + 5000;
        } else if ((m = DEFEATED.matcher(s)).matches()) {
            if (!seenEnd) reset();
            seenEnd = true;
            boss = m.group(1);
            time = m.group(2).trim();
            timePb = m.group(3) != null;
        } else if ((m = TEAM_SCORE.matcher(s)).matches()) {
            if (!seenEnd) reset();
            seenEnd = true;
            score = Integer.parseInt(m.group(1));
            scoreLetter = m.group(2);
            scorePb = m.group(3) != null;
        } else if ((m = EXP.matcher(s)).matches()) {
            xp.add("§3" + m.group(1).replace("Experience", "EXP").replace("Catacombs", "Cata"));
        } else if ((m = BITS.matcher(s)).matches()) {
            bits = m.group(1);
        } else if ((m = DAMAGE.matcher(s)).matches()) {
            damage = m.group(2) + (m.group(3) != null ? " (NEW PB!)" : "");
            combat.add("§e" + m.group(1));
        } else if ((m = HEAL.matcher(s)).matches()) {
            heal = m.group(2) + (m.group(3) != null ? " (NEW PB!)" : "");
            combat.add("§a" + m.group(1));
        } else if ((m = KILLS.matcher(s)).matches()) {
            kills = m.group(2) + (m.group(3) != null ? " (NEW PB!)" : "");
            combat.add(0, "§b" + m.group(1));
        } else if ((m = DEATHS.matcher(s)).matches()) {
            deaths = Integer.parseInt(m.group(2));
        } else if ((m = SECRETS.matcher(s)).matches()) {
            secrets = Integer.parseInt(m.group(2));
            fallbackAt = 0;
            print();
        }
    }

    private static void reset() {
        boss = time = scoreLetter = bits = null;
        score = deaths = secrets = 0;
        timePb = scorePb = false;
        damage = kills = heal = "0";
        xp.clear();
        combat.clear();
    }

    private static void print() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (boss == null && scoreLetter == null)) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        String floorName = floor == null || floor == DungeonFloor.UNKNOWN ? "?" : floor.name();

        MutableComponent out = Component.literal(chatBreak()).append("\n");
        out.append(center((floor != null && floor.isMaster() ? "§cMaster Mode" : "§cThe Catacombs") + " §r- §e" + floorName)).append("\n");
        String defeated = boss == null ? "§c§lFAILED" + (time != null ? " §a- §e" + time : "")
                : "§aDefeated §c" + boss + " §ain §e" + time + (timePb ? " §d§l(NEW RECORD!)" : "");
        out.append(center(defeated)).append("\n");
        out.append(center("§aScore: §6" + score + " §a(§b" + (scoreLetter == null ? "?" : scoreLetter) + "§a)"
                + (scorePb ? " §d§l(NEW RECORD!)" : "") + (bits != null ? "    §b" + bits : ""))).append("\n");
        if (!xp.isEmpty()) {
            String xpText = xp.get(0) + (xp.size() > 1 ? "  " + xp.get(1) : "");
            out.append(hover(center(xpText), String.join("\n", xp))).append("\n");
        }
        if (!combat.isEmpty()) {
            out.append(hover(center("§e" + damage + "§r-§b" + kills + "§r-§a" + heal), String.join("\n", combat))).append("\n");
        }
        String team = c.extraStatsTeam;
        if (!"Off".equals(team)) {
            int teamSecrets = Math.max(0, TabListUtils.getSecretsFound());
            int crypts = DungeonScore.getCryptCount();
            int teamDeaths = DungeonScore.getDeathCount();
            String personal = "§b" + secrets + "§r-§c" + deaths;
            String teamStr = "§b" + teamSecrets + "§r-§6" + crypts + "§r-§c" + teamDeaths;
            String text = switch (team) {
                case "Personal" -> personal;
                case "Team" -> teamStr;
                default -> personal + " §r/ " + teamStr;
            };
            String tip = "§bSecrets Found: " + secrets + "\n§cDeaths: " + deaths
                    + "\n\n§bTeam Secrets Found: " + teamSecrets + "\n§6Crypts: " + crypts + "\n§cTeam Deaths: " + teamDeaths;
            out.append(hover(center(text), tip)).append("\n");
        }
        if (c.extraStatsTeammates) {
            StringBuilder sb = new StringBuilder();
            String self = mc.player.getGameProfile().name();
            for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) {
                if (p.getName().equals(self)) continue;
                if (sb.length() > 0) sb.append("§r, ");
                sb.append(DungeonInfoHud.classColor(p.getDungeonClass())).append(p.getName());
            }
            out.append(center(sb.length() == 0 ? "§3Solo" : sb.toString())).append("\n");
        }
        out.append(chatBreak());
        mc.player.sendSystemMessage(out);
        seenEnd = false;
    }

    private static Component hover(String text, String tip) {
        return Component.literal(text).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(tip))));
    }

    private static int chatWidth() {
        Minecraft mc = Minecraft.getInstance();
        double scale = Math.max(0.1, mc.options.chatScale().get());
        return (int) (ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
    }

    private static String center(String text) {
        var font = Minecraft.getInstance().font;
        int pad = (chatWidth() - font.width(text)) / 2;
        int space = Math.max(1, font.width(" "));
        return pad <= 0 ? text : " ".repeat(pad / space) + text;
    }

    private static String chatBreak() {
        var font = Minecraft.getInstance().font;
        int dash = Math.max(1, font.width("▬"));
        return "§9" + "▬".repeat(Math.max(10, chatWidth() / dash));
    }
}
