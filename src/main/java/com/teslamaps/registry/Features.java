package com.teslamaps.registry;

/** Features built on the registry. One {@code X.register()} line each, grouped by area. */
final class Features {

    private Features() {}

    static void register() {
        // wave 1a dungeon-a
        com.teslamaps.dungeon.DungeonManager.register();
    }
}
