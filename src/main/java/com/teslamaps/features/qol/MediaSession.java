package com.teslamaps.features.qol;

// FloydAddons WindowsMediaSession + WindowsMediaKeys: now-playing via Windows' media session, control via media keys
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class MediaSession {

    public record Snapshot(boolean available, String source, String title, String artist, String album,
                           long positionMs, long reportedMs, long durationMs, boolean playing, String key,
                           String artwork, long sampledAt) {
        public long position(long now) {
            long p = playing ? positionMs + Math.max(0, now - sampledAt) : positionMs;
            return durationMs > 0 ? Math.max(0, Math.min(p, durationMs)) : Math.max(0, p);
        }
    }

    public static final Snapshot EMPTY = new Snapshot(false, "", "", "", "", 0, 0, 0, false, "", null, 0);
    public static final int VK_NEXT = 0xB0, VK_PREV = 0xB1, VK_PLAY_PAUSE = 0xB3;

    private static volatile Snapshot current = EMPTY;
    private static Process process;
    private static long lastStart;
    private static boolean hooked;

    private MediaSession() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static Snapshot snapshot() { return current; }

    public static synchronized void ensureRunning() {
        if (!isWindows() || (process != null && process.isAlive())) return;
        long now = System.currentTimeMillis();
        if (now - lastStart < 10_000L) return;
        lastStart = now;
        Path script = extract();
        if (script == null) return;
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString()).redirectErrorStream(false).start();
            process = p;
            if (!hooked) {
                hooked = true;
                Runtime.getRuntime().addShutdownHook(new Thread(MediaSession::stop, "TeslaMaps-Media-Stop"));
            }
            Thread reader = new Thread(() -> read(p), "TeslaMaps-Media");
            reader.setDaemon(true);
            reader.start();
            Thread err = new Thread(() -> drain(p.getErrorStream()), "TeslaMaps-Media-Err");
            err.setDaemon(true);
            err.start();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Media session start failed: {}", e.toString());
        }
    }

    public static synchronized boolean running() {
        return process != null;
    }

    public static synchronized void stop() {
        if (process != null) process.destroy();
        process = null;
        current = EMPTY;
    }

    private static void read(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                Snapshot next = parse(line);
                if (next != null && process == p) current = reconcile(current, next);
            }
        } catch (Exception ignored) {
        }
    }

    private static void drain(InputStream in) {
        try (in) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (Exception ignored) {
        }
    }

    private static Snapshot parse(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            long now = System.currentTimeMillis();
            if (!bool(o, "available")) return EMPTY;
            long pos = num(o, "positionMs");
            return new Snapshot(true, str(o, "source"), str(o, "title"), str(o, "artist"), str(o, "album"),
                    pos, pos, num(o, "durationMs"), bool(o, "playing"), str(o, "key"),
                    o.has("artwork") && !o.get("artwork").isJsonNull() ? o.get("artwork").getAsString() : null, now);
        } catch (Exception e) {
            return null;
        }
    }

    // Spotify only reports a new position every few seconds, so keep extrapolating between reports
    private static Snapshot reconcile(Snapshot prev, Snapshot next) {
        if (!prev.available || !next.available || !prev.key.equals(next.key)) return next;
        String art = next.artwork != null ? next.artwork : prev.artwork;
        long pos = next.positionMs, at = next.sampledAt;
        if (prev.playing && next.playing) {
            long projected = prev.position(next.sampledAt);
            if (next.reportedMs == prev.reportedMs) {
                pos = prev.positionMs;
                at = prev.sampledAt;
            } else if (next.reportedMs >= prev.reportedMs - 2000) {
                pos = Math.max(next.positionMs, projected);
            }
        }
        return new Snapshot(true, next.source, next.title, next.artist, next.album, pos, next.reportedMs,
                next.durationMs, next.playing, next.key, art, at);
    }

    private static Path extract() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("cache");
            Files.createDirectories(dir);
            Path target = dir.resolve("media-session.ps1");
            try (InputStream in = MediaSession.class.getResourceAsStream("/assets/teslamaps/media/media-session.ps1")) {
                if (in == null) return null;
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Media session script extract failed: {}", e.toString());
            return null;
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    public static boolean sendKey(int vk) {
        if (!isWindows()) return false;
        try {
            WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
            key(inputs[0], vk, false);
            key(inputs[1], vk, true);
            return User32.INSTANCE.SendInput(new WinDef.DWORD(inputs.length), inputs, inputs[0].size()).intValue() == inputs.length;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void key(WinUser.INPUT input, int vk, boolean up) {
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType(WinUser.KEYBDINPUT.class);
        input.input.ki = new WinUser.KEYBDINPUT();
        input.input.ki.wVk = new WinDef.WORD(vk);
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.dwFlags = new WinDef.DWORD(up ? WinUser.KEYBDINPUT.KEYEVENTF_KEYUP : 0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.write();
    }
}
