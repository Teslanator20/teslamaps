package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Set;

public class AutoRequeue {
    private static final java.util.regex.Pattern EXTRA_STATS = java.util.regex.Pattern.compile("^\\s*> EXTRA STATS <\\s*$");

    private static long requeueAt = 0L; // 0 = nothing scheduled
    private static boolean downtimeHold = false; // set by !dt -> pause auto-requeue for the run
    private static boolean blockNextRequeue = false; // a member left before run end -> skip the upcoming requeue
    private static long lastPartyRMs = 0L; // cooldown so multiple "r" messages don't requeue repeatedly
    // Devonian's AutoRequeueDungeons: dt list + party size at run start
    private static final Set<String> needsDowntime = new LinkedHashSet<>();
    private static boolean runEnded = false;
    private static int startSize = 0;
    private static long kuudraStart = 0L, lastKuudraEnd = 0L;
    private static String dotDt = null; // IQ-style ".dt [reason]" from the party
    private static Object kuudraLevel = null; // left the instance before the requeue -> drop it

    public static void setDowntimeHold(boolean hold) { downtimeHold = hold; if (hold) requeueAt = 0L; }

    public static void onChatMessage(String msg) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        msg = msg.replaceAll("(?i)§[0-9A-FK-OR]", ""); // strip legacy color codes

        String low = msg.toLowerCase();

        if (msg.equals("Starting in 1 second.")) {
            blockNextRequeue = false;
            runEnded = false;
            needsDowntime.clear();
            startSize = PartyTracker.size();
            return;
        }

        // system party events only - player chat always carries ": " and used to false-positive
        // on words like "kicked", silently eating the next requeue
        if (!msg.contains(": ")) {
            if (low.contains("joined the party")) { blockNextRequeue = false; return; }
            if ((low.contains("the party") && (low.contains("left") || low.contains("removed")
                    || low.contains("disbanded") || low.contains("kicked")))
                    || low.contains("no longer allowed to access this instance")) {
                if (requeueAt != 0L) requeueAt = 0L;
                else blockNextRequeue = true;
                return;
            }
        }

        if (c.autoRequeue && c.requeueKuudra && StringUtil.isServerMessage(msg)) {
            String t = msg.trim();
            if (t.equals(com.teslamaps.features.kuudra.KuudraRunInfo.START)) {
                kuudraStart = System.currentTimeMillis();
                blockNextRequeue = false;
                runEnded = false;
                needsDowntime.clear();
                startSize = PartyTracker.size();
                return;
            }
            if (t.equals("KUUDRA DOWN!") || t.startsWith("[NPC] Elle: Good job everyone. A hard fought battle come to an end.")) {
                long now = System.currentTimeMillis();
                if (now - lastKuudraEnd < 20_000L) return;
                lastKuudraEnd = now;
                onKuudraEnd(c, now);
                return;
            }
        }

        if (c.autoRequeue && msg.contains("> EXTRA STATS <")) {
            if (!EXTRA_STATS.matcher(msg).matches() || !StringUtil.isServerMessage(msg)) return;
            runEnded = true;
            if (blockNextRequeue) { blockNextRequeue = false; feedback("a member left this run"); return; } // a member left this run -> don't requeue
            if (c.requeueDowntimeList) {
                if (!needsDowntime.isEmpty()) {
                    com.teslamaps.features.Announce.party(String.join(", ", needsDowntime) + " needs downtime");
                    return;
                }
            } else if (downtimeHold) {
                feedback("someone asked for downtime");
                if (dotDt != null) { dotDt = null; downtimeHold = false; }
                return; // ChatCommands announces the reminders + clears the hold
            }
            requeueAt = System.currentTimeMillis() + Math.max(0, c.requeueDelaySeconds) * 1000L;
            return;
        }

        PlayerChat.Msg pm = PlayerChat.current();
        if (pm == null || pm.channel() != PlayerChat.Channel.PARTY) return;
        String content = pm.message().trim().toLowerCase();

        if (c.requeueKuudra && !c.requeueDowntimeList && (content.equals(".dt") || content.startsWith(".dt "))) {
            String reason = pm.message().trim().length() > 4 ? pm.message().trim().substring(4).trim() : "";
            dotDt = pm.author() + (reason.isEmpty() ? "" : ": " + (reason.length() > 60 ? reason.substring(0, 60) : reason));
            setDowntimeHold(true);
            local("§b" + pm.author() + " §7asked for downtime - auto-requeue paused");
            return;
        }

        if (c.requeueDowntimeList) {
            if (content.equals("dt") || content.startsWith("!dt") || content.equals("!downtime")) {
                if (needsDowntime.add(pm.author())) local("§b" + pm.author() + " §7needs downtime");
                if (requeueAt != 0L) requeueAt = 0L;
                return;
            }
            if (content.equals("!undt") || content.equals("!undowntime")) {
                needsDowntime.remove(pm.author());
                return;
            }
        }

        if (content.equals("r") || content.equals("!r") || content.equals("re") || content.equals("requeue")) {
            boolean wasDown = c.requeueDowntimeList && needsDowntime.remove(pm.author());
            if (wasDown) local("§a" + pm.author() + " is ready");
            if (c.requeueDowntimeList && !needsDowntime.isEmpty()) {
                feedback(String.join(", ", needsDowntime) + " still needs downtime");
                return;
            }
            if (wasDown && c.autoRequeue && runEnded) {
                requeueAt = System.currentTimeMillis() + 500L;
                return;
            }
            if (c.requeueOnPartyR && !content.equals("!r")) {
                long now = System.currentTimeMillis();
                if (now - lastPartyRMs < 10_000L) return; // 10s cooldown vs repeated "r" spam
                lastPartyRMs = now;
                blockNextRequeue = false;
                requeueAt = now + 500L;
            }
        }
    }

    private static void onKuudraEnd(TeslaMapsConfig c, long now) {
        runEnded = true;
        long runMs = kuudraStart > 0 ? now - kuudraStart : 0;
        kuudraStart = 0L;
        if (blockNextRequeue) { blockNextRequeue = false; feedback("a member left this run"); return; }
        if (c.requeueDowntimeList && !needsDowntime.isEmpty()) {
            cancelTitle(String.join(", ", needsDowntime));
            com.teslamaps.features.Announce.party(String.join(", ", needsDowntime) + " needs downtime");
            return;
        }
        if (!c.requeueDowntimeList && downtimeHold) {
            String who = com.teslamaps.features.ChatCommands.downtimeSummary();
            cancelTitle(who.isEmpty() ? dotDt : who);
            if (dotDt != null) { dotDt = null; downtimeHold = false; }
            feedback("someone asked for downtime");
            return;
        }
        if (c.requeueKuudraFastStop > 0 && runMs > 0 && runMs <= c.requeueKuudraFastStop * 1000L) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(5, 40, 10);
            mc.gui.setTitle(Component.literal("§e§lAUTO REQUEUE PAUSED"));
            mc.gui.setSubtitle(Component.literal("§fRun: " + String.format(java.util.Locale.ROOT, "%.2fs", runMs / 1000.0)));
            feedback("fast run (" + String.format(java.util.Locale.ROOT, "%.2fs", runMs / 1000.0) + ")");
            return;
        }
        requeueAt = now + Math.max(1000L, Math.max(0, c.requeueDelaySeconds) * 1000L);
        kuudraLevel = Minecraft.getInstance().level;
    }

    private static void cancelTitle(String who) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(5, 45, 10);
        mc.gui.setTitle(Component.literal("§c§lREQUEUE CANCELLED"));
        mc.gui.setSubtitle(Component.literal("§e" + (who == null || who.isEmpty() ? "DT requested" : who)));
    }

    public static void tick() {
        if (kuudraLevel != null && (requeueAt == 0L || Minecraft.getInstance().level != kuudraLevel)) {
            if (requeueAt != 0L) feedback("left the instance");
            requeueAt = 0L;
            kuudraLevel = null;
        }
        if (requeueAt == 0L || System.currentTimeMillis() < requeueAt) return;
        requeueAt = 0L;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!PartyTracker.mayLead()) { feedback("you are not the party leader"); return; }
        if (c.requeueCheckParty && startSize > 0 && PartyTracker.size() > 0 && startSize != PartyTracker.size()) {
            feedback("party size changed (" + startSize + " -> " + PartyTracker.size() + ")");
            return;
        }
        if (c.requeueMinParty > 1 && Math.max(1, PartyTracker.size()) < c.requeueMinParty) {
            feedback("not enough players in party (" + Math.max(1, PartyTracker.size()) + "/" + c.requeueMinParty + ")");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("instancerequeue");
    }

    private static void feedback(String why) {
        if (TeslaMapsConfig.get().requeueFeedback) local("§7Not requeueing: §f" + why);
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + text));
    }
}
