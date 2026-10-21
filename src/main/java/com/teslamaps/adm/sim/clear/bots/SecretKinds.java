// Ported from ADM's p3sim/clear/bots/BotWorldImpl.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.SecretKind;

/** Pure mapping from the rooms pack's secret type words to the run's SecretKind. */
public final class SecretKinds {
    private SecretKinds() {}

    public static SecretKind of(String type) {
        if (type == null) return SecretKind.CHEST;
        return switch (type) {
            case "item" -> SecretKind.ITEM;
            case "bat" -> SecretKind.BAT;
            case "essence" -> SecretKind.ESSENCE;
            case "redstoneKey" -> SecretKind.REDSTONE_KEY;
            case "lockedChest" -> SecretKind.LOCKED_CHEST;
            default -> SecretKind.CHEST;
        };
    }
}
