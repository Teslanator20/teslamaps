package com.teslamaps.features.capes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Shared capes between TeslaMaps users, backed by a Supabase project: cape_users
 * via PostgREST (anon read, RLS), textures in the public "capes" storage bucket,
 * writes through the "cape" edge function which verifies the vanilla Mojang
 * joinServer handshake - no password, no token stored.
 */
public class CapeSync {

    private static final Path CACHE_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("cape_cache");

    private static final long POLL_MS = 120_000;
    private static final long AUTH_REUSE_MS = 240_000;   // mojang session stays valid a while
    private static final int MAX_TEXTURE_BYTES = 2 * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** uuid (no dashes) -> capeId, plus hash for customs. Swapped whole on the main thread. */
    private record Remote(String capeId, String hash) {}
    private static volatile Map<String, Remote> remote = Map.of();

    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    /** Rebuilt per tick; the render path only does this one map get per avatar. */
    private static volatile Map<Integer, Identifier> byEntityId = Map.of();

    private static long lastPoll;
    private static volatile boolean polling;

    /** Cached handshake, reused while the server-side session check still passes. */
    private static String authServerId;
    private static long authTime;

    private static String baseUrl() {
        String url = TeslaMapsConfig.get().capeServerUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String anonKey() {
        return TeslaMapsConfig.get().capeServerKey.trim();
    }

    private static boolean enabled() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return !cfg.capeServerUrl.isBlank() && !cfg.capeServerKey.isBlank() && cfg.section("Render");
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || mc.level == null || mc.player == null) {
            if (!byEntityId.isEmpty()) byEntityId = Map.of();
            return;
        }

        long now = System.currentTimeMillis();
        if (!polling && now - lastPoll > POLL_MS) {
            lastPoll = now;
            poll();
        }

        Map<String, Remote> capes = remote;
        if (capes.isEmpty()) {
            if (!byEntityId.isEmpty()) byEntityId = Map.of();
            return;
        }

        Map<Integer, Identifier> next = new HashMap<>();
        int self = mc.player.getId();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getId() == self) continue;   // own cape stays the local pick
            Remote entry = capes.get(player.getUUID().toString().replace("-", ""));
            if (entry == null) continue;
            Identifier texture = resolve(entry);
            if (texture != null) next.put(player.getId(), texture);
        }
        byEntityId = next;
    }

    /** Called from CapeManager.textureFor for every non-local avatar, so one map get only. */
    public static Identifier textureFor(int entityId) {
        return byEntityId.get(entityId);
    }

    private static Identifier resolve(Remote entry) {
        if (entry.hash() == null) {
            CapeManager.Cape builtin = CapeManager.byId(entry.capeId());
            return builtin != null && !builtin.custom() ? builtin.texture() : null;
        }
        Identifier known = TEXTURES.get(entry.hash());
        if (known != null) return known;
        if (FAILED.containsKey(entry.hash())) return null;
        download(entry.hash());
        return null;
    }

    /** RLS only exposes active, non-banned rows, so this is a plain anon select. */
    private static void poll() {
        polling = true;
        new Thread(() -> {
            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create(
                                        baseUrl() + "/rest/v1/cape_users?select=uuid,cape_id,hash"))
                                .timeout(Duration.ofSeconds(20))
                                .header("apikey", anonKey())
                                .header("Authorization", "Bearer " + anonKey())
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return;
                JsonArray rows = JsonParser.parseString(response.body()).getAsJsonArray();
                Map<String, Remote> next = new HashMap<>();
                for (JsonElement element : rows) {
                    if (!element.isJsonObject()) continue;
                    JsonObject row = element.getAsJsonObject();
                    String uuid = string(row, "uuid");
                    String capeId = string(row, "cape_id");
                    String hash = string(row, "hash");
                    if (uuid == null || capeId == null || capeId.isEmpty()) continue;
                    if (hash != null && !hash.matches("[0-9a-f]{64}")) continue;
                    next.put(uuid.toLowerCase(java.util.Locale.ROOT), new Remote(capeId, hash));
                }
                remote = next;
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cape sync poll failed: {}", t.toString());
            } finally {
                polling = false;
            }
        }, "TeslaMaps cape sync").start();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static void download(String hash) {
        if (LOADING.putIfAbsent(hash, Boolean.TRUE) != null) return;
        new Thread(() -> {
            try {
                byte[] data;
                Path cached = CACHE_DIR.resolve(hash + ".png");
                if (Files.isRegularFile(cached)) {
                    data = Files.readAllBytes(cached);
                } else {
                    HttpResponse<byte[]> response = HTTP.send(
                            HttpRequest.newBuilder(URI.create(
                                            baseUrl() + "/storage/v1/object/public/capes/" + hash + ".png"))
                                    .timeout(Duration.ofSeconds(30)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != 200 || response.body().length > MAX_TEXTURE_BYTES) {
                        FAILED.put(hash, Boolean.TRUE);
                        return;
                    }
                    data = response.body();
                    // the file name is the sha-256 of its bytes - verify before trusting
                    if (!sha256(data).equals(hash)) {
                        FAILED.put(hash, Boolean.TRUE);
                        return;
                    }
                    Files.createDirectories(CACHE_DIR);
                    Path temp = cached.resolveSibling(hash + ".tmp");
                    Files.write(temp, data);
                    Files.move(temp, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                byte[] bytes = data;
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage image = CapeImport.toCapeLayout(NativeImage.read(bytes));
                        Identifier id = Identifier.fromNamespaceAndPath("teslamaps", "cape/sync/" + hash);
                        Minecraft.getInstance().getTextureManager()
                                .register(id, new DynamicTexture(() -> "TeslaMaps synced cape", image));
                        TEXTURES.put(hash, id);
                    } catch (Throwable t) {
                        FAILED.put(hash, Boolean.TRUE);
                    } finally {
                        LOADING.remove(hash);
                    }
                });
            } catch (Throwable t) {
                FAILED.put(hash, Boolean.TRUE);
                LOADING.remove(hash);
            }
        }, "TeslaMaps cape download").start();
    }

    // ---- upload side ----

    /**
     * Publishes the current local pick so everyone sees what is worn: builtins go up
     * as their id, customs as a downscaled png, "off" clears. Called automatically on
     * every selection change; feedback may be null (fire and forget).
     */
    public static void publishSelection(Consumer<String> feedback) {
        if (!enabled()) {
            if (feedback != null) feedback.accept("§cCape server is not configured");
            return;
        }
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        CapeManager.Cape cape = CapeManager.selected();
        boolean off = !cfg.capeEnabled || cape == null;
        new Thread(() -> {
            JsonObject body = new JsonObject();
            String done;
            if (off) {
                body.addProperty("action", "select");
                body.addProperty("capeId", "");
                done = "§7Cape cleared online";
            } else if (!cape.custom()) {
                body.addProperty("action", "select");
                body.addProperty("capeId", cape.id);
                done = "§aCape shown to everyone";
            } else {
                byte[] png;
                try {
                    png = encodeForUpload(Files.readAllBytes(cape.file));
                } catch (Throwable t) {
                    png = null;
                }
                if (png == null) {
                    result(feedback, "§cNot a valid cape texture");
                    return;
                }
                body.addProperty("action", "upload");
                body.addProperty("png", Base64.getEncoder().encodeToString(png));
                done = "§aCape shown to everyone";
            }
            result(feedback, callFunction(body, done));
        }, "TeslaMaps cape publish").start();
    }

    /** Puts the current custom cape into the shared gallery so others can wear it too. */
    public static void publishToGallery(Consumer<String> feedback) {
        if (!enabled()) {
            feedback.accept("§cCape server is not configured");
            return;
        }
        CapeManager.Cape cape = CapeManager.selected();
        if (cape == null || !TeslaMapsConfig.get().capeEnabled) {
            feedback.accept("§7Pick a cape first");
            return;
        }
        if (!cape.custom()) {
            feedback.accept("§7Builtin capes are already available to everyone");
            return;
        }
        new Thread(() -> {
            byte[] png;
            try {
                png = encodeForUpload(Files.readAllBytes(cape.file));
            } catch (Throwable t) {
                png = null;
            }
            if (png == null) {
                result(feedback, "§cNot a valid cape texture");
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("action", "gallery");
            body.addProperty("png", Base64.getEncoder().encodeToString(png));
            body.addProperty("name", cape.name);
            body.addProperty("category", "Community");
            String line = callFunction(body, "§aAdded to the gallery");
            if (line.startsWith("§a")) CapeGallery.refresh();
            result(feedback, line);
        }, "TeslaMaps gallery publish").start();
    }

    /** Admin only (server-side UUID whitelist): removes a cape from the shared gallery. */
    public static void galleryDelete(String slug, Consumer<String> feedback) {
        if (!enabled()) {
            feedback.accept("§cCape server is not configured");
            return;
        }
        new Thread(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("action", "gallery_delete");
            body.addProperty("path", "gallery/" + slug + ".png");
            String line = callFunction(body, "§7Removed from the gallery");
            if (line.startsWith("§7")) CapeGallery.refresh();
            result(feedback, line);
        }, "TeslaMaps gallery delete").start();
    }

    /** Reports another player's cape; 5 distinct reporters auto-ban it server-side. */
    public static void report(UUID target, Consumer<String> feedback) {
        if (!enabled()) {
            feedback.accept("§cCape server is not configured");
            return;
        }
        new Thread(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("action", "report");
            body.addProperty("targetUuid", target.toString());
            result(feedback, callFunction(body, "§7Reported"));
        }, "TeslaMaps cape report").start();
    }

    private static void result(Consumer<String> feedback, String line) {
        if (feedback == null) {
            if (line.startsWith("§c")) TeslaMaps.LOGGER.warn("Cape publish: {}", line.substring(2));
            return;
        }
        Minecraft.getInstance().execute(() -> feedback.accept(line));
    }

    private static String callFunction(JsonObject body, String successLine) {
        try {
            String[] auth = auth();
            if (auth == null) return "§cMojang auth failed";
            body.addProperty("username", auth[0]);
            body.addProperty("serverId", auth[1]);
            HttpResponse<String> response = send(body);
            if (response.statusCode() == 401) {
                // cached session went stale - one fresh handshake, then give up
                synchronized (CapeSync.class) { authServerId = null; }
                auth = auth();
                if (auth == null) return "§cMojang auth failed";
                body.addProperty("username", auth[0]);
                body.addProperty("serverId", auth[1]);
                response = send(body);
            }
            if (response.statusCode() != 200) {
                String message = null;
                try {
                    message = string(JsonParser.parseString(response.body()).getAsJsonObject(), "message");
                } catch (Throwable ignored) {
                }
                return "§c" + (message != null ? message : "Server said " + response.statusCode());
            }
            // the poll is 2 min - reflect the own change right away
            lastPoll = 0;
            return successLine;
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Cape server call failed: {}", t.toString());
            return "§cNo connection to the cape server";
        }
    }

    private static HttpResponse<String> send(JsonObject body) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/functions/v1/cape"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("apikey", anonKey())
                        .header("Authorization", "Bearer " + anonKey())
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Fits the png into the server's limits: 2:1 layout, max 1024x512, max 1 MB. */
    static byte[] encodeForUpload(byte[] original) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) return null;
            int w = image.getWidth(), h = image.getHeight();
            if (w != h * 2) return null;
            int targetW = Math.min(w, 1024);
            BufferedImage scaled = scale(image, targetW);
            byte[] out = toPng(scaled);
            // a photographic 1024x512 png can pass 1 MB; halve until it fits
            while (out.length > 1024 * 1024 && scaled.getWidth() > 64) {
                scaled = scale(scaled, scaled.getWidth() / 2);
                out = toPng(scaled);
            }
            return out.length <= 1024 * 1024 ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage source, int width) {
        BufferedImage scaled = new BufferedImage(width, width / 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, width / 2, null);
        g.dispose();
        return scaled;
    }

    private static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** joinServer handshake; the serverId is reused while the session check stays warm. */
    private static synchronized String[] auth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return null;
        String username = mc.getUser().getName();
        long now = System.currentTimeMillis();
        if (authServerId != null && now - authTime < AUTH_REUSE_MS) {
            return new String[]{username, authServerId};
        }
        try {
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            String serverId = new BigInteger(MessageDigest.getInstance("SHA-1").digest(random)).toString(16);
            UUID uuid = mc.getUser().getProfileId();
            mc.services().sessionService().joinServer(uuid, mc.getUser().getAccessToken(), serverId);
            Thread.sleep(250);   // let Mojang propagate the session
            authServerId = serverId;
            authTime = now;
            return new String[]{username, serverId};
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Mojang joinServer failed: {}", t.toString());
            return null;
        }
    }

    private static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
