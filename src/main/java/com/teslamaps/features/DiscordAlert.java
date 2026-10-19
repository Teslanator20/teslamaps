package com.teslamaps.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bot DMs; "ring" = re-ping every few seconds until the user answers in the DM. */
public final class DiscordAlert {

    private static final String API = "https://discord.com/api/v10";
    private static final long RING_EVERY_MS = 10_000L;
    private static final long POLL_EVERY_MS = 3_000L;
    private static final long RING_MAX_MS = 15 * 60_000L;

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeslaMaps-Discord");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean ringing;
    private static String dmChannel;
    private static String dmFor;

    private DiscordAlert() {}

    public static boolean configured() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return !c.discordBotToken.isBlank() && !c.discordUserId.isBlank();
    }

    public static boolean isRinging() { return ringing; }

    public static void stop() { ringing = false; }

    public static void message(String text) {
        if (!configured()) return;
        EXEC.submit(() -> {
            try {
                send(channel(), text);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Discord] dm failed: {}", e.getMessage());
            }
        });
    }

    public static void ring(String text) {
        if (!configured() || ringing) return;
        ringing = true;
        EXEC.submit(() -> {
            try {
                ringLoop(text);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Discord] ring failed: {}", e.getMessage());
            } finally {
                ringing = false;
            }
        });
    }

    private static void ringLoop(String text) throws Exception {
        String user = TeslaMapsConfig.get().discordUserId.trim();
        String ch = channel();
        String ping = "<@" + user + "> " + text + " (reply to stop)";
        String after = send(ch, ping);
        long start = System.currentTimeMillis();
        long lastSend = start;
        while (ringing && System.currentTimeMillis() - start < RING_MAX_MS) {
            Thread.sleep(POLL_EVERY_MS);
            if (!ringing) return;
            if (after != null && userReplied(ch, after, user)) {
                ringing = false;
                send(ch, "Ok, stopped.");
                return;
            }
            if (System.currentTimeMillis() - lastSend >= RING_EVERY_MS) {
                String id = send(ch, ping);
                if (after == null) after = id;
                lastSend = System.currentTimeMillis();
            }
        }
    }

    private static boolean userReplied(String ch, String after, String user) throws Exception {
        JsonElement body = request("GET", "/channels/" + ch + "/messages?limit=50&after=" + after, null);
        if (body == null || !body.isJsonArray()) return false;
        JsonArray msgs = body.getAsJsonArray();
        for (JsonElement m : msgs) {
            JsonObject author = m.getAsJsonObject().getAsJsonObject("author");
            if (author != null && user.equals(author.get("id").getAsString())) return true;
        }
        return false;
    }

    private static synchronized String channel() throws Exception {
        String user = TeslaMapsConfig.get().discordUserId.trim();
        if (dmChannel != null && user.equals(dmFor)) return dmChannel;
        JsonObject req = new JsonObject();
        req.addProperty("recipient_id", user);
        JsonElement body = request("POST", "/users/@me/channels", req);
        if (body == null) throw new IllegalStateException("could not open DM");
        dmChannel = body.getAsJsonObject().get("id").getAsString();
        dmFor = user;
        return dmChannel;
    }

    /** Message id, or null on failure. */
    private static String send(String ch, String text) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("content", text);
        JsonElement body = request("POST", "/channels/" + ch + "/messages", req);
        return body == null ? null : body.getAsJsonObject().get("id").getAsString();
    }

    private static JsonElement request(String method, String path, JsonObject json) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + TeslaMapsConfig.get().discordBotToken.trim())
                    .header("User-Agent", "DiscordBot (teslamaps, 1)");
            if (json != null) {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.toString()));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 429) {
                double wait = 1;
                try { wait = JsonParser.parseString(resp.body()).getAsJsonObject().get("retry_after").getAsDouble(); }
                catch (Exception ignored) {}
                Thread.sleep((long) (wait * 1000) + 100);
                continue;
            }
            if (resp.statusCode() / 100 != 2) {
                TeslaMaps.LOGGER.warn("[Discord] {} {} -> {} {}", method, path.replaceAll("\\?.*", ""),
                        resp.statusCode(), resp.body());
                return null;
            }
            return JsonParser.parseString(resp.body());
        }
        return null;
    }
}
