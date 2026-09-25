package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class PartyCategory {

    private PartyCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Party Chat & Alerts");
        b.toggle("Party Chat Commands (!8ball etc)", () -> config.chatCommands, v -> config.chatCommands = v);
        b.toggle("  Commands: Guild Chat", () -> config.chatCommandsGuild, v -> config.chatCommandsGuild = v);
        b.toggle("  Commands: Private Messages", () -> config.chatCommandsDm, v -> config.chatCommandsDm = v);
        b.toggle("  Commands: Co-op Chat", () -> config.chatCommandsCoop, v -> config.chatCommandsCoop = v);
        b.info("Guild/DM/Co-op answer only the harmless ones (8ball cf dice coords ping fps time)");
        b.toggle("  Commands: Whitelist Only", () -> config.chatCommandsWhitelistOnly, v -> config.chatCommandsWhitelistOnly = v);
        b.info("!pt !kick !promote !demote !allinv only from you + /tmap pcmd <name>");
        for (String k : com.teslamaps.features.ChatCommands.KEYS) {
            b.toggle("  !" + k, () -> com.teslamaps.features.ChatCommands.enabled(k), v -> {
                config.chatCommandsDisabled.remove(k);
                if (!v) config.chatCommandsDisabled.add(k);
            });
        }
        for (String k : com.teslamaps.features.ChatCommands.EXTRA_KEYS) {
            b.toggle("  !" + k, () -> com.teslamaps.features.ChatCommands.extraEnabled(k), v -> {
                config.chatCommandsExtraEnabled.remove(k);
                if (v) config.chatCommandsExtraEnabled.add(k);
            }).desc(switch (k) {
                case "tps" -> "Answers with the server TPS";
                case "location" -> "Answers with the island and the scoreboard location";
                case "holding" -> "Answers with the held item's name";
                case "boop" -> "!boop <name> runs /boop <name>";
                case "runs" -> "Answers with the Kuudra runs of this session";
                case "reinvite" -> "Invites the sender again after 5 s (leader only)";
                case "kickoffline" -> "Runs /party kickoffline (leader only)";
                default -> "\"From X: !invite\" in a private message gives a clickable \"invite X\" line with a ping";
            });
        }
        b.toggle("    !invite: Auto Confirm", () -> config.chatCommandsInviteAutoConfirm, v -> config.chatCommandsInviteAutoConfirm = v)
                .desc("Invites straight away instead of the clickable line (respects Whitelist Only)");
        b.toggle("  Downtime Reasons", () -> config.chatCommandsDtReasons, v -> config.chatCommandsDtReasons = v)
                .desc("!dt <reason> stores a reason; at the end of a dungeon or Kuudra run the party gets \"Downtime needed: name (reason)\" and you a title");
        b.toggle("Show PBs on Party Finder Join", () -> config.pbOnJoin, v -> config.pbOnJoin = v);
        b.toggle("  Full Stats Card (hover details)", () -> config.pbOnJoinCard, v -> config.pbOnJoinCard = v);
        b.toggle("  Class + Gear Hover (one-liner)", () -> config.pbOnJoinExtra, v -> config.pbOnJoinExtra = v)
                .desc("Adds the joined class and level, plus a [Gear] hover with armor, weapons and active pet");
        b.info("/cata <name> shows the card for anyone");
        b.toggle("  Party Check Commands", () -> config.partyCheckCommands, v -> config.partyCheckCommands = v)
                .desc("/pcheck shows the PB line of every party member, /gxp [player] the weekly guild EXP list, /ginfo [player] the guild data. Also /tmap pcheck|gxp|ginfo; the short forms need a rejoin after switching on");
        b.info("Key: /tmap apikey <key> (developer.hypixel.net)");
        b.toggle("Party Duplicate Alert", () -> config.partyDuplicateAlert, v -> config.partyDuplicateAlert = v);
        b.toggle("  Dup: Play Sound", () -> config.partyDuplicateSound, v -> config.partyDuplicateSound = v);
        b.toggle("  Dup: Announce to Party", () -> config.partyDuplicateMessage, v -> config.partyDuplicateMessage = v);
        b.toggle("  Dup: Check Before Start + Title", () -> config.partyDuplicatePreStart, v -> config.partyDuplicatePreStart = v);
        b.label("Auto Requeue");
        b.toggle("Auto Requeue (dungeon end)", () -> config.autoRequeue, v -> config.autoRequeue = v);
        b.toggle("Requeue on Party \"r\"", () -> config.requeueOnPartyR, v -> config.requeueOnPartyR = v);
        b.slider("Requeue Delay (s)", 0f, 30f,
                () -> (float) config.requeueDelaySeconds, v -> config.requeueDelaySeconds = Math.round(v));
        b.toggle("Wait for Downtime Players", () -> config.requeueDowntimeList, v -> config.requeueDowntimeList = v);
        b.info("dt / !dt puts a player on the list, r / !r takes them off; requeues once it is empty");
        b.toggle("Skip if Party Size Changed", () -> config.requeueCheckParty, v -> config.requeueCheckParty = v);
        b.toggle("Requeue Feedback in Chat", () -> config.requeueFeedback, v -> config.requeueFeedback = v);
    }
}
