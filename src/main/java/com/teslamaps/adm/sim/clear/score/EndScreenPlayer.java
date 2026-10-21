// Ported from ADM's p3sim/clear/score/EndScreenText.kt
package com.teslamaps.adm.sim.clear.score;

import java.util.Objects;

public final class EndScreenPlayer {
    public final String playerClass;
    public final double cataXp;
    public final int bits;
    public final long damage;
    public final long healing;
    public final int killed;
    public final int secretsFound;

    public EndScreenPlayer(String playerClass, double cataXp, int bits, long damage, long healing, int killed, int secretsFound) {
        this.playerClass = playerClass;
        this.cataXp = cataXp;
        this.bits = bits;
        this.damage = damage;
        this.healing = healing;
        this.killed = killed;
        this.secretsFound = secretsFound;
    }

    public EndScreenPlayer(String playerClass, double cataXp, int bits, long damage, long healing, int killed) { this(playerClass, cataXp, bits, damage, healing, killed, 0); }

    public EndScreenPlayer(String playerClass, double cataXp, int bits, long damage, long healing) { this(playerClass, cataXp, bits, damage, healing, 0, 0); }

    public EndScreenPlayer(String playerClass, double cataXp, int bits, long damage) { this(playerClass, cataXp, bits, damage, 0L, 0, 0); }

    public EndScreenPlayer(String playerClass, double cataXp, int bits) { this(playerClass, cataXp, bits, 0L, 0L, 0, 0); }

    public EndScreenPlayer(String playerClass, double cataXp) { this(playerClass, cataXp, 0, 0L, 0L, 0, 0); }

    public EndScreenPlayer(String playerClass) { this(playerClass, 0.0, 0, 0L, 0L, 0, 0); }

    public EndScreenPlayer() { this("Mage", 0.0, 0, 0L, 0L, 0, 0); }

    @Override public boolean equals(Object o) {
        return o instanceof EndScreenPlayer p && Objects.equals(playerClass, p.playerClass) && Double.compare(cataXp, p.cataXp) == 0 && bits == p.bits
                && damage == p.damage && healing == p.healing && killed == p.killed && secretsFound == p.secretsFound;
    }
    @Override public int hashCode() { return Objects.hash(playerClass, cataXp, bits, damage, healing, killed, secretsFound); }
    @Override public String toString() {
        return "EndScreenPlayer(playerClass=" + playerClass + ", cataXp=" + cataXp + ", bits=" + bits + ", damage=" + damage + ", healing=" + healing
                + ", killed=" + killed + ", secretsFound=" + secretsFound + ")";
    }
}
