package com.teslamaps.features;

// Ported from Odin's SoopyCommand.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public class SoopyCommand {

    private static final List<String> COMMANDS = List.of(
            "auctions", "bestiary", "bank", "classaverage", "currdungeon", "dojo", "dungeon", "essence", "faction",
            "guildof", "kuudra", "nucleus", "nw", "overflowskillaverage", "overflowskills", "pet", "rtca", "sblvl",
            "secrets", "skillaverage", "skills");
    private static final String URL = "https://soopy.dev/api/soopyv2/botcommand?m=%s&u=%s";
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SoopyCommand")
                .config("Chat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Soopy Commands");
                    b.toggle("/spcmd Soopy Bot Commands", () -> c.soopyCommands, v -> c.soopyCommands = v)
                            .desc("/spcmd <command> [player] asks soopy.dev (third-party) and prints the answer. nw, rtca, bestiary, skills, ... /spcmd help lists all.")
                            .tags("soopy", "spcmd", "networth", "rtca");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            for (String name : new String[]{"spcmd", "soopycmd"}) d.register(node(name));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> node(String name) {
        return ClientCommands.literal(name)
                .executes(c -> help())
                .then(ClientCommands.literal("help").executes(c -> help()))
                .then(ClientCommands.argument("command", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                            for (String s : COMMANDS) if (s.startsWith(typed) && !s.equals(typed)) sb.suggest(s);
                            return sb.buildFuture();
                        })
                        .executes(c -> run(StringArgumentType.getString(c, "command"), null))
                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                .suggests((c, sb) -> {
                                    var conn = Minecraft.getInstance().getConnection();
                                    if (conn == null) return sb.buildFuture();
                                    String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    for (var info : conn.getOnlinePlayers()) {
                                        String n = info.getProfile().name();
                                        if (n.toLowerCase(Locale.ROOT).startsWith(typed) && !n.equalsIgnoreCase(typed)) sb.suggest(n);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(c -> run(StringArgumentType.getString(c, "command"),
                                        StringArgumentType.getString(c, "player")))));
    }

    private static int help() {
        msg("§7/spcmd <command> [player]: §f" + String.join(", ", COMMANDS));
        return 1;
    }

    private static int run(String command, String player) {
        if (!cfg().soopyCommands) {
            msg("§7Soopy commands are off (Chat > Soopy Commands).");
            return 0;
        }
        String cmd = command.toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(cmd)) {
            msg("§cUnknown command. §7/spcmd help");
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        String user = player != null ? player : mc.player != null ? mc.player.getName().getString() : null;
        if (user == null) return 0;
        msg("§7Running command...");
        String url = String.format(URL, URLEncoder.encode(cmd, StandardCharsets.UTF_8), URLEncoder.encode(user, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "TeslaMaps").GET().build();
        CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> mc.execute(() -> {
            if (err != null) msg("§cFailed to fetch data: " + err.getMessage());
            else if (resp.statusCode() != 200) msg("§cFailed to fetch data: HTTP " + resp.statusCode());
            else msg(resp.body().trim());
        }));
        return 1;
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + text));
    }
}
