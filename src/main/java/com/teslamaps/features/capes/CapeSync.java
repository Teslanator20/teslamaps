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
 * Shared capes between TeslaMaps users, backed by a Supabase project: textures in the
 * public "capes" storage bucket, everything else through the "cape" edge function,
 * which verifies the vanilla Mojang joinServer handshake - no password, no token stored.
 *
 * Reads are scoped on purpose. cape_users has no anon select policy, so the table cannot
 * be dumped with the baked-in anon key; the function answers a list of uuids we already
 * know about (players in view, in the tab list, or the one a profile is open for).
 */
public class CapeSync {

    private static final Path CACHE_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("cape_cache");

    private static final long POLL_MS = 120_000;
    private static final long ASK_AGAIN_MS = 300_000;    // a player without a cape stays without one
    private static final long KEEP_TEXTURE_MS = 300_000; // grace before an unworn texture is released
    private static final int MAX_LOOKUP = 400;           // mirrors the cape function's cap
    private static final long AUTH_REUSE_MS = 240_000;   // mojang session stays valid a while
    private static final int MAX_PNG_BYTES = 2 * 1024 * 1024;
    /** Mirrors the cape function's gif limits, so a doomed upload fails before it starts. */
    private static final int MAX_GIF_BYTES = 8 * 1024 * 1024;
    private static final int MAX_GIF_PX = 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * uuid (no dashes) -> capeId, plus hash/ext/crop for customs. A custom cape is a
     * png or a gif; a gif keeps the crop it was imported with, so every client composes
     * the same sheet from the same untouched source bytes. Swapped whole on the main thread.
     */
    private record Remote(String capeId, String hash, String ext, CapeCompose.Crop crop) {
        boolean animated() { return "gif".equals(ext); }
    }
    private static final Map<String, Remote> remote = new ConcurrentHashMap<>();

    /** Uuids a lookup was asked for but that have not been sent yet, drained on the tick. */
    private static final java.util.Set<String> pending = ConcurrentHashMap.newKeySet();
    /** uuid -> when we last asked, so a capeless player is not re-requested every frame. */
    private static final Map<String, Long> asked = new ConcurrentHashMap<>();
    /** hash -> when it was last worn by anyone we saw; textures survive a short absence. */
    private static final Map<String, Long> seen = new ConcurrentHashMap<>();

    /** What a remote player wears - a still texture or an animation picked by wall clock. */
    private record Skin(Identifier still, GifCape gif) {
        Identifier current() { return gif != null ? gif.current() : still; }
    }

    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, GifCape> GIFS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    /** Rebuilt per tick; the render path only does this one map get per avatar. */
    private static volatile Map<Integer, Skin> byEntityId = Map.of();

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

    public static boolean enabled() {
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
        if (now - lastPoll > POLL_MS) {
            lastPoll = now;
            queueVisible();
        }
        drain();

        Map<String, Remote> capes = remote;
        if (capes.isEmpty()) {
            if (!byEntityId.isEmpty()) byEntityId = Map.of();
            return;
        }

        Map<Integer, Skin> next = new HashMap<>();
        int self = mc.player.getId();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getId() == self) continue;   // own cape stays the local pick
            Remote entry = capes.get(player.getUUID().toString().replace("-", ""));
            if (entry == null) continue;
            Skin skin = resolve(entry);
            if (skin != null) next.put(player.getId(), skin);
        }
        byEntityId = next;
    }

    /** Called from CapeManager.textureFor for every non-local avatar, so one map get only. */
    public static Identifier textureFor(int entityId) {
        Identifier preview = previews.get(entityId);
        if (preview != null) return preview;
        Skin skin = byEntityId.get(entityId);
        return skin == null ? null : skin.current();
    }

    /** Is this player a TeslaMaps user? Known because they publish a cape. */
    public static boolean isUser(String uuid) {
        if (uuid == null) return false;
        queue(uuid);
        return remote.containsKey(uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT));
    }

    /** The cape of any known user, resolved and downloaded on demand. */
    public static Identifier capeFor(String uuid) {
        if (uuid == null) return null;
        queue(uuid);
        Remote entry = remote.get(uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT));
        if (entry == null) return null;
        Skin skin = resolve(entry);
        return skin == null ? null : skin.current();
    }

    /** Lets the profile viewer put a cape on its own render entity. */
    public static void setPreview(int entityId, Identifier texture) {
        if (texture == null) previews.remove(entityId);
        else previews.put(entityId, texture);
    }

    private static final Map<Integer, Identifier> previews = new ConcurrentHashMap<>();

    private static Skin resolve(Remote entry) {
        if (entry.hash() == null) {
            CapeManager.Cape builtin = CapeManager.byId(entry.capeId());
            return builtin != null && !builtin.custom() ? new Skin(builtin.texture(), null) : null;
        }
        GifCape animated = GIFS.get(entry.hash());
        if (animated != null) return new Skin(null, animated);
        Identifier known = TEXTURES.get(entry.hash());
        if (known != null) return new Skin(known, null);
        if (FAILED.containsKey(entry.hash())) return null;
        download(entry);
        return null;
    }

    private static String plain(UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Everyone whose cape we could end up drawing: players in view plus the tab list. */
    private static void queueVisible() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (AbstractClientPlayer player : mc.level.players()) pending.add(plain(player.getUUID()));
        }
        if (mc.getConnection() != null) {
            for (var info : mc.getConnection().getOnlinePlayers()) pending.add(plain(info.getProfile().id()));
        }
        if (mc.player != null) pending.remove(plain(mc.player.getUUID()));
    }

    /** One uuid the poll would not cover on its own - the profile viewer's target. */
    private static void queue(String uuid) {
        String key = uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        if (remote.containsKey(key)) return;
        Long last = asked.get(key);
        if (last != null && System.currentTimeMillis() - last < ASK_AGAIN_MS) return;
        pending.add(key);
    }

    /** Sends whatever is queued as a single lookup. Called on the tick, so never concurrent. */
    private static void drain() {
        if (polling || pending.isEmpty()) return;
        java.util.List<String> batch = new java.util.ArrayList<>(pending);
        pending.clear();
        if (batch.size() > MAX_LOOKUP) batch = batch.subList(0, MAX_LOOKUP);
        java.util.List<String> uuids = batch;
        polling = true;
        new Thread(() -> {
            try {
                String[] auth = auth();
                if (auth == null) return;
                JsonObject body = new JsonObject();
                body.addProperty("action", "lookup");
                JsonArray list = new JsonArray();
                uuids.forEach(list::add);
                body.add("uuids", list);
                body.addProperty("username", auth[0]);
                body.addProperty("serverId", auth[1]);

                HttpResponse<String> response = send(body);
                if (response.statusCode() != 200) return;
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray rows = json.getAsJsonArray("capes");
                if (rows == null) return;

                long now = System.currentTimeMillis();
                Map<String, Remote> found = new HashMap<>();
                java.util.Set<String> answered = new java.util.HashSet<>();
                for (JsonElement element : rows) {
                    if (!element.isJsonObject()) continue;
                    JsonObject row = element.getAsJsonObject();
                    String uuid = string(row, "uuid");
                    String capeId = string(row, "cape_id");
                    String hash = string(row, "hash");
                    String ext = string(row, "ext");
                    if (uuid != null) {
                        Cosmetics.readRow(uuid, row);
                        answered.add(uuid.toLowerCase(java.util.Locale.ROOT));
                    }
                    if (uuid == null || capeId == null || capeId.isEmpty()) continue;
                    if (hash != null && !hash.matches("[0-9a-f]{64}")) continue;
                    // the extension is part of the storage key, so anything unknown is dropped
                    if (ext == null || ext.isEmpty()) ext = "png";
                    if (!ext.equals("png") && !ext.equals("gif")) continue;
                    found.put(uuid.toLowerCase(java.util.Locale.ROOT),
                            new Remote(capeId, hash, ext, CapeCompose.Crop.parse(string(row, "crop"))));
                }
                // asked for and not answered means the cape is gone, not that the row is stale
                for (String uuid : uuids) {
                    asked.put(uuid, now);
                    if (!found.containsKey(uuid)) remote.remove(uuid);
                    if (!answered.contains(uuid)) Cosmetics.forget(uuid);
                }
                remote.putAll(found);
                prune();
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cape sync lookup failed: {}", t.toString());
            } finally {
                polling = false;
            }
        }, "TeslaMaps cape sync").start();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    /**
     * Drops textures nobody wears anymore. Matters for gifs: a player swapping capes
     * would otherwise leave 60 frames on the gpu for the rest of the session. The
     * entity map is cleared in the same task, so no skin points at a released texture.
     */
    private static void prune() {
        if (TEXTURES.isEmpty() && GIFS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Remote entry : remote.values()) {
            if (entry.hash() != null) seen.put(entry.hash(), now);
        }
        // a lookup only covers who is around right now, so hold a texture over a short absence
        seen.values().removeIf(when -> now - when > KEEP_TEXTURE_MS);
        java.util.Set<String> keep = new java.util.HashSet<>(seen.keySet());
        Minecraft.getInstance().execute(() -> {
            byEntityId = Map.of();
            for (String hash : new java.util.ArrayList<>(GIFS.keySet())) {
                if (keep.contains(hash)) continue;
                GifCape gone = GIFS.remove(hash);
                if (gone != null) gone.release();
            }
            for (String hash : new java.util.ArrayList<>(TEXTURES.keySet())) {
                if (keep.contains(hash)) continue;
                Identifier gone = TEXTURES.remove(hash);
                if (gone != null) Minecraft.getInstance().getTextureManager().release(gone);
            }
            FAILED.keySet().retainAll(keep);
        });
    }

    private static void download(Remote entry) {
        String hash = entry.hash();
        String ext = entry.ext();
        CapeCompose.Crop crop = entry.crop();
        boolean gif = entry.animated();
        if (LOADING.putIfAbsent(hash, Boolean.TRUE) != null) return;
        new Thread(() -> {
            try {
                byte[] data;
                Path cached = CACHE_DIR.resolve(hash + "." + ext);
                if (Files.isRegularFile(cached)) {
                    data = Files.readAllBytes(cached);
                } else {
                    HttpResponse<byte[]> response = HTTP.send(
                            HttpRequest.newBuilder(URI.create(baseUrl()
                                            + "/storage/v1/object/public/capes/" + hash + "." + ext))
                                    .timeout(Duration.ofSeconds(30)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    int limit = gif ? MAX_GIF_BYTES : MAX_PNG_BYTES;
                    if (response.statusCode() != 200 || response.body().length > limit) {
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
                if (gif) {
                    uploadGif(hash, data, crop);
                    return;
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

    /**
     * Decodes a synced gif on the download thread and hands the frames to the texture
     * manager. Strangers' capes get the smaller budget - a crowded lobby can otherwise
     * hold a dozen animations at once.
     */
    private static void uploadGif(String hash, byte[] data, CapeCompose.Crop crop) {
        GifCape.Decoded decoded;
        try {
            decoded = GifCape.decode(data, crop, GifCape.Budget.SYNCED);
        } catch (Throwable t) {
            FAILED.put(hash, Boolean.TRUE);
            LOADING.remove(hash);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                GIFS.put(hash, GifCape.upload("sync/" + hash, decoded));
            } catch (Throwable t) {
                decoded.close();
                FAILED.put(hash, Boolean.TRUE);
            } finally {
                LOADING.remove(hash);
            }
        });
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
                byte[] animated = cape.animated ? raw(cape) : null;
                body.addProperty("action", "upload");
                if (animated != null) {
                    // the gif goes up untouched, everyone else composes it with the same crop
                    body.addProperty("png", Base64.getEncoder().encodeToString(animated));
                    CapeCompose.Crop crop = cape.crop();
                    if (crop != null) body.addProperty("crop", crop.store());
                    done = "§aAnimated cape shown to everyone";
                } else {
                    byte[] png = still(cape);
                    if (png == null) {
                        result(feedback, "§cNot a valid cape texture");
                        return;
                    }
                    body.addProperty("png", Base64.getEncoder().encodeToString(png));
                    done = cape.animated
                            ? "§eToo big to animate online - shared as a still"
                            : "§aCape shown to everyone";
                }
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
            byte[] animated = cape.animated ? raw(cape) : null;
            byte[] data = animated;
            if (data == null) {
                try {
                    data = encodeForUpload(cape.animated
                            ? GifCape.firstSheetPng(Files.readAllBytes(cape.file), cape.crop())
                            : Files.readAllBytes(cape.file));
                } catch (Throwable t) {
                    data = null;
                }
            }
            if (data == null) {
                result(feedback, "§cNot a valid cape texture");
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("action", "gallery");
            body.addProperty("png", Base64.getEncoder().encodeToString(data));
            body.addProperty("name", cape.name);
            body.addProperty("category", "Community");
            if (animated != null) {
                CapeCompose.Crop crop = cape.crop();
                if (crop != null) body.addProperty("crop", crop.store());
            }
            String line = callFunction(body, animated != null
                    ? "§aAdded to the gallery, animated"
                    : "§aAdded to the gallery");
            if (line.startsWith("§a")) CapeGallery.refresh();
            result(feedback, line);
        }, "TeslaMaps gallery publish").start();
    }

    /** Admin only (server-side UUID whitelist): removes a cape from the shared gallery. */
    public static void galleryDelete(String path, Consumer<String> feedback) {
        if (!enabled()) {
            feedback.accept("§cCape server is not configured");
            return;
        }
        new Thread(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("action", "gallery_delete");
            body.addProperty("path", path);
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

    public static String callFunction(JsonObject body, String successLine) {
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

    /**
     * The untouched gif of an animated cape, or null when it busts the server's limits -
     * the caller then falls back to publishing a still.
     */
    /**
     * An animated cape's bytes exactly as they go online, or null when the gif busts
     * the server's limits - the caller then falls back to {@link #still}.
     */
    private static byte[] raw(CapeManager.Cape cape) {
        try {
            byte[] data = Files.readAllBytes(cape.file);
            if (!GifCape.isGif(data) || data.length > MAX_GIF_BYTES) return null;
            int[] size = GifCape.canvasSize(data);
            if (size[0] > MAX_GIF_PX || size[1] > MAX_GIF_PX) return null;
            // without a crop the server wants a finished 2:1 sheet
            return cape.crop() == null && size[0] != size[1] * 2 ? null : data;
        } catch (Throwable t) {
            return null;
        }
    }

    /** What goes online for a local cape - an animated one is published as its first frame. */
    private static byte[] still(CapeManager.Cape cape) {
        try {
            byte[] data = Files.readAllBytes(cape.file);
            return encodeForUpload(cape.animated
                    ? GifCape.firstSheetPng(data, cape.crop()) : data);
        } catch (Throwable t) {
            return null;
        }
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
