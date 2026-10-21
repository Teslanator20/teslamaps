// Ported from ADM's p3sim/clear/hud/HudModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Everything the pure channel formatters read, gathered once per tick by the HUD module. */
public class HudContext {
    public final RunState state;
    public final RunStage stage;
    /** `F7`. */
    public final String floor;
    /** The run clock: negative in the countdown. */
    public final int t;
    /** Whether T0 is planned yet. */
    public final boolean t0Known;
    /** Server ticks since the run began. */
    public final int sinceBegin;
    public final HudStats stats;
    public final long teamDamage;
    public final long teamHealing;
    /** `MM/dd/yy` of the pre-start sidebar. */
    public final String dateText;
    public final int clearedGoldFrom;
    public final int clearedGreenFrom;
    public final List<String> extraSidebar;
    public final List<String> extraTab;
    public final Map<UUID, Integer> reviveStones;
    public final Map<Integer, String> failedBy;

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText, int clearedGoldFrom, int clearedGreenFrom, List<String> extraSidebar,
                      List<String> extraTab, Map<UUID, Integer> reviveStones, Map<Integer, String> failedBy) {
        this.state = state;
        this.stage = stage;
        this.floor = floor;
        this.t = t;
        this.t0Known = t0Known;
        this.sinceBegin = sinceBegin;
        this.stats = stats;
        this.teamDamage = teamDamage;
        this.teamHealing = teamHealing;
        this.dateText = dateText;
        this.clearedGoldFrom = clearedGoldFrom;
        this.clearedGreenFrom = clearedGreenFrom;
        this.extraSidebar = extraSidebar;
        this.extraTab = extraTab;
        this.reviveStones = reviveStones;
        this.failedBy = failedBy;
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText, int clearedGoldFrom, int clearedGreenFrom, List<String> extraSidebar,
                      List<String> extraTab, Map<UUID, Integer> reviveStones) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, dateText, clearedGoldFrom, clearedGreenFrom, extraSidebar, extraTab,
                reviveStones, Map.of());
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText, int clearedGoldFrom, int clearedGreenFrom, List<String> extraSidebar, List<String> extraTab) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, dateText, clearedGoldFrom, clearedGreenFrom, extraSidebar, extraTab,
                Map.of(), Map.of());
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText, int clearedGoldFrom, int clearedGreenFrom, List<String> extraSidebar) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, dateText, clearedGoldFrom, clearedGreenFrom, extraSidebar, List.of(),
                Map.of(), Map.of());
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText, int clearedGoldFrom, int clearedGreenFrom) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, dateText, clearedGoldFrom, clearedGreenFrom, List.of(), List.of(),
                Map.of(), Map.of());
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText, int clearedGoldFrom) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, dateText, clearedGoldFrom, HudConstants.CLEARED_GREEN_FROM);
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing, String dateText) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, dateText, HudConstants.CLEARED_GOLD_FROM, HudConstants.CLEARED_GREEN_FROM);
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage,
                      long teamHealing) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, teamHealing, "01/01/26");
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats, long teamDamage) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, teamDamage, 0L, "01/01/26");
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin, HudStats stats) {
        this(state, stage, floor, t, t0Known, sinceBegin, stats, 0L, 0L, "01/01/26");
    }

    public HudContext(RunState state, RunStage stage, String floor, int t, boolean t0Known, int sinceBegin) {
        this(state, stage, floor, t, t0Known, sinceBegin, HudStats.DEFAULT, 0L, 0L, "01/01/26");
    }

    /** The time the sidebar and tab print: zero before T0, frozen once the run ended. */
    public int elapsedTicks() {
        Integer cap = state.milestones.endT;
        return Math.max(0, cap != null ? Math.min(t, cap) : t);
    }

    /** Whether the run has started (Mort has spoken). */
    public boolean started() { return stage != RunStage.COUNTDOWN || (t0Known && t >= 0); }

    /** Ticks until T0 in the countdown; 100 while T0 is not planned. */
    public int ticksToStart() { return t0Known ? Math.max(0, -t) : 100; }

    /** The members in the order the tab lists them: viewer first when a member, then the others in join order. */
    public List<PlayerRecord> membersFor(UUID viewer) {
        List<PlayerRecord> all = new ArrayList<>(state.players.values());
        PlayerRecord me = null;
        for (PlayerRecord p : all) if (Objects.equals(p.uuid, viewer)) { me = p; break; }
        if (me == null) return all;
        List<PlayerRecord> out = new ArrayList<>();
        out.add(me);
        for (PlayerRecord p : all) if (p != me) out.add(p);
        return out;
    }

    /** The member's health as the sidebar prints it: a bot carries its own maximum (`extras["maxHp"]`). */
    public int hpOf(PlayerRecord p) {
        Object m = p.extras.get("maxHp");
        double max = m instanceof Number n ? n.doubleValue() : (double) stats.maxHp;
        return (int) Math.round(max * Math.max(0.0, p.hpPercent) / 100.0);
    }
}
