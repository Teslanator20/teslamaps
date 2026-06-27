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
package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.utils.TicTacToeUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TicTacToe {
    private static AABB nextBestMoveBox = null;
    private static DungeonRoom currentRoom = null;
    private static String lastBoardKey = null;
    private static TicTacToeUtils.BoardIndex cachedBestMove = null;

    public static void tick() {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveTicTacToe) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        try {
            AABB searchBox = mc.player.getBoundingBox().inflate(21);
            List<ItemFrame> itemFrames = mc.level.getEntitiesOfClass(
                    ItemFrame.class,
                    searchBox,
                    frame -> {
                        ItemStack stack = frame.getItem();
                        return !stack.isEmpty() && stack.getItem() instanceof MapItem;
                    }
            );

            if (itemFrames.isEmpty() || itemFrames.size() == 9 || (itemFrames.size() & 1) == 0) {
                nextBestMoveBox = null;
                return;
            }

            BlockPos firstFramePos = itemFrames.get(0).blockPosition();
            int[] gridPos = ComponentGrid.worldToGrid(firstFramePos.getX(), firstFramePos.getZ());
            if (gridPos == null) return;

            currentRoom = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
            if (currentRoom == null) return;

            int cornerX = currentRoom.getCornerX();
            int cornerZ = currentRoom.getCornerZ();
            int rotation = currentRoom.getRotation();
            if (rotation < 0) return; // Rotation not detected yet

            Map<BlockPos, Character> frameData = new HashMap<>();
            for (ItemFrame frame : itemFrames) {
                BlockPos pos = frame.blockPosition();
                ItemStack stack = frame.getItem();
                MapItemSavedData mapState = MapItem.getSavedData(stack, mc.level);
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

            char[][] board = new char[3][3];
            Map<String, BlockPos> boardToWorld = new HashMap<>();

            for (Map.Entry<BlockPos, Character> entry : frameData.entrySet()) {
                BlockPos worldPos = entry.getKey();
                char symbol = entry.getValue();

                int[] relative = worldToRelative(cornerX, cornerZ, rotation, worldPos);

                int relX = relative[0];
                int relY = relative[1];
                int relZ = relative[2];

                int row = 72 - relY;
                int col = 17 - relZ;

                TeslaMaps.LOGGER.debug("[TicTacToe] Frame {} at world {} -> relative ({},{},{}) -> row={}, col={}",
                    symbol, worldPos, relX, relY, relZ, row, col);

                if (row < 0 || row > 2 || col < 0 || col > 2) continue;

                board[row][col] = symbol;
                boardToWorld.put(row + "," + col, worldPos);
            }

            // minimax is expensive; only recompute when the board actually changes
            String boardKey = new String(board[0]) + new String(board[1]) + new String(board[2]);
            if (cachedBestMove == null || !boardKey.equals(lastBoardKey)) {
                cachedBestMove = TicTacToeUtils.getBestMove(board);
                lastBoardKey = boardKey;
            }
            TicTacToeUtils.BoardIndex bestMove = cachedBestMove;

            String key = bestMove.row() + "," + bestMove.column();
            BlockPos existingPos = boardToWorld.get(key);

            BlockPos worldPos;
            if (existingPos != null) {
                worldPos = existingPos;
            } else {
                int relY = 72 - bestMove.row();
                int relZ = 17 - bestMove.column();
                int relX = 8; // X is constant in relative space 

                worldPos = relativeToWorld(cornerX, cornerZ, rotation, relX, relY, relZ);
            }

            Direction facing = itemFrames.get(0).getDirection();
            double offsetX = -facing.getStepX() * 0.5;
            double offsetZ = -facing.getStepZ() * 0.5;

            nextBestMoveBox = new AABB(
                worldPos.getX() + offsetX, worldPos.getY(), worldPos.getZ() + offsetZ,
                worldPos.getX() + 1 + offsetX, worldPos.getY() + 1, worldPos.getZ() + 1 + offsetZ
            );

        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[TicTacToe] Error", e);
            reset();
        }
    }

    private static int[] rotatePos(int x, int z, int degree) {
        return switch (degree % 360) {
            case 0 -> new int[]{x, z};
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    private static int[] worldToRelative(int cornerX, int cornerZ, int rotation, BlockPos worldPos) {
        int dx = worldPos.getX() - cornerX;
        int dz = worldPos.getZ() - cornerZ;

        int[] rotated = rotatePos(dx, dz, rotation);
        return new int[]{rotated[0], worldPos.getY(), rotated[1]};
    }

    private static BlockPos relativeToWorld(int cornerX, int cornerZ, int rotation, int relX, int relY, int relZ) {
        int[] rotated = rotatePos(relX, relZ, (360 - rotation) % 360);
        return new BlockPos(cornerX + rotated[0], relY, cornerZ + rotated[1]);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveTicTacToe || nextBestMoveBox == null) {
            return;
        }

        try {
            double centerX = (nextBestMoveBox.minX + nextBestMoveBox.maxX) / 2;
            double centerY = (nextBestMoveBox.minY + nextBestMoveBox.maxY) / 2;
            double centerZ = (nextBestMoveBox.minZ + nextBestMoveBox.maxZ) / 2;

            AABB buttonBox = new AABB(
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
        lastBoardKey = null;
        cachedBestMove = null;
    }
}
