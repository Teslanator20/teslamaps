package com.teslamaps.features.qol;

// FloydAddons FloydMusicOverlay (HUD + media keys)
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
import java.util.Base64;

public final class MusicOverlay {

    private static final int W = 200, ART = 40, PAD = 4;
    private static final Identifier ART_ID = Identifier.fromNamespaceAndPath("teslamaps", "music_overlay_art");

    private static final MediaSession.Snapshot SAMPLE = new MediaSession.Snapshot(true, "Spotify.exe", "Current Song",
            "Artist Name", "", 83_000, 83_000, 214_000, true, "sample", null, 0);

    private static HudEditElement hud;
    private static boolean artLoaded;
    private static String artKey = "";
    private static String title = "", artist = "", time = "";
    private static float progress;
    private static boolean shown, playing;
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

    private static int height() {
        return ART + PAD * 2;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!shown || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), title, artist, time, progress, playing, artLoaded);
    }

    private static void drawSample(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        draw(g, mc, x, y, s, SAMPLE.title(), SAMPLE.artist(), "1:23 / 3:34", 83f / 214f, true, false);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String title, String artist,
                             String time, float progress, boolean playing, boolean art) {
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
        g.pose().popMatrix();
    }
}
