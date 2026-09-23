package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.PlayerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ChatCommands {

    private static final String[] EIGHT_BALL = {
            "It is certain", "It is decidedly so", "Without a doubt", "Yes definitely",
            "You may rely on it", "As I see it, yes", "Most likely", "Outlook good", "Yes",
            "Signs point to yes", "Reply hazy try again", "Ask again later", "Better not tell you now",
            "Cannot predict now", "Concentrate and ask again", "Don't count on it", "My reply is no",
            "My sources say no", "Outlook not so good", "Very doubtful"
    };

    /** Toggle keys, in help order. Also the per-command toggles in the Party category. */
    public static final String[] KEYS = {
            "8ball", "cf", "dice", "coords", "ping", "fps", "time", "missing",
            "warp", "allinvite", "pt", "kick", "promote", "demote", "dt", "floors"
    };
    // harmless ones, the only ones answered outside party chat
    private static final java.util.Set<String> ANY_CHANNEL =
            java.util.Set.of("8ball", "cf", "dice", "coords", "ping", "fps", "time");
    private static final java.util.Set<String> TRUSTED = java.util.Set.of("allinvite", "pt", "kick", "promote", "demote");
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("\\w{1,16}");

    private static final java.util.Set<String> downtimePlayers = new java.util.LinkedHashSet<>();

    public static void onChatMessage(String msg) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.chatCommands) return;
        msg = msg.replaceAll("(?i)§[0-9A-FK-OR]", ""); // Hypixel sends chat with legacy § codes

        if (msg.contains("> EXTRA STATS <") && !downtimePlayers.isEmpty()
                && com.teslamaps.utils.StringUtil.isServerMessage(msg)) {
            if (!cfg.requeueDowntimeList) for (String p : downtimePlayers) pc("Downtime: " + p);
            downtimePlayers.clear();
            com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(false);
        }

        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || !m.message().startsWith("!")) return;
        String reply = switch (m.channel()) {
            case PARTY -> "pc ";
            case GUILD -> cfg.chatCommandsGuild ? "gc " : null;
            case COOP -> cfg.chatCommandsCoop ? "cc " : null;
            case DM_FROM -> cfg.chatCommandsDm ? "msg " + m.author() + " " : null;
            default -> null;
        };
        if (reply == null) return;

        handle(m.message().substring(1).trim(), m.author(), m.channel() == PlayerChat.Channel.PARTY, reply);
    }

    private static String key(String c) {
        return switch (c) {
            case "8ball" -> "8ball";
            case "cf", "coinflip" -> "cf";
            case "dice" -> "dice";
            case "coords", "co" -> "coords";
            case "ping" -> "ping";
            case "fps" -> "fps";
            case "time" -> "time";
            case "missing", "miss" -> "missing";
            case "warp", "w" -> "warp";
            case "allinvite", "allinv" -> "allinvite";
            case "pt", "transfer" -> "pt";
            case "kick", "k" -> "kick";
            case "promote" -> "promote";
            case "demote" -> "demote";
            case "dt", "downtime", "undt", "undowntime" -> "dt";
            case "help", "h" -> "help";
            default -> floorInstance(c) != null ? "floors" : null;
        };
    }

    public static boolean enabled(String key) {
        return !TeslaMapsConfig.get().chatCommandsDisabled.contains(key);
    }

    private static String helpLine(boolean party) {
        StringBuilder sb = new StringBuilder("Commands:");
        for (String k : KEYS) {
            if (!enabled(k) || (!party && !ANY_CHANNEL.contains(k))) continue;
            sb.append(' ').append(switch (k) {
                case "dt" -> "dt undt";
                case "floors" -> "f1-f7 m1-m7 t1-t5";
                default -> k;
            });
        }
        return sb.toString();
    }

    private static void handle(String command, String sender, boolean party, String reply) {
        String[] w = command.trim().split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return;
        String c = w[0].toLowerCase();
        String k = key(c);
        if (k == null || (!k.equals("help") && !enabled(k))) return;
        if (!party && !k.equals("help") && !ANY_CHANNEL.contains(k)) return;
        Minecraft mc = Minecraft.getInstance();
        boolean self = sender.equalsIgnoreCase(mc.getUser().getName());
        if (TRUSTED.contains(k) && !trusted(sender, self)) return;
        String arg = w.length > 1 && NAME.matcher(w[1]).matches() ? PartyTracker.resolve(w[1]) : null;
        if (w.length > 1 && arg == null && (k.equals("pt") || k.equals("kick") || k.equals("promote") || k.equals("demote"))) return;
        sendDelay = self ? 10 : 0;
        replyPrefix = reply;

        switch (c) {
            case "8ball" -> say(EIGHT_BALL[(int) (Math.random() * EIGHT_BALL.length)]);
            case "cf", "coinflip" -> say(Math.random() < 0.5 ? "heads" : "tails");
            case "dice" -> say(String.valueOf(1 + (int) (Math.random() * 6)));
            case "coords", "co" -> say(coords());
            case "ping" -> {
                if (party) PingMeter.requestToParty();
                else say("Ping: " + Math.round(PingMeter.getLastPingMs()) + "ms");
            }
            case "fps" -> say("FPS: " + mc.getFps());
            case "time" -> say("Time: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            case "warp", "w" -> { if (PartyTracker.mayLead()) run("party warp"); }
            case "allinvite", "allinv" -> { if (PartyTracker.mayLead()) run("party settings allinvite"); }
            case "pt", "transfer" -> { if (PartyTracker.mayLead()) run("party transfer " + (arg != null ? arg : sender)); }
            case "kick", "k" -> { if (PartyTracker.mayLead()) run("party kick " + (arg != null ? arg : sender)); }
            case "promote" -> { if (PartyTracker.mayLead()) run("party promote " + (arg != null ? arg : sender)); }
            case "demote" -> { if (PartyTracker.mayLead()) run("party demote " + (arg != null ? arg : sender)); }
            case "dt", "downtime" -> {
                if (downtimePlayers.contains(sender)) { say(sender + " already has a downtime reminder!"); break; }
                downtimePlayers.add(sender);
                com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(true);
                say("Reminder set for end of run (" + sender + ") - auto-requeue paused");
            }
            case "undt", "undowntime" -> {
                if (!downtimePlayers.remove(sender)) { say(sender + " has no downtime reminder set!"); break; }
                if (downtimePlayers.isEmpty()) com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(false);
                say("Downtime reminder removed (" + sender + ")");
            }
            case "missing", "miss" -> say(com.teslamaps.safari.SafariTracker.missingLine());
            case "help", "h" -> say(helpLine(party));
            default -> {
                String instance = floorInstance(c);
                if (instance != null && PartyTracker.mayLead()) run("joininstance " + instance);
            }
        }
    }

    private static boolean trusted(String sender, boolean self) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return self || !cfg.chatCommandsWhitelistOnly || cfg.chatCommandsWhitelist.contains(sender.toLowerCase());
    }

    /** /tmap pcmd: adds or removes a whitelisted name, returns true when it is now on the list. */
    public static boolean toggleWhitelist(String name) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        String n = name.toLowerCase();
        boolean added = !cfg.chatCommandsWhitelist.remove(n);
        if (added) cfg.chatCommandsWhitelist.add(n);
        TeslaMapsConfig.save();
        return added;
    }

    private static String replyPrefix = "pc ";

    private static void say(String message) {
        run(replyPrefix + message);
    }

    public static boolean queueFloor(String code) {
        String instance = floorInstance(code);
        if (instance == null) return false;
        run("joininstance " + instance);
        return true;
    }

    public static String floorInstance(String c) {
        if (c.length() != 2) return null;
        char type = c.charAt(0);
        char num = c.charAt(1);
        if (type == 't') {
            String[] tiers = {"normal", "hot", "burning", "fiery", "infernal"};
            if (num < '1' || num > '5') return null;
            return "kuudra_" + tiers[num - '1'];
        }
        if ((type != 'f' && type != 'm') || num < '1' || num > '7') return null;
        String[] names = {"one", "two", "three", "four", "five", "six", "seven"};
        return (type == 'm' ? "master_" : "") + "catacombs_floor_" + names[num - '1'];
    }

    private static String coords() {
        BlockPos p = Minecraft.getInstance().player.blockPosition();
        return "x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ();
    }

    private static int sendDelay = 0; // ticks; own trigger message hits Hypixel's half-second chat throttle

    private static void pc(String message) {
        run("pc " + message);
    }

    private static void run(String command) {
        int delay = sendDelay;
        Runnable send = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
        };
        if (delay > 0) com.teslamaps.utils.TickScheduler.schedule(delay, send);
        else Minecraft.getInstance().execute(send);
    }
}
