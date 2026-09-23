package com.teslamaps.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.dungeon.puzzle.BoulderSolver;
import com.teslamaps.dungeon.puzzle.CreeperBeamsSolver;
import com.teslamaps.dungeon.puzzle.DungeonBlaze;
import com.teslamaps.dungeon.puzzle.QuizSolver;
import com.teslamaps.dungeon.puzzle.SpiritBearTimer;
import com.teslamaps.dungeon.puzzle.TPMazeSolver;
import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.TicTacToe;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.AutoWish;
import com.teslamaps.features.BearSpawnWarning;
import com.teslamaps.features.KeybindMessage;
import com.teslamaps.features.LividSolver;
import com.teslamaps.features.SecretWaypoints;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.MapRenderer;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.scanner.RoomScanner;
import com.teslamaps.scanner.SecretTracker;
import com.teslamaps.slayer.SlayerHUD;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Features that existed before the registry, in their old call order. */
final class CoreFeatures {

    private static final String PUZZLES = "Puzzles & Terminals";

    private CoreFeatures() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    static void register() {
        hud();
        world();
        ticks();
    }

    private static void hud() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, "darkmode_under"), Perf.hud("darkmode_under", (g, d) -> com.teslamaps.features.DarkMode.renderUnderHud(g)));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, "darkmode_over"), Perf.hud("darkmode_over", (g, d) -> com.teslamaps.features.DarkMode.renderOverHud(g)));

        Feature.of("Map").section("Map").hudForeground("map", MapRenderer::render);
        Feature.of("SkillTracker").section("Skills").hudForeground("skilltracker", com.teslamaps.features.SkillTracker::render);
        Feature.of("SkillExpDisplay").section("Skills").hudHiddenInInventory("skillexp", com.teslamaps.features.SkillExpDisplay::render);
        Feature.of("PetOverlay").section("Skills").hudHiddenInInventory("petoverlay", com.teslamaps.features.pets.PetOverlay::render);
        Feature.of("BestiaryTracker").section("Combat").hudForeground("bestiary", com.teslamaps.features.BestiaryTracker::render);
        Feature.of("FlareMode").section("Skills").hudForeground("flaremode", com.teslamaps.features.hunting.FlareMode::render);
        Feature.of("LividSolver").section("ESP").hud("livid", LividSolver::renderHUD);
        Feature.of("SpiritBearTimer").section(PUZZLES).hud("spiritbear", SpiritBearTimer::render);
        Feature.of("KickedTimer").hud("kicked", com.teslamaps.features.KickedTimer::render);
        Feature.of("PerformanceHud").hud("perfhud", com.teslamaps.features.PerformanceHud::render);
        Feature.of("RareDropEffects").section("Render").hud("raredrop", com.teslamaps.features.RareDropEffects::render);
        Feature.of("PestRoutine").section("Garden").hudForeground("pestroutine", com.teslamaps.features.PestRoutine::render);
        Feature.of("FarmProgress").section("Garden").hudForeground("farmbar", com.teslamaps.features.FarmProgress::render);
        Feature.of("PestOverlay").section("Garden").hudForeground("pestoverlay", com.teslamaps.features.PestOverlay::render);
        Feature.of("FarmProfitOverlay").section("Garden").hudForeground("farmprofit", com.teslamaps.features.FarmProfitOverlay::render);
        Feature.of("CharmedVisitors").section("Garden").hudForeground("charmedvisitors", com.teslamaps.features.CharmedVisitors::render);
        Feature.of("HarvestFeast").section("Garden").hudHiddenInInventory("feastseason", com.teslamaps.features.HarvestFeast::render);
        Feature.of("OverkillCounter").section("Timers").hud("overkill", com.teslamaps.features.OverkillCounter::render);
        Feature.of("BearSpawnWarning").section("Sounds").hud("bearspawn", BearSpawnWarning::render);
        Feature.of("ThornStunTimer").section(PUZZLES).hud("thornstun", com.teslamaps.features.ThornStunTimer::render);
        Feature.of("SprintingOverlay").section("Render").hud("sprinting", com.teslamaps.features.SprintingOverlay::render);
        Feature.of("SafariTracker").section("Critter Safari").hud("safari", com.teslamaps.safari.SafariTracker::render);
        Feature.of("ProtectorHud").section("The End").hudHiddenInInventory("protectorstatus", com.teslamaps.features.theend.ProtectorHud::render);
        Feature.of("ProtectorLootHud").section("The End").hudHiddenInInventory("protectorloot", com.teslamaps.features.theend.ProtectorLootHud::render);
        Feature.of("HotfPerkDisplay").section("Foraging").hud("hotfperks", com.teslamaps.features.foraging.HotfPerkDisplay::render);
        Feature.of("LassoDisplay").section("Foraging").hud("lasso", com.teslamaps.features.hunting.LassoDisplay::render);
        Feature.of("PickaxeAbility").section("Mining").hud("pickaxeability", com.teslamaps.features.mining.PickaxeAbility::render);
        Feature.of("MineshaftPity").section("Mining").hud("mineshaftpity", com.teslamaps.features.mining.MineshaftPity::render);
        Feature.of("LatherOverlay").section("Foraging").hud("lather", com.teslamaps.features.foraging.LatherOverlay::render);
        Feature.of("ContestTimers").section("Contest Timers").hud("contesttimers", com.teslamaps.features.ContestTimers::render);
        Feature.of("FishingBobberTimer").section("Fishing").hud("bobbertimer", com.teslamaps.features.FishingBobberTimer::render);
        Feature.of("FishingHookDisplay").section("Fishing").hud("hookdisplay", com.teslamaps.features.FishingHookDisplay::render);
        Feature.of("SharedImageChat").hud("chatimages", com.teslamaps.features.SharedImageChatRenderer::render);
        Feature.of("Deployables").section("Skills").hud("deployables", com.teslamaps.features.Deployables::render);
        Feature.of("DungeonTimers").section("Timers").hudForeground("dungeontimers", com.teslamaps.features.DungeonTimers::render);
        Feature.of("WitherDragons").when(() -> cfg().witherDragons).hud("dragontimer", com.teslamaps.dungeon.WitherDragons::renderHud);
        Feature.of("Splits").section("Score & Splits").hud("splits", com.teslamaps.dungeon.Splits::render);
        Feature.of("RoomSplits").section("Score & Splits").hud("roomsplits", com.teslamaps.dungeon.RoomSplits::render);
        Feature.of("WatcherAddons").section("Score & Splits").hud("watcherhud", com.teslamaps.dungeon.WatcherAddons::render);
        Feature.of("BloodCamp").section("Blood Camp").hud("bloodcamp", com.teslamaps.dungeon.BloodCamp::render);

        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original ->
                (ctx, delta) -> { if (!cfg().noEffects) original.extractRenderState(ctx, delta); });
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, original ->
                (ctx, delta) -> { if (!cfg().hideArmorBar) original.extractRenderState(ctx, delta); });
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original ->
                (ctx, delta) -> { if (!cfg().hideHungerBar) original.extractRenderState(ctx, delta); });
    }

    private static void world() {
        Feature.of("StarredMobESP").section("ESP").legit()
                .worldCtx(w -> StarredMobESP.renderWorldElements(w.pose(), w.buffers(), w.cam(), w.cam()));
        Feature.of("LividSolver").section("ESP").legit()
                .worldCtx(w -> LividSolver.renderWorld(w.pose(), w.buffers(), w.cam(), w.cam()));

        Feature.of("DungeonBlaze").section(PUZZLES).world(DungeonBlaze::render);
        Feature.of("ThreeWeirdos").section(PUZZLES).world(ThreeWeirdos::render);
        Feature.of("TicTacToe").section(PUZZLES).world(TicTacToe::render);
        Feature.of("BoulderSolver").section(PUZZLES).world(BoulderSolver::render);
        Feature.of("QuizSolver").section(PUZZLES).world(QuizSolver::render);
        Feature.of("TPMazeSolver").section(PUZZLES).world(TPMazeSolver::render);
        Feature.of("CreeperBeamsSolver").section(PUZZLES).world(CreeperBeamsSolver::render);
        Feature.of("WaterBoardSolver").section(PUZZLES).world(WaterBoardSolver::render);
        Feature.of("IceFillSolver").section(PUZZLES).world(com.teslamaps.dungeon.puzzle.IceFillSolver::render);
        Feature.of("IcePathSolver").section(PUZZLES).world(com.teslamaps.dungeon.puzzle.IcePathSolver::render);
        Feature.of("ArrowAlignSolver").section(PUZZLES).world(com.teslamaps.dungeon.puzzle.ArrowAlignSolver::render);
        Feature.of("TerracottaTimer").section(PUZZLES).world(com.teslamaps.dungeon.puzzle.TerracottaTimer::render);
        Feature.of("SimonSaysSolver").section(PUZZLES).world(com.teslamaps.dungeon.puzzle.SimonSaysSolver::render);

        Feature.of("WitherDragons").world(com.teslamaps.dungeon.WitherDragons::render);
        Feature.of("DragonESP").world(com.teslamaps.features.DragonESP::render);
        Feature.of("HighlightTeammates").section("ESP").legit().world(com.teslamaps.features.HighlightTeammates::render);
        Feature.of("SecretWaypoints").section("Secrets & Waypoints").world(SecretWaypoints::render);

        Feature.of("SecretClickHighlight").section("Secrets & Waypoints").world(com.teslamaps.features.SecretClickHighlight::render);
        Feature.of("ColorPortal").section("ESP").world(com.teslamaps.features.ColorPortal::render);
        Feature.of("HoppityEggESP").section("ESP").world(com.teslamaps.features.HoppityEggESP::render);
        Feature.of("HeadESP").section("ESP").world(com.teslamaps.features.HeadESP::render);

        Feature.of("SafariESP").section("Critter Safari").world(com.teslamaps.safari.SafariESP::render);
        Feature.of("EndProtector").section("The End").world(com.teslamaps.features.theend.EndProtector::render);
        Feature.of("MobVisuals").section("Mob Visuals").world(com.teslamaps.features.mobvisuals.MobVisualRender::render);

        Feature.of("CritterESP").section("Torrhus Canyon").world(com.teslamaps.features.CritterESP::render);
        Feature.of("TikiESP").section("Torrhus Canyon").world(com.teslamaps.features.TikiESP::render);
        Feature.of("QueenAntWarning").section("Torrhus Canyon").world(com.teslamaps.features.foraging.QueenAntWarning::render);

        Feature.of("GreenhousePlanner").section("Garden").world(com.teslamaps.features.GreenhousePlanner::render);

        Feature.of("HotspotRadar").section("Fishing").world(com.teslamaps.features.HotspotRadar::render);
        Feature.of("HotspotShare").section("Fishing").world(com.teslamaps.features.HotspotShare::render);
        Feature.of("ExplodingFrogESP").section("Fishing").world(com.teslamaps.features.ExplodingFrogESP::render);

        Feature.of("RescueMission").section("Crimson Isle").world(com.teslamaps.features.RescueMission::render);

        Feature.of("BlockOverlay").section("Render").world(com.teslamaps.features.BlockOverlay::render);
        Feature.of("ChatWaypoint").section("Render").world(com.teslamaps.features.ChatWaypoint::render);
        Feature.of("Etherwarp").section("Render").world(com.teslamaps.features.Etherwarp::render);
        Feature.of("NpcFinder").section("Render").world(com.teslamaps.features.npc.NpcFinder::render);

        Feature.of("SearchWaypoint").section("Inventory").world(com.teslamaps.features.SearchWaypoint::render);
        Feature.of("DungeonWaypoints").section("Secrets & Waypoints").world(com.teslamaps.dungeon.DungeonWaypoints::render);
        Feature.of("OrderedWaypoints").section("Ordered Waypoints").world(com.teslamaps.features.waypoints.OrderedWaypoints::render);
        Feature.of("PrinceWaypoints").section("Map").world(com.teslamaps.dungeon.PrinceWaypoints::render);

        Feature.of("BossESP").section("ESP").legit().world(com.teslamaps.esp.BossESP::render);
        Feature.of("CorpseESP").section("ESP").legit().world(com.teslamaps.esp.CorpseESP::render);
        Feature.of("M4Highlight").section("ESP").legit().world(com.teslamaps.features.M4Highlight::render);
        Feature.of("M4Trajectories").section("ESP").legit().world(com.teslamaps.features.M4Trajectories::render);
        Feature.of("M4Decoy").section("ESP").legit().world(com.teslamaps.features.M4Decoy::render);

        Feature.of("BloodCamp").section("Blood Camp").world(com.teslamaps.dungeon.BloodCamp::render);
        Feature.of("SlayerHUD").section("Combat").world(SlayerHUD::renderWorld);

        Feature.of("WorldScanner").section("Mining").world(com.teslamaps.features.WorldScanner::render);
        Feature.of("TikiESP").section("Mining").world(com.teslamaps.features.TikiESP::render);

        Feature.of("MimicChestESP").section("ESP").legit()
                .when(() -> DungeonManager.isInDungeon() && !MimicDetector.isMimicKilled()
                        && com.teslamaps.dungeon.DungeonScore.floorHasMimics())
                .world(CoreFeatures::renderMimicChestESP);
    }

    private static void ticks() {
        Feature.of("TooltipTestScreen").tickAlways(() -> com.teslamaps.debug.TooltipTestScreen.autoOpen(Minecraft.getInstance()));
        Feature.of("TickScheduler").tickAlways(com.teslamaps.utils.TickScheduler::tick);
        Feature.of("Customize").tickAlways(com.teslamaps.features.customize.Customize::tick);
        Feature.of("Updater").tickAlways(com.teslamaps.features.Updater::tick);
        Feature.of("GetFromSacks").tickAlways(com.teslamaps.features.GetFromSacks::tick);
        Feature.of("TerminalUtils").tickAlways(com.teslamaps.dungeon.terminals.TerminalUtils::onClientTick);

        Feature.of("DungeonManager").tick(DungeonManager::tick);
        Feature.of("RoomScanner").tick(RoomScanner::tick);
        Feature.of("PlayerTracker").tick(PlayerTracker::tick);
        Feature.of("MapScanner").tick(MapScanner::tick);
        Feature.of("StarredMobESP").section("ESP").tick(StarredMobESP::tick);
        Feature.of("CapeSync").section("Render").tick(com.teslamaps.features.capes.CapeSync::tick);
        Feature.of("CapeSimulation").section("Render").tick(com.teslamaps.features.capes.CapeSimulation::tick);
        Feature.of("GuiDump").tick(com.teslamaps.utils.GuiDump::tick);
        Feature.of("PestRoutine").section("Garden").tick(com.teslamaps.features.PestRoutine::tick);
        Feature.of("FarmProgress").section("Garden").tick(com.teslamaps.features.FarmProgress::tick);
        Feature.of("FarmProfit").section("Garden").tick(com.teslamaps.features.FarmProfit::tick);
        Feature.of("PestOverlay").section("Garden").tick(com.teslamaps.features.PestOverlay::tick);
        Feature.of("CharmedVisitors").section("Garden").tick(com.teslamaps.features.CharmedVisitors::tick);
        Feature.of("HarvestFeast").section("Garden").tick(com.teslamaps.features.HarvestFeast::tick);
        Feature.of("SixthVisitor").section("Garden").tick(com.teslamaps.features.SixthVisitor::tick);
        Feature.of("HuntingBox").section("Shards").tick(com.teslamaps.features.hunting.HuntingBox::tick);
        Feature.of("FarmingSensitivity").section("Garden").tick(com.teslamaps.features.FarmingSensitivity::tick);
        Feature.of("FarmingSensitivity").when(() -> !cfg().section("Garden")).tick(com.teslamaps.features.FarmingSensitivity::restore);
        Feature.of("SafariTracker").section("Critter Safari").tick(com.teslamaps.safari.SafariTracker::tick);
        Feature.of("SafariESP").section("Critter Safari").tick(com.teslamaps.safari.SafariESP::tick);
        Feature.of("EndProtector").section("The End").tick(com.teslamaps.features.theend.EndProtector::tick);
        Feature.of("MobVisuals").section("Mob Visuals").tick(com.teslamaps.features.mobvisuals.MobVisuals::tick);
        Feature.of("CritterESP").section("Torrhus Canyon").tick(com.teslamaps.features.CritterESP::tick);
        Feature.of("TikiESP").section("Torrhus Canyon").tick(com.teslamaps.features.TikiESP::tick);
        Feature.of("QueenAntWarning").section("Torrhus Canyon").tick(com.teslamaps.features.foraging.QueenAntWarning::tick);
        Feature.of("ChatTabs").tick(com.teslamaps.features.chattabs.ChatTabs::tick);
        Feature.of("PetTracker").section("Skills").tick(com.teslamaps.features.pets.PetTracker::tick);
        Feature.of("LassoDisplay").section("Foraging").tick(com.teslamaps.features.hunting.LassoDisplay::tick);
        Feature.of("OrderedWaypoints").section("Ordered Waypoints").tick(com.teslamaps.features.waypoints.OrderedWaypoints::tick);
        Feature.of("ExplodingFrogESP").section("Fishing").tick(com.teslamaps.features.ExplodingFrogESP::tick);
        Feature.of("HotspotShare").section("Fishing").tick(com.teslamaps.features.HotspotShare::tick);
        Feature.of("FishingBobberTimer").section("Fishing").tick(com.teslamaps.features.FishingBobberTimer::tick);
        Feature.of("FishingHookDisplay").section("Fishing").tick(com.teslamaps.features.FishingHookDisplay::tick);
        Feature.of("Deployables").when(() -> cfg().section("Skills") || cfg().section("Fishing")).tick(com.teslamaps.features.Deployables::tick);
        Feature.of("UmbrellaWarning").section("Fishing").tick(com.teslamaps.features.UmbrellaWarning::tick);
        Feature.of("HotspotRadar").section("Fishing").tick(com.teslamaps.features.HotspotRadar::tick);
        Feature.of("HotspotWatcher").section("Fishing").tick(com.teslamaps.features.HotspotWatcher::tick);
        Feature.of("RescueMission").section("Crimson Isle").tick(com.teslamaps.features.RescueMission::tick);
        Feature.of("PickaxeAbility").section("Mining").tick(com.teslamaps.features.mining.PickaxeAbility::tick);
        Feature.of("MineshaftPity").section("Mining").tick(com.teslamaps.features.mining.MineshaftPity::tick);
        Feature.of("HotmScan").section("Mining").tick(com.teslamaps.features.mining.HotmScan::tick);
        Feature.of("TranslucentLava").tick(com.teslamaps.features.TranslucentLava::tick);
        Feature.of("TranslucentWater").tick(com.teslamaps.features.TranslucentWater::tick);
        Feature.of("HoneyhiveReminder").section("Foraging").tick(com.teslamaps.features.foraging.HoneyhiveReminder::tick);
        Feature.of("LatherReminder").section("Foraging").tick(com.teslamaps.features.foraging.LatherReminder::tick);
        Feature.of("GreenhousePlanner").section("Garden").tick(com.teslamaps.features.GreenhousePlanner::tick);
        Feature.of("ContestTimers").section("Contest Timers").tick(com.teslamaps.features.ContestTimers::tick);
        Feature.of("SecretTracker").tick(SecretTracker::tick);
        Feature.of("SlayerHUD").section("Combat").tick(SlayerHUD::tick);
        Feature.of("SlayerAlerts").section("Combat").tick(com.teslamaps.slayer.SlayerAlerts::tick);
        Feature.of("SkillTracker").section("Skills").tick(com.teslamaps.features.SkillTracker::tick);
        Feature.of("BestiaryTracker").section("Combat").when(() -> cfg().bestiaryTracker).tick(com.teslamaps.features.BestiaryTracker::tick);
        Feature.of("FlareMode").section("Skills").when(() -> cfg().flareMode).tick(com.teslamaps.features.hunting.FlareMode::tick);
        Feature.of("AutoGFS").section("Auto").legit().tick(AutoGFS::tick);
        Feature.of("AutoWish").section("Auto").legit().tick(AutoWish::tick);
        Feature.of("LastBreathSound").section("Auto").tick(com.teslamaps.features.LastBreathSound::tick);
        Feature.of("TimerTriggers").section("Timers").tick(com.teslamaps.features.TimerTriggers::tick);
        Feature.of("SpiritPetReminder").section("Timers").tick(com.teslamaps.features.SpiritPetReminder::tick);
        Feature.of("ColorPortal").section("ESP").tick(com.teslamaps.features.ColorPortal::tick);
        Feature.of("HoppityEggESP").section("ESP").tick(com.teslamaps.features.HoppityEggESP::tick);
        Feature.of("HeadESP").section("ESP").tick(com.teslamaps.features.HeadESP::tick);
        Feature.of("BearSpawnWarning").section("Sounds").tick(BearSpawnWarning::tick);
        Feature.of("KeybindMessage").tick(KeybindMessage::tick);
        Feature.of("Zoom").section("Render").tick(com.teslamaps.features.Zoom::tick);
        Feature.of("LegitMode").tick(com.teslamaps.features.LegitMode::tick);
        Feature.of("LegitGuess").tick(com.teslamaps.map.LegitGuess::tick);
        Feature.of("LeapOverlay").section("Leap").tick(com.teslamaps.features.LeapOverlay::tick);
        Feature.of("MelodyMessage").section(PUZZLES).tick(com.teslamaps.features.MelodyMessage::tick);
        Feature.of("KickedTimer").tick(com.teslamaps.features.KickedTimer::tick);
        Feature.of("PerformanceHud").tick(com.teslamaps.features.PerformanceHud::tick);
        Feature.of("OverkillCounter").tick(com.teslamaps.features.OverkillCounter::tick);
        Feature.of("M4Mobs").section("ESP").tick(com.teslamaps.dungeon.M4Mobs::tick);
        Feature.of("M4Highlight").section("ESP").tick(com.teslamaps.features.M4Highlight::tick);
        Feature.of("M4Trajectories").section("ESP").tick(com.teslamaps.features.M4Trajectories::tick);
        Feature.of("M4Decoy").section("ESP").tick(com.teslamaps.features.M4Decoy::tick);
        Feature.of("BloodCamp").section("Blood Camp").tick(com.teslamaps.dungeon.BloodCamp::tick);
        Feature.of("WorldScanner").section("Mining").tick(com.teslamaps.features.WorldScanner::tick);
        Feature.of("AutoRequeue").section("Party").tick(com.teslamaps.dungeon.AutoRequeue::tick);
        Feature.of("LividSolver").section("ESP").legit().tick(LividSolver::tick);
        Feature.of("MimicDetector").tick(MimicDetector::tick);
        Feature.of("PartyDuplicateAlert").section("Party").tick(com.teslamaps.features.PartyDuplicateAlert::tick);
        Feature.of("DungeonBlaze").section(PUZZLES).tick(DungeonBlaze::tick);
        Feature.of("ThreeWeirdos").section(PUZZLES).tick(ThreeWeirdos::tick);
        Feature.of("TicTacToe").section(PUZZLES).tick(TicTacToe::tick);
        Feature.of("ArrowAlignSolver").section(PUZZLES).tick(com.teslamaps.dungeon.puzzle.ArrowAlignSolver::tick);
        Feature.of("BoulderSolver").section(PUZZLES).tick(BoulderSolver::tick);
        Feature.of("QuizSolver").section(PUZZLES).tick(QuizSolver::tick);
        Feature.of("TPMazeSolver").section(PUZZLES).tick(TPMazeSolver::tick);
        Feature.of("CreeperBeamsSolver").section(PUZZLES).tick(CreeperBeamsSolver::tick);
        Feature.of("SpiritBearTimer").section(PUZZLES).tick(SpiritBearTimer::tick);
        Feature.of("TerracottaTimer").section(PUZZLES).tick(com.teslamaps.dungeon.puzzle.TerracottaTimer::tick);
        Feature.of("SimonSaysSolver").section(PUZZLES).tick(com.teslamaps.dungeon.puzzle.SimonSaysSolver::tick);
        Feature.of("ThornStunTimer").section(PUZZLES).tick(com.teslamaps.features.ThornStunTimer::tick);
        Feature.of("CustomTitles").section(PUZZLES).tick(com.teslamaps.features.CustomTitles::tick);
        Feature.of("WaterBoardSolver").section(PUZZLES).tick(WaterBoardSolver::tick);
        Feature.of("IceFillSolver").section(PUZZLES).tick(com.teslamaps.dungeon.puzzle.IceFillSolver::tick);
        Feature.of("IcePathSolver").section(PUZZLES).tick(com.teslamaps.dungeon.puzzle.IcePathSolver::tick);
        Feature.of("PuzzleTimers").section(PUZZLES).tick(com.teslamaps.dungeon.puzzle.PuzzleTimers::tick);
        Feature.of("CombatTimers").section("Timers").tick(com.teslamaps.features.CombatTimers::tick);
        Feature.of("BackpackPreview").section("Inventory").tick(com.teslamaps.features.BackpackPreview::tick);
        Feature.of("ItemIndex").section("Inventory").tick(com.teslamaps.features.ItemIndex::tick);
        Feature.of("AuctionPriceCompare").section("Inventory").tick(com.teslamaps.features.AuctionPriceCompare::tick);
        Feature.of("WitherDragons").when(() -> cfg().witherDragons).tick(com.teslamaps.dungeon.WitherDragons::tick);
        Feature.of("SecretWaypoints").section("Secrets & Waypoints").tick(SecretWaypoints::tick);
        Feature.of("Splits").section("Score & Splits").tick(com.teslamaps.dungeon.Splits::tick);
        Feature.of("InstaClearAlert").section("Score & Splits").tick(com.teslamaps.dungeon.InstaClearAlert::tick);
        Feature.of("RoomSplits").section("Score & Splits").tick(com.teslamaps.dungeon.RoomSplits::tick);
        Feature.of("WatcherAddons").section("Score & Splits").tick(com.teslamaps.dungeon.WatcherAddons::tick);
    }

    private static void renderMimicChestESP(PoseStack matrices, Vec3 cameraPos) {
        if (!cfg().mimicChestESP) return;

        var mimicRooms = MimicDetector.getMimicRooms();
        if (mimicRooms.isEmpty()) return;

        int boxColor = 0xFFFF0000;  // Red
        int tracerColor = 0xFFFF6666;  // Light red

        for (BlockPos pos : MimicDetector.getTrappedChestPositions()) {
            int[] gridPos = com.teslamaps.scanner.ComponentGrid.worldToGrid(pos.getX(), pos.getZ());
            if (gridPos == null) continue;

            var room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
            if (room == null || !mimicRooms.contains(room)) continue;

            AABB chestBox = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);

            ESPRenderer.drawESPBox(matrices, chestBox, boxColor, cameraPos);

            if (cfg().mimicChestTracers) {
                Vec3 chestCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ESPRenderer.drawTracerFromCamera(matrices, chestCenter, tracerColor, cameraPos);
            }
        }
    }
}
