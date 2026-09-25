package com.teslamaps.features;

// Idea from SkyblockExtras' /sbechat; runs over the cape backend (Supabase)
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.capes.CapeSync;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat between TeslaMaps users. Writes go through the cape edge function (action
 * chat_send, Mojang-authed, so nobody can post under another name); reads poll the
 * public mod_chat table over PostgREST.
 */
public final class ModChat {

    private static final String P = "§d[TM] ";
    private static final long POLL_MS = 4000;
    private static final int MAX_LEN = 200;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private static volatile long lastId = -1;
    private static volatile boolean polling;
    private static volatile boolean warned;
    private static volatile int fails;
    private static long lastPoll;

    private ModChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ModChat").section("Chat")
                .when(() -> cfg().modChat)
                .tick(ModChat::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("TeslaMaps Chat");
                    b.toggle("TeslaMaps User Chat", () -> c.modChat, v -> { c.modChat = v; if (!v) lastId = -1; })
                            .desc("A chat channel between TeslaMaps users, outside of Hypixel. /tmc <message> or /tmap tmc <message>");
                    b.toggle("  Show Help Line on Join", () -> c.modChatJoinMessage, v -> c.modChatJoinMessage = v);
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("tmap").then(send(ClientCommands.literal("tmc"))));
            if (cfg().modChat) d.register(send(ClientCommands.literal("tmc")));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> send(LiteralArgumentBuilder<FabricClientCommandSource> b) {
        return b.then(ClientCommands.argument("message", StringArgumentType.greedyString())
                .executes(c -> send(c.getSource(), StringArgumentType.getString(c, "message"))));
    }

    private static int send(FabricClientCommandSource src, String message) {
        if (!cfg().modChat) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §7Turn on §fChat > TeslaMaps User Chat §7first"));
            return 0;
        }
        if (!CapeSync.enabled()) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §cBackend not configured"));
            return 0;
        }
        String text = StringUtil.strip(message).trim();
        if (text.isEmpty()) return 0;
        if (text.length() > MAX_LEN) text = text.substring(0, MAX_LEN);
        String out = text;
        new Thread(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("action", "chat_send");
            body.addProperty("message", out);
            String result = CapeSync.callFunction(body, "");
            if (!result.isEmpty()) show("§c" + result.replaceFirst("^§c", ""));
            lastPoll = 0;
        }, "TeslaMaps chat send").start();
        return 1;
    }

    private static void show(String line) {
        Minecraft.getInstance().execute(() -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal(P + line));
        });
    }

    private static void tick() {
        if (!CapeSync.enabled()) return;
        long now = System.currentTimeMillis();
        if (polling || now - lastPoll < POLL_MS << Math.min(fails, 6)) return;
        lastPoll = now;
        polling = true;
        boolean first = lastId < 0;
        new Thread(() -> {
            try {
                poll(first);
            } finally {
                polling = false;
            }
        }, "TeslaMaps chat poll").start();
    }

    private static void poll(boolean first) {
        TeslaMapsConfig c = cfg();
        String base = c.capeServerUrl.trim().replaceAll("/+$", "");
        String key = c.capeServerKey.trim();
        String query = first
                ? "/rest/v1/mod_chat?select=id&order=id.desc&limit=1"
                : "/rest/v1/mod_chat?select=id,username,message&id=gt." + lastId + "&order=id.asc&limit=50";
        try {
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(base + query))
                    .timeout(Duration.ofSeconds(10))
                    .header("apikey", key)
                    .header("Authorization", "Bearer " + key)
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                fails++;
                if (!warned) {
                    warned = true;
                    TeslaMaps.LOGGER.warn("Mod chat poll returned {}", resp.statusCode());
                }
                return;
            }
            JsonArray rows = JsonParser.parseString(resp.body()).getAsJsonArray();
            fails = 0;
            if (first) {
                lastId = rows.isEmpty() ? 0 : rows.get(0).getAsJsonObject().get("id").getAsLong();
                if (cfg().modChatJoinMessage) show("§7Connected - /tmc <message> to talk to other TeslaMaps users");
                return;
            }
            List<String> lines = new ArrayList<>();
            for (JsonElement e : rows) {
                JsonObject row = e.getAsJsonObject();
                long id = row.get("id").getAsLong();
                if (id > lastId) lastId = id;
                String user = StringUtil.strip(row.get("username").getAsString());
                String msg = StringUtil.strip(row.get("message").getAsString()).replace('§', '&');
                lines.add("§b" + user + "§7: §f" + msg);
            }
            for (String l : lines) show(l);
        } catch (Exception e) {
            fails++;
            if (!warned) {
                warned = true;
                TeslaMaps.LOGGER.warn("Mod chat poll failed: {}", e.toString());
            }
        }
    }
}
