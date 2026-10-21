// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

public class DamageRules {
    public final double trapPctOfMaxHp;
    public final String defenseFormula;
    public final JsonObject raw;

    public DamageRules(double trapPctOfMaxHp, String defenseFormula, JsonObject raw) {
        this.trapPctOfMaxHp = trapPctOfMaxHp;
        this.defenseFormula = defenseFormula;
        this.raw = raw;
    }
}
