// Ported from ADM's p3sim/run/RunConfig.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public interface LayoutSource {
    /** A random layout from the default pool. */
    final class Random implements LayoutSource {
        public static final Random INSTANCE = new Random();

        private Random() {}

        @Override public String toString() { return "Random"; }
    }

    /** The layout `LayoutPack.pick(seed)` gives. */
    final class Seed implements LayoutSource {
        public final long seed;

        public Seed(long seed) { this.seed = seed; }

        @Override public boolean equals(Object o) { return o instanceof Seed x && seed == x.seed; }
        @Override public int hashCode() { return Long.hashCode(seed); }
        @Override public String toString() { return "Seed(seed=" + seed + ")"; }
    }

    /** A recorded run's layout by id. */
    final class Recorded implements LayoutSource {
        public final String id;

        public Recorded(String id) { this.id = id; }

        @Override public boolean equals(Object o) { return o instanceof Recorded x && Objects.equals(id, x.id); }
        @Override public int hashCode() { return Objects.hashCode(id); }
        @Override public String toString() { return "Recorded(id=" + id + ")"; }
    }

    /** Practice: one room alone in the middle of the map. */
    final class Practice implements LayoutSource {
        public final String room;

        public Practice(String room) { this.room = room; }

        @Override public boolean equals(Object o) { return o instanceof Practice x && Objects.equals(room, x.room); }
        @Override public int hashCode() { return Objects.hashCode(room); }
        @Override public String toString() { return "Practice(room=" + room + ")"; }
    }
}
