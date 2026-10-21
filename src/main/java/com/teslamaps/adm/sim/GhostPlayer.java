package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostPlayer.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public class GhostPlayer {
    public final GhostRun run;
    private final World world;
    private final Body body;

    public GhostPlayer(GhostRun run, World world, Body body) {
        this.run = run;
        this.world = world;
        this.body = body;
        this.actions = build(run);
    }

    public interface World {
        Integer sectionStart(int s);
        boolean stationDone(String id);
        boolean canDo(String id);
        boolean gateDown(int s);
        boolean canGate(int s);
        Integer eeArrivedAt(int into, String who);
        Integer eeReadyAt(int into);
        Integer recoredAt(String who);
        P positionOf(String who);
    }

    public interface Body {
        void move(P p, float yaw, float pitch, String held);
        void complete(String id);
        void gate(int s);
        void leap(String who, P at);
        void swing();
        void eeOn(int into);
        void eeLeft(int into);
        void recore();
        void working(String id);
        void giveUp(String job);
    }

    public enum Kind { DONE, GATE, LEAP, EE_ON, EE_LEAVE, RECORE, ENTER, GO }

    public abstract static class Anchor {
        public final int rec;

        protected Anchor(int rec) { this.rec = rec; }

        public static class Section extends Anchor {
            public final int s;
            public Section(int s, int rec) { super(rec); this.s = s; }
        }

        public static class EeOn extends Anchor {
            public final int into;
            public final String who;
            public EeOn(int into, String who, int rec) { super(rec); this.into = into; this.who = who; }
        }

        public static class Ready extends Anchor {
            public final int into;
            public Ready(int into, int rec) { super(rec); this.into = into; }
        }

        public static class Recore extends Anchor {
            public final String who;
            public Recore(String who, int rec) { super(rec); this.who = who; }
        }

        @Override public String toString() { return getClass().getSimpleName() + "@" + rec; }
    }

    public static class Action {
        public final Kind kind;
        public final int rec;
        public final int settle;
        public final String arg;
        public final int k;
        public final List<Anchor> anchors;
        public final int waitedFor;
        public final int work;

        public Action(Kind kind, int rec, int settle, String arg, int k, List<Anchor> anchors) {
            this.kind = kind;
            this.rec = rec;
            this.settle = settle;
            this.arg = arg;
            this.k = k;
            this.anchors = anchors;
            Integer maxRec = null;
            for (Anchor a : anchors) if (maxRec == null || a.rec > maxRec) maxRec = a.rec;
            this.waitedFor = Math.max(settle, maxRec != null ? maxRec : settle);
            this.work = Math.max(rec - waitedFor, 0);
        }

        public String job() { return kind == Kind.GATE ? "gate " + k : arg; }

        @Override public String toString() {
            return kind + "(" + (kind == Kind.DONE || kind == Kind.LEAP ? arg : String.valueOf(k)) + " @" + rec + " settle " + settle + " work " + work + " " + anchors + ")";
        }
    }

    public final List<Action> actions;

    public int shift = 0;
    public int next = 0;
    public int shown = -1;
    private int waitingSince = -1;
    private P offset = new P(0.0, 0.0, 0.0);
    private int blendFrom = 0;
    private int blendEnd = -1;
    private final ArrayList<Pair<String, Integer>> leapt = new ArrayList<>();
    private String workingOn = null;

    public boolean finished() { return next >= actions.size() && shown >= run.end(); }

    public String status = "";

    public List<String> jobs() {
        List<String> out = new ArrayList<>();
        for (Action a : actions) if (a.kind == Kind.DONE || a.kind == Kind.GATE) out.add(a.job());
        return out;
    }

    public boolean leaptOnto(String who, int since) {
        for (Pair<String, Integer> l : leapt) if (l.first.equals(who) && l.second >= since) return true;
        return false;
    }

    public boolean expectsLeap(String who, int into) {
        for (int i = Math.min(next, actions.size()); i < actions.size(); i++) {
            Action a = actions.get(i);
            if (a.kind != Kind.LEAP || !a.arg.equals(who)) continue;
            for (Anchor an : a.anchors) if (an instanceof Anchor.EeOn e && e.into == into) return true;
        }
        return false;
    }

    public boolean earlyEnters(int into) {
        for (Action a : actions) if (a.kind == Kind.EE_ON && a.k == into) return true;
        return false;
    }

    public GhostRun.Frame eeSpot(int into) {
        for (Action a : actions) if (a.kind == Kind.EE_ON && a.k == into) return run.frame(a.rec);
        return null;
    }

    private Action actionAt(int i) { return i >= 0 && i < actions.size() ? actions.get(i) : null; }

    public void tick(int n) {
        int guard = 0;
        while (guard++ < 32) {
            Action a = actionAt(next);
            if (a == null) { show(n - shift); status = finished() ? "finished" : "playing out"; return; }
            int at = n - shift;
            if (at < a.settle) { show(at); status = "on its way to " + a; return; }
            if (waitingSince < 0) waitingSince = n;
            if (alreadyDone(a)) { fire(a, n); continue; }
            Integer ready = readyAt(a);
            if (ready == null || n < ready) {
                if (ready == null && gaveUp(a, n)) { fire(a, n); continue; }
                show(Math.min(at, hold(a)));
                if (ready == null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < a.anchors.size(); i++) { if (i > 0) sb.append(", "); sb.append(a.anchors.get(i)); }
                    status = "waiting: " + sb;
                } else status = a.kind + " in " + (ready - n);
                return;
            }
            if (!fire(a, n)) { show(Math.min(at, hold(a))); status = "blocked: " + a; return; }
        }
    }

    private boolean gaveUp(Action a, int n) { return waitingSince >= 0 && n - waitingSince >= MAX_WAIT + a.rec - a.settle; }

    private boolean alreadyDone(Action a) {
        return switch (a.kind) {
            case DONE -> world.stationDone(a.arg);
            case GATE -> world.gateDown(a.k);
            default -> false;
        };
    }

    private int hold(Action a) {
        return a.kind == Kind.DONE || a.kind == Kind.GATE || a.kind == Kind.EE_ON || a.kind == Kind.RECORE ? a.rec : Math.max(a.rec - 1, a.settle);
    }

    public Integer readyAt(Action a) {
        int g = a.settle + shift;
        for (Anchor an : a.anchors) {
            Integer live;
            if (an instanceof Anchor.Section s) live = world.sectionStart(s.s);
            else if (an instanceof Anchor.EeOn e) live = world.eeArrivedAt(e.into, e.who);
            else if (an instanceof Anchor.Ready r) live = world.eeReadyAt(r.into);
            else if (an instanceof Anchor.Recore r) live = world.recoredAt(r.who);
            else live = null;
            if (live == null) return null;
            g = Math.max(g, live);
        }
        return g + a.work;
    }

    private boolean fire(Action a, int n) {
        switch (a.kind) {
            case DONE -> {
                if (!world.stationDone(a.arg)) {
                    if (world.canDo(a.arg)) body.complete(a.arg);
                    else if (!gaveUp(a, n)) return false;
                    else body.giveUp(a.arg);
                }
            }
            case GATE -> {
                if (!world.gateDown(a.k)) {
                    if (world.canGate(a.k)) body.gate(a.k);
                    else if (!gaveUp(a, n)) return false;
                    else body.giveUp(a.job());
                }
            }
            case LEAP -> {
                P to = world.positionOf(a.arg);
                if (to != null) {
                    body.leap(a.arg, to);
                    leapt.add(Pair.of(a.arg, n));
                    offset = to.minus(run.frame(a.rec).p);
                    blendFrom = a.rec;
                    Action nx = actionAt(next + 1);
                    blendEnd = nx != null ? Math.max(nx.settle, a.rec) : a.rec;
                }
            }
            case EE_ON -> body.eeOn(a.k);
            case EE_LEAVE -> body.eeLeft(a.k);
            case RECORE -> body.recore();
            case ENTER, GO -> {}
        }
        waitingSince = -1;
        shift = n - a.rec;
        next++;
        show(a.rec);
        return true;
    }

    private void show(int at) {
        int t = Math.clamp(at, 0, run.end());
        if (t > shown) {
            int from = shown;
            shown = t;
            int swings = 0;
            for (GhostRun.Ev e : run.events) if (e.type.equals("swing") && e.n >= from + 1 && e.n <= t && swings++ < 3) body.swing();
            GhostRun.Frame f = run.frame(t);
            body.move(f.p.plus(blend(t)), f.yaw, f.pitch, f.held);
        }
        working();
    }

    private void working() {
        Action a = actionAt(next);
        String id = a != null && a.kind == Kind.DONE && shown >= a.settle && isTerminal(a.arg) && !world.stationDone(a.arg) ? a.arg : null;
        if (!java.util.Objects.equals(id, workingOn)) { workingOn = id; body.working(id); }
    }

    public P blend(int t) {
        if (blendEnd < 0 || t < blendFrom || t > blendEnd) return ZERO;
        if (blendEnd == blendFrom) return t == blendFrom ? offset : ZERO;
        return offset.times(((double) (blendEnd - t)) / (blendEnd - blendFrom));
    }

    public static final double SETTLE = 3.0;
    public static final int BACK = 40;
    public static final int MAX_WAIT = 600;
    private static final P ZERO = new P(0.0, 0.0, 0.0);

    public static String eeOwner(String planned, Set<String> players, List<String> ghostsThere, Set<String> ghosts) {
        if (planned != null && players.contains(planned)) return planned;
        if (!ghostsThere.isEmpty()) return ghostsThere.get(0);
        if (planned == null || ghosts.contains(planned)) return null;
        return planned;
    }

    public static int sectionOf(String job) {
        String g = job.startsWith("gate ") ? job.substring(5) : job;
        try {
            return Integer.parseInt(g);
        } catch (NumberFormatException e) {
            return Integer.parseInt(job.substring(1, 2));
        }
    }

    public static boolean isDevice(String id) { return id.endsWith(" SS") || id.endsWith(" Lights") || id.endsWith(" Arrows") || id.endsWith(" Target"); }

    private static final Pattern TERMINAL = Pattern.compile("S\\d T\\d");

    public static boolean isTerminal(String id) { return TERMINAL.matcher(id).matches(); }

    public static int settle(GhostRun run, int from, int at) {
        P p = run.frame(at).p;
        int m = at;
        while (m - 1 >= from && m - 1 >= 0 && run.frame(m - 1).p.dist(p) <= SETTLE) m--;
        return m;
    }

    private static final class Raw {
        final int n;
        final int order;
        final Function<Integer, Action> make;

        Raw(int n, int order, Function<Integer, Action> make) {
            this.n = n;
            this.order = order;
            this.make = make;
        }
    }

    private static Anchor.Section section(GhostRun run, int s) {
        if (s < 0 || s >= run.sectionN.length) return null;
        int v = run.sectionN[s];
        if (v >= 0 && s >= 2) return new Anchor.Section(s, v);
        return null;
    }

    private static List<Anchor> listOfNotNull(Anchor... xs) {
        List<Anchor> out = new ArrayList<>();
        for (Anchor x : xs) if (x != null) out.add(x);
        return out;
    }

    private static GhostRun.Ev lastOrNull(List<GhostRun.Ev> ev, java.util.function.Predicate<GhostRun.Ev> pred) {
        for (int i = ev.size() - 1; i >= 0; i--) if (pred.test(ev.get(i))) return ev.get(i);
        return null;
    }

    private static int indexOfFirst(List<GhostRun.Ev> ev, java.util.function.Predicate<GhostRun.Ev> pred) {
        for (int i = 0; i < ev.size(); i++) if (pred.test(ev.get(i))) return i;
        return -1;
    }

    public static List<Action> build(GhostRun run) {
        String me = run.clazz;
        List<GhostRun.Ev> ev = new ArrayList<>(run.events);
        ev.sort(Comparator.comparingInt(e -> e.n));
        ArrayList<Raw> raw = new ArrayList<>();
        for (int idx = 0; idx < ev.size(); idx++) {
            final int i = idx;
            final GhostRun.Ev e = ev.get(i);
            switch (e.type) {
                case "done" -> raw.add(new Raw(e.n, i, prev -> {
                    List<Anchor> anchors = isDevice(e.a) ? new ArrayList<>() : listOfNotNull(section(run, sectionOf(e.a)));
                    return new Action(Kind.DONE, e.n, settle(run, prev, e.n), e.a, 0, anchors);
                }));
                case "gate" -> raw.add(new Raw(e.n, i, prev -> new Action(Kind.GATE, e.n, settle(run, prev, e.n), "", e.k, listOfNotNull(section(run, e.k)))));
                case "leap" -> raw.add(new Raw(e.n, i, prev -> {
                    GhostRun.Ev onEe = lastOrNull(ev, it -> it.type.equals("ee") && it.a.equals(e.a) && it.n <= e.n);
                    GhostRun.Ev recore = lastOrNull(ev, it -> it.type.equals("recore") && it.a.equals(e.a) && it.n <= e.n);
                    int settle = settle(run, prev, Math.max(e.n - 1, prev));
                    List<Anchor> anchors = listOfNotNull(onEe != null ? new Anchor.EeOn(onEe.k, e.a, onEe.n) : null,
                        recore != null ? new Anchor.Recore(e.a, recore.n) : null, startedDuring(run, settle, e.n));
                    return new Action(Kind.LEAP, e.n, settle, e.a, 0, anchors);
                }));
                case "recore" -> {
                    if (e.a.equals(me)) raw.add(new Raw(e.n, i, prev -> new Action(Kind.RECORE, e.n, e.n, "", 0, new ArrayList<>())));
                }
                case "core" -> {
                    if (indexOfFirst(ev, it -> it.type.equals("core")) == i) raw.add(new Raw(e.n, i, prev ->
                        new Action(Kind.ENTER, e.n, settle(run, prev, Math.max(e.n - 1, prev)), "", 5, listOfNotNull(section(run, 5)))));
                }
                case "ee" -> {
                    if (e.a.equals(me) && indexOfFirst(ev, it -> it.type.equals("ee") && it.a.equals(me) && it.k == e.k) == i) {
                        final int into = e.k;
                        raw.add(new Raw(e.n, i, prev -> new Action(Kind.EE_ON, e.n, e.n, "", into, new ArrayList<>())));
                        int found = -1;
                        for (int x = 0; x < ev.size(); x++) {
                            GhostRun.Ev l = ev.get(x);
                            if (!(l.type.equals("left") && l.a.equals(me) && l.k == into && l.n >= e.n)) continue;
                            boolean back = false;
                            for (GhostRun.Ev it : ev) if (it.type.equals("back") && it.k == into && it.n >= l.n && it.n <= l.n + BACK) { back = true; break; }
                            if (!back) { found = x; break; }
                        }
                        final int j = found;
                        if (j >= 0) raw.add(new Raw(ev.get(j).n, j, prev -> {
                            GhostRun.Ev ready = lastOrNull(ev, it -> it.type.equals("landed") && it.n >= e.n && it.n <= ev.get(j).n);
                            List<Anchor> anchors = listOfNotNull(ready != null ? new Anchor.Ready(into, ready.n) : null, startedDuring(run, e.n, ev.get(j).n));
                            return new Action(Kind.EE_LEAVE, ev.get(j).n, e.n, "", into, anchors);
                        }));
                    }
                }
                default -> {}
            }
        }
        ArrayList<Action> out = new ArrayList<>();
        int prev = 0;
        List<Raw> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.<Raw>comparingInt(r -> r.n).thenComparingInt(r -> r.order));
        for (Raw r : sorted) {
            Action a = r.make.apply(prev);
            out.add(a.settle < prev ? new Action(a.kind, a.rec, Math.min(prev, a.rec), a.arg, a.k, a.anchors) : a);
            prev = a.rec;
        }
        List<Action> all = new ArrayList<>(out);
        all.addAll(departures(run, out));
        all.sort(Comparator.<Action>comparingInt(a -> a.rec).thenComparingInt(a -> a.kind == Kind.GO ? 0 : 1));
        return all;
    }

    public static final double STAND = 1.5;
    public static final int MIN_STAND = 5;

    private static Anchor.Section startedDuring(GhostRun run, int from, int to) {
        Integer best = null;
        for (int s = 2; s <= 5; s++) {
            int v = run.sectionN[s];
            if (v < from + 1 || v > to) continue;
            if (best == null || v > run.sectionN[best]) best = s;
        }
        return best == null ? null : new Anchor.Section(best, run.sectionN[best]);
    }

    public static List<Action> departures(GhostRun run, List<Action> actions) {
        ArrayList<Action> out = new ArrayList<>();
        int from = 0;
        for (int i = 0; i <= actions.size(); i++) {
            int until = i < actions.size() ? actions.get(i).settle : run.end();
            int m = from;
            while (m < until) {
                P p = run.frame(m).p;
                int e = m;
                while (e + 1 <= until && run.frame(e + 1).p.dist(p) <= STAND) e++;
                if (e - m >= MIN_STAND && e + 1 <= until) {
                    Anchor.Section it = startedDuring(run, m, e + 1);
                    if (it != null) out.add(new Action(Kind.GO, e + 1, m, "", it.s, List.of(it)));
                }
                m = e + 1;
            }
            if (i < actions.size()) from = Math.max(from, actions.get(i).rec);
        }
        return out;
    }
}
