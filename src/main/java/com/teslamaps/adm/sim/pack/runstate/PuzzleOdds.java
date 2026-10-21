// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** How many puzzles a layout has and which ones can occur. */
public class PuzzleOdds {
    public final int countOddsN;
    public final Map<Integer, Integer> counts;
    public final List<PuzzleEntry> pool;
    public final JsonObject raw;

    public PuzzleOdds(int countOddsN, Map<Integer, Integer> counts, List<PuzzleEntry> pool, JsonObject raw) {
        this.countOddsN = countOddsN;
        this.counts = counts;
        this.pool = pool;
        this.raw = raw;
    }

    public static class PuzzleEntry {
        public final String code;
        public final List<String> rooms;
        public final double mapShare;
        public final boolean exclusivePair;

        public PuzzleEntry(String code, List<String> rooms, double mapShare, boolean exclusivePair) {
            this.code = code;
            this.rooms = rooms;
            this.mapShare = mapShare;
            this.exclusivePair = exclusivePair;
        }
    }
}
