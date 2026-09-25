package com.teslamaps.features;

// Ported from Devonian's misc/chat/MutePartySpam.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import java.util.List;

public final class MutePartySpam {

    public static final List<String> DEFAULTS = List.of(
            "Bonzo Procced", "Phoenix Procced", "Spirit Procced", "Leaped to ", "Leaping to ", "I'm leaping to ",
            "[Leaped]: ➜", "Gained ", "At ", "at ", "Entering ", "BERS TEAM --> ", "Power: ", "UwUaddons", "Used ",
            "No Shop!", "No Triangle!", "No Equals!", "No Slash!", "No X Cannon!", "No X!", "FRESH", "[IQ]",
            "No xCannon!", "X Cannon", "[Skyblocker]", "Broken", "/tp ", "Zen", "MeowAddons", "!", "[!]");

    private static final long WINDOW_MS = 1500L;
    private static final String PING = "minecraft:entity.experience_orb.pickup";

    private static int pending;
    private static long pendingAt;

    private MutePartySpam() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MutePartySpam").section("Chat").when(() -> cfg().mutePartySpam)
                .chat(MutePartySpam::onChat);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("partyspam")
                        .then(ClientCommands.literal("list").executes(c -> {
                            c.getSource().sendFeedback(Component.literal("§a[TeslaMaps] §7Muted party prefixes: §f"
                                    + String.join("§7, §f", cfg().mutePartySpamPrefixes)));
                            return 1;
                        }))
                        .then(ClientCommands.literal("reset").executes(c -> {
                            cfg().mutePartySpamPrefixes = new java.util.ArrayList<>(DEFAULTS);
                            TeslaMapsConfig.save();
                            c.getSource().sendFeedback(Component.literal("§a[TeslaMaps] §7Party spam list reset"));
                            return 1;
                        }))
                        .then(ClientCommands.literal("add").then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String t = StringArgumentType.getString(c, "text");
                                    if (!cfg().mutePartySpamPrefixes.contains(t)) cfg().mutePartySpamPrefixes.add(t);
                                    TeslaMapsConfig.save();
                                    c.getSource().sendFeedback(Component.literal("§a[TeslaMaps] §7Added §f" + t));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("remove").then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String t = StringArgumentType.getString(c, "text");
                                    boolean ok = cfg().mutePartySpamPrefixes.remove(t);
                                    TeslaMapsConfig.save();
                                    c.getSource().sendFeedback(Component.literal(ok ? "§a[TeslaMaps] §7Removed §f" + t
                                            : "§a[TeslaMaps] §cNot in the list: §f" + t));
                                    return 1;
                                }))))));
    }

    private static void onChat(String raw) {
        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || m.channel() != PlayerChat.Channel.PARTY) return;
        String msg = StringUtil.strip(m.message());
        List<String> list = cfg().mutePartySpamPrefixes;
        for (int i = 0, n = list.size(); i < n; i++) {
            if (msg.startsWith(list.get(i))) {
                long now = System.currentTimeMillis();
                if (now - pendingAt > WINDOW_MS) pending = 0;
                pending++;
                pendingAt = now;
                return;
            }
        }
    }

    public static boolean active(TeslaMapsConfig c) {
        return c.mutePartySpam && pending > 0;
    }

    /** SoundPlayMixin: true = drop this ping. */
    public static boolean shouldMute(SoundInstance s, String id, float vol, float pitch) {
        if (!id.equals(PING) || s.getSource() != SoundSource.PLAYERS || vol != 1f || pitch != 1f) return false;
        if (System.currentTimeMillis() - pendingAt > WINDOW_MS) {
            pending = 0;
            return false;
        }
        pending--;
        return true;
    }
}
