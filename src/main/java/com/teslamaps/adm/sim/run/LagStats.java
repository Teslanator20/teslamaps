// Ported from ADM's p3sim/run/LagStats.kt
package com.teslamaps.adm.sim.run;

import java.util.ArrayList;
import java.util.List;

public class LagStats {
    public static final double FREEZE_MS = 100.0;
    public static final double BURST_MS = 45.0;
    public static final double BURST_WINDOW_MS = 5000.0;
    private static final double TICK_MS = 50.0;

    private final int keep;
    private final ArrayList<Long> times = new ArrayList<>();

    public LagStats() { this(100_000); }

    public LagStats(int keep) { this.keep = keep; }

    public void record(long nanos) {
        times.add(nanos);
        if (times.size() > keep) times.subList(0, keep / 10).clear();
    }

    public void clear() { times.clear(); }

    public int count() { return times.size(); }

    public static class Freeze {
        public final int atTick;
        public final double gapMs;
        public final double missedTicks;
        public final int burstTicks;
        public final double burstMeanMs;
        public final double recoveredTicks;

        public Freeze(int atTick, double gapMs, double missedTicks, int burstTicks, double burstMeanMs, double recoveredTicks) {
            this.atTick = atTick;
            this.gapMs = gapMs;
            this.missedTicks = missedTicks;
            this.burstTicks = burstTicks;
            this.burstMeanMs = burstMeanMs;
            this.recoveredTicks = recoveredTicks;
        }
    }

    public static class Report {
        public final int ticks;
        public final double seconds;
        public final List<Freeze> freezes;
        public final double lostTicks;

        public Report(int ticks, double seconds, List<Freeze> freezes, double lostTicks) {
            this.ticks = ticks;
            this.seconds = seconds;
            this.freezes = freezes;
            this.lostTicks = lostTicks;
        }

        public double lostPerMinute() { return seconds > 0 ? lostTicks * 60.0 / seconds : 0.0; }

        public double longestMs() {
            if (freezes.isEmpty()) return 0.0;
            double max = freezes.get(0).gapMs;
            for (Freeze f : freezes) if (f.gapMs > max) max = f.gapMs;
            return max;
        }

        public double recoveredShare() {
            double missed = 0.0;
            for (Freeze f : freezes) missed += f.missedTicks;
            if (missed <= 0) return 0.0;
            double rec = 0.0;
            for (Freeze f : freezes) rec += f.recoveredTicks;
            return rec / missed;
        }
    }

    public Report report() { return report(0.0); }

    public Report report(double lastSeconds) {
        if (times.size() < 2) return null;
        long end = times.get(times.size() - 1);
        int from = 0;
        if (lastSeconds > 0) {
            long cut = end - (long) (lastSeconds * 1e9);
            int idx = -1;
            for (int k = 0; k < times.size(); k++) if (times.get(k) >= cut) { idx = k; break; }
            from = Math.max(idx, 0);
        }
        if (times.size() - from < 2) return null;
        ArrayList<Freeze> freezes = new ArrayList<>();
        int i = from + 1;
        while (i < times.size()) {
            double gap = (times.get(i) - times.get(i - 1)) / 1e6;
            if (gap >= FREEZE_MS) {
                int n = 0;
                double sum = 0.0, gained = 0.0;
                int j = i + 1;
                while (j < times.size() && (times.get(j) - times.get(i)) / 1e6 <= BURST_WINDOW_MS) {
                    double d = (times.get(j) - times.get(j - 1)) / 1e6;
                    if (d >= BURST_MS) break;
                    n++; sum += d; gained += 1.0 - d / TICK_MS; j++;
                }
                freezes.add(new Freeze(i, gap, gap / TICK_MS - 1, n, n > 0 ? sum / n : 0.0, gained));
                i = j;
            } else i++;
        }
        double seconds = (times.get(times.size() - 1) - times.get(from)) / 1e9;
        int ticks = times.size() - from - 1;
        return new Report(ticks, seconds, freezes, seconds * 20.0 - ticks);
    }
}
