package com.teslamaps;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.command.TMapCommand;
import com.teslamaps.debug.Perf;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.dungeon.puzzle.DungeonBlaze;
import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.TicTacToe;
import com.teslamaps.dungeon.puzzle.BoulderSolver;
import com.teslamaps.dungeon.puzzle.QuizSolver;
import com.teslamaps.dungeon.puzzle.TPMazeSolver;
import com.teslamaps.dungeon.puzzle.CreeperBeamsSolver;
import com.teslamaps.dungeon.puzzle.SpiritBearTimer;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.AutoWish;
import com.teslamaps.features.LividSolver;
import com.teslamaps.features.SecretWaypoints;
import com.teslamaps.features.BearSpawnWarning;
import com.teslamaps.features.KeybindMessage;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.MapRenderer;
import com.teslamaps.render.TeslaRenderLayers;
import com.teslamaps.render.TeslaRenderPipelines;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.scanner.RoomScanner;
import com.teslamaps.scanner.SecretTracker;
import com.teslamaps.slayer.SlayerHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.teslamaps.render.ESPRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeslaMaps implements ClientModInitializer {
    public static final String MOD_ID = "teslamaps";
    public static final Logger LOGGER = LoggerFactory.getLogger("TeslaMaps");

    /** Solver / feature tracing. Silent unless /tmap debug is on. */
    public static void debug(String msg, Object... args) {
        if (TeslaMapsConfig.get().debugMode) LOGGER.info(msg, args);
    }

    private static TeslaMaps instance;

    /**
     * Registers one of our overlays. Attaching in front of PLAYER_LIST puts it *under* the tab
     * list, which is what you want while holding TAB - {@code addLast} drew it over the top.
     * Going through here also guarantees the Perf wrapper, so nothing stays invisible to
     * {@code /tmap perf}.
     */
    private static void hud(String name, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement element) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath(MOD_ID, name), Perf.hud(name, element));
    }

    /** true while the panel belongs to the HUD pass; false hands it to HudForeground. */
    private static boolean hudPass() {
        return !com.teslamaps.features.HudForeground.active();
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        long initStart = System.nanoTime();

        TeslaMapsConfig.load();
        com.teslamaps.features.StorageCache.load();
        com.teslamaps.features.ItemIndex.load();
        com.teslamaps.utils.SkyblockItems.load();
        com.teslamaps.utils.SackItems.load();
        com.teslamaps.utils.SeaCreatureList.load();
        com.teslamaps.features.Updater.init();
        com.teslamaps.features.customize.Customize.init();
        com.teslamaps.screen.customize.CustomizeButton.register();
        com.teslamaps.screen.ContestTimersButton.register();

        com.teslamaps.render.gui.ImageBackgrounds.init();
        com.teslamaps.features.capes.CapeManager.init();
        TeslaRenderPipelines.init();
        TeslaRenderLayers.init();
        com.teslamaps.render.gui.TeslaVertexFormats.init();
        com.teslamaps.render.gui.TeslaGuiPipelines.init();
        com.teslamaps.render.gui.ChromaPipelines.init();
        com.teslamaps.features.TranslucentLava.registerFluidModel();
        com.teslamaps.features.TranslucentWater.registerFluidModel();

        RoomDatabase.getInstance().load();

        com.teslamaps.dungeon.DungeonWaypoints.load();
        com.teslamaps.dungeon.PrinceWaypoints.load();
        com.teslamaps.features.waypoints.RouteStorage.load();
        com.teslamaps.features.GreenhousePlanner.loadStored();

        com.teslamaps.features.AuctionPriceInput.init();
        com.teslamaps.features.SearchOverlay.init();

        net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(data ->
                data instanceof com.teslamaps.features.CompactorPreview.Contents c
                        ? new com.teslamaps.render.gui.CompactorTooltip(c) : null);

        ClientCommandRegistrationCallback.EVENT.register(TMapCommand::register);

        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register(TMapCommand::expandShortcut);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(
                com.teslamaps.features.GetFromSacks::allowCommand);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(
                com.teslamaps.features.PestTimer::onCommandSent);

        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, ctx, flag, lines) -> {
                    com.teslamaps.features.EnchantColors.onTooltip(stack, lines);
                    if (TeslaMapsConfig.get().section("Skills")) com.teslamaps.features.SkillOverflow.onTooltip(stack, lines);
                });

        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(MOD_ID, "darkmode_under"), com.teslamaps.debug.Perf.hud("darkmode_under", (g, d) -> com.teslamaps.features.DarkMode.renderUnderHud(g)));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "darkmode_over"), com.teslamaps.debug.Perf.hud("darkmode_over", (g, d) -> com.teslamaps.features.DarkMode.renderOverHud(g)));

        // hudPass() false = inventory open, HudForeground draws this panel over the screen instead
        hud("map", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Map")) MapRenderer.render(g, d); });
        hud("skilltracker", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Skills")) com.teslamaps.features.SkillTracker.render(g, d); });
        hud("bestiary", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Combat")) com.teslamaps.features.BestiaryTracker.render(g, d); });
        hud("livid", (g, d) -> { if (TeslaMapsConfig.get().section("ESP")) LividSolver.renderHUD(g, d); });
        hud("spiritbear", (g, d) -> { if (TeslaMapsConfig.get().section("Puzzles & Terminals")) SpiritBearTimer.render(g, d); });
        hud("kicked", (g, d) -> com.teslamaps.features.KickedTimer.render(g, d));
        hud("raredrop", (g, d) -> { if (TeslaMapsConfig.get().section("Render")) com.teslamaps.features.RareDropEffects.render(g, d); });
        hud("pestroutine", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Garden")) com.teslamaps.features.PestRoutine.render(g, d); });
        hud("farmbar", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Garden")) com.teslamaps.features.FarmProgress.render(g, d); });
        hud("pestoverlay", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Garden")) com.teslamaps.features.PestOverlay.render(g, d); });
        hud("farmprofit", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Garden")) com.teslamaps.features.FarmProfitOverlay.render(g, d); });
        hud("charmedvisitors", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Garden")) com.teslamaps.features.CharmedVisitors.render(g, d); });
        hud("overkill", (g, d) -> { if (TeslaMapsConfig.get().section("Timers")) com.teslamaps.features.OverkillCounter.render(g, d); });
        hud("bearspawn", (g, d) -> { if (TeslaMapsConfig.get().section("Sounds")) BearSpawnWarning.render(g, d); });
        hud("thornstun", (g, d) -> { if (TeslaMapsConfig.get().section("Puzzles & Terminals")) com.teslamaps.features.ThornStunTimer.render(g, d); });
        hud("sprinting", (g, d) -> { if (TeslaMapsConfig.get().section("Render")) com.teslamaps.features.SprintingOverlay.render(g, d); });
        hud("safari", (g, d) -> { if (TeslaMapsConfig.get().section("Critter Safari")) com.teslamaps.safari.SafariTracker.render(g, d); });
        hud("hotfperks", (g, d) -> { if (TeslaMapsConfig.get().section("Foraging")) com.teslamaps.features.foraging.HotfPerkDisplay.render(g, d); });
        hud("lasso", (g, d) -> { if (TeslaMapsConfig.get().section("Foraging")) com.teslamaps.features.hunting.LassoDisplay.render(g, d); });
        hud("pickaxeability", (g, d) -> { if (TeslaMapsConfig.get().section("Mining")) com.teslamaps.features.mining.PickaxeAbility.render(g, d); });
        hud("mineshaftpity", (g, d) -> { if (TeslaMapsConfig.get().section("Mining")) com.teslamaps.features.mining.MineshaftPity.render(g, d); });
        hud("lather", (g, d) -> { if (TeslaMapsConfig.get().section("Foraging")) com.teslamaps.features.foraging.LatherOverlay.render(g, d); });
        hud("contesttimers", (g, d) -> { if (TeslaMapsConfig.get().section("Contest Timers")) com.teslamaps.features.ContestTimers.render(g, d); });
        hud("bobbertimer", (g, d) -> { if (TeslaMapsConfig.get().section("Fishing")) com.teslamaps.features.FishingBobberTimer.render(g, d); });
        hud("hookdisplay", (g, d) -> { if (TeslaMapsConfig.get().section("Fishing")) com.teslamaps.features.FishingHookDisplay.render(g, d); });
        hud("chatimages", (g, d) -> com.teslamaps.features.SharedImageChatRenderer.render(g, d));
        hud("deployables", (g, d) -> { if (TeslaMapsConfig.get().section("Skills")) com.teslamaps.features.Deployables.render(g, d); });
        hud("dungeontimers", (g, d) -> { if (hudPass() && TeslaMapsConfig.get().section("Timers")) com.teslamaps.features.DungeonTimers.render(g, d); });
        hud("dragontimer", (g, d) -> { if (TeslaMapsConfig.get().witherDragons) com.teslamaps.dungeon.WitherDragons.renderHud(g, d); });
        hud("splits", (g, d) -> { if (TeslaMapsConfig.get().section("Score & Splits")) com.teslamaps.dungeon.Splits.render(g, d); });
        hud("roomsplits", (g, d) -> { if (TeslaMapsConfig.get().section("Score & Splits")) com.teslamaps.dungeon.RoomSplits.render(g, d); });
        hud("watcherhud", (g, d) -> { if (TeslaMapsConfig.get().section("Score & Splits")) com.teslamaps.dungeon.WatcherAddons.render(g, d); });
        hud("bloodcamp", (g, d) -> { if (TeslaMapsConfig.get().section("Blood Camp")) com.teslamaps.dungeon.BloodCamp.render(g, d); });

        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original ->
                (ctx, delta) -> { if (!TeslaMapsConfig.get().noEffects) original.extractRenderState(ctx, delta); });

        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, original ->
                (ctx, delta) -> { if (!TeslaMapsConfig.get().hideArmorBar) original.extractRenderState(ctx, delta); });

        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original ->
                (ctx, delta) -> { if (!TeslaMapsConfig.get().hideHungerBar) original.extractRenderState(ctx, delta); });

        StarredMobESP.init();

        AutoGFS.init();

        com.teslamaps.features.WorldScanner.init();

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
                Vec3 playerEyePos = cameraPos;
                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) {
                    Perf.begin("StarredMobESP");
                    StarredMobESP.renderWorldElements(
                            context.poseStack(),
                            context.bufferSource(),
                            cameraPos,
                            playerEyePos
                    );
                    Perf.end();
                    Perf.begin("LividSolver");
                    LividSolver.renderWorld(
                            context.poseStack(),
                            context.bufferSource(),
                            cameraPos,
                            playerEyePos
                    );
                    Perf.end();
                }

                if (TeslaMapsConfig.get().section("Puzzles & Terminals")) {
                    Perf.begin("DungeonBlaze"); DungeonBlaze.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("ThreeWeirdos"); ThreeWeirdos.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("TicTacToe"); TicTacToe.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("BoulderSolver"); BoulderSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("QuizSolver"); QuizSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("TPMazeSolver"); TPMazeSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("CreeperBeamsSolver"); CreeperBeamsSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("WaterBoardSolver"); WaterBoardSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("IceFillSolver"); com.teslamaps.dungeon.puzzle.IceFillSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("IcePathSolver"); com.teslamaps.dungeon.puzzle.IcePathSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("ArrowAlignSolver"); com.teslamaps.dungeon.puzzle.ArrowAlignSolver.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("TerracottaTimer"); com.teslamaps.dungeon.puzzle.TerracottaTimer.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("SimonSaysSolver"); com.teslamaps.dungeon.puzzle.SimonSaysSolver.render(context.poseStack(), cameraPos); Perf.end();
                }
                Perf.begin("WitherDragons"); com.teslamaps.dungeon.WitherDragons.render(context.poseStack(), cameraPos); Perf.end();
                Perf.begin("DragonESP"); com.teslamaps.features.DragonESP.render(context.poseStack(), cameraPos); Perf.end();
                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) {
                    Perf.begin("HighlightTeammates"); com.teslamaps.features.HighlightTeammates.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Secrets & Waypoints")) {
                    Perf.begin("SecretWaypoints"); SecretWaypoints.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("ESP")) {
                    Perf.begin("SecretClickHighlight"); com.teslamaps.features.SecretClickHighlight.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("ColorPortal"); com.teslamaps.features.ColorPortal.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("HoppityEggESP"); com.teslamaps.features.HoppityEggESP.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("HeadESP"); com.teslamaps.features.HeadESP.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Critter Safari")) {
                    Perf.begin("SafariESP"); com.teslamaps.safari.SafariESP.render(context.poseStack(), cameraPos); Perf.end();
                }
                if (TeslaMapsConfig.get().section("Torrhus Canyon")) {
                    Perf.begin("CritterESP"); com.teslamaps.features.CritterESP.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("TikiESP"); com.teslamaps.features.TikiESP.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Garden")) {
                    Perf.begin("GreenhousePlanner"); com.teslamaps.features.GreenhousePlanner.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Fishing")) {
                    Perf.begin("HotspotRadar"); com.teslamaps.features.HotspotRadar.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("ExplodingFrogESP"); com.teslamaps.features.ExplodingFrogESP.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Render")) {
                    Perf.begin("BlockOverlay"); com.teslamaps.features.BlockOverlay.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("ChatWaypoint"); com.teslamaps.features.ChatWaypoint.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("Etherwarp"); com.teslamaps.features.Etherwarp.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Inventory")) {
                    Perf.begin("SearchWaypoint"); com.teslamaps.features.SearchWaypoint.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Secrets & Waypoints")) {
                    Perf.begin("DungeonWaypoints"); com.teslamaps.dungeon.DungeonWaypoints.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Ordered Waypoints")) {
                    Perf.begin("OrderedWaypoints"); com.teslamaps.features.waypoints.OrderedWaypoints.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Map")) {
                    Perf.begin("PrinceWaypoints"); com.teslamaps.dungeon.PrinceWaypoints.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) {
                    Perf.begin("BossESP"); com.teslamaps.esp.BossESP.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("CorpseESP"); com.teslamaps.esp.CorpseESP.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("M4Highlight"); com.teslamaps.features.M4Highlight.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("M4Trajectories"); com.teslamaps.features.M4Trajectories.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("M4Decoy"); com.teslamaps.features.M4Decoy.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Blood Camp")) {
                    Perf.begin("BloodCamp"); com.teslamaps.dungeon.BloodCamp.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Combat")) {
                    Perf.begin("SlayerHUD"); SlayerHUD.renderWorld(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("Mining")) {
                    Perf.begin("WorldScanner"); com.teslamaps.features.WorldScanner.render(context.poseStack(), cameraPos); Perf.end();
                    Perf.begin("TikiESP"); com.teslamaps.features.TikiESP.render(context.poseStack(), cameraPos); Perf.end();
                }

                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()
                        && DungeonManager.isInDungeon() && !MimicDetector.isMimicKilled() && com.teslamaps.dungeon.DungeonScore.floorHasMimics()) {
                    Perf.begin("MimicChestESP"); renderMimicChestESP(context.poseStack(), cameraPos); Perf.end();
                }
            }
        });

        // drop the per-tick entity snapshot; it rebuilds lazily on the first caller
        ClientTickEvents.START_CLIENT_TICK.register(client -> com.teslamaps.utils.EntityCache.invalidate());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Perf.begin("TickScheduler.tick"); com.teslamaps.utils.TickScheduler.tick(); Perf.end();
            com.teslamaps.features.customize.Customize.tick();
            Perf.begin("Updater.tick"); com.teslamaps.features.Updater.tick(); Perf.end();
            Perf.begin("GetFromSacks.tick"); com.teslamaps.features.GetFromSacks.tick(); Perf.end();
            Perf.begin("TerminalUtils.tick"); com.teslamaps.dungeon.terminals.TerminalUtils.onClientTick(); Perf.end();
            if (client.player != null && client.level != null) {
                TeslaMapsConfig cfg = TeslaMapsConfig.get();
                Perf.begin("DungeonManager.tick"); DungeonManager.tick(); Perf.end();
                Perf.begin("RoomScanner.tick"); RoomScanner.tick(); Perf.end();
                Perf.begin("PlayerTracker.tick"); PlayerTracker.tick(); Perf.end();
                Perf.begin("MapScanner.tick"); MapScanner.tick(); Perf.end();
                if (cfg.section("ESP")) { Perf.begin("StarredMobESP.tick"); StarredMobESP.tick(); Perf.end(); }
                if (cfg.section("Render")) { Perf.begin("CapeSync.tick"); com.teslamaps.features.capes.CapeSync.tick(); Perf.end(); }
                if (cfg.section("Render")) { Perf.begin("CapeSimulation.tick"); com.teslamaps.features.capes.CapeSimulation.tick(); Perf.end(); }
                Perf.begin("GuiDump.tick"); com.teslamaps.utils.GuiDump.tick(); Perf.end();
                if (cfg.section("Garden")) { Perf.begin("PestRoutine.tick"); com.teslamaps.features.PestRoutine.tick(); Perf.end(); }
                if (cfg.section("Garden")) { Perf.begin("FarmProgress.tick"); com.teslamaps.features.FarmProgress.tick(); Perf.end(); }
                if (cfg.section("Garden")) { Perf.begin("FarmProfit.tick"); com.teslamaps.features.FarmProfit.tick(); Perf.end(); }
                if (cfg.section("Garden")) { Perf.begin("PestOverlay.tick"); com.teslamaps.features.PestOverlay.tick(); Perf.end(); }
                if (cfg.section("Garden")) { Perf.begin("CharmedVisitors.tick"); com.teslamaps.features.CharmedVisitors.tick(); Perf.end(); }
                if (cfg.section("Shards")) { Perf.begin("HuntingBox.tick"); com.teslamaps.features.hunting.HuntingBox.tick(); Perf.end(); }
                if (cfg.section("Garden")) { Perf.begin("FarmingSensitivity.tick"); com.teslamaps.features.FarmingSensitivity.tick(); Perf.end(); }
                else { Perf.begin("FarmingSensitivity.tick"); com.teslamaps.features.FarmingSensitivity.restore(); Perf.end(); }
                if (cfg.section("Critter Safari")) {
                    Perf.begin("SafariTracker.tick"); com.teslamaps.safari.SafariTracker.tick(); Perf.end();
                    Perf.begin("SafariESP.tick"); com.teslamaps.safari.SafariESP.tick(); Perf.end();
                }
                if (cfg.section("Torrhus Canyon")) {
                    Perf.begin("CritterESP.tick"); com.teslamaps.features.CritterESP.tick(); Perf.end();
                    Perf.begin("TikiESP.tick"); com.teslamaps.features.TikiESP.tick(); Perf.end();
                }
                if (cfg.section("Foraging")) { Perf.begin("LassoDisplay.tick"); com.teslamaps.features.hunting.LassoDisplay.tick(); Perf.end(); }
                if (cfg.section("Ordered Waypoints")) { Perf.begin("OrderedWaypoints.tick"); com.teslamaps.features.waypoints.OrderedWaypoints.tick(); Perf.end(); }
                if (cfg.section("Fishing")) { Perf.begin("ExplodingFrogESP.tick"); com.teslamaps.features.ExplodingFrogESP.tick(); Perf.end(); }
                if (cfg.section("Fishing")) { Perf.begin("FishingBobberTimer.tick"); com.teslamaps.features.FishingBobberTimer.tick(); Perf.end(); }
                if (cfg.section("Fishing")) { Perf.begin("FishingHookDisplay.tick"); com.teslamaps.features.FishingHookDisplay.tick(); Perf.end(); }
                if (cfg.section("Skills") || cfg.section("Fishing")) { Perf.begin("Deployables.tick"); com.teslamaps.features.Deployables.tick(); Perf.end(); }
                if (cfg.section("Fishing")) { Perf.begin("UmbrellaWarning.tick"); com.teslamaps.features.UmbrellaWarning.tick(); Perf.end(); }
                if (cfg.section("Fishing")) { Perf.begin("HotspotRadar.tick"); com.teslamaps.features.HotspotRadar.tick(); Perf.end(); }
                if (cfg.section("Fishing")) { Perf.begin("HotspotWatcher.tick"); com.teslamaps.features.HotspotWatcher.tick(); Perf.end(); }
                if (cfg.section("Mining")) {
                    Perf.begin("PickaxeAbility.tick"); com.teslamaps.features.mining.PickaxeAbility.tick(); Perf.end();
                    Perf.begin("MineshaftPity.tick"); com.teslamaps.features.mining.MineshaftPity.tick(); Perf.end();
                    Perf.begin("HotmScan.tick"); com.teslamaps.features.mining.HotmScan.tick(); Perf.end();
                }
                Perf.begin("TranslucentLava.tick"); com.teslamaps.features.TranslucentLava.tick(); Perf.end();
                Perf.begin("TranslucentWater.tick"); com.teslamaps.features.TranslucentWater.tick(); Perf.end();
                if (cfg.section("Foraging")) {
                    Perf.begin("HoneyhiveReminder.tick"); com.teslamaps.features.foraging.HoneyhiveReminder.tick(); Perf.end();
                    Perf.begin("LatherReminder.tick"); com.teslamaps.features.foraging.LatherReminder.tick(); Perf.end();
                }
                if (cfg.section("Garden")) { Perf.begin("GreenhousePlanner.tick"); com.teslamaps.features.GreenhousePlanner.tick(); Perf.end(); }
                if (cfg.section("Contest Timers")) { Perf.begin("ContestTimers.tick"); com.teslamaps.features.ContestTimers.tick(); Perf.end(); }
                Perf.begin("SecretTracker.tick"); SecretTracker.tick(); Perf.end();
                if (cfg.section("Combat")) { Perf.begin("SlayerHUD.tick"); SlayerHUD.tick(); Perf.end(); }
                if (cfg.section("Combat")) { Perf.begin("SlayerAlerts.tick"); com.teslamaps.slayer.SlayerAlerts.tick(); Perf.end(); }
                if (cfg.section("Skills")) { Perf.begin("SkillTracker.tick"); com.teslamaps.features.SkillTracker.tick(); Perf.end(); }
                if (cfg.section("Combat") && cfg.bestiaryTracker) { Perf.begin("BestiaryTracker.tick"); com.teslamaps.features.BestiaryTracker.tick(); Perf.end(); }
                if (cfg.section("Auto")) {
                    if (!com.teslamaps.features.LegitMode.blocksCheats()) {
                        Perf.begin("AutoGFS.tick"); AutoGFS.tick(); Perf.end();
                        Perf.begin("AutoWish.tick"); AutoWish.tick(); Perf.end();
                    }
                    Perf.begin("LastBreathSound.tick"); com.teslamaps.features.LastBreathSound.tick(); Perf.end();
                }
                if (cfg.section("Timers")) { Perf.begin("TimerTriggers.tick"); com.teslamaps.features.TimerTriggers.tick(); Perf.end(); }
                if (cfg.section("Timers")) { Perf.begin("SpiritPetReminder.tick"); com.teslamaps.features.SpiritPetReminder.tick(); Perf.end(); }
                if (cfg.section("ESP")) { Perf.begin("ColorPortal.tick"); com.teslamaps.features.ColorPortal.tick(); Perf.end(); }
                if (cfg.section("ESP")) { Perf.begin("HoppityEggESP.tick"); com.teslamaps.features.HoppityEggESP.tick(); Perf.end(); }
                if (cfg.section("ESP")) { Perf.begin("HeadESP.tick"); com.teslamaps.features.HeadESP.tick(); Perf.end(); }
                if (cfg.section("Sounds")) { Perf.begin("BearSpawnWarning.tick"); BearSpawnWarning.tick(); Perf.end(); }
                Perf.begin("KeybindMessage.tick"); KeybindMessage.tick(); Perf.end();
                if (cfg.section("Render")) { Perf.begin("Zoom.tick"); com.teslamaps.features.Zoom.tick(); Perf.end(); }
                Perf.begin("LegitMode.tick"); com.teslamaps.features.LegitMode.tick(); Perf.end();
                Perf.begin("LegitGuess.tick"); com.teslamaps.map.LegitGuess.tick(); Perf.end();
                if (cfg.section("Leap")) { Perf.begin("LeapOverlay.tick"); com.teslamaps.features.LeapOverlay.tick(); Perf.end(); }
                if (cfg.section("Puzzles & Terminals")) { Perf.begin("MelodyMessage.tick"); com.teslamaps.features.MelodyMessage.tick(); Perf.end(); }
                Perf.begin("KickedTimer.tick"); com.teslamaps.features.KickedTimer.tick(); Perf.end();
                Perf.begin("OverkillCounter.tick"); com.teslamaps.features.OverkillCounter.tick(); Perf.end();
                if (cfg.section("ESP")) {
                    Perf.begin("M4Mobs.tick"); com.teslamaps.dungeon.M4Mobs.tick(); Perf.end();
                    Perf.begin("M4Highlight.tick"); com.teslamaps.features.M4Highlight.tick(); Perf.end();
                    Perf.begin("M4Trajectories.tick"); com.teslamaps.features.M4Trajectories.tick(); Perf.end();
                    Perf.begin("M4Decoy.tick"); com.teslamaps.features.M4Decoy.tick(); Perf.end();
                }
                if (cfg.section("Blood Camp")) { Perf.begin("BloodCamp.tick"); com.teslamaps.dungeon.BloodCamp.tick(); Perf.end(); }
                if (cfg.section("Mining")) { Perf.begin("WorldScanner.tick"); com.teslamaps.features.WorldScanner.tick(); Perf.end(); }
                if (cfg.section("Party")) { Perf.begin("AutoRequeue.tick"); com.teslamaps.dungeon.AutoRequeue.tick(); Perf.end(); }
                if (cfg.section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) { Perf.begin("LividSolver.tick"); LividSolver.tick(); Perf.end(); }
                Perf.begin("MimicDetector.tick"); MimicDetector.tick(); Perf.end();
                if (cfg.section("Party")) { Perf.begin("PartyDuplicateAlert.tick"); com.teslamaps.features.PartyDuplicateAlert.tick(); Perf.end(); }
                if (cfg.section("Puzzles & Terminals")) {
                Perf.begin("DungeonBlaze.tick"); DungeonBlaze.tick(); Perf.end();
                Perf.begin("ThreeWeirdos.tick"); ThreeWeirdos.tick(); Perf.end();
                Perf.begin("TicTacToe.tick"); TicTacToe.tick(); Perf.end();
                Perf.begin("ArrowAlignSolver.tick"); com.teslamaps.dungeon.puzzle.ArrowAlignSolver.tick(); Perf.end();
                Perf.begin("BoulderSolver.tick"); BoulderSolver.tick(); Perf.end();
                Perf.begin("QuizSolver.tick"); QuizSolver.tick(); Perf.end();
                Perf.begin("TPMazeSolver.tick"); TPMazeSolver.tick(); Perf.end();
                Perf.begin("CreeperBeamsSolver.tick"); CreeperBeamsSolver.tick(); Perf.end();
                Perf.begin("SpiritBearTimer.tick"); SpiritBearTimer.tick(); Perf.end();
                Perf.begin("TerracottaTimer.tick"); com.teslamaps.dungeon.puzzle.TerracottaTimer.tick(); Perf.end();
                Perf.begin("SimonSaysSolver.tick"); com.teslamaps.dungeon.puzzle.SimonSaysSolver.tick(); Perf.end();
                Perf.begin("ThornStunTimer.tick"); com.teslamaps.features.ThornStunTimer.tick(); Perf.end();
                Perf.begin("CustomTitles.tick"); com.teslamaps.features.CustomTitles.tick(); Perf.end();
                Perf.begin("WaterBoardSolver.tick"); WaterBoardSolver.tick(); Perf.end();
                Perf.begin("IceFillSolver.tick"); com.teslamaps.dungeon.puzzle.IceFillSolver.tick(); Perf.end();
                Perf.begin("IcePathSolver.tick"); com.teslamaps.dungeon.puzzle.IcePathSolver.tick(); Perf.end();
                Perf.begin("PuzzleTimers.tick"); com.teslamaps.dungeon.puzzle.PuzzleTimers.tick(); Perf.end();
                }
                if (cfg.section("Timers")) { Perf.begin("CombatTimers.tick"); com.teslamaps.features.CombatTimers.tick(); Perf.end(); }
                if (cfg.section("Inventory")) { Perf.begin("BackpackPreview.tick"); com.teslamaps.features.BackpackPreview.tick(); Perf.end(); }
                if (cfg.section("Inventory")) { Perf.begin("ItemIndex.tick"); com.teslamaps.features.ItemIndex.tick(); Perf.end(); }
                if (cfg.section("Inventory")) { Perf.begin("AuctionPriceCompare.tick"); com.teslamaps.features.AuctionPriceCompare.tick(); Perf.end(); }
                if (cfg.witherDragons) { Perf.begin("WitherDragons.tick"); com.teslamaps.dungeon.WitherDragons.tick(); Perf.end(); }
                if (cfg.section("Secrets & Waypoints")) { Perf.begin("SecretWaypoints.tick"); SecretWaypoints.tick(); Perf.end(); }
                if (cfg.section("Score & Splits")) {
                    Perf.begin("Splits.tick"); com.teslamaps.dungeon.Splits.tick(); Perf.end();
                    Perf.begin("InstaClearAlert.tick"); com.teslamaps.dungeon.InstaClearAlert.tick(); Perf.end();
                    Perf.begin("RoomSplits.tick"); com.teslamaps.dungeon.RoomSplits.tick(); Perf.end();
                    Perf.begin("WatcherAddons.tick"); com.teslamaps.dungeon.WatcherAddons.tick(); Perf.end();
                }
            }
        });

        LOGGER.info("init took {} ms", (System.nanoTime() - initStart) / 1_000_000);
    }

    public static TeslaMaps getInstance() {
        return instance;
    }

    private static void renderMimicChestESP(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().mimicChestESP) return;

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

            if (TeslaMapsConfig.get().mimicChestTracers) {
                Vec3 chestCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ESPRenderer.drawTracerFromCamera(matrices, chestCenter, tracerColor, cameraPos);
            }
        }
    }
}
