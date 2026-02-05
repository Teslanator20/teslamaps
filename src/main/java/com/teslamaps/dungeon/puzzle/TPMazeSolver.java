package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Teleport Maze Solver - Tracks visited portals and highlights correct ones.
 * Detects rotation by scanning for end portal frames.
 */
public class TPMazeSolver {

    // Relative positions of end portal frames in the room (rotation 0)
    private static final BlockPos[] PORTAL_POSITIONS_RELATIVE = {
        new BlockPos(4, 69, 28), new BlockPos(4, 69, 22), new BlockPos(4, 69, 20),
        new BlockPos(4, 69, 14), new BlockPos(4, 69, 12), new BlockPos(4, 69, 6),
        new BlockPos(10, 69, 28), new BlockPos(10, 69, 22), new BlockPos(10, 69, 20),
        new BlockPos(10, 69, 14), new BlockPos(10, 69, 12), new BlockPos(10, 69, 6),
        new BlockPos(12, 69, 28), new BlockPos(12, 69, 22), new BlockPos(15, 69, 14),
        new BlockPos(15, 69, 12), new BlockPos(18, 69, 28), new BlockPos(18, 69, 22),
        new BlockPos(20, 69, 28), new BlockPos(20, 69, 22), new BlockPos(20, 69, 20),
        new BlockPos(20, 69, 14), new BlockPos(20, 69, 12), new BlockPos(20, 69, 6),
        new BlockPos(26, 69, 28), new BlockPos(26, 69, 22), new BlockPos(26, 69, 20),
        new BlockPos(26, 69, 14), new BlockPos(26, 69, 12), new BlockPos(26, 69, 6)
    };

    private static Set<BlockPos> portalPositions = new HashSet<>();
    private static List<BlockPos> correctPortals = new ArrayList<>();
    private static Set<BlockPos> visitedPortals = new CopyOnWriteArraySet<>();
    private static String lastRoomName = "";
    private static Vec3d lastPlayerPos = null;
    private static long lastTeleportTime = 0;
    private static int detectedRotation = -1;

    public static void tick() {
        if (!TeslaMapsConfig.get().solveTPMaze) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Check if we're in Teleport Maze room
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Teleport Maze")) {
            if (!lastRoomName.equals("")) {
                reset();
            }
            return;
        }

        // Initialize portal positions on room enter
        if (!lastRoomName.equals("Teleport Maze")) {
            lastRoomName = "Teleport Maze";
            detectedRotation = -1;
            initializePortals(room);
        }

        // Track player position for teleport detection
        Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (lastPlayerPos != null) {
            double distance = currentPos.distanceTo(lastPlayerPos);
            // Teleport detected (moved more than 3 blocks instantly)
            if (distance > 3 && distance < 50) {
                onTeleport(currentPos, mc.player.getYaw(), mc.player.getPitch());
            }
        }
        lastPlayerPos = currentPos;
    }

    private static void initializePortals(DungeonRoom room) {
        portalPositions.clear();
        correctPortals.clear();
        visitedPortals.clear();

        BlockPos corner = room.getCorner();
        if (corner == null) return;

        // Detect rotation by checking which rotation gives most end portal frame matches
        detectedRotation = detectRotation(corner);
        if (detectedRotation == -1) {
            TeslaMaps.LOGGER.warn("[TPMazeSolver] Could not detect rotation, using 0");
            detectedRotation = 0;
        } else {
            TeslaMaps.LOGGER.info("[TPMazeSolver] Detected rotation: {}", detectedRotation);
        }

        for (BlockPos rel : PORTAL_POSITIONS_RELATIVE) {
            BlockPos worldPos = transformPos(rel, corner, detectedRotation);
            portalPositions.add(worldPos);
        }

        // Initially all portals are potentially correct
        correctPortals.addAll(portalPositions);

        TeslaMaps.LOGGER.info("[TPMazeSolver] Initialized {} portals at corner {} with rotation {}",
            portalPositions.size(), corner, detectedRotation);
    }

    /**
     * Detect room rotation by checking which rotation has the most end portal frame matches.
     */
    private static int detectRotation(BlockPos corner) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return -1;

        int bestRotation = -1;
        int bestMatches = 0;

        for (int rotation = 0; rotation < 4; rotation++) {
            int matches = 0;

            for (BlockPos rel : PORTAL_POSITIONS_RELATIVE) {
                BlockPos worldPos = transformPos(rel, corner, rotation);
                if (mc.world.getBlockState(worldPos).getBlock() == Blocks.END_PORTAL_FRAME) {
                    matches++;
                }
            }

            TeslaMaps.LOGGER.debug("[TPMazeSolver] Rotation {} has {} portal frame matches", rotation, matches);

            if (matches > bestMatches) {
                bestMatches = matches;
                bestRotation = rotation;
            }
        }

        // Need at least 5 matches to be confident
        return bestMatches >= 5 ? bestRotation : -1;
    }

    private static void onTeleport(Vec3d pos, float yaw, float pitch) {
        long now = System.currentTimeMillis();
        if (now - lastTeleportTime < 500) return; // Debounce
        lastTeleportTime = now;

        // Mark portals near current position as visited
        for (BlockPos portal : portalPositions) {
            Box portalBox = new Box(portal).expand(1.5, 0, 1.5);
            if (portalBox.contains(pos)) {
                visitedPortals.add(portal);
            }
        }

        // Filter correct portals based on where player is looking after teleport
        Vec3d lookDir = getVectorForRotation(pitch, yaw);

        correctPortals = new ArrayList<>(correctPortals.stream()
            .filter(p -> !visitedPortals.contains(p))
            .filter(p -> {
                // Check if portal is in look direction
                Box portalBox = new Box(p).expand(0.75, 2, 0.75);
                return isLookingAt(pos, lookDir, portalBox, 32.0);
            })
            .toList());

        // If no portals match, keep all non-visited
        if (correctPortals.isEmpty()) {
            correctPortals = new ArrayList<>(portalPositions.stream()
                .filter(p -> !visitedPortals.contains(p))
                .toList());
        }

        TeslaMaps.LOGGER.info("[TPMazeSolver] After teleport: {} correct, {} visited",
            correctPortals.size(), visitedPortals.size());
    }

    private static Vec3d getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    private static boolean isLookingAt(Vec3d pos, Vec3d dir, Box box, double maxDist) {
        for (double d = 0; d < maxDist; d += 0.5) {
            Vec3d point = pos.add(dir.multiply(d));
            if (box.contains(point)) return true;
        }
        return false;
    }

    private static BlockPos transformPos(BlockPos relative, BlockPos corner, int rotation) {
        int x = relative.getX();
        int z = relative.getZ();

        int rx, rz;
        switch (rotation) {
            case 1 -> { rx = 30 - z; rz = x; }
            case 2 -> { rx = 30 - x; rz = 30 - z; }
            case 3 -> { rx = z; rz = 30 - x; }
            default -> { rx = x; rz = z; }
        }

        return new BlockPos(corner.getX() + rx, relative.getY(), corner.getZ() + rz);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveTPMaze) return;
        if (portalPositions.isEmpty()) return;

        int colorOne = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeOne);
        int colorMultiple = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeMultiple);
        int colorVisited = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeVisited);
        int colorOther = 0x80FFFFFF; // Semi-transparent white for unvisited

        for (BlockPos portal : portalPositions) {
            Box box = new Box(portal);
            int color;

            if (correctPortals.contains(portal)) {
                color = correctPortals.size() == 1 ? colorOne : colorMultiple;
            } else if (visitedPortals.contains(portal)) {
                color = colorVisited;
            } else {
                color = colorOther;
            }

            ESPRenderer.drawFilledBox(matrices, box, color, cameraPos);
        }
    }

    public static void reset() {
        portalPositions.clear();
        correctPortals.clear();
        visitedPortals.clear();
        lastRoomName = "";
        lastPlayerPos = null;
        detectedRotation = -1;
    }
}
