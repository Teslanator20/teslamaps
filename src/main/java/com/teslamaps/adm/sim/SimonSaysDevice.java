package com.teslamaps.adm.sim;
// Ported from ADM's practice/SimonSaysDevice.kt

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SimonSaysDevice {
    private final Io io;

    public SimonSaysDevice(Io io) { this.io = io; }

    public interface Io {
        int[] newSequence();
        void lamp(int cell, boolean on);
        void button(int cell, boolean up, boolean pressed);
        default int lampTick(int j) { return 8 * j; }
        default void opened(int round) {}
        void completed(String by);
    }

    public enum Start { STARTED, RESET, DROPPED, IGNORED }

    public enum Press {
        NONE,
        IDLE,
        RIGHT,
        ROUND,
        WRONG,
    }

    public long now = 0L;

    private static final class Job {
        final long at;
        final int gen;
        final long order;
        final Runnable run;
        Job(long at, int gen, long order, Runnable run) { this.at = at; this.gen = gen; this.order = order; this.run = run; }
    }

    private final ArrayList<Job> jobs = new ArrayList<>();
    private int gen = 0;
    private long order = 0L;

    public void after(int ticks, Runnable run) { jobs.add(new Job(now + Math.max(ticks, 0), gen, order++, run)); }

    public int round = 0;
    public boolean started = false;
    public boolean done = false;
    public int[] sequence = new int[MAX_ROUNDS];
    private String lastStarter = "";
    public boolean accepting = false;
    public int next = 0;

    private final boolean[] lit = new boolean[16];
    private final boolean[] up = new boolean[16];
    private final long[] downUntil = new long[16];

    public final ArrayList<Integer> shown = new ArrayList<>();
    private int showId = 0;

    private static final class Last {
        final long tick;
        final double ms;
        Last(long tick, double ms) { this.tick = tick; this.ms = ms; }
    }

    private final HashMap<String, Last> lastPress = new HashMap<>();
    private final java.util.Random random = new java.util.Random();

    public Integer due() { return accepting && next < round ? sequence[next] : null; }

    public boolean pressable(int cell) { return up[cell] && downUntil[cell] <= now; }

    public void tick() {
        while (true) {
            List<Job> due = new ArrayList<>();
            for (Job j : jobs) if (j.at <= now) due.add(j);
            if (due.isEmpty()) break;
            jobs.removeAll(due);
            due.sort((a, b) -> Long.compare(a.order, b.order));
            for (Job j : due) if (j.gen == gen) j.run.run();
        }
        now++;
    }

    public Start start(String who, double ms) { return start(who, ms, true); }

    public Start start(String who, double ms, boolean limit) {
        if (done) return Start.IGNORED;
        if (limit && !limiter(who, ms)) return Start.DROPPED;
        if (started) {
            started = false;
            reset();
            return Start.RESET;
        }
        started = true;
        lastStarter = who;
        sequence = io.newSequence();
        after(ROUND_DELAY, () -> nextRound(lastStarter));
        return Start.STARTED;
    }

    private boolean limiter(String who, double ms) {
        double at = ms + random.nextGaussian() * JITTER_MS;
        Last last = lastPress.get(who);
        if (last != null) {
            long ticks = now - last.tick;
            if (ticks <= 0 || ticks == 1L && at - last.ms < LIMIT_MS) return false;
        }
        lastPress.put(who, new Last(now, at));
        return true;
    }

    private void reset() {
        round = 0; accepting = false; next = 0;
        clearLamps();
        for (int c = 0; c < 16; c++) setButton(c, false);
    }

    private void nextRound(String by) {
        if (done) return;
        round++;
        if (round > MAX_ROUNDS) { complete(by); return; }
        startShow();
    }

    public void showRound(int n) {
        if (done) return;
        sequence = io.newSequence();
        round = n - 1;
        nextRound(lastStarter);
    }

    private void startShow() {
        clearLamps();
        for (int c = 0; c < 16; c++) setButton(c, false);
        accepting = false; next = 0;
        final int id = ++showId;
        shown.clear();
        int n = round;
        for (int j = 0; j < n; j++) {
            final int jj = j;
            Runnable light = () -> {
                int cell = sequence[jj];
                setLamp(cell, true);
                setButton(cell, false);
                if (id == showId) shown.add(cell);
                after(io.lampTick(jj + 1) - io.lampTick(jj), () -> setLamp(cell, false));
            };
            int at = io.lampTick(j);
            if (at <= 0) light.run(); else after(at, light);
        }
        after(io.lampTick(n) + 10, () -> buttonsBack(true));
    }

    public void buttonsBack() { buttonsBack(false); }

    public void buttonsBack(boolean accept) {
        for (int c = 0; c < 16; c++) if (!lit[c]) setButton(c, true);
        if (accept && round >= 1 && !accepting && !done) {
            accepting = true;
            io.opened(round);
        }
    }

    private void complete(String by) {
        done = true;
        accepting = false;
        gen++;
        jobs.clear();
        clearLamps();
        for (int c = 0; c < 16; c++) setButton(c, false);
        shown.clear();
        io.completed(by);
    }

    public Press press(int cell, String who) {
        if (!pressable(cell)) return Press.NONE;
        io.button(cell, true, true);
        downUntil[cell] = now + 3;
        after(3, () -> { if (up[cell]) io.button(cell, true, false); });
        if (!accepting || done) return Press.IDLE;
        if (cell == sequence[next]) {
            next++;
            if (next < round) return Press.RIGHT;
            accepting = false;
            after(NEXT_ROUND_DELAY, () -> nextRound(who));
            return Press.ROUND;
        }
        accepting = false; next = 0; started = false; round = 0;
        after(3, () -> { for (int c = 0; c < 16; c++) setButton(c, false); });
        return Press.WRONG;
    }

    public void clear() {
        gen++;
        jobs.clear();
        round = 0; started = false; done = false; accepting = false; next = 0;
        shown.clear();
        lastPress.clear();
        clearLamps();
        for (int c = 0; c < 16; c++) setButton(c, false);
    }

    private void setLamp(int cell, boolean on) {
        if (lit[cell] == on) return;
        lit[cell] = on;
        io.lamp(cell, on);
    }

    private void clearLamps() { for (int c = 0; c < 16; c++) setLamp(c, false); }

    private void setButton(int cell, boolean show) {
        if (up[cell] == show) return;
        up[cell] = show;
        io.button(cell, show, false);
    }

    public static final int MAX_ROUNDS = 4;
    public static final int ROUND_DELAY = 4;
    public static final int NEXT_ROUND_DELAY = 6;
    public static final double LIMIT_MS = 49.0;
    public static final double JITTER_MS = 6.0;
}
