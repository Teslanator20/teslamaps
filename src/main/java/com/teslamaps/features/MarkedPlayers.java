package com.teslamaps.features;

// Ported from SkyHanni's MarkedPlayerManager.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class MarkedPlayers {

    private static final String P = "§b[TeslaMaps] ";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static final List<Player> inWorld = new ArrayList<>();
    private static final Set<String> inLobby = new HashSet<>();
    private static boolean lobbyKnown;
    private static int tabTimer;
    private static Object lastLevel;

    private MarkedPlayers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MarkedPlayers").section("Party")
                .when(() -> cfg().markedPlayers && !cfg().markedPlayerNames.isEmpty())
                .tick(MarkedPlayers::tick)
                .world(MarkedPlayers::render)
                .config("Party", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Marked Players");
                    b.toggle("Marked Players", () -> c.markedPlayers, v -> c.markedPlayers = v)
                            .desc("/tmap mark <name> toggles a player. Marked players are highlighted in the world and in chat");
                    b.toggle("  Highlight in World", () -> c.markedPlayersWorld, v -> c.markedPlayersWorld = v).gatedBy("Party");
                    b.espStyle("  Marked Player", () -> c.markedPlayersStyle);
                    b.toggle("  Color Name in Chat", () -> c.markedPlayersChat, v -> c.markedPlayersChat = v).gatedBy("Party");
                    b.color("    Chat Color", () -> c.markedPlayersChatColor, v -> c.markedPlayersChatColor = v);
                    b.toggle("  Lobby Join / Leave Message", () -> c.markedPlayersJoinLeave, v -> c.markedPlayersJoinLeave = v)
                            .gatedBy("Party").desc("Tells you when a marked player enters or leaves your lobby (tab list)");
                    b.info("/tmap mark <name> | /tmap mark list | /tmap mark clear");
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("mark")
                        .executes(c -> list())
                        .then(ClientCommands.literal("list").executes(c -> list()))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            cfg().markedPlayerNames.clear();
                            TeslaMapsConfig.save();
                            feedback("§7Marked players cleared");
                            return 1;
                        }))
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .suggests((c, sb) -> {
                                    var conn = Minecraft.getInstance().getConnection();
                                    String rem = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    if (conn != null) for (PlayerInfo i : conn.getOnlinePlayers()) {
                                        String n = i.getProfile().name();
                                        if (NAME.matcher(n).matches() && n.toLowerCase(Locale.ROOT).startsWith(rem) && !n.equalsIgnoreCase(rem)) sb.suggest(n);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(c -> toggle(StringArgumentType.getString(c, "name")))))));
    }

    private static void feedback(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal(P + s));
    }

    private static int list() {
        Set<String> names = cfg().markedPlayerNames;
        feedback(names.isEmpty() ? "§7No marked players" : "§7Marked: §e" + String.join("§7, §e", names));
        return 1;
    }

    private static int toggle(String name) {
        if (!NAME.matcher(name).matches()) { feedback("§cNot a player name"); return 0; }
        String low = name.toLowerCase(Locale.ROOT);
        Set<String> names = cfg().markedPlayerNames;
        if (names.remove(low)) feedback("§cUnmarked §b" + name);
        else {
            names.add(low);
            feedback("§aMarked §b" + name + (cfg().markedPlayers ? "" : " §7(Party > Marked Players is off)"));
        }
        TeslaMapsConfig.save();
        return 1;
    }

    private static boolean marked(String name) {
        return cfg().markedPlayerNames.contains(name.toLowerCase(Locale.ROOT));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        inWorld.clear();
        if (cfg().markedPlayersWorld) {
            EspStyle style = EspStyle.of(cfg().markedPlayersStyle);
            for (Player p : mc.level.players()) {
                if (p == mc.player || !marked(p.getGameProfile().name())) continue;
                inWorld.add(p);
                style.tickEntity(p);
            }
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            inLobby.clear();
            lobbyKnown = false;
            tabTimer = 60;   // tab fills after the join
        }
        if (!cfg().markedPlayersJoinLeave || --tabTimer > 0) return;
        tabTimer = 40;
        var conn = mc.getConnection();
        if (conn == null) return;
        Set<String> now = new HashSet<>();
        for (PlayerInfo i : conn.getOnlinePlayers()) {
            if (i.getProfile().id().version() != 4) continue;
            String n = i.getProfile().name();
            if (marked(n) && !n.equalsIgnoreCase(mc.getUser().getName())) now.add(n);
        }
        if (lobbyKnown) {
            for (String n : now) if (!inLobby.contains(n)) feedback("§b" + n + " §ajoined your lobby");
            for (String n : inLobby) if (!now.contains(n)) feedback("§b" + n + " §cleft your lobby");
        } else if (!now.isEmpty()) {
            feedback("§7In this lobby: §b" + String.join("§7, §b", now));
        }
        lobbyKnown = true;
        inLobby.clear();
        inLobby.addAll(now);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (inWorld.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().markedPlayersStyle);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Player p : inWorld) if (!p.isRemoved()) style.drawEntity(pose, p, cam, pt);
    }

    private record Seg(String text, Style style) {}

    /** ChatMixin, right before the line is stored. */
    public static Component rewrite(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.markedPlayers || !c.markedPlayersChat || c.markedPlayerNames.isEmpty() || !c.section("Party")) return message;
        try {
            return color(message, c);
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static Component color(Component message, TeslaMapsConfig c) {
        String plain = message.getString();
        String low = plain.toLowerCase(Locale.ROOT);
        boolean[] hit = null;
        for (String n : c.markedPlayerNames) {
            int from = 0, i;
            while ((i = low.indexOf(n, from)) >= 0) {
                int end = i + n.length();
                from = end;
                if (i > 0 && isNameChar(low.charAt(i - 1)) && !(i >= 2 && low.charAt(i - 2) == '§')) continue;
                if (end < low.length() && isNameChar(low.charAt(end))) continue;
                if (hit == null) hit = new boolean[plain.length()];
                for (int k = i; k < end; k++) hit[k] = true;
            }
        }
        if (hit == null) return message;

        List<Seg> segs = new ArrayList<>();
        message.visit((style, text) -> {
            segs.add(new Seg(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        TextColor color = TextColor.fromRgb(TeslaMapsConfig.parseColor(c.markedPlayersChatColor) & 0xFFFFFF);
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Seg s : segs) {
            String t = s.text;
            int start = 0;
            for (int k = 1; k <= t.length(); k++) {
                boolean cut = k == t.length() || at(hit, pos + k) != at(hit, pos + k - 1);
                if (!cut) continue;
                Style st = at(hit, pos + start) ? s.style.withColor(color) : s.style;
                out.append(Component.literal(t.substring(start, k)).withStyle(st));
                start = k;
            }
            pos += t.length();
        }
        return pos == plain.length() ? out : message;
    }

    private static boolean at(boolean[] hit, int i) {
        return i >= 0 && i < hit.length && hit[i];
    }

    private static boolean isNameChar(char ch) {
        return ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }
}
