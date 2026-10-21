package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostCapture.kt

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GhostRecording {
    public final String clazz;
    public final String skill;
    public final ArrayList<GhostRun.Frame> frames = new ArrayList<>();
    public final ArrayList<GhostRun.Ev> events = new ArrayList<>();
    public final int[] sectionN;

    public GhostRecording(String clazz, String skill) {
        this.clazz = clazz;
        this.skill = skill;
        int[] s = new int[6];
        Arrays.fill(s, -1);
        s[1] = 0;
        this.sectionN = s;
    }

    public void frame(int n, GhostRun.Frame f) {
        if (n < 0) return;
        while (frames.size() < n) frames.add(frames.isEmpty() ? f : frames.get(frames.size() - 1));
        if (frames.size() == n) frames.add(f); else frames.set(n, f);
    }

    public void event(int n, String type, String a, int k) { if (n >= 0) events.add(new GhostRun.Ev(n, type, a, k)); }

    public void event(int n, String type, String a) { event(n, type, a, 0); }

    public void event(int n, String type) { event(n, type, "", 0); }

    public void section(int s, int n) { if (s >= 1 && s <= 5 && sectionN[s] < 0) sectionN[s] = n; }

    public GhostRun finish() { return finish(System.currentTimeMillis()); }

    public GhostRun finish(long date) {
        int time = sectionN[5];
        if (time < 0 || frames.isEmpty() || time >= frames.size()) return null;
        int end = frames.size() - 1;
        List<GhostRun.Ev> evs = new ArrayList<>();
        for (GhostRun.Ev e : events) {
            if (e.n < 0 || e.n > end) continue;
            if (!e.type.equals("leap")) { evs.add(e); continue; }
            Integer m = null;
            double best = 0;
            for (int it = e.n; it <= Math.min(e.n + 2, end); it++) {
                if (it <= 0) continue;
                double d = frames.get(it).p.dist(frames.get(it - 1).p);
                if (m == null || d > best) { m = it; best = d; }
            }
            if (m != null && frames.get(m).p.dist(frames.get(m - 1).p) > LEAP_JUMP) evs.add(new GhostRun.Ev(m, e.type, e.a, e.k));
            else evs.add(e);
        }
        evs.sort((x, y) -> Integer.compare(x.n, y.n));
        return new GhostRun(clazz, skill, time, new ArrayList<>(frames), evs, sectionN.clone(), date);
    }

    public static final double LEAP_JUMP = 4.0;
}
