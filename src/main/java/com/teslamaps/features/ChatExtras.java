package com.teslamaps.features;

// Mute from HypixelSimpleChat's Mute,
// far deaths from SkyHanni's PlayerDeathMessages, scam hints after SkyblockExtras
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Player-chat extras: scam hint, mute list, far-away death lines. */
public final class ChatExtras {

    private static final Pattern DEATH = Pattern.compile("^☠ (\\w+) (.+)$");
    private static final Pattern PADDING = Pattern.compile("[\\s\\u2800\\u3164\\u200b]{12,}");
    private static final String[] SCAM = {
            "you died and lost", "has been kicked from the guild", "kicked you from the guild",
            "was kicked from the guild", "blacklisted", "you are now op", "you are now an operator",
            "friend request from", "[watchdog", "watchdog has banned", "you have been banned",
            "your account has been", "[admin]", "[owner]", "[staff]", "[gm]", "[mod] ", "[helper]",
            "free coins", "coins giveaway", "cheap coins", "selling coins", "discord.gg/", "security alert"};

    private static final Map<String, Long> seen = new HashMap<>();
    private static int ticks;

    private static String pendingText;

    private ChatExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChatScamWarning").section("Chat").when(() -> cfg().chatScamWarning)
                .config("Chat", b -> b.toggle("Scam Warning", () -> cfg().chatScamWarning, v -> cfg().chatScamWarning = v)
                        .desc("Marks player messages that fake a system line (lost coins, guild kick, staff, padding to a new line) with a red \"Likely scam\""));
        Feature.of("ChatMute").section("Chat").when(() -> cfg().chatMute)
                .config("Chat", b -> {
                    b.toggle("Mute Players", () -> cfg().chatMute, v -> cfg().chatMute = v)
                            .desc("Hides every message of the listed players: all chat, party, guild (bridge names too), co-op and DMs");
                    b.searchList("  Muted Players", () -> new ArrayList<>(cfg().chatMutedPlayers),
                            n -> cfg().chatMutedPlayers.contains(n),
                            n -> { if (!cfg().chatMutedPlayers.remove(n)) cfg().chatMutedPlayers.add(n); });
                    b.info("/tmap mute <name> · /tmap unmute <name> · /tmap mute lists them");
                });
        Feature.of("HideFarDeaths").section("Chat").when(() -> cfg().hideFarDeaths)
                .tick(ChatExtras::tick)
                .config("Chat", b -> b.toggle("Hide Far Death Messages", () -> cfg().hideFarDeaths, v -> cfg().hideFarDeaths = v)
                        .desc("Hides \"☠ <player> ...\" lines of players who were not within 25 blocks in the last 30 s. Not in Dungeons or Kuudra"));

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("mute")
                        .executes(c -> {
                            List<String> l = cfg().chatMutedPlayers;
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fMuted players: "
                                    + (l.isEmpty() ? "§7(none)" : "§e" + String.join("§7, §e", l))));
                            return 1;
                        })
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    String n = StringArgumentType.getString(c, "name").trim();
                                    boolean added = cfg().chatMutedPlayers.stream().noneMatch(s -> s.equalsIgnoreCase(n));
                                    if (added) cfg().chatMutedPlayers.add(n);
                                    if (!cfg().chatMute) cfg().chatMute = true;
                                    TeslaMapsConfig.save();
                                    c.getSource().sendFeedback(Component.literal(added ? "§a[TeslaMaps] §7Muted §f" + n
                                            : "§e[TeslaMaps] §7Already muted: §f" + n));
                                    return 1;
                                })))
                .then(ClientCommands.literal("unmute")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    String n = StringArgumentType.getString(c, "name").trim();
                                    boolean removed = cfg().chatMutedPlayers.removeIf(s -> s.equalsIgnoreCase(n));
                                    TeslaMapsConfig.save();
                                    c.getSource().sendFeedback(Component.literal(removed ? "§a[TeslaMaps] §7Unmuted §f" + n
                                            : "§e[TeslaMaps] §7Not muted: §f" + n));
                                    return 1;
                                })))));
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.distanceToSqr(mc.player) > 625) continue;
            seen.put(p.getGameProfile().name(), now);
        }
        if (ticks % 1200 == 0) seen.values().removeIf(t -> now - t > 60_000);
    }

    // ---- hide (ChatMixin, before the chat filter) ----

    public static boolean hide(String text, boolean fromServer, PlayerChat.Msg pm) {
        TeslaMapsConfig c = cfg();
        if (c.chatMute && pm != null && muted(pm)) return true;
        return c.hideFarDeaths && fromServer && pm == null && farDeath(text);
    }

    private static boolean muted(PlayerChat.Msg pm) {
        if (pm.own() || pm.channel() == PlayerChat.Channel.NPC) return false;
        List<String> list = cfg().chatMutedPlayers;
        if (list.isEmpty()) return false;
        String author = pm.author();
        String bridge = null;
        if (pm.channel() == PlayerChat.Channel.GUILD || pm.channel() == PlayerChat.Channel.OFFICER
                || pm.channel() == PlayerChat.Channel.PARTY) {
            String msg = pm.message();
            int i = msg.indexOf(": ");
            if (i > 0 && i <= 32) bridge = msg.substring(0, i).toLowerCase(Locale.ROOT);
        }
        for (String n : list) {
            if (n == null || n.isBlank()) continue;
            if (n.equalsIgnoreCase(author)) return true;
            if (bridge != null && bridge.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean farDeath(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("☠ ")) return false;
        Matcher m = DEATH.matcher(t);
        if (!m.matches()) return false;
        String name = m.group(1);
        if (name.equals("You") || !SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()
                || SkyblockUtils.getArea().toLowerCase(Locale.ROOT).contains("kuudra")) return false;
        var player = Minecraft.getInstance().player;
        if (player != null && player.getGameProfile().name().equalsIgnoreCase(name)) return false;
        Long last = seen.get(name);
        return last == null || System.currentTimeMillis() - last > 30_000;
    }

    // ---- rewrite: decided at HEAD, applied right before the line is stored ----

    /** From ChatMixin once the line is known to stay. */
    public static void prepare(String text, boolean fromServer, PlayerChat.Msg pm) {
        pendingText = null;
        if (pm == null || pm.channel() == PlayerChat.Channel.NPC) return;
        if (cfg().chatScamWarning && !pm.own() && pm.channel() != PlayerChat.Channel.DM_TO && scam(pm.message())) pendingText = text;
    }

    public static Component rewrite(Component message) {
        if (pendingText == null) return message;
        String text = pendingText;
        pendingText = null;
        if (!message.getString().equals(text) || !cfg().chatScamWarning) return message;
        return Component.empty()
                .append(Component.literal("⚠ Likely scam » ").withStyle(st -> st.withColor(ChatFormatting.RED).withBold(true)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                "§cThis player message imitates a system line.\n§7Hypixel never sends system messages through player chat.")))))
                .append(message);
    }

    private static boolean scam(String msg) {
        String l = StringUtil.strip(msg).toLowerCase(Locale.ROOT);
        for (String k : SCAM) if (l.contains(k)) return true;
        return PADDING.matcher(msg).find();
    }
}
