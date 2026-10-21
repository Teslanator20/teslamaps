// Ported from ADM's p3sim/clear/secrets/Loot.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.List;

public final class LootKt {
    private LootKt() {}

    public static final List<String> ESSENCE_TYPES = List.of("Ice", "Gold", "Spider", "Diamond");

    public static String essenceColour(String type) {
        return switch (type) {
            case "Gold" -> "§6";
            case "Spider" -> "§5";
            case "Wither" -> "§d";
            default -> "§b";
        };
    }
}
