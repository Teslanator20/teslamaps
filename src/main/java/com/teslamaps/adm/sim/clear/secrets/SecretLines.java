// Ported from ADM's p3sim/clear/secrets/Loot.kt
package com.teslamaps.adm.sim.clear.secrets;

public final class SecretLines {
    private SecretLines() {}

    public static final String WITHER_ESSENCE_SELF = "§fYou found a §r§dWither Essence§r§f! Everyone gains an extra essence!";

    public static String witherEssenceOther(String colour, String name) {
        return colour + name + " §r§ffound a §r§dWither Essence§r§f! Everyone gains an extra essence!";
    }

    private static String lump(Drop.Essence drop) { return LootKt.essenceColour(drop.type) + "x" + drop.amount + " " + drop.type + " Essence§r§f!"; }

    public static String chestEssenceSelf(Drop.Essence drop) { return "§6§lESSENCE! §r§fYou found §r" + lump(drop); }

    public static String chestEssenceOther(String colour, String name, Drop.Essence drop) {
        return "§6§lESSENCE! §r" + colour + name + " §r§ffound §r" + lump(drop);
    }

    public static String obtained(String who, LootItem item, int count) {
        return who + "§r§f §r§ehas obtained §r" + item.colour + item.displayName + (count > 1 ? " §r§8x" + count : "") + "§r§e!";
    }
}
