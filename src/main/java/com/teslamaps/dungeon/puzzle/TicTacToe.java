package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.utils.TicTacToeUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * TicTacToe puzzle solver - shows the best move.
 * Tic Tac Toe puzzle solver - shows optimal move.
 */
public class TicTacToe {
    private static Box nextBestMoveBox = null;
    private static DungeonRoom currentRoom = null;

    // Room-relative coordinates for the tic-tac-toe board 
    // Y: 72=row0, 71=row1, 70=row2
    // Z: 17=col0, 16=col1, 15=col2
    // X: 8 (constant)

    public static void tick() {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveTicTacToe) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            reset();
            return;
        }

        try {
            // Search for item frames with maps within 21 blocks
            Box searchBox = mc.player.getBoundingBox().expand(21);
            List<ItemFrameEntity> itemFrames = mc.world.getEntitiesByClass(
                    ItemFrameEntity.class,
                    searchBox,
                    frame -> {
                        ItemStack stack = frame.getHeldItemStack();
                        return !stack.isEmpty() && stack.getItem() instanceof FilledMapItem;
                    }
            );

            // Need at least 1 frame to find the room, and odd number (but not 9) for player's turn
            if (itemFrames.isEmpty() || itemFrames.size() == 9 || (itemFrames.size() & 1) == 0) {
                nextBestMoveBox = null;
                return;
            }

            // Find room from first frame
            BlockPos firstFramePos = itemFrames.get(0).getBlockPos();
            int[] gridPos = ComponentGrid.worldToGrid(firstFramePos.getX(), firstFramePos.getZ());
            if (gridPos == null) return;

            currentRoom = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
            if (currentRoom == null) return;

            // Get room corner and rotation
            int cornerX = currentRoom.getCornerX();
            int cornerZ = currentRoom.getCornerZ();
            int rotation = currentRoom.getRotation();
            if (rotation < 0) return; // Rotation not detected yet

            // Collect all frame positions and their map colors
            Map<BlockPos, Character> frameData = new HashMap<>();
            for (ItemFrameEntity frame : itemFrames) {
                BlockPos pos = frame.getBlockPos();
                ItemStack stack = frame.getHeldItemStack();
                MapState mapState = FilledMapItem.getMapState(stack, mc.world);
                if (mapState == null) continue;

                byte[] colors = mapState.colors;
                if (colors != null && colors.length > 8256) {
                    int middleColor = colors[8256] & 0xFF;
                    if (middleColor == 114) {
                        frameData.put(pos, 'X');
                    } else if (middleColor == 33) {
                        frameData.put(pos, 'O');
                    }
                }
            }

            if (frameData.isEmpty()) return;

            // Convert world positions to room-relative and build board
            char[][] board = new char[3][3];
            Map<String, BlockPos> boardToWorld = new HashMap<>();

            for (Map.Entry<BlockPos, Character> entry : frameData.entrySet()) {
                BlockPos worldPos = entry.getKey();
                char symbol = entry.getValue();

                // Convert to room-relative coordinates
                int[] relative = worldToRelative(cornerX, cornerZ, rotation, worldPos);

                int relX = relative[0];
                int relY = relative[1];
                int relZ = relative[2];

                // Map relative coords to board indices
                // Y: 72=row0, 71=row1, 70=row2
                int row = 72 - relY;
                // Z: 17=col0, 16=col1, 15=col2
                int col = 17 - relZ;

                TeslaMaps.LOGGER.debug("[TicTacToe] Frame {} at world {} -> relative ({},{},{}) -> row={}, col={}",
                    symbol, worldPos, relX, relY, relZ, row, col);

                if (row < 0 || row > 2 || col < 0 || col > 2) continue;

                board[row][col] = symbol;
                boardToWorld.put(row + "," + col, worldPos);
            }

            // Calculate best move
            TicTacToeUtils.BoardIndex bestMove = TicTacToeUtils.getBestMove(board);

            // Get world position for best move
            String key = bestMove.row() + "," + bestMove.column();
            BlockPos existingPos = boardToWorld.get(key);

            BlockPos worldPos;
            if (existingPos != null) {
                worldPos = existingPos;
            } else {
                // Convert board indices back to world position
                int relY = 72 - bestMove.row();
                int relZ = 17 - bestMove.column();
                int relX = 8; // X is constant in relative space 

                worldPos = relativeToWorld(cornerX, cornerZ, rotation, relX, relY, relZ);
            }

            // Offset 0.5 blocks away from player (opposite of item frame facing direction)
            Direction facing = itemFrames.get(0).getHorizontalFacing();
            double offsetX = -facing.getOffsetX() * 0.5;
            double offsetZ = -facing.getOffsetZ() * 0.5;

            nextBestMoveBox = new Box(
                worldPos.getX() + offsetX, worldPos.getY(), worldPos.getZ() + offsetZ,
                worldPos.getX() + 1 + offsetX, worldPos.getY() + 1, worldPos.getZ() + 1 + offsetZ
            );

        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[TicTacToe] Error", e);
            reset();
        }
    }

    /**
     * Rotate coordinates by given degrees by degree.
     * 0° -> (x, z), 90° -> (z, -x), 180° -> (-x, -z), 270° -> (-z, x)
     */
    private static int[] rotatePos(int x, int z, int degree) {
        return switch (degree % 360) {
            case 0 -> new int[]{x, z};
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    /**
     * Convert world position to room-relative coordinates.
     */
    private static int[] worldToRelative(int cornerX, int cornerZ, int rotation, BlockPos worldPos) {
        // Get offset from corner
        int dx = worldPos.getX() - cornerX;
        int dz = worldPos.getZ() - cornerZ;

        // Rotate by rotation degrees to get relative coords
        int[] rotated = rotatePos(dx, dz, rotation);
        return new int[]{rotated[0], worldPos.getY(), rotated[1]};
    }

    /**
     * Convert room-relative coordinates to world position.
     */
    private static BlockPos relativeToWorld(int cornerX, int cornerZ, int rotation, int relX, int relY, int relZ) {
        // Rotate by (360 - rotation) to convert back to world
        int[] rotated = rotatePos(relX, relZ, (360 - rotation) % 360);
        return new BlockPos(cornerX + rotated[0], relY, cornerZ + rotated[1]);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveTicTacToe || nextBestMoveBox == null) {
            return;
        }

        try {
            double centerX = (nextBestMoveBox.minX + nextBestMoveBox.maxX) / 2;
            double centerY = (nextBestMoveBox.minY + nextBestMoveBox.maxY) / 2;
            double centerZ = (nextBestMoveBox.minZ + nextBestMoveBox.maxZ) / 2;

            // Small button-sized box
            Box buttonBox = new Box(
                centerX - 0.2, centerY - 0.15, centerZ - 0.1,
                centerX + 0.2, centerY + 0.15, centerZ + 0.1
            );

            ESPRenderer.drawFilledBox(matrices, buttonBox, 0x8000FF00, cameraPos);
            ESPRenderer.drawBoxOutline(matrices, buttonBox, 0xFF00FF00, 3.0f, cameraPos);
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[TicTacToe] Error rendering", e);
        }
    }

    public static void reset() {
        nextBestMoveBox = null;
        currentRoom = null;
    }
}
