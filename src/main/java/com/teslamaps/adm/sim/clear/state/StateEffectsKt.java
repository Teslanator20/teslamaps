// Ported from ADM's p3sim/clear/state/StateEffects.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.pack.layouts.DoorKind;

public final class StateEffectsKt {
    private StateEffectsKt() {}

    public static boolean hasBody(DoorKind kind) {
        return kind == DoorKind.WITHER || kind == DoorKind.BLOOD || kind == DoorKind.FAIRY || kind == DoorKind.ENTRANCE;
    }
}
