// Ported from ADM's p3sim/clear/bots/PlanExecutor.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** Plays a ClearPlan: positions for any tick and the acts done on schedule through a BotWorld. Pure and deterministic. */
public class PlanExecutor {
    public ClearPlan plan;
    private final BotWorld world;
    private final int graceTicks;

    private static final class ActState {
        final Act act;
        boolean done = false;
        int nextTry = Integer.MIN_VALUE;
        /** An OpenDoor whose seat has stood beside the door. */
        boolean reached = false;

        ActState(Act act) { this.act = act; }
    }

    private List<ActState> states = List.of();

    /** Finished acts by kind and room, kept across a re-plan. */
    private final HashSet<String> finished = new HashSet<>();

    public PlanExecutor(ClearPlan plan, BotWorld world, int graceTicks) {
        this.plan = plan;
        this.world = world;
        this.graceTicks = graceTicks;
        load(plan);
    }

    public PlanExecutor(ClearPlan plan, BotWorld world) { this(plan, world, BotTuning.HUMAN_ROOM_GRACE_TICKS); }

    private static String key(Act a) {
        if (a instanceof OpenDoor o) return "door:" + o.door;
        if (a instanceof ClearMobs c) return "white:" + c.room;
        if (a instanceof FindSecrets f) return "green:" + f.room;
        if (a instanceof SolvePuzzle p) return "puzzle:" + p.room;
        throw new IllegalStateException("unknown act " + a);
    }

    private void load(ClearPlan p) {
        plan = p;
        ArrayList<ActState> list = new ArrayList<>();
        for (SeatTrack tr : p.tracks) for (Act a : tr.acts) {
            ActState s = new ActState(a);
            if (finished.contains(key(s.act))) s.done = true;
            list.add(s);
        }
        list.sort(Comparator.<ActState>comparingInt(s -> s.act.t()).thenComparingInt(s -> s.act.seat()));
        states = list;
    }

    /** Swaps in a re-plan. Acts already finished are not done twice. */
    public void replace(ClearPlan p) { load(p); }

    /** Where [seat] stands at [t] by the plan. */
    public Pt positionOf(int seat, int t) {
        SeatTrack tr = plan.track(seat);
        return tr != null ? tr.positionAt(t) : null;
    }

    /** The room work [seat] is in the middle of at [t], or null. A door is not room work. */
    public Act workOf(int seat, int t) {
        for (ActState s : states) if (!s.done && s.act.seat() == seat && !(s.act instanceof OpenDoor) && s.act.t() <= t && t <= s.act.until()) return s.act;
        return null;
    }

    /** A key was picked up at [t]: a door already waiting for one is tried again [reaction] ticks later. */
    public void keyArrived(int t, int reaction) {
        for (ActState s : states) if (!s.done && s.act instanceof OpenDoor && s.act.t() <= t && s.nextTry > t + reaction) s.nextTry = t + reaction;
    }

    public void keyArrived(int t) { keyArrived(t, KEY_REACTION_TICKS); }

    /** The acts not done yet. */
    public List<Act> pending() {
        ArrayList<Act> out = new ArrayList<>();
        for (ActState s : states) if (!s.done) out.add(s.act);
        return out;
    }

    /** Does the work that is due at tick [t]. */
    public void tick(int t) {
        for (ActState s : states) {
            if (s.done) continue;
            Act a = s.act;
            if (a.t() > t) continue;
            if (t < s.nextTry) continue;
            if (a instanceof OpenDoor od) {
                if (!s.reached && od.spot != null && t < od.t() + OPEN_GATE_MAX) {
                    Pt here = positionOf(od.seat(), t);
                    if (here != null && here.dist(od.spot) > BotTuning.OPEN_REACH) { s.nextTry = t + RETRY_TICKS; continue; }
                }
                s.reached = true;
                if (world.openDoor(od.seat(), od.door)) finish(s); else s.nextTry = t + RETRY_TICKS;
            } else if (a instanceof ClearMobs cm) {
                window(s, t, cm.t(), cm.until(), cm.room, false, f -> world.killRequired(cm.seat(), cm.room, f));
            } else if (a instanceof FindSecrets fs) {
                window(s, t, fs.t(), fs.until(), fs.room, true, f -> world.findSecrets(fs.seat(), fs.room, f));
            } else if (a instanceof SolvePuzzle sp) {
                if (t < sp.until()) continue;
                if (world.roomDone(sp.room, true) || world.solvePuzzle(sp.seat(), sp.room)) finish(s); else s.nextTry = t + RETRY_TICKS;
            }
        }
    }

    private void window(ActState s, int t, int from, int until, int room, boolean green, Predicate<Double> work) {
        if (world.roomDone(room, green)) { finish(s); return; }
        double f = until <= from ? 1.0 : Math.clamp((double) (t - from) / (until - from), 0.0, 1.0);
        if (t >= until && world.humanInside(room) && t < until + graceTicks) { s.nextTry = t + RETRY_TICKS; return; }
        double partial = t >= until ? 1.0 : f;
        if (work.test(partial) && t >= until) finish(s); else if (partial >= 1.0) s.nextTry = t + RETRY_TICKS;
    }

    private void finish(ActState s) { s.done = true; finished.add(key(s.act)); }

    /** Forgets what is finished (a new run, or a restore to an earlier moment). */
    public void forgetFinished() { finished.clear(); load(plan); }

    public static final int RETRY_TICKS = 5;
    /** Ticks from a key being taken to a waiting opener's next try. */
    public static final int KEY_REACTION_TICKS = 2;
    /** Ticks past its time that a door waits for its opener to come within reach before it opens anyway. */
    public static final int OPEN_GATE_MAX = 600;
}
