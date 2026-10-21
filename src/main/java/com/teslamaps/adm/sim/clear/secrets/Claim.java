// Ported from ADM's p3sim/clear/secrets/SecretBook.kt
package com.teslamaps.adm.sim.clear.secrets;

public interface Claim {
    final class Credited implements Claim {
        public final SecretSite site;
        public final int autoCredited;

        public Credited(SecretSite site, int autoCredited) { this.site = site; this.autoCredited = autoCredited; }
    }

    final class Duplicate implements Claim {
        public static final Duplicate INSTANCE = new Duplicate();

        private Duplicate() {}

        @Override public String toString() { return "Duplicate"; }
    }

    final class RoomFull implements Claim {
        public static final RoomFull INSTANCE = new RoomFull();

        private RoomFull() {}

        @Override public String toString() { return "RoomFull"; }
    }

    final class NoRoom implements Claim {
        public static final NoRoom INSTANCE = new NoRoom();

        private NoRoom() {}

        @Override public String toString() { return "NoRoom"; }
    }
}
