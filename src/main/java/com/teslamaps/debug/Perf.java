package com.teslamaps.debug;

import com.teslamaps.TeslaMaps;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-feature timers for the render, tick and mixin paths. A sampling profiler
 * reports Java frames and averages; this reports feature names and the worst
 * single call, which is what actually shows up as a stutter.
 *
 * Off by default. Enable with {@code /tmap perf on}, read with {@code /tmap perf}.
 */
public class Perf {

    /** Read directly at every call site, so the disabled path is one static field load. */
    public static boolean enabled = false;

    public static final class Entry {
        public final String name;
        public long calls;
        public long totalNs;
        public long maxNs;        // worst single call
        public long frameNs;      // accumulated within the current frame
        public long maxFrameNs;   // worst frame total (a feature called many times per frame)

        Entry(String name) {
            this.name = name;
        }
    }

    private static final Map<String, Entry> entries = new HashMap<>();

    private static final int MAX_DEPTH = 16;
    private static final Entry[] stack = new Entry[MAX_DEPTH];
    private static final long[] startNs = new long[MAX_DEPTH];
    private static int depth = 0;

    private static long frames = 0;
    private static long lastFrameNs = 0;
    private static long spikeThresholdNs = 0;
    private static long spikes = 0;

    private static Entry entry(String name) {
        Entry e = entries.get(name);
        if (e == null) {
            e = new Entry(name);
            entries.put(name, e);
        }
        return e;
    }

    public static void begin(String name) {
        if (!enabled || depth >= MAX_DEPTH) return;
        stack[depth] = entry(name);
        startNs[depth] = System.nanoTime();
        depth++;
    }

    public static void end() {
        if (!enabled || depth == 0) return;
        depth--;
        long ns = System.nanoTime() - startNs[depth];
        Entry e = stack[depth];
        stack[depth] = null;
        e.calls++;
        e.totalNs += ns;
        e.frameNs += ns;
        if (ns > e.maxNs) e.maxNs = ns;
    }

    /** Wraps a hud element at registration, so every overlay is measured by name. */
    public static HudElement hud(String name, HudElement element) {
        return (GuiGraphicsExtractor ctx, DeltaTracker delta) -> {
            if (!enabled) {
                try {
                    element.extractRenderState(ctx, delta);
                } catch (Throwable e) {
                    com.teslamaps.features.ErrorManager.caught(name, e);
                }
                return;
            }
            begin(name);
            try {
                element.extractRenderState(ctx, delta);
            } catch (Throwable e) {
                com.teslamaps.features.ErrorManager.caught(name, e);
            } finally {
                end();
            }
        };
    }

    /**
     * Called once per rendered frame from {@code PerfFrameMixin}. Closes out the
     * per-frame totals and reports a frame that ran far over the recent norm.
     */
    public static void frame(long frameTimeNs) {
        if (!enabled) return;
        depth = 0; // a crash mid-frame must not leave the stack unbalanced
        frames++;

        boolean spike = spikeThresholdNs > 0 && frameTimeNs > spikeThresholdNs;
        List<String> hot = spike ? new ArrayList<>() : null;
        for (Entry e : entries.values()) {
            long ns = e.frameNs;
            if (ns > e.maxFrameNs) e.maxFrameNs = ns;
            e.frameNs = 0;
            if (spike && ns > 0) hot.add(us(ns) + "|" + e.name);
        }

        if (spike && !hot.isEmpty()) {
            spikes++;
            // sort descending by the leading microsecond count
            hot.sort((x, y) -> Long.compare(
                    Long.parseLong(y.substring(0, y.indexOf('|'))),
                    Long.parseLong(x.substring(0, x.indexOf('|')))));
            StringBuilder sb = new StringBuilder("[Perf] frame ")
                    .append(frameTimeNs / 1_000_000L).append("ms:");
            for (int i = 0; i < Math.min(6, hot.size()); i++) {
                String h = hot.get(i);
                int bar = h.indexOf('|');
                sb.append(' ').append(h, bar + 1, h.length()).append('=').append(h, 0, bar).append("us");
            }
            TeslaMaps.LOGGER.info(sb.toString());
        }

        lastFrameNs = frameTimeNs;
    }

    private static long us(long ns) {
        return ns / 1000L;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (on) reset();
    }

    /** Frames slower than this get their per-feature breakdown logged. 0 disables. */
    public static void setSpikeThresholdMs(int ms) {
        spikeThresholdNs = ms <= 0 ? 0 : ms * 1_000_000L;
    }

    public static int spikeThresholdMs() {
        return (int) (spikeThresholdNs / 1_000_000L);
    }

    public static void reset() {
        entries.clear();
        depth = 0;
        frames = 0;
        spikes = 0;
    }

    public static long frames() {
        return frames;
    }

    public static long spikes() {
        return spikes;
    }

    public static long lastFrameMs() {
        return lastFrameNs / 1_000_000L;
    }

    /** Snapshot sorted by total time, worst first. */
    public static List<Entry> snapshot() {
        List<Entry> out = new ArrayList<>(entries.values());
        out.sort((a, b) -> Long.compare(b.totalNs, a.totalNs));
        return out;
    }
}
