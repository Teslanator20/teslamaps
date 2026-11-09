package com.teslamaps.features;

// Multi invite fix: Skytils' PartyFeatures (multiplePartyInviteFix)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PartyInvites {

    private static final Set<String> SUBCOMMANDS = Set.of("accept", "chat", "demote", "disband", "help", "invite",
            "join", "kick", "kickoffline", "leave", "list", "mute", "poll", "private", "promote", "settings",
            "transfer", "warp", "hijack", "challenge", "digest", "poke", "allinvite", "confirm", "deny");
    private static final Pattern NAME = Pattern.compile("\\w{1,16}");

    private static Feature feature;

    private PartyInvites() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PartyInvites").section("Party")
                .config("Party", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Invites");
                    b.toggle("Multi Party Invite Fix", () -> c.multiPartyInviteFix, v -> c.multiPartyInviteFix = v)
                            .desc("/p name1 name2 name3 is sent as one /p invite per player, avoids Hypixel's ghost party bug");
                });
        ClientSendMessageEvents.ALLOW_COMMAND.register(PartyInvites::allowCommand);
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
}
