// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.List;

public final class SecretRules {
    private SecretRules() {}

    public enum Repeat { SILENT, SEARCHED }

    public static Repeat repeatClick(boolean trapped, boolean reward, boolean finished) {
        return trapped || (reward && !finished) ? Repeat.SILENT : Repeat.SEARCHED;
    }

    public static final String CHEST_SEARCHED = "§cThis chest has already been searched!";
    public static final String CHEST_LOCKED = "§cThat chest is locked!";
    public static final String LEVER_OPENING = "§cYou hear the sound of something opening...";
    public static final String LEVER_USED = "§cThis lever has already been used.";
    public static final String KEY_FOUND = "§r§aYou found a Secret Redstone Key!";
    public static final String SOCKET_USED = "§7§oYou hear something open...";

    public static List<String> leverLines(boolean alreadyUsed, boolean sneaking) {
        if (alreadyUsed) return List.of(LEVER_USED);
        if (sneaking) return List.of(LEVER_OPENING, LEVER_USED);
        return List.of(LEVER_OPENING);
    }
}
