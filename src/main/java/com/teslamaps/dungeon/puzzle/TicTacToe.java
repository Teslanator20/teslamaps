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
    private static List<TicTacToeUtils.BoardIndex> cachedAllBest = List.of();
    private static boolean cachedGamble = false;
    private static TicTacToeUtils.BoardIndex cachedPrediction = null;
    private static List<AABB> bestBoxes = List.of();
    private static AABB predictedBox = null;
    private static Set<BlockPos> allowedButtons = Set.of();
    private static Set<BlockPos> emptyButtons = Set.of();
    private static long solvedAt = 0;
    private static final java.util.regex.Pattern SOLVED = java.util.regex.Pattern.compile("^PUZZLE SOLVED! (\\w{1,16}) tied Tic Tac Toe!.*");

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
                clearSolution();
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
                int[] val = new int[1];
                cachedAllBest = TicTacToeUtils.bestMoves(board, true, val);
                cachedGamble = val[0] < 0;
                cachedPrediction = predict(board, cachedBestMove);
                lastBoardKey = boardKey;
            }

            Direction facing = itemFrames.get(0).getDirection();
            TeslaMapsConfig c = TeslaMapsConfig.get();
            List<AABB> boxes = new ArrayList<>();
            if (c.tttShowAllBest && !cachedAllBest.isEmpty()) {
                for (TicTacToeUtils.BoardIndex m : cachedAllBest) boxes.add(buttonBox(cellPos(m, boardToWorld, cornerX, cornerZ, rotation), facing));
            } else {
                boxes.add(buttonBox(cellPos(cachedBestMove, boardToWorld, cornerX, cornerZ, rotation), facing));
            }
            Set<BlockPos> allowed = new HashSet<>();
            allowed.add(cellPos(cachedBestMove, boardToWorld, cornerX, cornerZ, rotation));
            for (TicTacToeUtils.BoardIndex m : cachedAllBest) allowed.add(cellPos(m, boardToWorld, cornerX, cornerZ, rotation));
            Set<BlockPos> empty = new HashSet<>();
            int xs = 0, os = 0;
            for (int r = 0; r < 3; r++) {
                for (int col = 0; col < 3; col++) {
                    if (board[r][col] == 'X') xs++;
                    else if (board[r][col] == 'O') os++;
                    else empty.add(cellPos(new TicTacToeUtils.BoardIndex(r, col), boardToWorld, cornerX, cornerZ, rotation));
                }
            }
            // every frame read and mapped, AI one ahead: only then is blocking trusted
            boolean valid = xs + os == itemFrames.size() && xs == os + 1;
            bestBoxes = boxes;
            nextBestMoveBox = boxes.get(0);
            predictedBox = cachedPrediction == null ? null
                    : buttonBox(cellPos(cachedPrediction, boardToWorld, cornerX, cornerZ, rotation), facing);
            allowedButtons = valid ? allowed : Set.of();
            emptyButtons = valid ? empty : Set.of();
            solvedAt = System.currentTimeMillis();

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
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.solveTicTacToe || nextBestMoveBox == null) {
            return;
        }

        try {
            int best = TeslaMapsConfig.parseColor(c.tttGamble && cachedGamble ? c.colorTttGamble : c.colorTttBest);
            for (AABB box : bestBoxes) PuzzleDraw.box(matrices, box, best, c.tttStyle, 3.0f, cameraPos, true);
            if (c.tttPrediction && predictedBox != null) {
                PuzzleDraw.box(matrices, predictedBox, TeslaMapsConfig.parseColor(c.colorTttPrediction), c.tttStyle, 3.0f, cameraPos, true);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[TicTacToe] Error rendering", e);
        }
    }

    private static TicTacToeUtils.BoardIndex predict(char[][] board, TicTacToeUtils.BoardIndex ours) {
        char[][] b = new char[3][];
        for (int i = 0; i < 3; i++) b[i] = board[i].clone();
        if (b[ours.row()][ours.column()] != '\0') return null;
        b[ours.row()][ours.column()] = 'O';
        List<TicTacToeUtils.BoardIndex> ai = TicTacToeUtils.bestMoves(b, false, null);
        if (ai.isEmpty()) return null;
        TicTacToeUtils.BoardIndex x = ai.get(0);
        b[x.row()][x.column()] = 'X';
        List<TicTacToeUtils.BoardIndex> next = TicTacToeUtils.bestMoves(b, true, null);
        return next.isEmpty() ? null : next.get(0);
    }

    private static BlockPos cellPos(TicTacToeUtils.BoardIndex m, Map<String, BlockPos> boardToWorld, int cornerX, int cornerZ, int rotation) {
        BlockPos existing = boardToWorld.get(m.row() + "," + m.column());
        if (existing != null) return existing;
        return relativeToWorld(cornerX, cornerZ, rotation, 8, 72 - m.row(), 17 - m.column());
    }

    private static AABB buttonBox(BlockPos worldPos, Direction facing) {
        double cx = worldPos.getX() + 0.5 - facing.getStepX() * 0.5;
        double cy = worldPos.getY() + 0.5;
        double cz = worldPos.getZ() + 0.5 - facing.getStepZ() * 0.5;
        return new AABB(cx - 0.2, cy - 0.15, cz - 0.1, cx + 0.2, cy + 0.15, cz + 0.1);
    }

    /** true = swallow the click: a board button that is not a best move (sneak overrides). */
    public static boolean blockClick(BlockPos pos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.solveTicTacToe || !c.tttBlockWrongClicks || com.teslamaps.features.LegitMode.blocksCheats()) return false;
        if (!c.section("Puzzles & Terminals")) return false;
        if (nextBestMoveBox == null || allowedButtons.isEmpty()) return false;
        if (System.currentTimeMillis() - solvedAt > 500) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isCrouching()) return false;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return false;
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || !"Tic Tac Toe".equals(room.getName())) return false;
        if (mc.level.getBlockState(pos).getBlock() != net.minecraft.world.level.block.Blocks.STONE_BUTTON) return false;
        if (!emptyButtons.contains(pos)) return false;
        return !allowedButtons.contains(pos);
    }

    public static void onChat(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.tttDoneMessage || !DungeonManager.isInDungeon()) return;
        java.util.regex.Matcher m = SOLVED.matcher(com.teslamaps.utils.StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !m.group(1).equals(mc.getUser().getName())) return;
        com.teslamaps.features.Announce.party("Tic Tac Toe done");
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("TicTacToeDone").section("Puzzles & Terminals").legit()
                .when(() -> TeslaMapsConfig.get().solveTicTacToe && TeslaMapsConfig.get().tttDoneMessage)
                .serverChat(TicTacToe::onChat);
    }

    private static void clearSolution() {
        nextBestMoveBox = null;
        bestBoxes = List.of();
        predictedBox = null;
        allowedButtons = Set.of();
        emptyButtons = Set.of();
    }

    public static void reset() {
        clearSolution();
        cachedAllBest = List.of();
        cachedGamble = false;
        cachedPrediction = null;
        currentRoom = null;
        lastBoardKey = null;
        cachedBestMove = null;
    }
}
