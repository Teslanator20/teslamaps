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
    /** Added later, opt-in (chatCommandsExtraEnabled) so old configs keep their behaviour. */
    public static final String[] EXTRA_KEYS = {"tps", "location", "holding", "boop", "runs", "reinvite", "kickoffline", "invite"};
    private static final java.util.Set<String> EXTRA_ANY = java.util.Set.of("tps", "location", "holding", "boop", "runs");
    // harmless ones, the only ones answered outside party chat
    private static final java.util.Set<String> ANY_CHANNEL =
            java.util.Set.of("8ball", "cf", "dice", "coords", "ping", "fps", "time");
    private static final java.util.Set<String> TRUSTED = java.util.Set.of("allinvite", "pt", "kick", "promote", "demote", "reinvite", "kickoffline", "invite");
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("\\w{1,16}");

    private static final java.util.Set<String> downtimePlayers = new java.util.LinkedHashSet<>();
    private static final java.util.Map<String, String> downtimeReasons = new java.util.LinkedHashMap<>();
    private static final String KUUDRA_END = "[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!";
    private static int kuudraRuns;
    private static long lastKuudraEnd;

    /** "name: reason, ..." for the requeue-cancelled title. */
    public static String downtimeSummary() {
        StringBuilder sb = new StringBuilder();
        for (String p : downtimePlayers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
            String r = downtimeReasons.get(p);
            if (r != null) sb.append(": ").append(r);
        }
        return sb.toString();
    }

    public static void onChatMessage(String msg) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.chatCommands) return;
        msg = msg.replaceAll("(?i)§[0-9A-FK-OR]", ""); // Hypixel sends chat with legacy § codes

        boolean kuudraEnd = false;
        if (msg.startsWith("[NPC] Elle: Good job everyone.") || msg.trim().equals("KUUDRA DOWN!")) {
            String t = msg.trim();
            long now = System.currentTimeMillis();
            if ((t.equals(KUUDRA_END) || t.equals("KUUDRA DOWN!")) && now - lastKuudraEnd > 20000
                    && com.teslamaps.utils.StringUtil.isServerMessage(msg)) {
                lastKuudraEnd = now;
                kuudraRuns++;
                kuudraEnd = true;
            }
        }
        boolean runEnd = msg.contains("> EXTRA STATS <") && com.teslamaps.utils.StringUtil.isServerMessage(msg);
        if ((runEnd || (kuudraEnd && cfg.chatCommandsDtReasons)) && !downtimePlayers.isEmpty()) {
            if (cfg.chatCommandsDtReasons) announceReasons();
            else for (String p : downtimePlayers) pc("Downtime: " + p);
            downtimePlayers.clear();
            downtimeReasons.clear();
            com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(false);
        }

        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || !m.message().startsWith("!")) return;
        if (m.channel() == PlayerChat.Channel.DM_FROM && extraEnabled("invite")) {
            String inv = m.message().substring(1).trim().toLowerCase();
            if (inv.equals("invite") || inv.equals("inv")) { dmInvite(m.author()); return; }
        }
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
            case "tps" -> "tps";
            case "location", "loc" -> "location";
            case "holding" -> "holding";
            case "boop" -> "boop";
            case "runs" -> "runs";
            case "reinvite", "reinv" -> "reinvite";
            case "kickoffline", "ko" -> "kickoffline";
            default -> floorInstance(c) != null ? "floors" : null;
        };
    }

    public static boolean enabled(String key) {
        for (String e : EXTRA_KEYS) if (e.equals(key)) return extraEnabled(key);
        return !TeslaMapsConfig.get().chatCommandsDisabled.contains(key);
    }

    public static boolean extraEnabled(String key) {
        return TeslaMapsConfig.get().chatCommandsExtraEnabled.contains(key);
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
        for (String k : EXTRA_KEYS) {
            if (!extraEnabled(k) || k.equals("invite") || (!party && !EXTRA_ANY.contains(k))) continue;
            sb.append(' ').append(k);
        }
        return sb.toString();
    }

    private static void handle(String command, String sender, boolean party, String reply) {
        String[] w = command.trim().split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return;
        String c = w[0].toLowerCase();
        String k = key(c);
        if (k == null || (!k.equals("help") && !enabled(k))) return;
        if (!party && !k.equals("help") && !ANY_CHANNEL.contains(k) && !EXTRA_ANY.contains(k)) return;
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
            case "tps" -> say(String.format(java.util.Locale.ROOT, "TPS: %.1f", com.teslamaps.utils.ServerTicks.tps()));
            case "location", "loc" -> say("Location: " + location());
            case "holding" -> {
                var held = mc.player == null ? null : mc.player.getMainHandItem();
                say("Holding: " + (held == null || held.isEmpty() ? "Nothing :(" : com.teslamaps.utils.StringUtil.strip(held.getHoverName().getString())));
            }
            case "boop" -> { if (arg != null) run("boop " + arg); }
            case "runs" -> say("Kuudra runs this session: " + kuudraRuns);
            case "reinvite", "reinv" -> {
                if (!PartyTracker.mayLead()) break;
                sendDelay = 100;
                run("party invite " + sender);
            }
            case "kickoffline", "ko" -> { if (PartyTracker.mayLead()) run("party kickoffline"); }
            case "dt", "downtime" -> {
                if (downtimePlayers.contains(sender)) { say(sender + " already has a downtime reminder!"); break; }
                downtimePlayers.add(sender);
                if (TeslaMapsConfig.get().chatCommandsDtReasons) {
                    String reason = w.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(w, 1, w.length)) : "No reason given";
                    downtimeReasons.put(sender, reason.length() > 60 ? reason.substring(0, 60) : reason);
                }
                com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(true);
                say("Reminder set for end of run (" + sender + ") - auto-requeue paused");
            }
            case "undt", "undowntime" -> {
                if (!downtimePlayers.remove(sender)) { say(sender + " has no downtime reminder set!"); break; }
                downtimeReasons.remove(sender);
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

    private static String location() {
        String area = com.teslamaps.utils.SkyblockUtils.getArea();
        String sub = null;
        for (String l : com.teslamaps.utils.ScoreboardUtils.getScoreboardLines()) {
            String t = com.teslamaps.utils.StringUtil.strip(l).trim();
            if (t.startsWith("⏣") || t.startsWith("ф")) { sub = t.substring(1).trim(); break; }
        }
        if (area.isEmpty()) return sub != null ? sub : "Unknown";
        return sub != null && !sub.equalsIgnoreCase(area) ? area + " (" + sub + ")" : area;
    }

    private static void dmInvite(String sender) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.chatCommands || !NAME.matcher(sender).matches()) return;
        Minecraft mc = Minecraft.getInstance();
        if (sender.equalsIgnoreCase(mc.getUser().getName())) return;
        if (cfg.chatCommandsInviteAutoConfirm && trusted(sender, false)) {
            sendDelay = 0;
            run("party invite " + sender);
            return;
        }
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[TeslaMaps] §aClick to invite §6" + sender + " §ato your party")
                    .withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/party invite " + sender))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                    net.minecraft.network.chat.Component.literal("§6/party invite " + sender)))));
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        });
    }

    private static void announceReasons() {
        StringBuilder line = new StringBuilder("Downtime needed: ");
        boolean first = true;
        for (String p : downtimePlayers) {
            if (!first) line.append(", ");
            first = false;
            line.append(p).append(" (").append(downtimeReasons.getOrDefault(p, "No reason given")).append(")");
        }
        String out = line.toString();
        com.teslamaps.utils.TickScheduler.schedule(30, () -> {
            Minecraft mc = Minecraft.getInstance();
            com.teslamaps.features.Announce.party(out);
            if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[TeslaMaps] §c" + out));
            mc.gui.setTitle(net.minecraft.network.chat.Component.literal("§cPlayers need DT"));
        });
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
            String[] tiers = {"NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL"};
            if (num < '1' || num > '5') return null;
            return "KUUDRA_" + tiers[num - '1'];
        }
        if ((type != 'f' && type != 'm') || num < '1' || num > '7') return null;
        String[] names = {"ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"};
        return (type == 'm' ? "MASTER_" : "") + "CATACOMBS_FLOOR_" + names[num - '1'];
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
            com.teslamaps.features.ChatQueue.command(command);
        };
        if (delay > 0) com.teslamaps.utils.TickScheduler.schedule(delay, send);
        else Minecraft.getInstance().execute(send);
    }
}
