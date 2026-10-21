// Ported from ADM's p3sim/clear/secrets/Blessings.kt
package com.teslamaps.adm.sim.clear.secrets;

public enum Blessing {
    POWER("Power"), LIFE("Life"), WISDOM("Wisdom"), STONE("Stone"), TIME("Time");

    public final String displayName;

    Blessing(String displayName) { this.displayName = displayName; }
}
