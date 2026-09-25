package com.teslamaps.features.qol;

// FloydAddons FloydMusicOverlay (HUD + media keys) + Skysoft SpotifyLyrics (synced lyrics from lrclib.net)
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MusicOverlay {

    private static final int W = 200, ART = 40, PAD = 4;
    private static final Identifier ART_ID = Identifier.fromNamespaceAndPath("teslamaps", "music_overlay_art");
    private static final Pattern LRC = Pattern.compile("^\\[(\\d{1,3}):(\\d{2}(?:\\.\\d{1,3})?)]\\s*(.*)$");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private record Line(long ms, String text) {}

    private static final MediaSession.Snapshot SAMPLE = new MediaSession.Snapshot(true, "Spotify.exe", "Current Song",
            "Artist Name", "", 83_000, 83_000, 214_000, true, "sample", null, 0);

    private static HudEditElement hud;
    private static boolean artLoaded;
    private static String artKey = "";
    private static String title = "", artist = "", time = "", lyric = "", lyricNext = "";
    private static float progress;
    private static boolean shown, playing;
    private static volatile List<Line> lyrics = List.of();
    private static String lyricsKey = "";
    private static boolean playDown, nextDown, prevDown;

    private MusicOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("musicOverlay", "Music Overlay")
                .enabled(() -> cfg().musicOverlay)
                .size(() -> W, MusicOverlay::height)
                .preview((g, mc, x, y, s) -> drawSample(g, mc, x, y, s));
        Feature.of("MusicOverlaySession").tickAlways(MusicOverlay::lifecycle);
        Feature.of("MusicOverlay").when(() -> cfg().musicOverlay)
                .tickAlways(MusicOverlay::tick)
                .hud("musicoverlay", MusicOverlay::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Music");
                    b.toggle("Music Overlay", () -> c.musicOverlay, v -> c.musicOverlay = v)
                            .desc("Windows only. Shows the song playing in Spotify, the browser or any other player (cover, title, artist, progress). Position in /tmap gui.");
                    b.toggle("  Cover Art", () -> c.musicOverlayArt, v -> c.musicOverlayArt = v);
                    b.toggle("  Synced Lyrics (lrclib.net)", () -> c.musicOverlayLyrics, v -> c.musicOverlayLyrics = v);
                    b.keybind("  Play / Pause Key", () -> c.musicPlayPauseKey, v -> c.musicPlayPauseKey = v);
                    b.keybind("  Next Song Key", () -> c.musicNextKey, v -> c.musicNextKey = v);
                    b.keybind("  Previous Song Key", () -> c.musicPrevKey, v -> c.musicPrevKey = v);
                });
    }

    private static void lifecycle() {
        if (cfg().musicOverlay) {
            MediaSession.ensureRunning();
        } else if (MediaSession.running() || shown) {
            MediaSession.stop();
            shown = false;
            clearArt();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean free = mc.screen == null;
        playDown = edge(free, c.musicPlayPauseKey, playDown, MediaSession.VK_PLAY_PAUSE);
        nextDown = edge(free, c.musicNextKey, nextDown, MediaSession.VK_NEXT);
        prevDown = edge(free, c.musicPrevKey, prevDown, MediaSession.VK_PREV);

        MediaSession.Snapshot s = MediaSession.snapshot();
        if (!s.available()) {
            shown = false;
            if (artLoaded) clearArt();
            return;
        }
        shown = true;
        if (c.musicOverlayArt && !s.key().equals(artKey)) {
            if (s.artwork() != null) {
                loadArt(mc, s);
            } else if (artLoaded) {
                clearArt();
                artKey = s.key();
            }
        }
        int textW = W - PAD * 2 - (showArt() ? ART + 6 : 0);
        title = mc.font.plainSubstrByWidth(s.title().isBlank() ? "Unknown song" : s.title(), textW);
        artist = mc.font.plainSubstrByWidth(s.artist().isBlank() ? source(s.source()) : s.artist(), textW);
        long pos = s.position(System.currentTimeMillis());
        progress = s.durationMs() > 0 ? Math.min(1f, pos / (float) s.durationMs()) : 0f;
        time = fmt(pos) + " / " + fmt(s.durationMs());
        playing = s.playing();

        if (c.musicOverlayLyrics) {
            if (!s.key().equals(lyricsKey)) {
                lyricsKey = s.key();
                lyrics = List.of();
                fetchLyrics(s);
            }
            List<Line> ls = lyrics;
            int i = active(ls, pos);
            lyric = i >= 0 ? mc.font.plainSubstrByWidth(ls.get(i).text, W - PAD * 2) : "";
            lyricNext = i + 1 < ls.size() && i + 1 >= 0 ? mc.font.plainSubstrByWidth(ls.get(i + 1).text, W - PAD * 2) : "";
        } else {
            lyric = lyricNext = "";
        }
    }

    private static boolean edge(boolean free, int key, boolean was, int vk) {
        boolean down = free && key != -1 && KeyNames.isDown(key);
        if (down && !was) MediaSession.sendKey(vk);
        return down;
    }

    private static boolean showArt() {
        return cfg().musicOverlayArt;
    }

    private static String source(String src) {
        String l = src.toLowerCase();
        if (l.contains("spotify")) return "Spotify";
        if (l.contains("chrome") || l.contains("edge") || l.contains("firefox")) return "Browser";
        return "Now Playing";
    }

    private static String fmt(long ms) {
        long sec = Math.max(0, ms) / 1000;
        return sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60;
    }

    private static void loadArt(Minecraft mc, MediaSession.Snapshot s) {
        artKey = s.key();
        try {
            byte[] bytes = Base64.getDecoder().decode(s.artwork());
            if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024) return;
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
            if (img.getWidth() > 1024 || img.getHeight() > 1024) {
                img.close();
                return;
            }
            mc.getTextureManager().register(ART_ID, new DynamicTexture(() -> "TeslaMaps music art", img));
            artLoaded = true;
        } catch (Throwable t) {
            clearArt();
            artKey = s.key();
        }
    }

    private static void clearArt() {
        if (artLoaded) Minecraft.getInstance().getTextureManager().release(ART_ID);
        artLoaded = false;
        artKey = "";
    }

    private static void fetchLyrics(MediaSession.Snapshot s) {
        String key = s.key();
        String artistName = s.artist();
        int dash = artistName.indexOf(" — ");
        if (dash > 0) artistName = artistName.substring(0, dash);
        String q = "track_name=" + enc(s.title()) + "&artist_name=" + enc(artistName);
        String get = q + (s.album().isBlank() ? "" : "&album_name=" + enc(s.album()))
                + (s.durationMs() > 0 ? "&duration=" + s.durationMs() / 1000 : "");
        request("https://lrclib.net/api/get?" + get).thenAccept(body -> {
            List<Line> ls = body == null ? List.of() : parseGet(body);
            if (!ls.isEmpty()) {
                if (key.equals(lyricsKey)) lyrics = ls;
                return;
            }
            request("https://lrclib.net/api/search?" + q).thenAccept(sb -> {
                List<Line> found = sb == null ? List.of() : parseSearch(sb);
                if (key.equals(lyricsKey)) lyrics = found;
            });
        });
    }

    private static java.util.concurrent.CompletableFuture<String> request(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "TeslaMaps (personal Minecraft mod)").GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> r.statusCode() == 200 ? r.body() : null)
                .exceptionally(t -> null);
    }

    private static List<Line> parseGet(String body) {
        try {
            JsonElement e = JsonParser.parseString(body).getAsJsonObject().get("syncedLyrics");
            return e == null || e.isJsonNull() ? List.of() : parseLrc(e.getAsString());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<Line> parseSearch(String body) {
        try {
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                JsonElement e = o.get("syncedLyrics");
                if (e != null && !e.isJsonNull()) {
                    List<Line> ls = parseLrc(e.getAsString());
                    if (!ls.isEmpty()) return ls;
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            String text = m.group(3).trim();
            if (text.isEmpty()) continue;
            long ms = Long.parseLong(m.group(1)) * 60_000L + Math.round(Double.parseDouble(m.group(2)) * 1000);
            out.add(new Line(ms, text));
        }
        out.sort((a, b) -> Long.compare(a.ms, b.ms));
        return out;
    }

    private static int active(List<Line> ls, long pos) {
        int lo = 0, hi = ls.size() - 1, res = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ls.get(mid).ms <= pos) {
                res = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return res;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static int height() {
        return ART + PAD * 2 + (cfg().musicOverlayLyrics ? 22 : 0);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!shown || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), title, artist, time, progress, playing, lyric, lyricNext, artLoaded);
    }

    private static void drawSample(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        draw(g, mc, x, y, s, SAMPLE.title(), SAMPLE.artist(), "1:23 / 3:34", 83f / 214f, true,
                cfg().musicOverlayLyrics ? "Current lyric line" : "", cfg().musicOverlayLyrics ? "Next lyric line" : "", false);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String title, String artist,
                             String time, float progress, boolean playing, String lyric, String lyricNext, boolean art) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, W, height());
        int tx = PAD;
        if (showArt()) {
            if (art) {
                g.blit(ART_ID, PAD, PAD, PAD + ART, PAD + ART, 0f, 1f, 0f, 1f);
            } else {
                g.fill(PAD, PAD, PAD + ART, PAD + ART, 0xFF282828);
                g.centeredText(mc.font, "♪", PAD + ART / 2, PAD + ART / 2 - 4, 0xFFAAAAAA);
            }
            tx += ART + 6;
        }
        int bw = W - PAD - tx;
        g.text(mc.font, title, tx, PAD + 1, 0xFFFFFFFF, true);
        g.text(mc.font, artist, tx, PAD + 12, 0xFFAAAAAA, true);
        int by = PAD + 25;
        g.fill(tx, by, tx + bw, by + 3, 0x553A3A3A);
        int filled = (int) (bw * progress);
        if (filled > 0) g.fill(tx, by, tx + filled, by + 3, 0xFF1DB954);
        g.text(mc.font, time, tx, PAD + 31, 0xFFAAAAAA, true);
        g.text(mc.font, playing ? "▶" : "||", tx + bw - 8, PAD + 31, 0xFFAAAAAA, true);
        if (cfg().musicOverlayLyrics) {
            int ly = PAD + ART + 3;
            g.text(mc.font, lyric, PAD, ly, 0xFFFFFFFF, true);
            g.text(mc.font, lyricNext, PAD, ly + 10, 0xFF888888, true);
        }
        g.pose().popMatrix();
    }
}
