// Ported from ADM's p3sim/clear/mobs/MobAi.kt
package com.teslamaps.adm.sim.clear.mobs;

import java.util.Objects;

public interface AiAction {
    final class Idle implements AiAction {
        public static final Idle INSTANCE = new Idle();
        private Idle() {}
        @Override public String toString() { return "Idle"; }
    }

    final class MoveTo implements AiAction {
        public final double x;
        public final double z;
        public final double speed;

        public MoveTo(double x, double z, double speed) { this.x = x; this.z = z; this.speed = speed; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MoveTo m)) return false;
            return Double.compare(x, m.x) == 0 && Double.compare(z, m.z) == 0 && Double.compare(speed, m.speed) == 0;
        }
        @Override public int hashCode() { return Objects.hash(x, z, speed); }
        @Override public String toString() { return "MoveTo(x=" + x + ", z=" + z + ", speed=" + speed + ")"; }
    }

    final class Melee implements AiAction {
        public static final Melee INSTANCE = new Melee();
        private Melee() {}
        @Override public String toString() { return "Melee"; }
    }

    final class Shoot implements AiAction {
        public static final Shoot INSTANCE = new Shoot();
        private Shoot() {}
        @Override public String toString() { return "Shoot"; }
    }

    final class Teleport implements AiAction {
        public final double x;
        public final double z;

        public Teleport(double x, double z) { this.x = x; this.z = z; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Teleport t)) return false;
            return Double.compare(x, t.x) == 0 && Double.compare(z, t.z) == 0;
        }
        @Override public int hashCode() { return Objects.hash(x, z); }
        @Override public String toString() { return "Teleport(x=" + x + ", z=" + z + ")"; }
    }
}
