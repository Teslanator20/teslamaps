package com.teslamaps.features;

// Markdown ported from SkyOcean's MarkdownChat, mute from HypixelSimpleChat's Mute,
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Player-chat extras: markdown, scam hint, mute list, far-away death lines. */
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
    private static boolean pendingMarkdown;
    private static String pendingMsg;
    private static boolean pendingScam;

    private ChatExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChatMarkdown").section("Chat").when(() -> cfg().chatMarkdown)
                .config("Chat", b -> b.toggle("Markdown In Chat", () -> cfg().chatMarkdown, v -> cfg().chatMarkdown = v)
                        .desc("**bold**, *italic*, __underline__, ~~strike~~ and ||spoiler|| in player messages turn into real formatting. Only you see it"));
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
        pendingMarkdown = false;
        pendingScam = false;
        pendingMsg = null;
        if (pm == null || pm.channel() == PlayerChat.Channel.NPC) return;
        TeslaMapsConfig c = cfg();
        String msg = pm.message();
        boolean md = c.chatMarkdown && hasMarkers(msg);
        boolean scam = c.chatScamWarning && !pm.own() && pm.channel() != PlayerChat.Channel.DM_TO && scam(msg);
        if (!md && !scam) return;
        pendingText = text;
        pendingMarkdown = md;
        pendingMsg = msg;
        pendingScam = scam;
    }

    public static Component rewrite(Component message) {
        if (pendingText == null) return message;
        String text = pendingText;
        boolean md = pendingMarkdown, scam = pendingScam;
        String msg = pendingMsg;
        pendingText = null;
        pendingMsg = null;
        String s = message.getString();
        if (!s.equals(text)) return message;
        Component out = message;
        if (md && cfg().chatMarkdown) {
            try {
                Component r = markdown(message, s, msg);
                if (r != null) out = r;
            } catch (RuntimeException ignored) {}
        }
        if (scam && cfg().chatScamWarning) {
            out = Component.empty()
                    .append(Component.literal("⚠ Likely scam » ").withStyle(st -> st.withColor(ChatFormatting.RED).withBold(true)
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                    "§cThis player message imitates a system line.\n§7Hypixel never sends system messages through player chat.")))))
                    .append(out);
        }
        return out;
    }

    private static boolean scam(String msg) {
        String l = StringUtil.strip(msg).toLowerCase(Locale.ROOT);
        for (String k : SCAM) if (l.contains(k)) return true;
        return PADDING.matcher(msg).find();
    }

    private static boolean hasMarkers(String msg) {
        return msg.indexOf('*') >= 0 || msg.contains("__") || msg.contains("~~") || msg.contains("||");
    }

    // ---- markdown ----

    private static final byte BOLD = 1, UNDERLINE = 2, OBFUSCATED = 4, STRIKE = 8, ITALIC = 16;
    private static final char[] DOUBLES = {'*', '_', '|', '~'};
    private static final byte[] DOUBLE_MASK = {BOLD, UNDERLINE, OBFUSCATED, STRIKE};

    private static Component markdown(Component message, String full, String msg) {
        if (!full.endsWith(msg)) return null;
        int start = full.length() - msg.length();
        byte[] mods = new byte[full.length()];
        findSections(mods, start, full, full.length() - 1, (byte) 0);
        boolean any = false;
        for (int i = start; i < mods.length; i++) if (mods[i] != 0) { any = true; break; }
        if (!any) return null;

        List<Style> styles = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        message.visit((style, text) -> {
            styles.add(style);
            parts.add(text);
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        int idx = 0;
        for (int p = 0; p < parts.size(); p++) {
            String part = parts.get(p);
            Style base = styles.get(p);
            byte cur = 0;
            for (int j = 0; j < part.length(); j++, idx++) {
                byte m = idx < mods.length ? mods[idx] : 0;
                if (m == -1) continue;
                if (m != cur && !run.isEmpty()) {
                    out.append(Component.literal(run.toString()).setStyle(apply(base, cur, run.toString())));
                    run.setLength(0);
                }
                cur = m;
                run.append(part.charAt(j));
            }
            if (!run.isEmpty()) {
                out.append(Component.literal(run.toString()).setStyle(apply(base, cur, run.toString())));
                run.setLength(0);
            }
        }
        return out;
    }

    private static Style apply(Style s, byte m, String text) {
        if (m <= 0) return s;
        if ((m & BOLD) != 0) s = s.withBold(true);
        if ((m & ITALIC) != 0) s = s.withItalic(true);
        if ((m & UNDERLINE) != 0) s = s.withUnderlined(true);
        if ((m & STRIKE) != 0) s = s.withStrikethrough(true);
        if ((m & OBFUSCATED) != 0) {
            s = s.withObfuscated(true);
            if (s.getHoverEvent() == null) s = s.withHoverEvent(new HoverEvent.ShowText(Component.literal(text)));
        }
        return s;
    }

    private static void findSections(byte[] mods, int cursor, String content, int spanEnd, byte modifier) {
        char previous = 0;
        int skip = cursor - 1;
        int last = Math.min(spanEnd, content.length() - 1);
        for (int current = cursor; current <= last; current++) {
            if (current <= skip) continue;
            char ch = content.charAt(current);
            mods[current] = modifier;

            String until = null;
            byte fmt = 0;
            for (int f = 0; f < DOUBLES.length; f++) {
                if (ch == DOUBLES[f] && previous == DOUBLES[f]) {
                    until = "" + ch + ch;
                    fmt = DOUBLE_MASK[f];
                    break;
                }
            }
            if (until == null && previous == '*') {
                until = "*";
                fmt = ITALIC;
            }

            if (until != null) {
                previous = 0;
                int end = content.indexOf(until, current + (until.length() > 1 ? 1 : 0));
                if (end == -1 || end > spanEnd) continue;
                skip = end + until.length() - 1;
                if (until.length() > 1) {
                    mods[current] = -1;
                    mods[end + 1] = -1;
                }
                if (current - 1 >= 0) mods[current - 1] = -1;
                findSections(mods, current + until.length() - 1, content, end - 1, (byte) (modifier | fmt));
                mods[end] = -1;
            } else {
                previous = ch;
            }
        }
    }
}
