/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.command.TMapCommand;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.dungeon.puzzle.DungeonBlaze;
// import com.teslamaps.dungeon.puzzle.StartsWithTerminal;
// import com.teslamaps.dungeon.puzzle.SelectAllTerminal;
// import com.teslamaps.dungeon.puzzle.ClickInOrderTerminal;
// import com.teslamaps.dungeon.puzzle.CorrectPanesTerminal;
// import com.teslamaps.dungeon.puzzle.MelodyTerminal;
// import com.teslamaps.dungeon.puzzle.RubixTerminal;
import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.TicTacToe;
// import com.teslamaps.dungeon.puzzle.ChronomatronSolver;
// import com.teslamaps.dungeon.puzzle.SuperpairsSolver;
// import com.teslamaps.dungeon.puzzle.UltrasequencerSolver;
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

    private static TeslaMaps instance;

    @Override
    public void onInitializeClient() {
        instance = this;

        TeslaMapsConfig.load();
        com.teslamaps.features.StorageCache.load();

        TeslaRenderPipelines.init();
        TeslaRenderLayers.init();

        RoomDatabase.getInstance().load();

        com.teslamaps.dungeon.DungeonWaypoints.load();
        com.teslamaps.dungeon.PrinceWaypoints.load();

        ClientCommandRegistrationCallback.EVENT.register(TMapCommand::register);

        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register(TMapCommand::expandShortcut);

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "map"), (g, d) -> { if (TeslaMapsConfig.get().section("Map")) MapRenderer.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "slayer"), (g, d) -> { if (TeslaMapsConfig.get().section("Slayer")) SlayerHUD.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "livid"), (g, d) -> { if (TeslaMapsConfig.get().section("ESP")) LividSolver.renderHUD(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "spiritbear"), (g, d) -> { if (TeslaMapsConfig.get().section("Puzzles")) SpiritBearTimer.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "bearspawn"), (g, d) -> { if (TeslaMapsConfig.get().section("Sounds")) BearSpawnWarning.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "thornstun"), (g, d) -> { if (TeslaMapsConfig.get().section("Puzzles")) com.teslamaps.features.ThornStunTimer.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "sprinting"), (g, d) -> { if (TeslaMapsConfig.get().section("Render")) com.teslamaps.features.SprintingOverlay.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "dungeontimers"), (g, d) -> { if (TeslaMapsConfig.get().section("Timers")) com.teslamaps.features.DungeonTimers.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "splits"), (g, d) -> { if (TeslaMapsConfig.get().section("Score & Splits")) com.teslamaps.dungeon.Splits.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "roomsplits"), (g, d) -> { if (TeslaMapsConfig.get().section("Score & Splits")) com.teslamaps.dungeon.RoomSplits.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "watcherhud"), (g, d) -> { if (TeslaMapsConfig.get().section("Score & Splits")) com.teslamaps.dungeon.WatcherAddons.render(g, d); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "bloodcamp"), (g, d) -> { if (TeslaMapsConfig.get().section("Blood Camp")) com.teslamaps.dungeon.BloodCamp.render(g, d); });

        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original ->
                (ctx, delta) -> { if (!TeslaMapsConfig.get().noEffects) original.extractRenderState(ctx, delta); });

        StarredMobESP.init();

        AutoGFS.init();

        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
                Vec3 playerEyePos = cameraPos;
                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) {
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
                }

                if (TeslaMapsConfig.get().section("Puzzles")) {
                    DungeonBlaze.render(context.poseStack(), cameraPos);
                    ThreeWeirdos.render(context.poseStack(), cameraPos);
                    TicTacToe.render(context.poseStack(), cameraPos);
                    BoulderSolver.render(context.poseStack(), cameraPos);
                    QuizSolver.render(context.poseStack(), cameraPos);
                    TPMazeSolver.render(context.poseStack(), cameraPos);
                    CreeperBeamsSolver.render(context.poseStack(), cameraPos);
                    WaterBoardSolver.render(context.poseStack(), cameraPos);
                    com.teslamaps.dungeon.puzzle.IceFillSolver.render(context.poseStack(), cameraPos);
                    com.teslamaps.dungeon.puzzle.IcePathSolver.render(context.poseStack(), cameraPos);
                }
                if (TeslaMapsConfig.get().section("Dragons")) {
                    com.teslamaps.dungeon.WitherDragons.render(context.poseStack(), cameraPos);
                    com.teslamaps.features.DragonESP.render(context.poseStack(), cameraPos);
                }
                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) {
                    com.teslamaps.features.HighlightTeammates.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("Waypoints")) {
                    SecretWaypoints.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("ESP")) {
                    com.teslamaps.features.SecretClickHighlight.render(context.poseStack(), cameraPos);
                    com.teslamaps.features.ColorPortal.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("Render")) {
                    com.teslamaps.features.BlockOverlay.render(context.poseStack(), cameraPos);
                    com.teslamaps.features.ChatWaypoint.render(context.poseStack(), cameraPos);
                    com.teslamaps.features.Etherwarp.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("Waypoints")) {
                    com.teslamaps.dungeon.DungeonWaypoints.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("Map")) {
                    com.teslamaps.dungeon.PrinceWaypoints.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) {
                    com.teslamaps.esp.BossESP.render(context.poseStack(), cameraPos);
                    com.teslamaps.esp.CorpseESP.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("Blood Camp")) {
                    com.teslamaps.dungeon.BloodCamp.render(context.poseStack(), cameraPos);
                }

                if (TeslaMapsConfig.get().section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()
                        && DungeonManager.isInDungeon() && !MimicDetector.isMimicKilled() && com.teslamaps.dungeon.DungeonScore.floorHasMimics()) {
                    renderMimicChestESP(context.poseStack(), cameraPos);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null) {
                TeslaMapsConfig cfg = TeslaMapsConfig.get();
                DungeonManager.tick();
                RoomScanner.tick();
                PlayerTracker.tick();
                MapScanner.tick();
                if (cfg.section("ESP")) StarredMobESP.tick();
                SecretTracker.tick();
                if (cfg.section("Slayer")) SlayerHUD.tick();
                if (cfg.section("Auto")) {
                    if (!com.teslamaps.features.LegitMode.blocksCheats()) {
                        AutoGFS.tick();
                        AutoWish.tick();
                    }
                    com.teslamaps.features.LastBreathSound.tick();
                }
                if (cfg.section("Timers")) com.teslamaps.features.TimerTriggers.tick();
                if (cfg.section("Timers")) com.teslamaps.features.SpiritPetReminder.tick();
                if (cfg.section("ESP")) com.teslamaps.features.ColorPortal.tick();
                if (!com.teslamaps.features.LegitMode.blocksCheats()) SecretClicker.tick();
                if (cfg.section("Sounds")) BearSpawnWarning.tick();
                KeybindMessage.tick();
                if (cfg.section("Render")) com.teslamaps.features.Zoom.tick();
                com.teslamaps.features.LegitMode.tick();
                com.teslamaps.map.LegitGuess.tick();
                if (cfg.section("Blood Camp")) com.teslamaps.dungeon.BloodCamp.tick();
                if (cfg.section("Party")) com.teslamaps.dungeon.AutoRequeue.tick();
                if (cfg.section("ESP") && !com.teslamaps.features.LegitMode.blocksCheats()) LividSolver.tick();
                MimicDetector.tick();
                if (cfg.section("Party")) com.teslamaps.features.PartyDuplicateAlert.tick();
                if (cfg.section("Puzzles")) {
                DungeonBlaze.tick();
                ThreeWeirdos.tick();
                TicTacToe.tick();
                // replaced by features.TerminalSolver (per-frame highlight); kept for reference
                // StartsWithTerminal.tick();
                // SelectAllTerminal.tick();
                // ClickInOrderTerminal.tick();
                // CorrectPanesTerminal.tick();
                // MelodyTerminal.tick();
                // RubixTerminal.tick();
                // experimentation table solvers, disabled for now
                // ChronomatronSolver.tick();
                // SuperpairsSolver.tick();
                // UltrasequencerSolver.tick();
                BoulderSolver.tick();
                QuizSolver.tick();
                TPMazeSolver.tick();
                CreeperBeamsSolver.tick();
                SpiritBearTimer.tick();
                com.teslamaps.features.ThornStunTimer.tick();
                com.teslamaps.features.CustomTitles.tick();
                WaterBoardSolver.tick();
                com.teslamaps.dungeon.puzzle.IceFillSolver.tick();
                com.teslamaps.dungeon.puzzle.IcePathSolver.tick();
                com.teslamaps.dungeon.puzzle.PuzzleTimers.tick();
                }
                if (cfg.section("Timers")) com.teslamaps.features.CombatTimers.tick();
                if (cfg.section("Inventory")) com.teslamaps.features.BackpackPreview.tick();
                if (cfg.section("Dragons")) com.teslamaps.dungeon.WitherDragons.tick();
                if (cfg.section("Waypoints")) SecretWaypoints.tick();
                if (cfg.section("Score & Splits")) {
                    com.teslamaps.dungeon.Splits.tick();
                    com.teslamaps.dungeon.InstaClearAlert.tick();
                    com.teslamaps.dungeon.RoomSplits.tick();
                    com.teslamaps.dungeon.WatcherAddons.tick();
                }
            }
        });
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
