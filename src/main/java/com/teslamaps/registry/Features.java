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
        com.teslamaps.features.WardrobeKeys.register();
        com.teslamaps.features.SkillLevelStack.register();
        // wave 1b economy
        com.teslamaps.features.BazaarOwnOrders.register();
        com.teslamaps.features.ContainerValue.register();
        // wave 1b hud
        com.teslamaps.features.CombatTimers.register();
        com.teslamaps.safari.SafariTracker.register();
        // wave 1b inventory-b
        com.teslamaps.utils.SbProfile.register();
        com.teslamaps.features.StorageOverlay.register();
        com.teslamaps.features.StorageApiFill.register();
        // wave 1b chat
        com.teslamaps.features.ChatWaypoint.register();
        com.teslamaps.features.ChatSpamHud.register();
        // wave 1c combat-pets
        com.teslamaps.slayer.SlayerHUD.register();
        com.teslamaps.slayer.SlayerQuest.register();
        com.teslamaps.features.croesus.KuudraKeys.register();
    }
}
