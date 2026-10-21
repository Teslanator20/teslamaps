// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.Objects;
import java.util.UUID;

/** Who hears or sees something. */
public interface Audience {
    /** The whole party. */
    final class Everyone implements Audience {
        public static final Everyone INSTANCE = new Everyone();

        private Everyone() {}

        @Override public String toString() { return "Everyone"; }
    }

    final class Only implements Audience {
        public final UUID player;

        public Only(UUID player) { this.player = player; }

        @Override public boolean equals(Object o) { return o instanceof Only x && Objects.equals(player, x.player); }
        @Override public int hashCode() { return Objects.hashCode(player); }
        @Override public String toString() { return "Only(player=" + player + ")"; }
    }

    final class AllBut implements Audience {
        public final UUID player;

        public AllBut(UUID player) { this.player = player; }

        @Override public boolean equals(Object o) { return o instanceof AllBut x && Objects.equals(player, x.player); }
        @Override public int hashCode() { return Objects.hashCode(player); }
        @Override public String toString() { return "AllBut(player=" + player + ")"; }
    }
}
