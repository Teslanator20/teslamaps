// Ported from ADM's p3sim/clear/bots/BotTuning.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

/** Every number the bots module uses that the data packs do not measure, in one place. Pure. */
public final class BotTuning {
    private BotTuning() {}

    // ---- movement

    /** Visible walking speed of a bot, blocks per tick. */
    public static final double WALK_BPT = 0.85;

    /** A leg that needs more than this speed is played as hops of HOP_BLOCKS. */
    public static final double HOP_ABOVE_BPT = 1.25;

    /** One blink hop, blocks. */
    public static final double HOP_BLOCKS = 10.0;

    /** The fastest a bot is ever planned to cover ground, blocks per tick. */
    public static final double MAX_TRAVEL_BPT = 3.0;

    /** Planning speed when the run-state pack has no class speeds. */
    public static final double DEFAULT_PLAN_BPT = 1.2;

    /** Ticks a door needs after its first block turns to barrier before a walker may pass. */
    public static final int DOOR_PASSABLE_AFTER = 15;

    // ---- the door and key schedule

    public static final double PACE_CORRELATION = 0.85;

    public static final double ROOM_PACE_CORRELATION = 0.25;

    public static final int MIN_DOOR_GAP = 12;

    public static final int MIN_CLEAR_TICKS = 8;

    public static final int OPENER_LEAD = 6;

    public static final double SECOND_OPENER_CHANCE = 0.42;

    public static final double KEY_SPLIT_CHANCE = 0.84;

    /** How far from the door's gap the opener stands when the door falls, (share of draws, distance). */
    public static final List<Pair<Double, Double>> OPENER_STAND_BLOCKS = List.of(Pair.of(0.0, 0.7), Pair.of(0.2, 1.6), Pair.of(0.5, 3.1), Pair.of(1.0, 4.7));

    public static final double OPEN_REACH = 5.0;

    public static final int OPEN_SETTLE = 2;

    // ---- the rooms

    /** Multiplies every sampled room duration (calibrated). */
    public static final double ROOM_WORK_SCALE = 0.3;

    public static final double PUZZLE_AFFINITY_PENALTY = 120.0;

    /** Longest a class's room-time multiplier may move away from 1.0 either way (start, end). */
    public static final double[] CLASS_SCALE_RANGE = new double[]{0.75, 1.35};

    public static final int MIN_ROOM_DWELL = 20;

    public static final int MIN_GREEN_TASK = 60;

    public static final String FLOOR = "F7";

    // ---- when the player is in the way

    public static final int COVER_AFTER_TICKS = 600;

    public static final int HUMAN_ROOM_GRACE_TICKS = 400;

    public static final int REPLAN_DEBOUNCE = 10;

    // ---- bodies

    public static final double BODY_RADIUS = 72.0;

    public static final double BODY_HYSTERESIS = 12.0;

    /** The floor height a bot stands at when nothing is loaded to ask. */
    public static final double DEFAULT_FLOOR_Y = 69.0;
}
