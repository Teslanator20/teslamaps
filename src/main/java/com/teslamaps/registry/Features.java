package com.teslamaps.registry;

/** Features built on the registry. One {@code X.register()} line each, grouped by area. */
final class Features {

    private Features() {}

    static void register() {
        // wave 1a dungeon-a
        com.teslamaps.dungeon.DungeonManager.register();
        com.teslamaps.api.ElectionApi.register();
        com.teslamaps.dungeon.DungeonScore.register();
        com.teslamaps.features.SoulweaverHider.register();
        // wave 1a puzzles
        com.teslamaps.dungeon.puzzle.PuzzleTimers.register();
        // wave 1a dungeon-b
        com.teslamaps.dungeon.DungeonWaypoints.register();
        // wave 1a map
        com.teslamaps.esp.DoorRoute.register();
        com.teslamaps.features.SecretWaypoints.register();
        // wave 1a boss
        com.teslamaps.features.BossTickHuds.register();
        com.teslamaps.features.LividTimer.register();
        com.teslamaps.dungeon.puzzle.TerracottaTimer.register();
        com.teslamaps.features.DragonESP.register();
        // wave 1b inventory-a
        com.teslamaps.features.ItemProtection.register();
    }
}
