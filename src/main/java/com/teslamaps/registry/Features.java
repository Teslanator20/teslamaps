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
        // w04-04-inv
        com.teslamaps.features.TooltipTweaks.register();
        com.teslamaps.features.EquipmentDisplay.register();
        com.teslamaps.features.ExperimentHelper.register();
        // w04-06-inv
        com.teslamaps.features.EnchantTooltipTweaks.register();
        com.teslamaps.features.ForgeRecipeViewer.register();
        com.teslamaps.features.EnchantTablePanel.register();
        com.teslamaps.features.FancyCraftingTable.register();
        com.teslamaps.features.HoppityCollectionStats.register();
        // w04-05-inv
        com.teslamaps.features.Experimentation.register();
        com.teslamaps.features.FullInventoryWarning.register();
        com.teslamaps.features.FocusMode.register();
        com.teslamaps.features.XpInTooltips.register();
        com.teslamaps.features.DuplicateItems.register();
        com.teslamaps.features.HideExCoopMembers.register();
        com.teslamaps.features.HoppityWarpUniques.register();
        // w04-08-inv
        com.teslamaps.features.CompactLevelBars.register();
        com.teslamaps.features.ItemPickupAlert.register();
        com.teslamaps.features.MuseumDonations.register();
        com.teslamaps.features.ReforgeStop.register();
        com.teslamaps.features.PreventPlacing.register();
        // w04-09-inv
        com.teslamaps.features.MenuHighlights.register();
        com.teslamaps.features.MuseumHelper.register();
        com.teslamaps.features.ObtainedDate.register();
        com.teslamaps.features.MinionHelper.register();
        com.teslamaps.features.PowerStoneStats.register();
        // w04-01-inv
        com.teslamaps.features.AnimatedDyeFix.register();
        com.teslamaps.features.AnvilHelper.register();
        com.teslamaps.features.accessories.AccessoryHelper.register();
        com.teslamaps.features.CraftHelper.register();
        // w04-02-inv
        com.teslamaps.features.CustomWardrobe.register();
        com.teslamaps.features.TooltipExtras.register();
        com.teslamaps.features.BackpackSlot.register();
        com.teslamaps.features.AttributeOverlay.register();
        com.teslamaps.features.customize.ItemModelMigration.register();
        com.teslamaps.features.BrewingStandOverlay.register();
        // w04-07-inv
        com.teslamaps.features.StarUpgradeCost.register();
        com.teslamaps.features.MagicalPowerStack.register();
        // w05-02-inv
        com.teslamaps.features.MenuSlotText.register();
        com.teslamaps.features.StatIcons.register();
        // w05-04-inv
        com.teslamaps.features.TooltipBorder.register();
        com.teslamaps.features.HotxPerkCurrency.register();
        com.teslamaps.features.ToolkitCropIcons.register();
        // w05-09-inv
        com.teslamaps.features.InstaLoadoutSwap.register();
        // w05-01-inv
        com.teslamaps.features.RavengardTooltips.register();
        com.teslamaps.features.WizardPortalLore.register();
        com.teslamaps.features.StackingEnchantHud.register();
        com.teslamaps.features.ConsumableProtection.register();
        com.teslamaps.features.ItemStars.register();
        com.teslamaps.features.SkyblockIdTooltip.register();
        // w05-08-inv
        com.teslamaps.features.SlotText.register();
        com.teslamaps.features.SearchHighlight.register();
        // w05-07-inv
        com.teslamaps.features.SlotLock.register();
        com.teslamaps.features.SackTable.register();
        com.teslamaps.features.RomanNumerals.register();
        // w05-00-inv
        com.teslamaps.features.OldMasterStar.register();
        com.teslamaps.features.ItemLoreExtras.register();
        com.teslamaps.features.ReforgeStoneStats.register();
        com.teslamaps.features.CrownValueOverlay.register();
        // w05-03-inv
        com.teslamaps.features.ItemList.register();
        com.teslamaps.features.StorageGuiScale.register();
        com.teslamaps.features.TooltipMenuExtras.register();
        // w05-05-inv
        com.teslamaps.features.DevTools.register();
        com.teslamaps.features.HideNotClickable.register();
        // w05-06-inv
        com.teslamaps.features.SackApi.register();
        com.teslamaps.features.ItemNumbers.register();
        com.teslamaps.features.mining.HotmPerks.register();
        com.teslamaps.features.GemstoneBuffs.register();
        com.teslamaps.features.ItemCycle.register();
        // w06-00-qol
        com.teslamaps.features.CommandHelpers.register();
        com.teslamaps.features.StopMovingOnDeath.register();
        com.teslamaps.features.AlwaysShowItemName.register();
        // w06-02-qol
        com.teslamaps.features.MiscQol.register();
        com.teslamaps.features.Achievements.register();
        // w06-09-qol
        com.teslamaps.features.SpiderRelics.register();
        com.teslamaps.features.RavengardLootBags.register();
        com.teslamaps.features.TeleportPads.register();
        com.teslamaps.features.SkyblockTimeReminder.register();
        com.teslamaps.features.SeeThroughWindow.register();
        com.teslamaps.features.SoopyCommand.register();
        com.teslamaps.features.SpyMode.register();
        // w06-04-qol
        com.teslamaps.features.Reminders.register();
        com.teslamaps.features.DamageAlert.register();
        com.teslamaps.features.ContainerTriggers.register();
        com.teslamaps.features.HarpHelper.register();
        com.teslamaps.features.DiscordRpc.register();
        // w06-03-qol
        com.teslamaps.features.qol.FairySouls.register();
        com.teslamaps.features.qol.GameTweaks.register();
        com.teslamaps.features.qol.EnchantedClock.register();
        com.teslamaps.features.qol.UpgradeReminder.register();
        com.teslamaps.features.qol.CityProject.register();
        com.teslamaps.features.qol.CenturyCake.register();
        // w06-06-qol
        com.teslamaps.features.MinionFeatures.register();
        com.teslamaps.features.ClickFixes.register();
        com.teslamaps.features.MobSpawnAlert.register();
        com.teslamaps.features.IslandAlerts.register();
        // w06-08-qol
        com.teslamaps.features.PreviousLobby.register();
        com.teslamaps.features.QuickCraftConfirm.register();
        com.teslamaps.features.AutoShiftClick.register();
        com.teslamaps.features.StreamerMode.register();
        com.teslamaps.features.ScreenshotPreview.register();
        // w06-01-qol
        com.teslamaps.features.BuildersWandPreview.register();
        com.teslamaps.features.WindowTweaks.register();
        com.teslamaps.features.BrewingStandColors.register();
        com.teslamaps.features.BingoCard.register();
        com.teslamaps.features.ArrowHitSound.register();
        // w06-05-qol
        com.teslamaps.features.FancyWarpMenu.register();
        com.teslamaps.features.DevWorldTools.register();
        com.teslamaps.features.DeathSound.register();
        com.teslamaps.features.CameraTweaks.register();
        com.teslamaps.features.InfernoMinion.register();
        // w06-07-qol
        com.teslamaps.features.CallLinks.register();
        com.teslamaps.features.SkyblockClock.register();
        com.teslamaps.features.navigation.IslandNavigation.register();
        com.teslamaps.features.MonoAudio.register();
        com.teslamaps.features.QolTweaks.register();
        com.teslamaps.features.RenderHiders.register();
        com.teslamaps.features.ServerRestartWarning.register();
        // w07-03-qol
        com.teslamaps.debug.PacketLogger.register();
        com.teslamaps.features.OldSkyblockMenu.register();
        // w07-04-qol
        com.teslamaps.features.ChatCalculator.register();
        com.teslamaps.features.SoundMutes.register();
        // w07-01-qol
        com.teslamaps.features.CommandShortcuts.register();
        com.teslamaps.features.AbiphoneAccept.register();
        com.teslamaps.features.DebugDump.register();
        // w07-07-hud
        com.teslamaps.features.tracker.CustomTrackers.register();
        com.teslamaps.features.hud.BeaconPower.register();
        com.teslamaps.features.hud.CustomTextBox.register();
        com.teslamaps.features.hud.MovementSpeed.register();
        com.teslamaps.features.hud.CrownOfAvarice.register();
        // w07-08-hud
        com.teslamaps.features.ExecutionLog.register();
        com.teslamaps.features.ChickenHeadTimer.register();
        com.teslamaps.features.CakeTracker.register();
        com.teslamaps.features.theend.DraconicSacrificeTracker.register();
        com.teslamaps.features.ItemPickupLog.register();
        com.teslamaps.features.CustomTodos.register();
        // w07-06-hud
        com.teslamaps.features.CustomScoreboard.register();
        com.teslamaps.features.ArmorHud.register();
        com.teslamaps.features.ArmorStackHud.register();
        com.teslamaps.features.AccurateAbsorption.register();
        com.teslamaps.features.AllSkillsDisplay.register();
        com.teslamaps.features.SkillGoals.register();
        // w07-09-hud
        com.teslamaps.features.SbItemCooldowns.register();
        com.teslamaps.features.InventoryHud.register();
        com.teslamaps.features.theend.EyesPlacedHud.register();
        com.teslamaps.features.theend.EndStatsHud.register();
        com.teslamaps.features.FatalTempo.register();
        // w07-02-qol
        com.teslamaps.features.DebugCopy.register();
        com.teslamaps.features.CommandChains.register();
        com.teslamaps.features.AbiphoneHelper.register();
        com.teslamaps.features.waypoints.WaypointGroups.register();
        // w08-00-hud
        com.teslamaps.features.hud.TabStatsHud.register();
        com.teslamaps.features.tracker.TrackerRows.register();
        com.teslamaps.features.ExperimentStats.register();
        com.teslamaps.features.hud.LobbyPlayers.register();
        com.teslamaps.features.hud.MaxwellHud.register();
        // w08-01-hud
        com.teslamaps.features.hud.FancyTabHud.register();
        com.teslamaps.features.HoppityEventStats.register();
        com.teslamaps.features.hud.LimboTracker.register();
        com.teslamaps.features.tracker.ProfitTracker.register();
        com.teslamaps.features.hud.PlayerStats.register();
        com.teslamaps.features.hud.QuiverHud.register();
        // w08-07-hud
        com.teslamaps.features.LootCounters.register();
        com.teslamaps.features.hud.TabWidgetHud.register();
        // w07-05-qol
        com.teslamaps.features.ScoreboardLogger.register();
        com.teslamaps.features.WikiLookup.register();
        com.teslamaps.features.waypoints.RouteKeys.register();
        com.teslamaps.features.AbilityMenuFix.register();
        // w07-00-qol
        com.teslamaps.features.qol.MusicOverlay.register();
        com.teslamaps.features.qol.Level7Guide.register();
        com.teslamaps.features.WarpTransferWait.register();
        com.teslamaps.features.qol.TiaRelayHelper.register();
        com.teslamaps.features.qol.QueueEstimate.register();
        com.teslamaps.features.qol.CalcXp.register();
        com.teslamaps.features.qol.ClockOffset.register();
        // w08-06-hud
        com.teslamaps.features.AbilityCooldowns.register();
        com.teslamaps.features.TabWidgetHint.register();
        // w08-03-hud
        com.teslamaps.features.hud.PlayersInRange.register();
        com.teslamaps.features.ReaperTimer.register();
        com.teslamaps.features.SkeletonHelmet.register();
        com.teslamaps.features.JyrreTimer.register();
        // w08-05-hud
        com.teslamaps.features.tracker.Tracker.register();
        com.teslamaps.features.hud.MovableVanillaHud.register();
        com.teslamaps.features.hud.VanillaManaBar.register();
        com.teslamaps.features.hud.SmallHuds.register();
        // w08-04-hud
        com.teslamaps.features.hud.ProfileInfo.register();
        com.teslamaps.features.hud.TitleStack.register();
        com.teslamaps.features.hud.SpringBootsHeight.register();
        com.teslamaps.features.DailyTimers.register();
        // w08-08-vis
        com.teslamaps.features.ArrowVisuals.register();
        com.teslamaps.features.CustomCrosshair.register();
        com.teslamaps.features.OldItemTextures.register();
        com.teslamaps.features.BetterContainers.register();
        // w08-02-hud
        com.teslamaps.features.ScreenFlash.register();
        com.teslamaps.features.Notifications.register();
        com.teslamaps.features.hud.EffectTimers.register();
        com.teslamaps.features.hud.MayorOverlay.register();
        com.teslamaps.features.hud.SackHud.register();
        com.teslamaps.features.hud.MinionWidget.register();
        com.teslamaps.features.BitsWarning.register();
        // w08-09-vis
        com.teslamaps.features.CommonSeaCreatureHider.register();
        com.teslamaps.features.ChumBucketHider.register();
        com.teslamaps.features.ArachneTagHider.register();
        com.teslamaps.features.ColorParticleFix.register();
        com.teslamaps.features.WitherCloakShields.register();
        com.teslamaps.features.LookTargeting.register();
        // w10-00-vis
        com.teslamaps.features.BlockEsp.register();
        com.teslamaps.features.ChestEsp.register();
        // w10-09-chat
        com.teslamaps.features.DungeonChatFilter.register();
        com.teslamaps.features.ChatCompactRulesIslands.register();
        com.teslamaps.features.ChatQueue.register();
        com.teslamaps.features.TerminatorCooldownMute.register();
        com.teslamaps.features.ImageLinkPreview.register();
        // w10-03-chat
        com.teslamaps.features.ChatCompacts.register();
        // w10-05-chat
        com.teslamaps.features.RareDropNames.register();
        com.teslamaps.features.HoppityCompactChat.register();
        // w10-04-chat
        com.teslamaps.features.ChatAlerts.register();
        com.teslamaps.features.ChatSoundResponse.register();
        // w10-07-chat
        com.teslamaps.features.SackMessages.register();
        com.teslamaps.features.SkyblockXpChat.register();
        // w10-06-chat
        com.teslamaps.features.ChatExtras.register();
        com.teslamaps.features.ChatRewrites.register();
        // w10-02-chat
        com.teslamaps.features.ConfirmPrompt.register();
        com.teslamaps.features.ChatSearch.register();
        com.teslamaps.features.ChatEmotes.register();
        com.teslamaps.features.ChatCompactsMore.register();
        // w09-08-vis
        com.teslamaps.features.ParticleHider.register();
        // w10-01-chat
        com.teslamaps.features.ChatLog.register();
        com.teslamaps.features.CurrentChatDisplay.register();
        // w09-02-vis
        com.teslamaps.features.ItemAnimations.register();
        com.teslamaps.features.GyroWandRange.register();
        com.teslamaps.features.ParticleHiders.register();
        com.teslamaps.features.FollowingLine.register();
        com.teslamaps.features.VisualFixes.register();
        // w10-08-chat
        com.teslamaps.features.PlayerChatFormat.register();
        com.teslamaps.features.ChatTranslator.register();
        com.teslamaps.features.SmoothChat.register();
        com.teslamaps.features.ReportAutoConfirm.register();
        com.teslamaps.command.ExtraSuggestions.register();
        // w09-04-vis
        com.teslamaps.features.VisualTweaks.register();
        com.teslamaps.features.CompactTabList.register();
        // w09-01-vis
        com.teslamaps.features.AbilityRadius.register();
        com.teslamaps.features.DroppedItemScaling.register();
        com.teslamaps.features.EndChestTint.register();
        com.teslamaps.features.HurtTint.register();
        com.teslamaps.features.DvdLogo.register();
        // w09-00-vis
        com.teslamaps.features.BigItems.register();
        com.teslamaps.features.cosmetic.CustomSkin.register();
        com.teslamaps.features.cosmetic.ConeHat.register();
        com.teslamaps.features.cosmetic.CustomHubMap.register();
        com.teslamaps.features.EndermanTeleportHider.register();
        com.teslamaps.features.EntityHighlight.register();
        // w09-07-vis
        com.teslamaps.features.ZeroHpNametags.register();
        // w09-06-vis
        com.teslamaps.features.TransparentArmor.register();
        com.teslamaps.features.ZealotColor.register();
        com.teslamaps.features.TextVisuals.register();
        // w09-03-vis
        com.teslamaps.features.ChangeCrouch.register();
        com.teslamaps.features.HideGlitchMobs.register();
        com.teslamaps.features.TooltipLineSmoother.register();
        com.teslamaps.features.HeadDisplaySize.register();
        com.teslamaps.safari.HideonwallPerches.register();
        com.teslamaps.features.LowerEndermanNametags.register();
        // w09-09-vis
        com.teslamaps.features.IslandClouds.register();
        com.teslamaps.features.IHateDoors.register();
        com.teslamaps.features.Trajectories.register();
        com.teslamaps.features.CustomEspRules.register();
        com.teslamaps.features.ParkourGuides.register();
        // w09-05-vis
        com.teslamaps.features.MobIcons.register();
        com.teslamaps.features.capes.Cosmetics.register();
        com.teslamaps.features.Cosmetics.register();
        com.teslamaps.features.PlayerCosmetics.register();
        com.teslamaps.features.PlayerModels.register();
        com.teslamaps.features.PetDragon.register();
        com.teslamaps.features.TripwireRecolor.register();
        // w12-01-gui
        com.teslamaps.features.HideSillyButtons.register();
        // w12-05-perf
        com.teslamaps.features.KeepTerrainLoaded.register();
        com.teslamaps.features.ErrorManager.register();
        com.teslamaps.features.inspector.Inspector.register();
        // w12-00-gui
        com.teslamaps.features.customize.ModifyIndicator.register();
        com.teslamaps.features.QuickModSwitch.register();
        // w12-03-perf
        com.teslamaps.features.PerfOptions.register();
        com.teslamaps.features.HideFarEntities.register();
        // w12-06-eco
        com.teslamaps.features.bazaar.BazaarOrderTracker.register();
        com.teslamaps.features.bazaar.PurchaseGuard.register();
        com.teslamaps.features.bazaar.ManageAuctions.register();
        com.teslamaps.features.bazaar.AuctionOutbid.register();
        com.teslamaps.features.bazaar.BazaarQuickQuantities.register();
        com.teslamaps.features.bazaar.BazaarReorder.register();
        com.teslamaps.features.bazaar.BazaarPurchaseHelper.register();
        // w12-02-gui
        com.teslamaps.dungeon.ClassColors.register();
        com.teslamaps.features.TabWidgetGuide.register();
        // w11-04-par
        com.teslamaps.features.PartyCheck.register();
        com.teslamaps.features.MarkedPlayers.register();
        com.teslamaps.features.SocialScreenFilter.register();
        com.teslamaps.features.ScamCheck.register();
        com.teslamaps.features.ModChat.register();
        // w11-08-gui
        com.teslamaps.config.ConfigBackups.register();
        com.teslamaps.features.FeatureToggleKeys.register();
        // w12-07-eco
        com.teslamaps.features.BitsShopHelper.register();
        com.teslamaps.features.TradeValue.register();
        com.teslamaps.features.BazaarFlipTracker.register();
        com.teslamaps.features.BazaarChatSummary.register();
        // w11-07-gui
        com.teslamaps.features.ConfigAccess.register();
        com.teslamaps.utils.UserSounds.register();
        FeatureConditions.register();
        com.teslamaps.features.CustomFont.register();
        com.teslamaps.features.hud.TabWidgetsMenuHelper.register();
        // w11-09-gui
        com.teslamaps.features.RadialMenus.register();
        com.teslamaps.features.HudLayouts.register();
        com.teslamaps.features.KeybindSync.register();
        com.teslamaps.features.JoinTips.register();
        // w11-01-par
        com.teslamaps.features.KuudraPartyFinder.register();
        com.teslamaps.features.AcceptLastInvite.register();
        com.teslamaps.features.ManaDrainAnnounce.register();
        com.teslamaps.features.MutePartySpam.register();
        com.teslamaps.safari.SafariHotspotPost.register();
        com.teslamaps.safari.HunterTrades.register();
        // w11-06-gui
        com.teslamaps.features.WorldOverlayToggle.register();
        com.teslamaps.features.NewOptions.register();
        // w11-02-par
        com.teslamaps.features.PartyInvites.register();
        com.teslamaps.features.capes.TeslaUserLabels.register();
        com.teslamaps.features.FancyPartyFinder.register();
        // w11-03-par
        com.teslamaps.features.Reparty.register();
        com.teslamaps.features.PartyListFormat.register();
        com.teslamaps.features.PartyMembersHud.register();
        com.teslamaps.features.PartyJoinAlerts.register();
        com.teslamaps.features.ThrottleNotifier.register();
        // w11-05-par
        com.teslamaps.features.PartyShortcuts.register();
        com.teslamaps.features.HighlightTeammates.register();
        com.teslamaps.features.DianaPartyFinder.register();
        com.teslamaps.features.PositionalMessages.register();
        // w11-00-chat
        com.teslamaps.features.ChatEncryption.register();
        // w12-08-eco
        com.teslamaps.features.AnitaShop.register();
        com.teslamaps.features.BazaarHelpers.register();
        com.teslamaps.features.CraftMaterials.register();
        com.teslamaps.features.InstaSellHighlight.register();
        com.teslamaps.features.PriceHistory.register();
        com.teslamaps.features.AuctionSortWarning.register();
        com.teslamaps.features.theend.DragonProfitTracker.register();
        // w12-09-eco
        com.teslamaps.features.BazaarFillEstimate.register();
        com.teslamaps.features.PriceLookup.register();
        com.teslamaps.features.SackValueHud.register();
        com.teslamaps.features.WardrobeValue.register();
        // w14-08-min
        com.teslamaps.features.mining.CorleoneTitle.register();
        com.teslamaps.features.mining.CorpseKeyAnnounce.register();
        com.teslamaps.features.mining.CrystalHollowsOverlay.register();
        com.teslamaps.features.mining.BlockStrengthGuide.register();
        com.teslamaps.features.mining.BlockBreakPrediction.register();
        com.teslamaps.features.mining.HollowsAreaWalls.register();
        // w14-05-gar
        com.teslamaps.features.JacobContests.register();
        com.teslamaps.features.PestFinder.register();
        com.teslamaps.features.PestKillTracker.register();
        com.teslamaps.features.PestTraps.register();
        com.teslamaps.features.PestSpawnStats.register();
        com.teslamaps.features.GardenWarpCommands.register();
        com.teslamaps.features.mobvisuals.PestGlowColors.register();
        // w13-06-gar
        com.teslamaps.features.garden.GreenhouseModels.register();
        com.teslamaps.features.garden.Composter.register();
        com.teslamaps.features.garden.ComposterOverlay.register();
        com.teslamaps.features.garden.ComposterMenu.register();
        com.teslamaps.features.garden.CropMilestoneHud.register();
        com.teslamaps.features.garden.FarmingFortuneHud.register();
        com.teslamaps.features.garden.CropFever.register();
        com.teslamaps.features.garden.VisitorOffer.register();
        // w14-01-gar
        com.teslamaps.features.visitors.Visitors.register();
        com.teslamaps.features.visitors.VisitorShoppingList.register();
        com.teslamaps.features.visitors.VisitorOffer.register();
        com.teslamaps.features.visitors.VisitorRewardGuard.register();
        com.teslamaps.features.visitors.VisitorArrival.register();
        com.teslamaps.features.visitors.VisitorStatusHighlight.register();
        com.teslamaps.features.gardenqol.MutationsWebsite.register();
        com.teslamaps.features.gardenqol.GardenSafety.register();
        com.teslamaps.features.gardenqol.BarnSolvers.register();
        // w14-07-min
        com.teslamaps.features.mining.Commissions.register();
        com.teslamaps.features.mining.CommissionWaypoints.register();
        com.teslamaps.features.mining.CommissionMobHighlight.register();
        com.teslamaps.features.mining.CommissionTips.register();
        com.teslamaps.features.mining.NucleusTracker.register();
        com.teslamaps.features.mining.HollowsMap.register();
        com.teslamaps.features.mining.TreasureChestHelper.register();
        // w14-02-gar
        com.teslamaps.features.YawPitchHud.register();
        com.teslamaps.features.SpeedPresets.register();
        com.teslamaps.features.RareCropTracker.register();
        com.teslamaps.features.StereoHarmony.register();
        com.teslamaps.features.GardenPlotBorders.register();
        com.teslamaps.features.VisitorChat.register();
        com.teslamaps.features.npc.VisitorLogbook.register();
        com.teslamaps.features.VisitorStats.register();
        // w14-03-gar
        com.teslamaps.features.GardenTrashBlocks.register();
        com.teslamaps.features.PhantomleafSolver.register();
        com.teslamaps.features.VisitorsOutsideGarden.register();
        com.teslamaps.features.GardenMenuHelpers.register();
        // w14-04-gar
        com.teslamaps.features.FarmingToolTooltips.register();
        com.teslamaps.features.GardenPlotMenu.register();
        com.teslamaps.features.FarmingStatsHud.register();
        com.teslamaps.features.GardenUptime.register();
        com.teslamaps.features.FarmKeys.register();
        com.teslamaps.features.CarrolynHelper.register();
        // w13-01-eco
        com.teslamaps.features.foraging.StarlynCouponProfit.register();
        com.teslamaps.features.SupercraftHelper.register();
        com.teslamaps.features.NpcTradeCosts.register();
        com.teslamaps.features.RngMeterHelper.register();
        com.teslamaps.features.TradeHistory.register();
        // w13-07-gar
        com.teslamaps.features.GardenMilestoneData.register();
        com.teslamaps.features.BestCropTime.register();
        com.teslamaps.features.DnaAnalyzerSolver.register();
        com.teslamaps.features.CropStartLocation.register();
        com.teslamaps.features.GardenLevelHud.register();
        com.teslamaps.features.CropFeverTracker.register();
        com.teslamaps.features.CropMilestoneMenu.register();
        com.teslamaps.features.FarmingRngTitles.register();
        // w13-08-gar
        com.teslamaps.features.CropStats.register();
        com.teslamaps.features.AtmosphericFilter.register();
        com.teslamaps.features.EliteLeaderboards.register();
        com.teslamaps.features.CropSpeedMeter.register();
        com.teslamaps.features.CropCalcCommands.register();
        com.teslamaps.features.FortuneGuide.register();
        com.teslamaps.features.FarmToolOverlays.register();
        com.teslamaps.features.JacobContestFF.register();
        // w13-09-gar
        com.teslamaps.features.garden.GardenCropData.register();
        com.teslamaps.features.garden.ContestStatsSummary.register();
        com.teslamaps.features.garden.JacobContestMenus.register();
        com.teslamaps.features.garden.JacobContestTimes.register();
        com.teslamaps.features.garden.OptimalSpeed.register();
        com.teslamaps.features.garden.HoeLevelDisplay.register();
        com.teslamaps.features.garden.GreenhouseGrowthTimer.register();
        com.teslamaps.features.garden.PestGuess.register();
        com.teslamaps.features.garden.CropMoneyDisplay.register();
        // w14-00-gar
        com.teslamaps.features.MantidKillDisplay.register();
        com.teslamaps.features.GlowingMushrooms.register();
        com.teslamaps.features.MousematAngles.register();
        com.teslamaps.features.NoBreak.register();
        com.teslamaps.features.PestSpawnSound.register();
        com.teslamaps.features.PestBaitPanel.register();
        com.teslamaps.features.NoCropRotation.register();
        com.teslamaps.features.PbFortuneGain.register();
        // w13-00-eco
        com.teslamaps.features.NpcDayLimit.register();
        com.teslamaps.features.PricePaid.register();
        com.teslamaps.features.hud.FireSaleWidget.register();
        com.teslamaps.features.PesthunterProfit.register();
        com.teslamaps.features.FancyAuctionHouse.register();
        // w14-06-gar
        com.teslamaps.features.GardenPlots.register();
        com.teslamaps.features.SixthVisitor.register();
        com.teslamaps.features.PlotSprays.register();
        // w13-05-pv
        com.teslamaps.features.PlayerPeek.register();
        // w13-02-eco
        com.teslamaps.features.AuctionPriceInput.register();
        com.teslamaps.features.SearchOverlay.register();
        com.teslamaps.features.BazaarOrderTooltip.register();
        com.teslamaps.features.EssenceShopCosts.register();
        com.teslamaps.features.EscrowFix.register();
        // w15-09-sla
        com.teslamaps.slayer.RemainingSlayerKills.register();
        com.teslamaps.slayer.SlayerTransparency.register();
        com.teslamaps.slayer.Rev5TntPing.register();
        com.teslamaps.slayer.InfernoHelper.register();
        com.teslamaps.features.KeeperWaypoints.register();
        // w15-05-min
        com.teslamaps.features.mining.MiningSoundMutes.register();
        com.teslamaps.features.mining.WishingCompassSolver.register();
        com.teslamaps.features.mining.PickobulusHelper.register();
        com.teslamaps.features.mining.ScathaAlerts.register();
        com.teslamaps.features.mining.PrecisionMiningHighlight.register();
        // w14-09-min
        com.teslamaps.features.mining.DarkMonolith.register();
        com.teslamaps.features.mining.NucleusChatFilter.register();
        com.teslamaps.features.mining.HollowsPass.register();
        com.teslamaps.features.mining.HollowsDeathWaypoint.register();
        com.teslamaps.features.mining.CommissionBlockColor.register();
        com.teslamaps.features.mining.DeepCavernsGuide.register();
        com.teslamaps.features.mining.NucleusOverlay.register();
    }
}
