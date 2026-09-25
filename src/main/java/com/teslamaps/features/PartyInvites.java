package com.teslamaps.features;

// Multi invite fix: Skytils' PartyFeatures (multiplePartyInviteFix). Boop invite: SkyHanni's PartyOnBoop.kt. /rboop: SkyOcean's ReplyBoop.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyInvites {

    private static final Set<String> SUBCOMMANDS = Set.of("accept", "chat", "demote", "disband", "help", "invite",
            "join", "kick", "kickoffline", "leave", "list", "mute", "poll", "private", "promote", "settings",
            "transfer", "warp", "hijack", "challenge", "digest", "poke", "allinvite", "confirm", "deny");
    private static final Pattern NAME = Pattern.compile("\\w{1,16}");
    private static final Pattern DM = Pattern.compile("^From (?!stash)(?:\\[[^]]+] )?(\\w{1,16}): (.*)$");
    private static final long REPLY_WINDOW_MS = 5 * 60_000L;

    private static Feature feature;
    private static String lastSender;
    private static long lastSenderAt;

    private PartyInvites() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PartyInvites").section("Party")
                .chat(PartyInvites::onChat)
                .config("Party", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Invites & Boop");
                    b.toggle("Multi Party Invite Fix", () -> c.multiPartyInviteFix, v -> c.multiPartyInviteFix = v)
                            .desc("/p name1 name2 name3 is sent as one /p invite per player, avoids Hypixel's ghost party bug");
                    b.toggle("Party On Boop", () -> c.partyOnBoop, v -> c.partyOnBoop = v)
                            .desc("When someone boops you, a clickable line offers to invite them to your party");
                    b.toggle("  Only On Bingo Profiles", () -> c.partyOnBoopBingoOnly, v -> c.partyOnBoopBingoOnly = v);
                    b.toggle("/rboop Command", () -> c.replyBoop, v -> c.replyBoop = v)
                            .desc("/rboop boops the last player who messaged you (last 5 minutes)");
                });
        ClientSendMessageEvents.ALLOW_COMMAND.register(PartyInvites::allowCommand);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) ->
                d.register(ClientCommands.literal("rboop").executes(x -> { replyBoop(); return 1; })));
    }

    private static boolean allowCommand(String command) {
        if (command == null || !cfg().multiPartyInviteFix || feature == null || !feature.active()) return true;
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 3) return true;
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!root.equals("p") && !root.equals("party")) return true;
        if (SUBCOMMANDS.contains(parts[1].toLowerCase(Locale.ROOT))) return true;
        for (int i = 1; i < parts.length; i++) if (!NAME.matcher(parts[i]).matches()) return true;
        for (int i = 1; i < parts.length; i++) Announce.command("p invite " + parts[i]);
        return false;
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (!s.startsWith("From ")) return;
        Matcher m = DM.matcher(s);
        if (!m.matches()) return;
        String name = m.group(1);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || name.equalsIgnoreCase(mc.getUser().getName())) return;
        lastSender = name;
        lastSenderAt = System.currentTimeMillis();
        if (!cfg().partyOnBoop || !m.group(2).trim().equals("Boop!")) return;
        if (cfg().partyOnBoopBingoOnly && !bingo()) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eClick to invite §f" + name + " §eto the party!")
                .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/party invite " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("/party invite " + name)))));
    }

    private static boolean bingo() {
        for (String l : ScoreboardUtils.getScoreboardLines()) if (l.contains("Ⓑ")) return true;
        return false;
    }

    private static void replyBoop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!cfg().replyBoop) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7/rboop is off (Party > Invites & Boop)."));
            return;
        }
        if (lastSender == null || System.currentTimeMillis() - lastSenderAt > REPLY_WINDOW_MS) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cNobody messaged you in the last 5 minutes."));
            return;
        }
        ChatQueue.command("boop " + lastSender);
    }
}
