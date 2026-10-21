// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.Pair;

import java.util.List;

/** The fitted team score. See {@code ScoreModule} for evaluation. */
public class ScoreModel {
    public final int periodTicks;
    public final int firstUpdateTick;
    public final String endScreen;
    public final double maxSecretPoints;
    public final int secretsPctDecimals;
    /** {@code table[c]} for {@code c} completed tiles (37 entries, c = 0..36). */
    public final double[] roomsTable;
    public final String roomsInterp;
    public final String roomsRounding;
    public final String deathsFormula;
    /** Points per unsolved puzzle, NEGATIVE when fitted. Null = not fitted ({@code UNMEASURED_PUZZLE_PENALTY}). */
    public final Double puzzlePenaltyPerUnsolved;
    public final double cryptWeight;
    public final int cryptCap;
    public final double mimicWeight, princeWeight, batWeight;
    public final boolean bonusWeightsFitted;
    /** Speed term as {@code [seconds, points]} pairs, piecewise linear. */
    public final List<Pair<Double, Double>> speedPointsSec;
    public final boolean speedFitted;
    public final double constant;
    public final JsonObject raw;
    /** The fitted weight on the secrets term (1.0 when the pack has none). */
    public final double secretWeight;
    /** {@code S} is recomputed at {@code T0 + clockOriginTicksAfterT0 + periodTicks * k} for k >= 1. */
    public final int clockOriginTicksAfterT0;
    /** The pack's own statement of the exact formula, or null. */
    public final String evaluation;

    public ScoreModel(int periodTicks, int firstUpdateTick, String endScreen, double maxSecretPoints, int secretsPctDecimals, double[] roomsTable,
                      String roomsInterp, String roomsRounding, String deathsFormula, Double puzzlePenaltyPerUnsolved, double cryptWeight, int cryptCap,
                      double mimicWeight, double princeWeight, double batWeight, boolean bonusWeightsFitted, List<Pair<Double, Double>> speedPointsSec,
                      boolean speedFitted, double constant, JsonObject raw, double secretWeight, int clockOriginTicksAfterT0, String evaluation) {
        this.periodTicks = periodTicks;
        this.firstUpdateTick = firstUpdateTick;
        this.endScreen = endScreen;
        this.maxSecretPoints = maxSecretPoints;
        this.secretsPctDecimals = secretsPctDecimals;
        this.roomsTable = roomsTable;
        this.roomsInterp = roomsInterp;
        this.roomsRounding = roomsRounding;
        this.deathsFormula = deathsFormula;
        this.puzzlePenaltyPerUnsolved = puzzlePenaltyPerUnsolved;
        this.cryptWeight = cryptWeight;
        this.cryptCap = cryptCap;
        this.mimicWeight = mimicWeight;
        this.princeWeight = princeWeight;
        this.batWeight = batWeight;
        this.bonusWeightsFitted = bonusWeightsFitted;
        this.speedPointsSec = speedPointsSec;
        this.speedFitted = speedFitted;
        this.constant = constant;
        this.raw = raw;
        this.secretWeight = secretWeight;
        this.clockOriginTicksAfterT0 = clockOriginTicksAfterT0;
        this.evaluation = evaluation;
    }

    public ScoreModel(int periodTicks, int firstUpdateTick, String endScreen, double maxSecretPoints, int secretsPctDecimals, double[] roomsTable,
                      String roomsInterp, String roomsRounding, String deathsFormula, Double puzzlePenaltyPerUnsolved, double cryptWeight, int cryptCap,
                      double mimicWeight, double princeWeight, double batWeight, boolean bonusWeightsFitted, List<Pair<Double, Double>> speedPointsSec,
                      boolean speedFitted, double constant, JsonObject raw, double secretWeight, int clockOriginTicksAfterT0) {
        this(periodTicks, firstUpdateTick, endScreen, maxSecretPoints, secretsPctDecimals, roomsTable, roomsInterp, roomsRounding, deathsFormula,
                puzzlePenaltyPerUnsolved, cryptWeight, cryptCap, mimicWeight, princeWeight, batWeight, bonusWeightsFitted, speedPointsSec, speedFitted,
                constant, raw, secretWeight, clockOriginTicksAfterT0, null);
    }

    public ScoreModel(int periodTicks, int firstUpdateTick, String endScreen, double maxSecretPoints, int secretsPctDecimals, double[] roomsTable,
                      String roomsInterp, String roomsRounding, String deathsFormula, Double puzzlePenaltyPerUnsolved, double cryptWeight, int cryptCap,
                      double mimicWeight, double princeWeight, double batWeight, boolean bonusWeightsFitted, List<Pair<Double, Double>> speedPointsSec,
                      boolean speedFitted, double constant, JsonObject raw, double secretWeight) {
        this(periodTicks, firstUpdateTick, endScreen, maxSecretPoints, secretsPctDecimals, roomsTable, roomsInterp, roomsRounding, deathsFormula,
                puzzlePenaltyPerUnsolved, cryptWeight, cryptCap, mimicWeight, princeWeight, batWeight, bonusWeightsFitted, speedPointsSec, speedFitted,
                constant, raw, secretWeight, 0);
    }

    public ScoreModel(int periodTicks, int firstUpdateTick, String endScreen, double maxSecretPoints, int secretsPctDecimals, double[] roomsTable,
                      String roomsInterp, String roomsRounding, String deathsFormula, Double puzzlePenaltyPerUnsolved, double cryptWeight, int cryptCap,
                      double mimicWeight, double princeWeight, double batWeight, boolean bonusWeightsFitted, List<Pair<Double, Double>> speedPointsSec,
                      boolean speedFitted, double constant, JsonObject raw) {
        this(periodTicks, firstUpdateTick, endScreen, maxSecretPoints, secretsPctDecimals, roomsTable, roomsInterp, roomsRounding, deathsFormula,
                puzzlePenaltyPerUnsolved, cryptWeight, cryptCap, mimicWeight, princeWeight, batWeight, bonusWeightsFitted, speedPointsSec, speedFitted,
                constant, raw, 1.0);
    }
}
