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
        // wave 1c party-sounds
        com.teslamaps.utils.PartyTracker.register();
        // wave 1c fishing-safari
        com.teslamaps.features.FishingBobberTimer.register();
        com.teslamaps.safari.SafariBanners.register();
        // wave 1c visuals
        com.teslamaps.features.Etherwarp.register();
        com.teslamaps.features.BlockOverlay.register();
        com.teslamaps.features.mobvisuals.MobVisualRender.register();
        // wave 1c garden
        com.teslamaps.features.FarmingSensitivity.register();
        com.teslamaps.features.FarmProgress.register();
        // wave 1c mining-foraging
        com.teslamaps.esp.CorpseFinder.register();
        com.teslamaps.features.mining.PickaxeAbility.register();
        com.teslamaps.features.foraging.FloorDrops.register();
        com.teslamaps.features.foraging.HoneyhiveHelper.register();
        // wave 1c qol
        com.teslamaps.features.ImplosionHider.register();
        com.teslamaps.features.npc.NpcWarps.register();
        com.teslamaps.features.EventTracker.register();
        // wave 1c combat-pets
        com.teslamaps.slayer.SlayerHUD.register();
        com.teslamaps.slayer.SlayerQuest.register();
        com.teslamaps.features.croesus.KuudraKeys.register();
        com.teslamaps.features.pets.PetStore.register();
        com.teslamaps.features.pets.PetOverlay.register();
        // w02-06-term
        com.teslamaps.dungeon.TerminalSplits.register();
        // w02-03-puz
        com.teslamaps.dungeon.puzzle.PuzzleHud.register();
        com.teslamaps.dungeon.puzzle.QuizExtras.register();
        com.teslamaps.dungeon.puzzle.WaterboardPreviewer.register();
        com.teslamaps.dungeon.puzzle.BlazeSpots.register();
        // w02-05-term
        com.teslamaps.features.TerminalProgress.register();
        com.teslamaps.features.TerminalTitles.register();
        com.teslamaps.features.TerminalSounds.register();
        com.teslamaps.features.MelodyDisplay.register();
        // w02-08-boss
        com.teslamaps.features.BossHpDisplays.register();
        com.teslamaps.dungeon.F7Devices.register();
        com.teslamaps.dungeon.MaxorCrystals.register();
        com.teslamaps.dungeon.DragonHitCount.register();
        // w02-09-boss
        com.teslamaps.features.SadanFeatures.register();
        com.teslamaps.features.HideWitherKing.register();
        com.teslamaps.features.ScarfSpawnTimer.register();
        com.teslamaps.features.KingRelics.register();
        com.teslamaps.features.LastBreathSound.register();
        // w02-00-map
        com.teslamaps.dungeon.MapExtras.register();
        com.teslamaps.dungeon.RoomAlerts.register();
        com.teslamaps.features.SecretHud.register();
        // w02-04-puz
        com.teslamaps.dungeon.puzzle.TicTacToe.register();
        // w02-02-map
        com.teslamaps.features.StarMobRadar.register();
        // w02-07-boss
        com.teslamaps.features.TerminalWaypoints.register();
        com.teslamaps.features.FireFreezeTimer.register();
        com.teslamaps.features.GuardianHealth.register();
        com.teslamaps.features.DimensionalSlash.register();
        com.teslamaps.features.F7Titles.register();
        com.teslamaps.features.F7BossSounds.register();
        com.teslamaps.features.DragonRecolor.register();
        // w02-01-map
        com.teslamaps.features.BloodCheck.register();
        com.teslamaps.dungeon.RushSplits.register();
        com.teslamaps.features.MapPeek.register();
        // w03-03-dun
        com.teslamaps.features.DungeonCleanEnd.register();
        com.teslamaps.features.BreakerCharges.register();
        com.teslamaps.features.CopyFails.register();
        com.teslamaps.features.IcedMobs.register();
        com.teslamaps.features.PartyFinderHelper.register();
        com.teslamaps.dungeon.RunLogger.register();
        // w03-06-dun
        com.teslamaps.features.croesus.ChestProfitHud.register();
        com.teslamaps.features.TeammateHealth.register();
        com.teslamaps.features.WarpCooldownBlock.register();
        com.teslamaps.player.PlayerTracker.register();
        // w03-00-boss
        com.teslamaps.dungeon.WatcherBar.register();
        com.teslamaps.dungeon.DragonExtras.register();
        com.teslamaps.features.LividSolver.register();
        // w03-08-dun
        com.teslamaps.features.NonStarredNametags.register();
        // w03-09-dun
        com.teslamaps.dungeon.Splits.register();
        com.teslamaps.features.KeyHud.register();
        com.teslamaps.features.SecretPickupHud.register();
        com.teslamaps.features.DungeonGambling.register();
        com.teslamaps.features.TotemAnimation.register();
        com.teslamaps.features.EasySuperboom.register();
        // w03-07-dun
        com.teslamaps.dungeon.ScoreHud.register();
        com.teslamaps.dungeon.ExtraStats.register();
        com.teslamaps.dungeon.DungeonInfoHud.register();
        com.teslamaps.dungeon.PartyFinderMarks.register();
        com.teslamaps.dungeon.DungeonSession.register();
        // w03-05-dun
        com.teslamaps.features.SecretTrackerLocator.register();
        com.teslamaps.dungeon.ServerLagTimer.register();
        com.teslamaps.features.DungeonLevelSlotText.register();
        com.teslamaps.features.SwapSound.register();
        com.teslamaps.features.TreasureTalismanAlert.register();
        com.teslamaps.features.AutoGFS.register();
        // w03-01-boss
        com.teslamaps.features.FuckDiorite.register();
        com.teslamaps.features.DragonStackAimer.register();
        // w03-02-dun
        com.teslamaps.features.BlessingHud.register();
        com.teslamaps.features.croesus.CroesusChestCounter.register();
        com.teslamaps.features.DungeonDeathCounter.register();
        com.teslamaps.dungeon.RoomVisits.register();
        com.teslamaps.features.RareGroundDrops.register();
        // w03-04-dun
        com.teslamaps.features.croesus.LootLogger.register();
        com.teslamaps.features.BossBarHealth.register();
        com.teslamaps.features.DungeonTabColors.register();
        com.teslamaps.features.MageBeam.register();
        com.teslamaps.dungeon.RoomPreview.register();
        com.teslamaps.dungeon.TeamStats.register();
        com.teslamaps.features.ShadowAssassinAlert.register();
        // w04-03-inv
        com.teslamaps.features.MoveInsertIntoSack.register();
        com.teslamaps.features.ArrowPoison.register();
        com.teslamaps.features.RevertAxes.register();
        com.teslamaps.features.BingoCardTips.register();
        com.teslamaps.features.ItemTooltipExtras.register();
        // w04-00-dun
        com.teslamaps.features.DiamanteAlert.register();
    }
}
