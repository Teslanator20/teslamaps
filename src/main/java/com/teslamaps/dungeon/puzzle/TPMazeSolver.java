package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
    private static Vec3 lastPlayerPos = null;
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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

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
        Vec3 currentPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (lastPlayerPos != null) {
            double distance = currentPos.distanceTo(lastPlayerPos);
            // Teleport detected (moved more than 3 blocks instantly)
            if (distance > 3 && distance < 50) {
                onTeleport(currentPos, mc.player.getYRot(), mc.player.getXRot());
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;

        int bestRotation = -1;
        int bestMatches = 0;

        for (int rotation = 0; rotation < 4; rotation++) {
            int matches = 0;

            for (BlockPos rel : PORTAL_POSITIONS_RELATIVE) {
                BlockPos worldPos = transformPos(rel, corner, rotation);
                if (mc.level.getBlockState(worldPos).getBlock() == Blocks.END_PORTAL_FRAME) {
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

    private static void onTeleport(Vec3 pos, float yaw, float pitch) {
        long now = System.currentTimeMillis();
        if (now - lastTeleportTime < 500) return; // Debounce
        lastTeleportTime = now;

        // Mark portals near current position as visited
        for (BlockPos portal : portalPositions) {
            AABB portalBox = new AABB(portal).inflate(1.5, 0, 1.5);
            if (portalBox.contains(pos)) {
                visitedPortals.add(portal);
            }
        }

        // Filter correct portals based on where player is looking after teleport
        Vec3 lookDir = getVectorForRotation(pitch, yaw);

        correctPortals = new ArrayList<>(correctPortals.stream()
            .filter(p -> !visitedPortals.contains(p))
            .filter(p -> {
                // Check if portal is in look direction
                AABB portalBox = new AABB(p).inflate(0.75, 2, 0.75);
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

    private static Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = Mth.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f1 = Mth.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f2 = -Mth.cos(-pitch * 0.017453292F);
        float f3 = Mth.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    private static boolean isLookingAt(Vec3 pos, Vec3 dir, AABB box, double maxDist) {
        for (double d = 0; d < maxDist; d += 0.5) {
            Vec3 point = pos.add(dir.scale(d));
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

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveTPMaze) return;
        if (portalPositions.isEmpty()) return;

        int colorOne = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeOne);
        int colorMultiple = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeMultiple);
        int colorVisited = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeVisited);
        int colorOther = 0x80FFFFFF; // Semi-transparent white for unvisited

        for (BlockPos portal : portalPositions) {
            AABB box = new AABB(portal);
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
