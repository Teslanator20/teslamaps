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
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * TicTacToe puzzle solver - shows the best move.
 * Uses frame positions to auto-detect puzzle orientation.
 */
public class TicTacToe {
    private static Box nextBestMoveBox = null;
    private static DungeonRoom currentRoom = null;

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

            // Build board from frames - try to detect which coordinates map to which rows/cols
            char[][] board = new char[3][3];
            BoardMapping mapping = null;

            for (ItemFrameEntity frame : itemFrames) {
                BlockPos framePos = frame.getBlockPos();
                BlockPos relative = currentRoom.actualToRelative(framePos);

                if (mapping == null) {
                    // Auto-detect mapping from first frame position
                    mapping = detectMapping(relative);
                    TeslaMaps.LOGGER.info("[TicTacToe] Detected mapping from frame at relative {}: {}", relative, mapping);
                }

                // Get board position using detected mapping
                int[] boardPos = mapping.getPosition(relative);
                if (boardPos == null) continue;

                int row = boardPos[0];
                int col = boardPos[1];

                // Get map state and read pixel color
                ItemStack stack = frame.getHeldItemStack();
                MapState mapState = FilledMapItem.getMapState(stack, mc.world);
                if (mapState == null) continue;

                byte[] colors = mapState.colors;
                if (colors != null && colors.length > 8256) {
                    int middleColor = colors[8256] & 0xFF;
                    if (middleColor == 114) {
                        board[row][col] = 'X';
                    } else if (middleColor == 33) {
                        board[row][col] = 'O';
                    }
                }
            }

            if (mapping == null) return;

            // Calculate best move
            TicTacToeUtils.BoardIndex bestMove = TicTacToeUtils.getBestMove(board);

            // Convert to world position
            BlockPos relativePos = mapping.getWorldPos(bestMove.row(), bestMove.column());
            BlockPos worldPos = currentRoom.relativeToActual(relativePos);

            nextBestMoveBox = new Box(worldPos);
            TeslaMaps.LOGGER.info("[TicTacToe] Best move: [{},{}] -> world {}", bestMove.row(), bestMove.column(), worldPos);

        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[TicTacToe] Error", e);
            reset();
        }
    }

    /**
     * Auto-detect which wall the puzzle is on based on frame coordinates.
     */
    private static BoardMapping detectMapping(BlockPos relative) {
        int x = relative.getX();
        int y = relative.getY();
        int z = relative.getZ();

        // Try each orientation and return the first one where this position is valid
        BoardMapping[] mappings = {
            new NorthMapping(),
            new EastMapping(),
            new SouthMapping(),
            new WestMapping()
        };

        for (BoardMapping mapping : mappings) {
            int[] pos = mapping.getPosition(relative);
            if (pos != null) {
                return mapping;
            }
        }

        // Default to North if nothing matched
        TeslaMaps.LOGGER.warn("[TicTacToe] No mapping matched for {}, defaulting to North", relative);
        return new NorthMapping();
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveTicTacToe || nextBestMoveBox == null) {
            return;
        }

        try {
            ESPRenderer.drawFilledBox(matrices, nextBestMoveBox, 0x8000FF00, cameraPos);
            ESPRenderer.drawBoxOutline(matrices, nextBestMoveBox, 0xFF00FF00, 5.0f, cameraPos);
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[TicTacToe] Error rendering", e);
        }
    }

    public static void reset() {
        nextBestMoveBox = null;
        currentRoom = null;
    }

    // Coordinate mapping interface
    private interface BoardMapping {
        int[] getPosition(BlockPos relative);  // Returns [row, col] or null
        BlockPos getWorldPos(int row, int col);  // Returns relative position for board position
    }

    // North wall (Z near 0) - X=8, Z=17/16/15
    private static class NorthMapping implements BoardMapping {
        public int[] getPosition(BlockPos rel) {
            int row = switch (rel.getY()) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int col = switch (rel.getZ()) {
                case 17 -> 0;
                case 16 -> 1;
                case 15 -> 2;
                default -> -1;
            };
            return (row != -1 && col != -1) ? new int[]{row, col} : null;
        }

        public BlockPos getWorldPos(int row, int col) {
            return new BlockPos(8, 72 - row, 17 - col);
        }

        public String toString() { return "North"; }
    }

    // South wall - Z=22, X=17/16/15 (or similar range)
    private static class SouthMapping implements BoardMapping {
        public int[] getPosition(BlockPos rel) {
            int row = switch (rel.getY()) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int col = switch (rel.getX()) {
                case 17 -> 0;
                case 16 -> 1;
                case 15 -> 2;
                default -> -1;
            };
            return (row != -1 && col != -1 && rel.getZ() == 22) ? new int[]{row, col} : null;
        }

        public BlockPos getWorldPos(int row, int col) {
            return new BlockPos(17 - col, 72 - row, 22);
        }

        public String toString() { return "South"; }
    }

    // East wall (X near 31) - Z=8, X=14/15/16
    private static class EastMapping implements BoardMapping {
        public int[] getPosition(BlockPos rel) {
            int row = switch (rel.getY()) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int col = switch (rel.getX()) {
                case 14 -> 0;
                case 15 -> 1;
                case 16 -> 2;
                default -> -1;
            };
            return (row != -1 && col != -1) ? new int[]{row, col} : null;
        }

        public BlockPos getWorldPos(int row, int col) {
            return new BlockPos(14 + col, 72 - row, 8);
        }

        public String toString() { return "East"; }
    }

    // West wall (X near 0) - Z=23, X=17/16/15
    private static class WestMapping implements BoardMapping {
        public int[] getPosition(BlockPos rel) {
            int row = switch (rel.getY()) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int col = switch (rel.getX()) {
                case 17 -> 0;
                case 16 -> 1;
                case 15 -> 2;
                default -> -1;
            };
            return (row != -1 && col != -1) ? new int[]{row, col} : null;
        }

        public BlockPos getWorldPos(int row, int col) {
            return new BlockPos(17 - col, 72 - row, 23);
        }

        public String toString() { return "West"; }
    }
}
