// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

import java.util.Map;

public class CheckStates {
    public final Map<String, TypeStates> types;
    public final boolean greenNeedsAllSecrets;
    public final boolean zeroSecretGoesStraightToGreen;
    public final JsonObject raw;

    public CheckStates(Map<String, TypeStates> types, boolean greenNeedsAllSecrets, boolean zeroSecretGoesStraightToGreen, JsonObject raw) {
        this.types = types;
        this.greenNeedsAllSecrets = greenNeedsAllSecrets;
        this.zeroSecretGoesStraightToGreen = zeroSecretGoesStraightToGreen;
        this.raw = raw;
    }
}
