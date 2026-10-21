// Ported from ADM's p3sim/run/Run.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * One full run: countdown, clear, blood, boss entry, boss, end. Owns the clock, the stage machine, RunState and the RunBus, and
 * calls every RunSystem in module order. Server thread only; the client reads ClientRun.
 * serverTick: absolute (Fight.serverTick); t(): ticks since T0, negative in the countdown.
 */
public class Run {
    public final String id;
    public final RunConfig config;
    public final DungeonPlan plan;
    public final PackSet packs;
    public final RunBus bus;
    /** The systems called, in order. */
    private final List<? extends RunSystem> systems;

    /** Seeded from the run's seed. Modules derive their own by subRandom. */
    public final KRandom random;

    public RunStage stage = RunStage.COUNTDOWN;

    /** Absolute server tick of the current tick. */
    public int serverTick = 0;

    /** Absolute server tick that is T0, once known. */
    public Integer t0Tick = null;

    public final RunState state;

    /** Runs.start installs the real store. */
    public SavepointStore savepoints = NoSavepoints.INSTANCE;

    private boolean started = false;
    private boolean ended = false;

    /** What turns the BOSS stage into the boss sim's phase chain (BossHandoff.start); null in tests. */
    public BiConsumer<Run, BossEntryOptions> bossStarter = null;

    private final FailureLog failures = new FailureLog();

    public Run(String id, RunConfig config, DungeonPlan plan, PackSet packs, RunBus bus, List<? extends RunSystem> systems) {
        this.id = id;
        this.config = config;
        this.plan = plan;
        this.packs = packs;
        this.bus = bus;
        this.systems = systems;
        this.random = new KRandom(config.seed);
        this.state = new RunState(plan, bus, this::t);
        // the fan-out of bus events to the systems, in module order
        bus.subscribe(RunBus.SYSTEMS_PRIORITY, e -> { for (RunSystem s : systems) guard(s, "onEvent", () -> s.onEvent(this, e)); });
        // a game that joins later has never heard the stage
        bus.subscribe(RunBus.SYSTEMS_PRIORITY - 1, e -> { if (e instanceof PlayerJoined && started && !ended) broadcastStage(); });
    }

    public Run(String id, RunConfig config, DungeonPlan plan, PackSet packs, RunBus bus) {
        this(id, config, plan, packs, bus, AdsModules.all());
    }

    public Run(String id, RunConfig config, DungeonPlan plan, PackSet packs) {
        this(id, config, plan, packs, new RunBus());
    }

    /** One purpose ("mobs", "bots"...): same seed + same salt = same sequence. */
    public KRandom subRandom(String salt) {
        return new KRandom(config.seed ^ ((long) salt.hashCode() * -0x61c8864680b583ebL));
    }

    /** Run clock: server ticks since T0. 0 until T0 is known. */
    public int t() { return serverTick - (t0Tick != null ? t0Tick : serverTick); }

    /** The systems this run calls, in order (the savepoint store walks them). */
    public List<? extends RunSystem> systemList() { return systems; }

    /** Sets T0; a restore uses it to put the clock back: setT0(serverTick - savepoint.t). */
    public void setT0(int tick) {
        t0Tick = tick;
        state.milestones.t0Tick = tick;
    }

    /** Puts the run clock back to savedT; t0WasKnown false makes T0 unknown again. */
    public void restoreClock(int savedT, boolean t0WasKnown) {
        if (t0WasKnown) setT0(serverTick - savedT);
        else { t0Tick = null; state.milestones.t0Tick = null; }
    }

    /** Begins in COUNTDOWN: onRunStart, onStage(null, COUNTDOWN), RunStarted, StageChanged. Once. */
    public void begin(int atServerTick) {
        if (started) throw new IllegalStateException("run already begun");
        started = true;
        packs.pin();
        serverTick = atServerTick;
        state.milestones.countdownStartTick = atServerTick;
        for (RunSystem s : systems) guard(s, "onRunStart", () -> s.onRunStart(this));
        for (RunSystem s : systems) guard(s, "onStage", () -> s.onStage(this, null, RunStage.COUNTDOWN));
        // Odin's bridge forces "in boss" until a stage message says otherwise
        broadcastStage();
        bus.post(new RunStarted(t()));
        bus.post(new StageChanged(t(), null, RunStage.COUNTDOWN));
    }

    /** One server tick: the clock, then onTick on every system. */
    public void tick(int atServerTick) {
        if (!started || ended) return;
        serverTick = atServerTick;
        for (RunSystem s : systems) guard(s, "onTick", () -> s.onTick(this));
    }

    /** Moves to [to] if RunStage.canGo allows it, else logs and returns false. */
    public boolean requestStage(RunStage to) {
        if (ended) return false;
        if (!RunStage.canGo(stage, to)) { Ec.LOG.warn("[ads] refused stage change " + stage + " -> " + to); return false; }
        moveTo(to);
        return true;
    }

    /** Engine only: no checks (a savepoint restore going back). */
    public void forceStage(RunStage to) { moveTo(to); }

    private void moveTo(RunStage to) {
        RunStage from = stage;
        stage = to;
        for (RunSystem s : systems) guard(s, "onStage", () -> s.onStage(this, from, to));
        bus.post(new StageChanged(t(), from, to));
        broadcastStage();
        if (to == RunStage.END && !ended) finish(EndReason.COMPLETED);
    }

    /** Ends the run: stage END, onRunEnd, RunEnded. Idempotent. */
    public void end(EndReason reason) {
        if (ended) return;
        if (stage != RunStage.END) {
            RunStage from = stage;
            stage = RunStage.END;
            for (RunSystem s : systems) guard(s, "onStage", () -> s.onStage(this, from, RunStage.END));
            bus.post(new StageChanged(t(), from, RunStage.END));
            broadcastStage();
        }
        finish(reason);
    }

    private void finish(EndReason reason) {
        if (ended) return;
        ended = true;
        if (started) packs.unpin();
        state.milestones.endT = state.milestones.endT != null ? state.milestones.endT : t();
        for (RunSystem s : systems) guard(s, "onRunEnd", () -> s.onRunEnd(this, reason));
        bus.post(new RunEnded(t(), reason));
    }

    /** True once end has run. */
    public boolean isOver() { return ended; }

    /** Shortcut for savepoints.take. */
    public Savepoint savepoint(String label, Integer roomIndex) { return savepoints.take(this, label, roomIndex); }

    public Savepoint savepoint(String label) { return savepoint(label, null); }

    /** Hands the party to the boss sim: BOSS_ENTRY -> BOSS, then bossStarter. From an earlier stage it walks forward first. */
    public void beginBoss(BossEntryOptions options) {
        if (ended || stage == RunStage.BOSS || stage == RunStage.END) { Ec.LOG.warn("[ads] beginBoss ignored in stage " + stage); return; }
        while (stage.ordinal() < RunStage.BOSS_ENTRY.ordinal()) {
            if (!requestStage(RunStage.values()[stage.ordinal() + 1])) return;
        }
        if (!requestStage(RunStage.BOSS)) return;
        BiConsumer<Run, BossEntryOptions> start = bossStarter;
        if (start == null) Ec.LOG.warn("[ads] no boss starter is installed; options=" + options);
        else {
            try { start.accept(this, options); } catch (Throwable t) { Ec.LOG.error("[ads] the boss start failed", t); }
        }
    }

    public void beginBoss() { beginBoss(new BossEntryOptions()); }

    /** Tells every client the stage. */
    public void broadcastStage() { RunWire.broadcast(this); }

    private void guard(RunSystem s, String what, Runnable block) {
        try {
            block.run();
        } catch (Throwable t) {
            // first full, the rest counted
            Integer held = failures.admit(s.id() + "." + what);
            if (held != null) Ec.LOG.error("[ads] " + s.id() + "." + what + " failed" + failures.suffix(held), t);
        }
    }
}
