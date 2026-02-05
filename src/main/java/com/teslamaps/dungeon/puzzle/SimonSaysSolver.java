package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simon Says Solver for F7/M7 Phase 3.
 * Tracks the button sequence and shows which buttons to click.
 */
public class SimonSaysSolver {

    private static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);

    // Grid of button positions (4x4)
    private static final Set<BlockPos> GRID = Set.of(
        new BlockPos(110, 123, 92), new BlockPos(110, 123, 93), new BlockPos(110, 123, 94), new BlockPos(110, 123, 95),
        new BlockPos(110, 122, 92), new BlockPos(110, 122, 93), new BlockPos(110, 122, 94), new BlockPos(110, 122, 95),
        new BlockPos(110, 121, 92), new BlockPos(110, 121, 93), new BlockPos(110, 121, 94), new BlockPos(110, 121, 95),
        new BlockPos(110, 120, 92), new BlockPos(110, 120, 93), new BlockPos(110, 120, 94), new BlockPos(110, 120, 95)
    );

    private static final List<BlockPos> clickSequence = new ArrayList<>();
    private static int clickIndex = 0;
    private static boolean firstPhase = true;
    private static int lastLanternTick = -1;
    private static int startClickCount = 0;
    private static long lastTickTime = 0;

    public static void tick() {
        if (!TeslaMapsConfig.get().solveSimonSays) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) {
            reset();
            return;
        }

        // Only active in F7/M7 phase 3 (Goldor)
        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F7") && !floor.contains("M7"))) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Check if player is near Simon Says device
        BlockPos playerPos = mc.player.getBlockPos();
        if (playerPos.getSquaredDistance(110, 121, 91) > 400) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTickTime < 50) return;
        lastTickTime = now;

        // Track lantern changes to sea lantern -> obsidian (sequence display)
        if (lastLanternTick >= 0) {
            lastLanternTick++;
            if (lastLanternTick > 10) {
                // Check if grid has enough buttons (pattern reset)
                int buttonCount = 0;
                for (BlockPos pos : GRID) {
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.STONE_BUTTON) {
                        buttonCount++;
                    }
                }
                if (buttonCount > 8) {
                    TeslaMaps.LOGGER.info("[SimonSays] Grid reset detected");
                    firstPhase = false;
                    startClickCount = 0;
                }
            }
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().solveSimonSays) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // Start button pressed - reset sequence
        if (pos.equals(START_BUTTON)) {
            if (newState.getBlock() == Blocks.STONE_BUTTON &&
                newState.contains(Properties.POWERED) && newState.get(Properties.POWERED)) {
                clickSequence.clear();
                clickIndex = 0;
                firstPhase = true;
                lastLanternTick = -1;
                TeslaMaps.LOGGER.info("[SimonSays] Start button pressed, resetting");
            }
            return;
        }

        // Check for lantern display (x=111, behind the buttons)
        if (pos.getX() == 111 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95) {
            if (oldState.getBlock() == Blocks.SEA_LANTERN && newState.getBlock() == Blocks.OBSIDIAN) {
                // Lantern turned off - add to sequence
                if (!clickSequence.contains(pos)) {
                    clickSequence.add(pos.toImmutable());
                    lastLanternTick = 0;
                    TeslaMaps.LOGGER.info("[SimonSays] Added to sequence: {} (total: {})", pos, clickSequence.size());

                    // First phase correction
                    if (firstPhase) {
                        if (clickSequence.size() == 2) {
                            // Reverse first two
                            BlockPos first = clickSequence.get(0);
                            clickSequence.set(0, clickSequence.get(1));
                            clickSequence.set(1, first);
                        } else if (clickSequence.size() == 3) {
                            // Remove second-to-last
                            clickSequence.remove(1);
                        }
                    }
                }
            }
        }

        // Check for button press (x=110)
        if (pos.getX() == 110 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95) {
            if (oldState.getBlock() == Blocks.STONE_BUTTON &&
                newState.contains(Properties.POWERED) && newState.get(Properties.POWERED)) {
                // Button pressed - advance click index
                BlockPos lanternPos = pos.east(); // x+1 is the lantern position
                int index = clickSequence.indexOf(lanternPos);
                if (index >= 0) {
                    clickIndex = index + 1;
                    if (clickIndex >= clickSequence.size()) {
                        clickSequence.clear();
                        clickIndex = 0;
                        firstPhase = false;
                        TeslaMaps.LOGGER.info("[SimonSays] Round complete!");
                    }
                }
            } else if (newState.isAir()) {
                // Button removed (puzzle reset)
                clickSequence.clear();
                clickIndex = 0;
            }
        }
    }

    public static void onChatMessage(String message) {
        if (message.equals("[BOSS] Goldor: Who dares trespass into my domain?")) {
            startClickCount = 0;
        }
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveSimonSays) return;
        if (clickSequence.isEmpty() || clickIndex >= clickSequence.size()) return;

        int firstColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSimonSaysFirst);
        int secondColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSimonSaysSecond);
        int thirdColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSimonSaysThird);

        for (int i = clickIndex; i < clickSequence.size(); i++) {
            BlockPos lanternPos = clickSequence.get(i);
            // Button is at x-1 from lantern
            BlockPos buttonPos = lanternPos.west();

            int color = switch (i - clickIndex) {
                case 0 -> firstColor;
                case 1 -> secondColor;
                default -> thirdColor;
            };

            // Draw box around button
            Box box = new Box(
                buttonPos.getX() + 0.05, buttonPos.getY() + 0.37, buttonPos.getZ() + 0.3,
                buttonPos.getX() - 0.15, buttonPos.getY() + 0.63, buttonPos.getZ() + 0.7
            );
            ESPRenderer.drawESPBox(matrices, box, color, cameraPos);
        }
    }

    public static void reset() {
        clickSequence.clear();
        clickIndex = 0;
        firstPhase = true;
        lastLanternTick = -1;
        startClickCount = 0;
    }
}
