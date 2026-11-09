package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Odin-style performance HUD: FPS / TPS / Ping. TPS from the time between
// ClientboundSetTimePacket arrivals (server sends one every 20 ticks), ping via PingMeter.
public class PerformanceHud {
    public static final com.teslamaps.utils.LineOrder.Def LINES = com.teslamaps.utils.LineOrder.define("perfhud",
            new String[]{"fps", "tps", "ping", "cps", "day", "lag", "coords", "looking", "time"},
            new String[]{"FPS", "TPS", "Ping", "CPS", "Lobby Day", "Lag (zzz)", "Coordinates", "Looking At", "Time"});
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
        if (Minecraft.getInstance().gui.screen() != null) return;
        long now = System.currentTimeMillis();
        if (button == 0) leftClicks[leftIdx++ & 63] = now;
        else if (button == 1) rightClicks[rightIdx++ & 63] = now;
    }

    private static int cps(long[] clicks, long now) {
        int n = 0;
        for (long t : clicks) if (now - t <= 1000) n++;
        return n;
    }

    private static float tpsStat(String mode) {
        synchronized (tpsLog) {
            if (tpsCount == 0) return 20f;
            if ("Current".equals(mode)) return tpsLog[(tpsIndex - 1 + tpsLog.length) % tpsLog.length];
            if ("Min".equals(mode) || "Max".equals(mode)) {
                boolean min = "Min".equals(mode);
                float r = tpsLog[0];
                for (int i = 1; i < tpsCount; i++) r = min ? Math.min(r, tpsLog[i]) : Math.max(r, tpsLog[i]);
                return r;
            }
        }
        return averageTps();
    }

    private static String tpsColor(float tps) {
        return tps >= 19.5f ? "§a" : tps >= 17f ? "§2" : tps >= 14f ? "§e" : tps >= 10f ? "§6" : "§c";
    }

    private static String pingColor(float ms) {
        return ms < 50 ? "§a" : ms < 100 ? "§2" : ms < 150 ? "§e" : ms < 200 ? "§6" : "§c";
    }

    private static String fpsColor(int fps, int limit) {
        if (limit <= 0 || limit >= 260) limit = 240;
        float f = fps / (float) limit;
        return f >= 0.75f ? "§a" : f >= 0.5f ? "§e" : f >= 0.25f ? "§6" : "§c";
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
        if (mc.getConnection() == null) { lastTimePacket = 0L; tpsCount = 0; PingMeter.clearSamples(); return; }

        if (cfg.perfHudPing && --pingCooldown <= 0) {
            PingMeter.requestSilent();
            pingCooldown = 40; // every 2s
        }

        com.teslamaps.utils.LineOrder.Lines out = com.teslamaps.utils.LineOrder.lines("perfhud");
        boolean colored = cfg.perfHudColorCoded;
        if (cfg.perfHudFps) {
            int fps = mc.getFps();
            out.key("fps").add("FPS: \0" + (colored ? fpsColor(fps, mc.options.framerateLimit().get()) : "") + fps);
        }
        if (cfg.perfHudTps) {
            String mode = cfg.perfHudTpsMode == null ? "Average" : cfg.perfHudTpsMode;
            float tps = tpsStat(mode);
            out.key("tps").add(("Average".equals(mode) ? "TPS: " : mode + " TPS: ") + "\0" + (colored ? tpsColor(tps) : "") + String.format("%.1f", tps));
        }
        if (cfg.perfHudPing) {
            String mode = cfg.perfHudPingMode == null ? "Instant" : cfg.perfHudPingMode;
            float ping = "Median".equals(mode) ? PingMeter.medianPingMs()
                    : "Mean".equals(mode) ? PingMeter.meanPingMs() : PingMeter.getLastPingMs();
            out.key("ping").add("Ping: \0" + (colored ? pingColor(ping) : "") + (int) ping + "ms");
        }
        if (cfg.perfHudCps) {
            long now = System.currentTimeMillis();
            out.key("cps").add("CPS: \0" + cps(leftClicks, now) + " | " + cps(rightClicks, now));
        }
        if (cfg.perfHudDay && mc.level != null) out.key("day").add("Day: \0" + mc.level.getGameTime() / 24000L);
        if (cfg.perfHudLag && !mc.isLocalServer() && com.teslamaps.utils.SkyblockUtils.isOnHypixel()) {
            long last = com.teslamaps.utils.ServerTickTimer.lastPingMs();
            long dt = last == 0 ? 0 : System.currentTimeMillis() - last;
            if (dt >= cfg.perfHudLagThreshold) {
                String col = dt < 500 ? "§a" : dt < 1000 ? "§e" : dt < 2000 ? "§6" : "§c";
                out.key("lag").add("zzz for \0" + col + String.format(java.util.Locale.ROOT, "%.2fs", dt / 1000.0));
            }
        }
        // HUD-041
        if (cfg.perfHudCoords && mc.player != null) {
            var p = mc.player;
            out.key("coords").add("XYZ: \0" + String.format(java.util.Locale.ROOT, "%.1f / %.1f / %.1f (%.1f / %.1f)",
                    p.getX(), p.getY(), p.getZ(), net.minecraft.util.Mth.wrapDegrees(p.getYRot()), p.getXRot()));
            if (cfg.perfHudLookingAt && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh
                    && bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var b = bh.getBlockPos();
                out.key("looking").add("Looking at: \0" + b.getX() + " / " + b.getY() + " / " + b.getZ());
            }
        }
        if (cfg.perfHudTime) out.key("time").add(timeLabel(cfg) + "\0" + clock(cfg));
        java.util.List<String> built = out.build();
        java.util.List<String> l = new java.util.ArrayList<>(built.size());
        java.util.List<String> v = new java.util.ArrayList<>(built.size());
        for (String e : built) {
            int i = e.indexOf('\0');
            l.add(e.substring(0, i));
            v.add(e.substring(i + 1));
        }
        labels = l.toArray(new String[0]);
        values = v.toArray(new String[0]);
    }

    private static final java.time.format.DateTimeFormatter H24 = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private static final java.time.format.DateTimeFormatter H24S = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final java.time.format.DateTimeFormatter H12 = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter H12S = java.time.format.DateTimeFormatter.ofPattern("h:mm:ss a", java.util.Locale.ENGLISH);

    private static String timeLabel(TeslaMapsConfig cfg) {
        if ("Symbol".equals(cfg.perfHudTimeLabel)) return "⌚ ";
        return "None".equals(cfg.perfHudTimeLabel) ? "" : "Time: ";
    }

    private static String clock(TeslaMapsConfig cfg) {
        boolean h12 = "12h".equals(cfg.perfHudTimeFormat);
        var f = h12 ? (cfg.perfHudTimeSeconds ? H12S : H12) : (cfg.perfHudTimeSeconds ? H24S : H24);
        return java.time.LocalTime.now().format(f);
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
        String[][] lv = previewLines();
        draw(context, mc, x, y, scale, lv[0], lv[1]);
    }

    private static String[][] previewLines() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean extras = cfg.perfHudCps || cfg.perfHudDay || cfg.perfHudTime || cfg.perfHudLag || cfg.perfHudCoords
                || !"Average".equals(cfg.perfHudTpsMode) || cfg.perfHudColorCoded;
        if (extras && labels.length > 0) return new String[][]{labels, values};
        String[] l = cfg.perfHudCps ? SAMPLE_LABELS_CPS : SAMPLE_LABELS, v = cfg.perfHudCps ? SAMPLE_VALUES_CPS : SAMPLE_VALUES;
        if (cfg.perfHudDay) {
            l = java.util.Arrays.copyOf(l, l.length + 1); l[l.length - 1] = "Day: ";
            v = java.util.Arrays.copyOf(v, v.length + 1); v[v.length - 1] = "12";
        }
        if (cfg.perfHudTime) {
            l = java.util.Arrays.copyOf(l, l.length + 1); l[l.length - 1] = timeLabel(cfg);
            v = java.util.Arrays.copyOf(v, v.length + 1); v[v.length - 1] = cfg.perfHudTimeSeconds ? "12:00:00 PM" : "12:00 PM";
        }
        return new String[][]{l, v};
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
        String[][] lv = previewLines();
        int w = 0, sum = 0;
        for (int i = 0; i < lv[0].length; i++) {
            int lw = mc.font.width(lv[0][i]) + mc.font.width(lv[1][i]);
            w = Math.max(w, lw);
            sum += lw + (i > 0 ? mc.font.width(" ") : 0);
        }
        return cfg.perfHudVertical ? w : sum;
    }

    public static int height(Minecraft mc) {
        if (!TeslaMapsConfig.get().perfHudVertical) return mc.font.lineHeight;
        return (mc.font.lineHeight + 1) * Math.max(1, previewLines()[0].length);
    }
}
