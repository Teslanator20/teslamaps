// Ported from ADM's p3sim/clear/blood/camp/CampMech.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The Watcher's blood camp on the main server (Floor 7), as measured on recorded fights. Ticks are door ticks. */
public final class CampMech {
    private CampMech() {}

    public static final int LOOP = 10;
    public static final int FAST_PERIOD = 40;
    public static final int SLOW_PERIOD = 60;

    public static final int FIRST_LATER = 420;
    public static final int OPEN_FIRST = 90;
    public static final int OPEN_PERIOD = 30;

    public static final double V_OPEN = 0.70;
    public static final double V_HOME = 0.50;
    public static final double V_FAST = 0.70 * 0.875;
    public static final double V_SLOW = 0.50 * 0.875;

    public static final double ARRIVE_R2 = 1.5;

    public static final int THROW_DELAY = 10;

    public static final int FLIGHT_INITIAL = 80;
    public static final int FLIGHT_LATER = 40;

    public static final int LATER_SPAWN = 39;
    public static final int INITIAL_SPAWN = 79;

    public static final int HOME_MIN = 40;

    public static final int TALK_AFTER = 18;
    public static final int TALK_BEFORE = 10;

    public static final int KILLS_TO_DEFEAT = 15;
    public static final int LATER_THROWS = 11;

    public static final int PASS_WINDOW = 20;

    /** First loop tick strictly after d. */
    public static int loopTickAfter(int d) { return Math.floorDiv(d, LOOP) * LOOP + LOOP; }

    public static boolean isLoop(int r) { return Math.floorMod(r, LOOP) == 0; }

    public enum Speed {
        OPEN(V_OPEN), HOME(V_HOME), FAST(V_FAST), SLOW(V_SLOW);

        public final double v;

        Speed(double v) { this.v = v; }
    }

    /** One flight: moving ticks and the rest point. */
    public static final class Fly {
        public final int n;
        public final Vec3 end;

        public Fly(int n, Vec3 end) {
            this.n = n;
            this.end = end;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Fly f && n == f.n && Objects.equals(end, f.end);
        }
        @Override public int hashCode() { return Objects.hash(n, end); }
        @Override public String toString() { return "Fly(n=" + n + ", end=" + end + ")"; }
    }

    public static Fly flyTo(Vec3 p0, Vec3 target, double v) {
        double d = target.minus(p0).length();
        if (d * d < ARRIVE_R2) return new Fly(0, p0);
        int k = Math.max(0, (int) Math.floor((d - Math.sqrt(ARRIVE_R2)) / v) + 1);
        while (k > 0 && sq(d - (k - 1) * v) < ARRIVE_R2) k--;
        while (sq(d - k * v) >= ARRIVE_R2) k++;
        Vec3 dir = target.minus(p0).div(d);
        return new Fly(k, p0.plus(dir.times(v).times((double) k)));
    }

    /** One block above the head stand. */
    public static Vec3 target(Vec3 station) { return new Vec3(station.x, station.y + 1.0, station.z); }

    private static double sq(double x) { return x * x; }

    // ---- the Watcher's chat queue

    public enum Line {
        REACT(42), SPAWN(62), HANDLE(62), ENOUGH(62), INTRO(42),
        ATTACK(42), LEDGE(62);

        public final int busy;

        Line(int busy) { this.busy = busy; }
    }

    /** A line asked for on tick t. */
    public static final class Request {
        public final int t;
        public final Line line;

        public Request(int t, Line line) {
            this.t = t;
            this.line = line;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Request r && t == r.t && line == r.line;
        }
        @Override public int hashCode() { return Objects.hash(t, line); }
        @Override public String toString() { return "Request(t=" + t + ", line=" + line + ")"; }
    }

    /** A line printed on tick at, asked for on tick requested. */
    public static final class Printed {
        public final int at;
        public final Line line;
        public final int requested;

        public Printed(int at, Line line, int requested) {
            this.at = at;
            this.line = line;
            this.requested = requested;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Printed p && at == p.at && line == p.line && requested == p.requested;
        }
        @Override public int hashCode() { return Objects.hash(at, line, requested); }
        @Override public String toString() { return "Printed(at=" + at + ", line=" + line + ", requested=" + requested + ")"; }
    }

    /** First in, first out. */
    public static List<Printed> chatLines(List<Request> requests, int until) {
        List<Printed> out = new ArrayList<>();
        int free = Integer.MIN_VALUE / 2;
        List<Request> sorted = new ArrayList<>(requests);
        sorted.sort(Comparator.<Request>comparingInt(r -> r.t).thenComparingInt(r -> r.line == Line.REACT ? 0 : 1));
        for (Request r : sorted) {
            int p = Math.max(r.t, free);
            if (p >= until) break;
            out.add(new Printed(p, r.line, r.t));
            free = p + r.line.busy;
        }
        return out;
    }

    public static boolean talking(List<Request> requests, int d) {
        List<Request> asked = new ArrayList<>();
        for (Request r : requests) if (r.t <= d) asked.add(r);
        List<Printed> lines = chatLines(asked, d + TALK_BEFORE + 1);
        for (Printed it : lines) {
            if ((it.at >= d - TALK_AFTER && it.at <= d) || (it.at > d && it.at <= d + TALK_BEFORE && it.requested <= d)) return true;
        }
        return false;
    }

    public static int passTick(int l, List<Printed> lines) {
        Printed prev = null;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).at < l) { prev = lines.get(i); break; }
        }
        if (prev == null) return l;
        int free = prev.at + prev.line.busy;
        int diff = free - l;
        return (diff >= 1 && diff <= PASS_WINDOW) ? free : l;
    }

    public static List<Request> requestsOf(List<Request> printed) {
        List<Request> out = new ArrayList<>();
        Request prev = null;
        List<Request> sorted = new ArrayList<>(printed);
        sorted.sort(Comparator.comparingInt(r -> r.t));
        for (Request p : sorted) {
            Request before = prev;
            boolean free = before == null || p.t > before.t + before.line.busy || p.line == Line.ATTACK || p.line == Line.LEDGE;
            out.add(new Request(free ? p.t : Math.min(p.t, before.t + 1), p.line));
            prev = p;
        }
        return out;
    }

    public static Line lineOf(String chat) {
        String prefix = "[BOSS] The Watcher: ";
        if (!chat.startsWith(prefix)) return null;
        String text = chat.substring(prefix.length());
        if (text.startsWith("You have proven yourself")) return null;
        if (text.equals(CampLines.INTRO) || text.equals(CampLines.INTRO_OTHER)) return Line.INTRO;
        if (CampLines.SPAWN.contains(text)) return Line.SPAWN;
        if (CampLines.REACT.contains(text)) return Line.REACT;
        if (CampLines.HANDLE.contains(text)) return Line.HANDLE;
        if (CampLines.ENOUGH.contains(text)) return Line.ENOUGH;
        if (CampLines.ATTACK.contains(text)) return Line.ATTACK;
        if (CampLines.LEDGE.contains(text)) return Line.LEDGE;
        return Line.REACT;
    }
}
