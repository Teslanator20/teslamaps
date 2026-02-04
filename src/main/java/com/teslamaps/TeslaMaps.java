package com.teslamaps;

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
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.LividSolver;
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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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

        // Register /tmap command
        ClientCommandRegistrationCallback.EVENT.register(TMapCommand::register);

        // Register HUD render callback
        HudRenderCallback.EVENT.register(MapRenderer::render);
        HudRenderCallback.EVENT.register(SlayerHUD::render);
        // HUD indicators removed - using 3D tracers instead
        HudRenderCallback.EVENT.register(LividSolver::renderHUD);

        // Initialize starred mob ESP
        StarredMobESP.init();

        // Initialize AutoGFS
        AutoGFS.init();

        // Register world render event for 3D tracers/beacons
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (context.consumers() != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
                Vec3d playerEyePos = cameraPos;
                StarredMobESP.renderWorldElements(
                        context.matrices(),
                        context.consumers(),
                        cameraPos,
                        playerEyePos
                );
                LividSolver.renderWorld(
                        context.matrices(),
                        context.consumers(),
                        cameraPos,
                        playerEyePos
                );

                // Render puzzle solvers
                DungeonBlaze.render(context.matrices(), cameraPos);
                ThreeWeirdos.render(context.matrices(), cameraPos);
                TicTacToe.render(context.matrices(), cameraPos);

                // Render mimic trapped chest ESP (only on F6, F7, M6, M7)
                if (DungeonManager.isInDungeon() && !MimicDetector.isMimicKilled() && com.teslamaps.dungeon.DungeonScore.floorHasMimics()) {
                    renderMimicChestESP(context.matrices(), cameraPos);
                }
            }
        });

        // Register tick event for dungeon detection and scanning
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                DungeonManager.tick();
                RoomScanner.tick();
                PlayerTracker.tick();
                MapScanner.tick();
                StarredMobESP.tick();
                SecretTracker.tick();
                SlayerHUD.tick();
                AutoGFS.tick();
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
            }
        });
    }

    public static TeslaMaps getInstance() {
        return instance;
    }

    /**
     * Render ESP for trapped chests (only in actual mimic room).
     */
    private static void renderMimicChestESP(MatrixStack matrices, Vec3d cameraPos) {
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
            Box chestBox = new Box(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);

            ESPRenderer.drawESPBox(matrices, chestBox, boxColor, cameraPos);

            // Draw tracer to chest
            if (TeslaMapsConfig.get().mimicChestTracers) {
                Vec3d chestCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ESPRenderer.drawTracerFromCamera(matrices, chestCenter, tracerColor, cameraPos);
            }
        }
    }
}
