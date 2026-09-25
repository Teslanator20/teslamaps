package com.teslamaps.features.qol;

// SkyHanni ComputerTimeOffset: NTP check of the PC clock + detection of clock jumps while playing
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

public final class ClockOffset {

    private static final String[] SERVERS = {"time.google.com", "pool.ntp.org", "time.windows.com"};
    private static final long WARN_MS = 5000L;

    private static volatile boolean running;
    private static boolean checkedOnce;
    private static boolean wasOff;
    private static long lastCheck;
    private static long lastWall, lastNano;
    private static int timer;

    private ClockOffset() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ClockOffset").when(() -> cfg().clockOffsetWarning)
                .tick(ClockOffset::tick)
                .config("Interface", b -> b.toggle("Wrong PC Clock Warning", () -> cfg().clockOffsetWarning, v -> cfg().clockOffsetWarning = v)
                        .desc("Checks your PC clock against an NTP time server and warns in chat when it is 5s or more off (contest, feast and pest timers rely on it). Also rechecks when the clock jumps while playing."));
    }

    private static void tick() {
        if (++timer % 20 != 0) return;
        long wall = System.currentTimeMillis(), nano = System.nanoTime();
        boolean jumped = false;
        if (lastNano != 0) {
            long expected = (nano - lastNano) / 1_000_000L;
            jumped = Math.abs((wall - lastWall) - expected) > 1000L;
        }
        lastWall = wall;
        lastNano = nano;
        if (!SkyblockUtils.isOnHypixel()) return;
        if (checkedOnce && !jumped) return;
        if (running || wall - lastCheck < 30_000L) return;
        checkedOnce = true;
        lastCheck = wall;
        running = true;
        Thread t = new Thread(ClockOffset::check, "TeslaMaps-NTP");
        t.setDaemon(true);
        t.start();
    }

    private static void check() {
        try {
            Long offset = null;
            for (String host : SERVERS) {
                try {
                    offset = query(host);
                    break;
                } catch (Exception e) {
                    TeslaMaps.debug("[ClockOffset] {} failed: {}", host, e.toString());
                }
            }
            if (offset == null) return;
            long off = offset;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> report(mc, off));
        } finally {
            running = false;
        }
    }

    private static void report(Minecraft mc, long offset) {
        if (mc.player == null || !cfg().clockOffsetWarning) return;
        if (Math.abs(offset) < WARN_MS) {
            if (wasOff) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] Your PC clock is accurate again."));
            wasOff = false;
            return;
        }
        wasOff = true;
        String secs = String.format(Locale.ROOT, "%.1fs", Math.abs(offset) / 1000.0);
        String dir = offset > 0 ? "behind" : "ahead";
        Component msg = Component.literal("§c[TeslaMaps] Your PC clock is " + secs + " " + dir
                + ". §eTimers like contests and the Harvest Feast will be wrong until you fix it.");
        String link = fixLink();
        if (link != null) {
            msg = msg.copy().append(Component.literal(" §e§n[How to fix]").withStyle(s -> s
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(link)))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(link)))));
        }
        mc.player.sendSystemMessage(msg);
    }

    private static String fixLink() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "https://support.microsoft.com/en-us/windows/dfaa7122-479f-5b98-2a7b-fa0b6e01b261";
        if (os.contains("mac")) return "https://support.apple.com/guide/mac-help/mchlp2996/mac";
        if (os.contains("nux") || os.contains("nix")) return "https://unix.stackexchange.com/a/79116";
        return null;
    }

    // SNTP: offset = ((t2 - t1) + (t3 - t4)) / 2, positive = PC clock is behind
    private static long query(String host) throws Exception {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(5000);
            byte[] req = new byte[48];
            req[0] = 0x1B;
            InetAddress addr = InetAddress.getByName(host);
            long t1 = System.currentTimeMillis();
            s.send(new DatagramPacket(req, req.length, addr, 123));
            DatagramPacket resp = new DatagramPacket(new byte[48], 48);
            s.receive(resp);
            long t4 = System.currentTimeMillis();
            byte[] r = resp.getData();
            long t2 = timestamp(r, 32), t3 = timestamp(r, 40);
            if (t3 <= 0) throw new IllegalStateException("empty reply");
            return ((t2 - t1) + (t3 - t4)) / 2;
        }
    }

    private static long timestamp(byte[] b, int off) {
        long sec = 0, frac = 0;
        for (int i = 0; i < 4; i++) sec = (sec << 8) | (b[off + i] & 0xFF);
        for (int i = 4; i < 8; i++) frac = (frac << 8) | (b[off + i] & 0xFF);
        return (sec - 2_208_988_800L) * 1000L + frac * 1000L / 0x1_0000_0000L;
    }
}
