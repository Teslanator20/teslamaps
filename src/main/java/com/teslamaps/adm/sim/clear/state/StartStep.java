// Ported from ADM's p3sim/clear/state/StateController.kt
package com.teslamaps.adm.sim.clear.state;

public interface StartStep {
    final class Ready implements StartStep {
        public final int index;

        public Ready(int index) { this.index = index; }

        @Override public boolean equals(Object o) { return o instanceof Ready r && index == r.index; }
        @Override public int hashCode() { return Integer.hashCode(index); }
        @Override public String toString() { return "Ready(index=" + index + ")"; }
    }

    final class Starting implements StartStep {
        public final int seconds;

        public Starting(int seconds) { this.seconds = seconds; }

        @Override public boolean equals(Object o) { return o instanceof Starting s && seconds == s.seconds; }
        @Override public int hashCode() { return Integer.hashCode(seconds); }
        @Override public String toString() { return "Starting(seconds=" + seconds + ")"; }
    }

    final class Mort implements StartStep {
        public final int index;

        public Mort(int index) { this.index = index; }

        @Override public boolean equals(Object o) { return o instanceof Mort m && index == m.index; }
        @Override public int hashCode() { return Integer.hashCode(index); }
        @Override public String toString() { return "Mort(index=" + index + ")"; }
    }

    final class EntranceDoorFalls implements StartStep {
        public static final EntranceDoorFalls INSTANCE = new EntranceDoorFalls();
        private EntranceDoorFalls() {}
        @Override public String toString() { return "EntranceDoorFalls"; }
    }

    final class FreeDoorFalls implements StartStep {
        public static final FreeDoorFalls INSTANCE = new FreeDoorFalls();
        private FreeDoorFalls() {}
        @Override public String toString() { return "FreeDoorFalls"; }
    }

    final class EntranceDiscovered implements StartStep {
        public static final EntranceDiscovered INSTANCE = new EntranceDiscovered();
        private EntranceDiscovered() {}
        @Override public String toString() { return "EntranceDiscovered"; }
    }

    final class EntranceGreen implements StartStep {
        public static final EntranceGreen INSTANCE = new EntranceGreen();
        private EntranceGreen() {}
        @Override public String toString() { return "EntranceGreen"; }
    }

    final class PracticeGo implements StartStep {
        public static final PracticeGo INSTANCE = new PracticeGo();
        private PracticeGo() {}
        @Override public String toString() { return "PracticeGo"; }
    }
}
