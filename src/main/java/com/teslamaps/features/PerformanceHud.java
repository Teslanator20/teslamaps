package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Odin-style performance HUD: FPS / TPS / Ping. TPS from the time between
// ClientboundSetTimePacket arrivals (server sends one every 20 ticks), ping via PingMeter.
public class PerformanceHud {
    public static final String SAMPLE_TEXT = "FPS: 240 TPS: 20.0 Ping: 45ms";

    private static volatile long lastTimePacket = 0L;
    private static final float[] tpsLog = new float[10];
    private static int tpsIndex = 0;
    private static int tpsCount = 0;
    private static volatile float currentTps = 20f;

    private static int pingCooldown = 0;
    private static String cachedLine = "";
    private static String[] cachedLines = new String[0];

    // netty thread - timestamps only
    public static void onTimePacket() {
        long now = System.currentTimeMillis();
        if (lastTimePacket != 0L) {
            float tps = Math.min(20f, 20000f / Math.max(1, now - lastTimePacket));
            currentTps = tps;
            synchronized (tpsLog) {
                tpsLog[tpsIndex] = tps;
                tpsIndex = (tpsIndex + 1) % tpsLog.length;
                if (tpsCount < tpsLog.length) tpsCount++;
            }
        }
        lastTimePacket = now;
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

        StringBuilder line = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>(3);
        if (cfg.perfHudFps) append(line, lines, "FPS: ", String.valueOf(mc.getFps()));
        if (cfg.perfHudTps) append(line, lines, "TPS: ", String.format("%.1f", averageTps()));
        if (cfg.perfHudPing) append(line, lines, "Ping: ", (int) PingMeter.getLastPingMs() + "ms");
        cachedLine = line.toString();
        cachedLines = lines.toArray(new String[0]);
    }

    private static void append(StringBuilder line, java.util.List<String> lines, String label, String value) {
        if (line.length() > 0) line.append(' ');
        line.append("§b").append(label).append("§f").append(value);
        lines.add("§b" + label + "§f" + value);
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHud || cachedLines.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;
        draw(context, mc, cfg.perfHudX, cfg.perfHudY, cfg.perfHudScale, cfg.perfHudVertical, cachedLine, cachedLines);
    }

    /** HUD editor preview. */
    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        String[] sample = { "§bFPS: §f240", "§bTPS: §f20.0", "§bPing: §f45ms" };
        draw(context, mc, x, y, scale, TeslaMapsConfig.get().perfHudVertical, SAMPLE_TEXT.replace("FPS:", "§bFPS:§f").replace("TPS:", "§bTPS:§f").replace("Ping:", "§bPing:§f"), sample);
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, boolean vertical, String line, String[] lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        if (vertical) {
            int dy = 0;
            for (String l : lines) {
                context.text(mc.font, l, 0, dy, 0xFFFFFFFF, true);
                dy += mc.font.lineHeight + 1;
            }
        } else {
            context.text(mc.font, line, 0, 0, 0xFFFFFFFF, true);
        }
        pose.popMatrix();
    }

    public static int width(Minecraft mc) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHudVertical) return mc.font.width(SAMPLE_TEXT);
        return mc.font.width("Ping: 45ms");
    }

    public static int height(Minecraft mc) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.perfHudVertical) return mc.font.lineHeight;
        return (mc.font.lineHeight + 1) * 3;
    }
}
