// Ported from ADM's p3sim/clear/blood/BloodFlow.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.Objects;

/**
 * The blood room's stage as a pure state machine: CLOSED -> CAMP -> PASSED -> PORTAL -> ENTERED. Ask step() again until it
 * answers NONE; all ticks are absolute server ticks.
 */
public class BloodFlow {
    public enum Phase { CLOSED, CAMP, PASSED, PORTAL, ENTERED }

    public enum Step { NONE, SAY_PASS, STRIKE, OPEN_PORTAL, REMOVE_WATCHER, ENTER }

    /** Ticks from the door opening after which the camp is ended by force; 0 = never. */
    public final int campTimeoutTicks;
    private final KRandom random;

    public Phase phase = Phase.CLOSED;
    public Integer openedAt = null;
    public Integer passedAt = null;
    public boolean timedOut = false;
    public int kills = 0;
    public BloodExit exit = null;
    public Integer contactAt = null;

    private boolean struck = false;
    private boolean watcherGone = false;
    private boolean expedited = false;

    public BloodFlow(int campTimeoutTicks, KRandom random) {
        this.campTimeoutTicks = campTimeoutTicks;
        this.random = random;
    }

    public BloodFlow(int campTimeoutTicks) { this(campTimeoutTicks, new KRandom(0)); }

    public boolean portalOpen() { return phase == Phase.PORTAL || phase == Phase.ENTERED; }

    public boolean watcherRemoved() { return watcherGone; }

    public void open(int tick) {
        if (phase != Phase.CLOSED) return;
        phase = Phase.CAMP;
        openedAt = tick;
    }

    public int ticksSinceOpen(int tick) { return openedAt != null ? Math.max(tick - openedAt, 0) : 0; }

    /** The camp's counted kills are now total; true when this call was the defeat. */
    public boolean noteKills(int total, int tick) {
        if (phase == Phase.CLOSED || phase == Phase.ENTERED) return false;
        kills = Math.max(kills, WatcherStats.clamp(total));
        if (kills >= WatcherStats.KILLS_TO_DEFEAT && exit == null) {
            exit = BloodExit.roll(tick, random);
            return true;
        }
        return false;
    }

    public Step step(int tick, boolean campFinished, boolean contact) {
        switch (phase) {
            case CLOSED, ENTERED -> { return Step.NONE; }
            case CAMP -> {
                boolean late = campTimeoutTicks > 0 && ticksSinceOpen(tick) >= campTimeoutTicks;
                boolean portalDue = exit != null && tick >= exit.strikeAt();
                if (!campFinished && !late && !portalDue && !expedited) return Step.NONE;
                timedOut = !campFinished && late;
                phase = Phase.PASSED;
                passedAt = tick;
                // a camp that never reported its kills was defeated when it ended
                if (exit == null) { kills = WatcherStats.KILLS_TO_DEFEAT; exit = BloodExit.roll(tick, random); }
                return Step.SAY_PASS;
            }
            default -> {}
        }
        BloodExit e = exit;
        if (e == null) return Step.NONE;
        if (!struck && (expedited || tick >= e.strikeAt())) { struck = true; return Step.STRIKE; }
        if (phase == Phase.PASSED) {
            if (expedited || tick >= e.portalAt()) { phase = Phase.PORTAL; return Step.OPEN_PORTAL; }
            if (!watcherGone && tick >= e.watcherGoneAt()) { watcherGone = true; return Step.REMOVE_WATCHER; }
            return Step.NONE;
        }
        // PORTAL
        if (!watcherGone && (expedited || tick >= e.watcherGoneAt())) { watcherGone = true; return Step.REMOVE_WATCHER; }
        if (contact && contactAt == null) contactAt = tick;
        Integer c = contactAt;
        if (expedited || (c != null && tick >= c + BloodExit.ENTRY_LAG)) { phase = Phase.ENTERED; return Step.ENTER; }
        return Step.NONE;
    }

    /** Skips every wait (dev command). */
    public void expedite() {
        if (phase != Phase.CLOSED && phase != Phase.ENTERED) expedited = true;
    }

    public void reset() {
        phase = Phase.CLOSED; openedAt = null; passedAt = null; timedOut = false;
        kills = 0; exit = null; contactAt = null;
        struck = false; watcherGone = false; expedited = false;
    }

    /** For a savepoint: ticks relative to now. */
    public FlowSnapshot snapshot(int tick) {
        return new FlowSnapshot(
                phase, openedAt != null ? tick - openedAt : null, passedAt != null ? tick - passedAt : null, timedOut,
                kills, exit != null ? tick - exit.defeatTick : null, exit != null ? exit.portalDelay : null,
                contactAt != null ? tick - contactAt : null, struck, watcherGone);
    }

    public void restore(FlowSnapshot s, int tick) {
        reset();
        phase = s.phase; openedAt = s.sinceOpen != null ? tick - s.sinceOpen : null; passedAt = s.sincePass != null ? tick - s.sincePass : null; timedOut = s.timedOut;
        kills = WatcherStats.clamp(s.kills);
        Integer d = s.sinceDefeat; Integer delay = s.portalDelay;
        exit = d != null && delay != null ? new BloodExit(tick - d, delay) : null;
        contactAt = s.sinceContact != null ? tick - s.sinceContact : null;
        struck = s.struck; watcherGone = s.watcherGone;
    }

    /** The since* fields are ticks AGO as of the savepoint (null when it had not happened). */
    public static final class FlowSnapshot {
        public final Phase phase;
        public final Integer sinceOpen;
        public final Integer sincePass;
        public final boolean timedOut;
        public final int kills;
        public final Integer sinceDefeat;
        public final Integer portalDelay;
        public final Integer sinceContact;
        public final boolean struck;
        public final boolean watcherGone;

        public FlowSnapshot(Phase phase, Integer sinceOpen, Integer sincePass, boolean timedOut, int kills, Integer sinceDefeat,
                            Integer portalDelay, Integer sinceContact, boolean struck, boolean watcherGone) {
            this.phase = phase;
            this.sinceOpen = sinceOpen;
            this.sincePass = sincePass;
            this.timedOut = timedOut;
            this.kills = kills;
            this.sinceDefeat = sinceDefeat;
            this.portalDelay = portalDelay;
            this.sinceContact = sinceContact;
            this.struck = struck;
            this.watcherGone = watcherGone;
        }

        public FlowSnapshot(Phase phase, Integer sinceOpen, Integer sincePass, boolean timedOut) {
            this(phase, sinceOpen, sincePass, timedOut, 0, null, null, null, false, false);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof FlowSnapshot x && phase == x.phase && Objects.equals(sinceOpen, x.sinceOpen) && Objects.equals(sincePass, x.sincePass)
                    && timedOut == x.timedOut && kills == x.kills && Objects.equals(sinceDefeat, x.sinceDefeat) && Objects.equals(portalDelay, x.portalDelay)
                    && Objects.equals(sinceContact, x.sinceContact) && struck == x.struck && watcherGone == x.watcherGone;
        }
        @Override public int hashCode() { return Objects.hash(phase, sinceOpen, sincePass, timedOut, kills, sinceDefeat, portalDelay, sinceContact, struck, watcherGone); }
        @Override public String toString() {
            return "FlowSnapshot(phase=" + phase + ", sinceOpen=" + sinceOpen + ", sincePass=" + sincePass + ", timedOut=" + timedOut + ", kills=" + kills
                    + ", sinceDefeat=" + sinceDefeat + ", portalDelay=" + portalDelay + ", sinceContact=" + sinceContact + ", struck=" + struck
                    + ", watcherGone=" + watcherGone + ")";
        }
    }
}
