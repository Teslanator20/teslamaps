// Ported from ADM's p3sim/clear/bots/BotEffects.kt
package com.teslamaps.adm.sim.clear.bots;

/** Which step sound a teammate makes on a floor (the mapping from the floor's sound group is assumed). */
public final class StepSounds {
    private StepSounds() {}

    /** [group] is the floor block's sound group in lower case, "" when unknown. */
    public static StepSound forFloor(String group) {
        return switch (group) {
            case "gravel", "dirt", "coarse_dirt", "rooted_dirt", "mud" -> StepSound.GRAVEL;
            case "wool", "carpet", "moss" -> StepSound.WOOL;
            case "wood", "planks", "log", "bamboo_wood", "nether_wood" -> StepSound.WOOD;
            case "grass", "azalea", "fern" -> StepSound.GRASS;
            default -> StepSound.GLASS;
        };
    }
}
