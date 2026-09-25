package com.teslamaps.features;

// Own name: HypixelSimpleChat's highlightSelf. Badges: SkyOcean's ProfileInChat + Skysoft's PlayerBadges.
// Piggy link: SkyOcean's PiggyBankRepairHelper. Join/leave: Zen's FriendJoinMessage/GuildJoinMessage.
// Explosive shot: NoammAddons' ChatFeatures. Bridge: DulkirMod's Bridge.
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatRewrites {

    private ChatRewrites() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Pattern PIGGY = Pattern.compile(
            "^You died(?: and your piggy bank cracked!|, lost [\\d.,]* coins and your piggy bank broke!)$");
    private static final Pattern EXPLOSIVE = Pattern.compile(
            "^Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,.]+) damage\\.$");
    private static final Pattern JOIN_LEAVE = Pattern.compile("^(Friend|Guild) > ([A-Za-z0-9_]{1,16}) (joined|left)\\.$");
    private static final Pattern BRIDGE = Pattern.compile(
            "^(?:Guild|Officer) > (?:\\[[^\\]]{1,20}] )?([A-Za-z0-9_]{1,16})(?: \\[[^\\]]{1,20}])?: (.{1,40}?)(?: » | > |: )(.*)$",
            Pattern.DOTALL);
    private static final Pattern TAB_NAME = Pattern.compile(
            "^(?:\\[\\d{1,4}] )?(?:[^\\w\\s\\[]{1,2} )?(?:\\[[^\\]]{1,20}] )?([A-Za-z0-9_]{1,16})");

    private static final String BADGE_CHARS = "♲☀Ⓑ⚒ቾ";
    private static final long BADGE_TTL = 10 * 60_000L;

    private record Badge(Component symbols, long seen) {}

    private static final Map<String, Badge> BADGES = new HashMap<>();
    private static Object badgeLevel;
    private static int badgeTicks;

    public static void register() {
        Feature.of("ProfileBadges").section("Chat").when(() -> cfg().chatProfileBadges)
                .tick(ChatRewrites::tickBadges);
        Feature.of("PiggyRepairLink").section("Chat").when(() -> cfg().piggyRepairLink)
                .serverChat(ChatRewrites::onPiggy);
        Feature.of("ExplosiveShotPerEnemy").section("Chat").when(() -> cfg().explosiveShotPerEnemy)
                .serverChat(ChatRewrites::onExplosive)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Chat Extras");
                    b.toggle("Profile Badges In Chat", () -> c.chatProfileBadges, v -> c.chatProfileBadges = v)
                            .desc("Adds the ♲ Ironman / Ⓑ Bingo / ☀ Stranded symbol (and Crimson faction) from the tab list behind the sender's name");
                    b.toggle("Compact Friend/Guild Join & Leave", () -> c.compactJoinLeave, v -> c.compactJoinLeave = v)
                            .desc("\"Friend > Name joined.\" becomes \"F >> Name\", left = \"F << Name\"; same with G for the guild");
                    b.toggle("Piggy Bank Repair Link", () -> c.piggyRepairLink, v -> c.piggyRepairLink = v)
                            .desc("When your piggy bank cracks or breaks, a clickable line runs /gfs ENCHANTED_PORK 8");
                    b.toggle("Explosive Shot Damage Per Enemy", () -> c.explosiveShotPerEnemy, v -> c.explosiveShotPerEnemy = v)
                            .desc("Adds a line with the damage per enemy after \"Your Explosive Shot hit N enemies\"");
                    b.toggle("Guild Bridge Formatting", () -> c.bridgeFormat, v -> c.bridgeFormat = v)
                            .desc("Guild lines of your bridge bot (\"Guild > Bot: Name » text\") become \"Bridge > Name: text\". Set the bot with /tmap bridgebot <name>");
                    b.info("  Bridge bot: /tmap bridgebot <name>");
                    b.color("  Bridge Name Colour", () -> c.bridgeNameColor, v -> c.bridgeNameColor = v);
                    b.toggle("Log Search (/tmap logsearch)", () -> c.logSearch, v -> c.logSearch = v)
                            .desc("Searches the chat of every log file, zipped ones too. Text or /regex/");
                });
    }

    // ---- server chat triggers ----

    private static void onPiggy(String text) {
        if (!PIGGY.matcher(StringUtil.stripTrim(text)).matches()) return;
        TickScheduler.schedule(1, () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eClick here to get 8 Enchanted Pork from your sacks.")
                    .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/gfs ENCHANTED_PORK 8"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7/gfs ENCHANTED_PORK 8")))));
        });
    }

    private static void onExplosive(String text) {
        Matcher m = EXPLOSIVE.matcher(StringUtil.stripTrim(text));
        if (!m.matches()) return;
        int hits;
        double total;
        try {
            hits = Integer.parseInt(m.group(1));
            total = Double.parseDouble(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        long per = (long) (hits > 0 ? total / hits : total);
        String msg = "§b[TeslaMaps] §aExplosive shot did §e" + String.format(Locale.ROOT, "%,d", per) + "§a damage per enemy.";
        TickScheduler.schedule(1, () -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(Component.literal(msg));
        });
    }

    // ---- tab list badges ----

    private static void tickBadges() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != badgeLevel) {
            badgeLevel = mc.level;
            BADGES.clear();
        }
        if (++badgeTicks < 40 || mc.getConnection() == null) return;
        badgeTicks = 0;
        long now = System.currentTimeMillis();
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display != null) scanTabName(display, now);
            PlayerTeam team = info.getTeam();
            if (team != null) {
                Component b = badgesIn(team.getPlayerSuffix(), 0);
                if (b != null) BADGES.put(info.getProfile().name().toLowerCase(Locale.ROOT), new Badge(b, now));
            }
        }
        Iterator<Badge> it = BADGES.values().iterator();
        while (it.hasNext()) if (now - it.next().seen > BADGE_TTL) it.remove();
    }

    private static void scanTabName(Component display, long now) {
        String plain = plainOf(display);
        if (!hasBadge(plain)) return;
        Matcher m = TAB_NAME.matcher(plain);
        if (!m.find()) return;
        Component b = badgesIn(display, m.end(1));
        if (b != null) BADGES.put(m.group(1).toLowerCase(Locale.ROOT), new Badge(b, now));
    }

    private static boolean hasBadge(String s) {
        for (int i = 0; i < s.length(); i++) if (BADGE_CHARS.indexOf(s.charAt(i)) >= 0) return true;
        return false;
    }

    /** Badge symbols from char index {@code from} on, with their colours; null if none. */
    private static Component badgesIn(Component c, int from) {
        if (c == null) return null;
        MutableComponent out = Component.empty();
        int[] idx = {0};
        boolean[] any = {false};
        StringDecomposer.iterateFormatted(c, Style.EMPTY, (pos, style, cp) -> {
            int i = idx[0];
            idx[0] += Character.charCount(cp);
            if (i >= from && cp < 0x10000 && BADGE_CHARS.indexOf((char) cp) >= 0) {
                if (any[0]) out.append(Component.literal(" "));
                out.append(Component.literal(Character.toString(cp)).withStyle(style));
                any[0] = true;
            }
            return true;
        });
        return any[0] ? out : null;
    }

    // ---- line rewrites (ChatMixin, right before the line is stored) ----

    private record Run(Style style, String text) {}

    public static Component rewrite(Component message) {
        TeslaMapsConfig c = cfg();
        boolean bridge = c.bridgeFormat && c.bridgeBotName != null && !c.bridgeBotName.isBlank();
        if (!(c.chatHighlightOwnName || c.chatProfileBadges || c.compactJoinLeave || bridge) || !c.section("Chat")) return message;
        try {
            return rewriteInner(message, c, bridge);
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static Component rewriteInner(Component message, TeslaMapsConfig c, boolean bridge) {
        String raw = message.getString();
        String stripped = StringUtil.stripTrim(raw);
        boolean imageLine = SharedImageChat.urlForLine(raw) != null;

        if (c.compactJoinLeave && (stripped.startsWith("Friend > ") || stripped.startsWith("Guild > "))) {
            Component compact = compactJoin(message);
            if (compact != null) return compact;
        }

        Component out = message;
        if (bridge && !imageLine && (stripped.startsWith("Guild > ") || stripped.startsWith("Officer > "))) {
            Component b = bridge(out, c);
            if (b != null) out = b;
        }
        if (c.chatProfileBadges && !imageLine && !BADGES.isEmpty()) {
            Component b = addBadge(out);
            if (b != null) out = b;
        }
        if (c.chatHighlightOwnName) {
            Component h = highlightOwn(out, c);
            if (h != null) out = h;
        }
        return out;
    }

    private static Component compactJoin(Component message) {
        List<Run> runs = flatten(message);
        String plain = plain(runs);
        int lead = 0;
        while (lead < plain.length() && plain.charAt(lead) == ' ') lead++;
        Matcher m = JOIN_LEAVE.matcher(plain.substring(lead).stripTrailing());
        if (!m.matches()) return null;
        boolean joined = m.group(3).equals("joined");
        String tag = (m.group(1).equals("Friend") ? "F" : "G") + (joined ? " >> " : " << ");
        MutableComponent out = Component.literal(tag).withStyle(joined ? ChatFormatting.GREEN : ChatFormatting.RED);
        out.append(build(slice(runs, lead + m.start(2), lead + m.end(2))));
        return out.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(message)));
    }

    private static Component bridge(Component message, TeslaMapsConfig c) {
        List<Run> runs = flatten(message);
        String plain = plain(runs);
        int lead = 0;
        while (lead < plain.length() && plain.charAt(lead) == ' ') lead++;
        Matcher m = BRIDGE.matcher(plain.substring(lead));
        if (!m.matches() || !m.group(1).equalsIgnoreCase(c.bridgeBotName.trim())) return null;
        String name = m.group(2).trim();
        if (name.isEmpty()) return null;
        int color = TeslaMapsConfig.parseColor(c.bridgeNameColor) & 0xFFFFFF;
        MutableComponent out = Component.literal("Bridge > ").withStyle(ChatFormatting.DARK_GREEN);
        out.append(Component.literal(name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        out.append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
        out.append(build(slice(runs, lead + m.start(3), plain.length())));
        return out;
    }

    private static Component addBadge(Component message) {
        PlayerChat.Msg msg = PlayerChat.parse(message);
        if (msg == null || msg.channel() == PlayerChat.Channel.DM_TO || msg.channel() == PlayerChat.Channel.NPC) return null;
        Badge badge = BADGES.get(msg.author().toLowerCase(Locale.ROOT));
        if (badge == null) return null;
        List<Run> runs = flatten(message);
        String plain = plain(runs);
        int sep = plain.indexOf(": ");
        if (sep < 0) return null;
        int at = plain.lastIndexOf(msg.author(), sep);
        if (at < 0) return null;
        int end = at + msg.author().length();
        if (hasBadge(plain.substring(end, sep))) return null;
        MutableComponent out = build(slice(runs, 0, end));
        out.append(Component.literal(" ").append(badge.symbols));
        out.append(build(slice(runs, end, plain.length())));
        return out;
    }

    private static Component highlightOwn(Component message, TeslaMapsConfig c) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        String name = player.getGameProfile().name();
        if (name == null || name.isEmpty()) return null;
        if (!message.getString().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return null;
        List<Run> runs = flatten(message);
        String plain = plain(runs);
        String lower = plain.toLowerCase(Locale.ROOT), needle = name.toLowerCase(Locale.ROOT);
        List<int[]> hits = new ArrayList<>();
        for (int i = lower.indexOf(needle); i >= 0; i = lower.indexOf(needle, i + needle.length())) {
            int e = i + needle.length();
            if (i > 0 && isNameChar(plain.charAt(i - 1))) continue;
            if (e < plain.length() && isNameChar(plain.charAt(e))) continue;
            hits.add(new int[]{i, e});
        }
        if (hits.isEmpty()) return null;
        TextColor color = TextColor.fromRgb(TeslaMapsConfig.parseColor(c.chatOwnNameColor) & 0xFFFFFF);
        MutableComponent out = Component.empty();
        int pos = 0;
        for (int[] h : hits) {
            out.append(build(slice(runs, pos, h[0])));
            for (Run r : slice(runs, h[0], h[1])) out.append(Component.literal(r.text).setStyle(r.style.withColor(color)));
            pos = h[1];
        }
        out.append(build(slice(runs, pos, plain.length())));
        return out;
    }

    private static boolean isNameChar(char ch) {
        return ch == '_' || Character.isLetterOrDigit(ch);
    }

    // ---- flat runs: § codes resolved into styles ----

    private static List<Run> flatten(Component c) {
        List<Run> runs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Style[] cur = {null};
        StringDecomposer.iterateFormatted(c, Style.EMPTY, (pos, style, cp) -> {
            if (cur[0] != null && !cur[0].equals(style)) {
                runs.add(new Run(cur[0], sb.toString()));
                sb.setLength(0);
            }
            cur[0] = style;
            sb.appendCodePoint(cp);
            return true;
        });
        if (cur[0] != null && !sb.isEmpty()) runs.add(new Run(cur[0], sb.toString()));
        return runs;
    }

    private static String plainOf(Component c) {
        StringBuilder sb = new StringBuilder();
        StringDecomposer.iterateFormatted(c, Style.EMPTY, (pos, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    private static String plain(List<Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (Run r : runs) sb.append(r.text);
        return sb.toString();
    }

    private static List<Run> slice(List<Run> runs, int from, int to) {
        List<Run> out = new ArrayList<>();
        int off = 0;
        for (Run r : runs) {
            int s = off, e = off + r.text.length();
            off = e;
            int a = Math.max(s, from), b = Math.min(e, to);
            if (a < b) out.add(new Run(r.style, r.text.substring(a - s, b - s)));
        }
        return out;
    }

    private static MutableComponent build(List<Run> runs) {
        MutableComponent out = Component.empty();
        for (Run r : runs) out.append(Component.literal(r.text).setStyle(r.style));
        return out;
    }

    // ---- /tmap bridgebot ----

    public static LiteralArgumentBuilder<FabricClientCommandSource> bridgeCommand() {
        return ClientCommands.literal("bridgebot")
                .executes(ctx -> {
                    String bot = cfg().bridgeBotName;
                    ctx.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7Bridge bot: §f"
                            + (bot == null || bot.isBlank() ? "§8(none)" : bot) + " §7- /tmap bridgebot <name> | clear"));
                    return 1;
                })
                .then(ClientCommands.literal("clear").executes(ctx -> {
                    cfg().bridgeBotName = "";
                    TeslaMapsConfig.save();
                    ctx.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7Bridge bot cleared"));
                    return 1;
                }))
                .then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name").trim();
                    cfg().bridgeBotName = name;
                    TeslaMapsConfig.save();
                    ctx.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7Bridge bot set to §f" + name
                            + (cfg().bridgeFormat ? "" : " §8(Guild Bridge Formatting is off, Chat settings)")));
                    return 1;
                }));
    }
}
