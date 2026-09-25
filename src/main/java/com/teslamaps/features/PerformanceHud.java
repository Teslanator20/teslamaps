package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Odin-style performance HUD: FPS / TPS / Ping. TPS from the time between
// ClientboundSetTimePacket arrivals (server sends one every 20 ticks), ping via PingMeter.
public class PerformanceHud {
    public static final String SAMPLE_TEXT = "FPS: 240 TPS: 20.0 Ping: 45ms";
    private static final String[] SAMPLE_LABELS = { "FPS: ", "TPS: ", "Ping: " };
    private static final String[] SAMPLE_VALUES = { "240", "20.0", "45ms" };
    private static final String[] SAMPLE_LABELS_CPS = { "FPS: ", "TPS: ", "Ping: ", "CPS: " };
    private static final String[] SAMPLE_VALUES_CPS = { "240", "20.0", "45ms", "8 | 12" };
    private static final long[] leftClicks = new long[64], rightClicks = new long[64];
    private static int leftIdx, rightIdx;

    private static volatile long lastTimePacket = 0L;
    private static final float[] tpsLog = new float[10];
    private static int tpsIndex = 0;
    private static int tpsCount = 0;

    private static int pingCooldown = 0;
    private static String[] labels = new String[0];
    private static String[] values = new String[0];

    // netty thread - timestamps only
    public static void onTimePacket() {
        long now = System.currentTimeMillis();
        if (lastTimePacket != 0L) {
            float tps = Math.min(20f, 20000f / Math.max(1, now - lastTimePacket));
            synchronized (tpsLog) {
                tpsLog[tpsIndex] = tps;
                tpsIndex = (tpsIndex + 1) % tpsLog.length;
                if (tpsCount < tpsLog.length) tpsCount++;
            }
        }
        lastTimePacket = now;
    }

    // HUD-020, from MouseHandler.onButton
    public static void onClick(int button) {
        if (!TeslaMapsConfig.get().perfHud || !TeslaMapsConfig.get().perfHudCps) return;
        if (Minecraft.getInstance().screen != null) return;
        long now = System.currentTimeMillis();
        if (button == 0) leftClicks[leftIdx++ & 63] = now;
        else if (button == 1) rightClicks[rightIdx++ & 63] = now;
    }

    private static int cps(long[] clicks, long now) {
        int n = 0;
        for (long t : clicks) if (now - t <= 1000) n++;
        return n;
    }

    private static float averageTps() {
        synchronized (tpsLog) {
            if (tpsCount == 0) return 20f;
            float sum = 0;
            for (int i = 0; i < tpsCount; i++) sum += tpsLog[i];
            return sum / tpsCount;
        }
    }

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) { lastTimePacket = 0L; tpsCount = 0; return; }

        if (cfg.perfHudPing && --pingCooldown <= 0) {
            PingMeter.requestSilent();
            pingCooldown = 40; // every 2s
        }

        java.util.List<String> l = new java.util.ArrayList<>(4);
        java.util.List<String> v = new java.util.ArrayList<>(4);
        if (cfg.perfHudFps) { l.add("FPS: "); v.add(String.valueOf(mc.getFps())); }
        if (cfg.perfHudTps) { l.add("TPS: "); v.add(String.format("%.1f", averageTps())); }
        if (cfg.perfHudPing) { l.add("Ping: "); v.add((int) PingMeter.getLastPingMs() + "ms"); }
        if (cfg.perfHudCps) {
            long now = System.currentTimeMillis();
            l.add("CPS: ");
            v.add(cps(leftClicks, now) + " | " + cps(rightClicks, now));
        }
        if (cfg.perfHudLag && !mc.isLocalServer() && com.teslamaps.utils.SkyblockUtils.isOnHypixel()) {
            long last = com.teslamaps.utils.ServerTickTimer.lastPingMs();
            long dt = last == 0 ? 0 : System.currentTimeMillis() - last;
            if (dt >= cfg.perfHudLagThreshold) {
                String col = dt < 500 ? "§a" : dt < 1000 ? "§e" : dt < 2000 ? "§6" : "§c";
                l.add("zzz for ");
                v.add(col + String.format(java.util.Locale.ROOT, "%.2fs", dt / 1000.0));
            }
        }
        labels = l.toArray(new String[0]);
        values = v.toArray(new String[0]);
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHud || labels.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;
        draw(context, mc, cfg.perfHudX, cfg.perfHudY, cfg.perfHudScale, labels, values);
    }

    /** HUD editor preview. */
    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        boolean cps = TeslaMapsConfig.get().perfHudCps;
        draw(context, mc, x, y, scale, cps ? SAMPLE_LABELS_CPS : SAMPLE_LABELS, cps ? SAMPLE_VALUES_CPS : SAMPLE_VALUES);
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String[] labels, String[] values) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        int labelColor = TeslaMapsConfig.parseColor(cfg.perfHudLabelColor);
        int valueColor = TeslaMapsConfig.parseColor(cfg.perfHudValueColor);
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int dx = 0, dy = 0;
        for (int i = 0; i < labels.length; i++) {
            context.text(mc.font, labels[i], dx, dy, labelColor, true);
            context.text(mc.font, values[i], dx + mc.font.width(labels[i]), dy, valueColor, true);
            if (cfg.perfHudVertical) dy += mc.font.lineHeight + 1;
            else dx += mc.font.width(labels[i]) + mc.font.width(values[i]) + mc.font.width(" ");
        }
        pose.popMatrix();
    }

    public static int width(Minecraft mc) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHudVertical) return mc.font.width(SAMPLE_TEXT) + (cfg.perfHudCps ? mc.font.width(" CPS: 8 | 12") : 0);
        return mc.font.width("Ping: 45ms");
    }

    public static int height(Minecraft mc) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHudVertical) return mc.font.lineHeight;
        return (mc.font.lineHeight + 1) * (cfg.perfHudCps ? 4 : 3);
    }
}
