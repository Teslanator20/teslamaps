package com.teslamaps;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.command.TMapCommand;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.dungeon.puzzle.DungeonBlaze;
import com.teslamaps.dungeon.puzzle.StartsWithTerminal;
import com.teslamaps.dungeon.puzzle.SelectAllTerminal;
import com.teslamaps.dungeon.puzzle.ClickInOrderTerminal;
import com.teslamaps.dungeon.puzzle.CorrectPanesTerminal;
import com.teslamaps.dungeon.puzzle.MelodyTerminal;
import com.teslamaps.dungeon.puzzle.RubixTerminal;
import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.TicTacToe;
import com.teslamaps.dungeon.puzzle.ChronomatronSolver;
import com.teslamaps.dungeon.puzzle.SuperpairsSolver;
import com.teslamaps.dungeon.puzzle.UltrasequencerSolver;
import com.teslamaps.dungeon.puzzle.BoulderSolver;
import com.teslamaps.dungeon.puzzle.QuizSolver;
import com.teslamaps.dungeon.puzzle.TPMazeSolver;
import com.teslamaps.dungeon.puzzle.CreeperBeamsSolver;
import com.teslamaps.dungeon.puzzle.SimonSaysSolver;
import com.teslamaps.dungeon.puzzle.ArrowAlignSolver;
import com.teslamaps.dungeon.puzzle.TerracottaTimer;
import com.teslamaps.dungeon.puzzle.SpiritBearTimer;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.AutoWish;
import com.teslamaps.features.LividSolver;
import com.teslamaps.features.SecretClicker;
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

    private static TeslaMaps instance;

    @Override
    public void onInitializeClient() {
        instance = this;

        // Load config
        TeslaMapsConfig.load();

        // Initialize render pipelines (must be early)
        TeslaRenderPipelines.init();
        TeslaRenderLayers.init();

        // Load room database
        RoomDatabase.getInstance().load();

        // Load Odin-format dungeon waypoints
        com.teslamaps.dungeon.DungeonWaypoints.load();

        // Register /tmap command
        ClientCommandRegistrationCallback.EVENT.register(TMapCommand::register);

        // Register keybind that sends a configurable chat message (/tmap msg <text>)
        KeybindMessage.register();

        // Register HUD elements (26.1.2: HudRenderCallback -> HudElementRegistry)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "map"), MapRenderer::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "slayer"), SlayerHUD::render);
        // HUD indicators removed - using 3D tracers instead
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "livid"), LividSolver::renderHUD);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "spiritbear"), SpiritBearTimer::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "waterboard"), WaterBoardSolver::renderHud);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "bearspawn"), BearSpawnWarning::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "splits"), com.teslamaps.dungeon.Splits::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "bloodcamp"), com.teslamaps.dungeon.BloodCamp::render);

        // Initialize starred mob ESP
        StarredMobESP.init();

        // Initialize AutoGFS
        AutoGFS.init();

        // Register world render event for 3D tracers/beacons
        // 26.1.2: WorldRenderEvents.AFTER_ENTITIES -> LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
                Vec3 playerEyePos = cameraPos;
                StarredMobESP.renderWorldElements(
                        context.poseStack(),
                        context.bufferSource(),
                        cameraPos,
                        playerEyePos
                );
                LividSolver.renderWorld(
                        context.poseStack(),
                        context.bufferSource(),
                        cameraPos,
                        playerEyePos
                );

                // Render puzzle solvers
                DungeonBlaze.render(context.poseStack(), cameraPos);
                ThreeWeirdos.render(context.poseStack(), cameraPos);
                TicTacToe.render(context.poseStack(), cameraPos);
                BoulderSolver.render(context.poseStack(), cameraPos);
                QuizSolver.render(context.poseStack(), cameraPos);
                TPMazeSolver.render(context.poseStack(), cameraPos);
                CreeperBeamsSolver.render(context.poseStack(), cameraPos);
                SimonSaysSolver.render(context.poseStack(), cameraPos);
                ArrowAlignSolver.render(context.poseStack(), cameraPos);
                TerracottaTimer.render(context.poseStack(), cameraPos);
                WaterBoardSolver.render(context.poseStack(), cameraPos);

                // Render secret waypoints
                SecretWaypoints.render(context.poseStack(), cameraPos);

                // Etherwarp guess box
                com.teslamaps.features.Etherwarp.render(context.poseStack(), cameraPos);

                // Odin-format dungeon waypoints
                com.teslamaps.dungeon.DungeonWaypoints.render(context.poseStack(), cameraPos);

                // Render mimic trapped chest ESP (only on F6, F7, M6, M7)
                if (DungeonManager.isInDungeon() && !MimicDetector.isMimicKilled() && com.teslamaps.dungeon.DungeonScore.floorHasMimics()) {
                    renderMimicChestESP(context.poseStack(), cameraPos);
                }
            }
        });

        // Register tick event for dungeon detection and scanning
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null) {
                DungeonManager.tick();
                RoomScanner.tick();
                PlayerTracker.tick();
                MapScanner.tick();
                StarredMobESP.tick();
                SecretTracker.tick();
                SlayerHUD.tick();
                AutoGFS.tick();
                AutoWish.tick();
                SecretClicker.tick();
                BearSpawnWarning.tick();
                KeybindMessage.tick();
                com.teslamaps.dungeon.BloodCamp.tick();
                LividSolver.tick();
                MimicDetector.tick();
                DungeonBlaze.tick();
                ThreeWeirdos.tick();
                TicTacToe.tick();
                StartsWithTerminal.tick();
                SelectAllTerminal.tick();
                ClickInOrderTerminal.tick();
                CorrectPanesTerminal.tick();
                MelodyTerminal.tick();
                RubixTerminal.tick();
                // Experiment solvers (Superpairs table)
                ChronomatronSolver.tick();
                SuperpairsSolver.tick();
                UltrasequencerSolver.tick();
                // New puzzle solvers
                BoulderSolver.tick();
                QuizSolver.tick();
                TPMazeSolver.tick();
                CreeperBeamsSolver.tick();
                SimonSaysSolver.tick();
                ArrowAlignSolver.tick();
                TerracottaTimer.tick();
                SpiritBearTimer.tick();
                WaterBoardSolver.tick();
                SecretWaypoints.tick();
            }
        });
    }

    public static TeslaMaps getInstance() {
        return instance;
    }

    /**
     * Render ESP for trapped chests (only in actual mimic room).
     */
    private static void renderMimicChestESP(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().mimicChestESP) return;

        // Only render chests in the actual mimic room(s)
        var mimicRooms = MimicDetector.getMimicRooms();
        if (mimicRooms.isEmpty()) return;

        int boxColor = 0xFFFF0000;  // Red
        int tracerColor = 0xFFFF6666;  // Light red

        for (BlockPos pos : MimicDetector.getTrappedChestPositions()) {
            // Check if this chest is in a mimic room
            int[] gridPos = com.teslamaps.scanner.ComponentGrid.worldToGrid(pos.getX(), pos.getZ());
            if (gridPos == null) continue;

            var room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
            if (room == null || !mimicRooms.contains(room)) continue;

            // Create box around the chest block
            AABB chestBox = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);

            ESPRenderer.drawESPBox(matrices, chestBox, boxColor, cameraPos);

            // Draw tracer to chest
            if (TeslaMapsConfig.get().mimicChestTracers) {
                Vec3 chestCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ESPRenderer.drawTracerFromCamera(matrices, chestCenter, tracerColor, cameraPos);
            }
        }
    }
}
