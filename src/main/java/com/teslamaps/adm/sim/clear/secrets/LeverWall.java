// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.Locale;
import java.util.Set;

public final class LeverWall {
    private LeverWall() {}

    public static final int[] TICKS = new int[9];

    static {
        for (int i = 0; i < 9; i++) TICKS[i] = i * 5;
    }

    public static final Set<String> SILENT_ROOMS = Set.of("buttons", "old trap", "bridges");
    public static final double LEVER_CLICK_SHARE = 0.16;

    public static boolean ticks(String roomName) { return !SILENT_ROOMS.contains(roomName.strip().toLowerCase(Locale.ROOT)); }
}
