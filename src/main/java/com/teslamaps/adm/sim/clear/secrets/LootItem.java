// Ported from ADM's p3sim/clear/secrets/Loot.kt
package com.teslamaps.adm.sim.clear.secrets;

public enum LootItem {
    SUPERBOOM("Superboom TNT", "SUPERBOOM_TNT", "§9", "rare"),
    REVIVE_STONE("Revive Stone", "REVIVE_STONE", "§6", "legendary"),
    SPIRIT_LEAP("Spirit Leap", "SPIRIT_LEAP", "§9", "rare", 5),
    HEALING_POTION("Healing VIII Splash Potion", "POTION", "§5", "common"),
    DECOY("Decoy", "DUNGEON_DECOY", "§a", "uncommon"),
    INFLATABLE_JERRY("Inflatable Jerry", "INFLATABLE_JERRY", "§f", "common", 2),
    TRAINING_WEIGHTS("Training Weights", "TRAINING_WEIGHTS", "§a", "uncommon"),
    TRAP("Trap", "DUNGEON_TRAP", "§a", "uncommon"),
    DEFUSE_KIT("Defuse Kit", "DEFUSE_KIT", "§a", "uncommon"),
    CANDYCOMB("Candycomb", "CANDYCOMB", "§9", "rare");

    public final String displayName;
    public final String id;
    public final String colour;
    public final String style;
    public final int floorCount;

    LootItem(String displayName, String id, String colour, String style, int floorCount) {
        this.displayName = displayName;
        this.id = id;
        this.colour = colour;
        this.style = style;
        this.floorCount = floorCount;
    }

    LootItem(String displayName, String id, String colour, String style) { this(displayName, id, colour, style, 1); }

    LootItem(String displayName, String id, String colour) { this(displayName, id, colour, "common", 1); }

    LootItem(String displayName, String id) { this(displayName, id, "§9", "common", 1); }
}
