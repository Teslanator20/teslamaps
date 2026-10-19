package com.teslamaps.features.secretsync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ProfileKeyPair;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only listener on Skyblocker's secret sync websocket. Authenticates with the Mojang
 * profile key pair like Skyblocker does, under the mod id "maps". Never publishes.
 */
class HyskyBridge {
    private static final String AUTH_URL = "https://hysky.de/api/aaron/authenticate";
    private static final String WS_URL = "wss://ws.hysky.de";
    private static final String MOD_ID = "Skyblocker";
    private static final long RETRY_MS = 15 * 60_000;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final SocketClient socket = new SocketClient("SkyblockerSync");

    private static volatile String token;
    private static volatile long tokenExpires;
    private static volatile boolean authBusy;
    private static volatile boolean rejected;
    private static long retryAt;
    private static String subscribed;

    static void tick(String server) {
        if (rejected) return;
        long now = System.currentTimeMillis();
        if (token == null || now > tokenExpires - 60_000) {
            if (!authBusy && now >= retryAt) authenticate();
            return;
        }
        if (!socket.open()) {
            subscribed = null;
            if (!socket.connecting()) {
                socket.connect(WS_URL, Map.of("Authorization", "Bearer " + token, "User-Agent", userAgent()),
                        HyskyBridge::onMessage, () -> {});
            }
            return;
        }
        if (!server.equals(subscribed)) {
            if (subscribed != null) socket.send(payload("unsubscribe", subscribed));
            socket.send(payload("subscribe", server));
            subscribed = server;
            TeslaMaps.debug("[SkyblockerSync] subscribed {}", server);
        }
    }

    static void leave() {
        if (subscribed == null && !socket.open()) return;
        if (subscribed != null) socket.send(payload("unsubscribe", subscribed));
        subscribed = null;
        socket.close();
    }

    private static String payload(String type, String server) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("service", "DUNGEON_SECRETS");
        o.addProperty("serverId", server);
        return o.toString();
    }

    private static void onMessage(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        if (!"response".equals(o.has("type") ? o.get("type").getAsString() : null)) {
            TeslaMaps.debug("[SkyblockerSync] {}", text);
            return;
        }
        if (!o.has("message") || !o.get("message").isJsonObject()) return;
        JsonObject msg = o.getAsJsonObject("message");
        Minecraft.getInstance().execute(() -> SecretSync.handle(msg, "skyblocker"));
    }

    private static void authenticate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;
        authBusy = true;
        mc.getProfileKeyPairManager().prepareKeyPair().thenAcceptAsync(opt -> {
            try {
                if (opt.isEmpty() || opt.get().publicKey().data().hasExpired()) {
                    TeslaMaps.LOGGER.warn("[SkyblockerSync] no profile keys");
                    retryAt = System.currentTimeMillis() + RETRY_MS;
                    return;
                }
                ProfileKeyPair keys = opt.get();
                UUID random = UUID.randomUUID();
                byte[] original = ByteBuffer.allocate(16)
                        .putLong(random.getMostSignificantBits()).putLong(random.getLeastSignificantBits()).array();
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(keys.privateKey());
                sig.update(original);

                JsonObject keyPair = new JsonObject();
                keyPair.addProperty("uuid", mc.getUser().getProfileId().toString());
                keyPair.addProperty("publicKey", Base64.getMimeEncoder().encodeToString(keys.publicKey().data().key().getEncoded()));
                keyPair.addProperty("publicKeySignature", Base64.getEncoder().encodeToString(keys.publicKey().data().keySignature()));
                keyPair.addProperty("expiresAt", keys.publicKey().data().expiresAt().toEpochMilli());
                JsonObject signed = new JsonObject();
                signed.addProperty("original", Base64.getEncoder().encodeToString(original));
                signed.addProperty("signed", Base64.getEncoder().encodeToString(sig.sign()));
                JsonObject body = new JsonObject();
                body.add("keyPair", keyPair);
                body.add("signedData", signed);
                body.addProperty("mod", MOD_ID);
                body.addProperty("minecraftVersion", version("minecraft"));
                body.addProperty("modVersion", version("teslamaps"));

                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(AUTH_URL))
                                .timeout(Duration.ofSeconds(15))
                                .header("Content-Type", "application/json")
                                .header("User-Agent", userAgent())
                                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    TeslaMaps.LOGGER.warn("[SkyblockerSync] auth refused {}: {}", response.statusCode(), response.body());
                    // a 4xx means the mod id is not accepted - do not keep knocking this session
                    if (response.statusCode() >= 400 && response.statusCode() < 500) rejected = true;
                    retryAt = System.currentTimeMillis() + RETRY_MS;
                    return;
                }
                JsonObject info = JsonParser.parseString(response.body()).getAsJsonObject();
                token = info.get("token").getAsString();
                tokenExpires = info.get("expiresAt").getAsLong();
                TeslaMaps.LOGGER.info("[SkyblockerSync] authenticated");
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[SkyblockerSync] auth failed: {}", e.toString());
                retryAt = System.currentTimeMillis() + RETRY_MS;
            } finally {
                authBusy = false;
            }
        });
    }

    private static String userAgent() {
        return MOD_ID + "/" + version("teslamaps") + " (" + version("minecraft") + ")";
    }

    private static String version(String mod) {
        return FabricLoader.getInstance().getModContainer(mod)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
