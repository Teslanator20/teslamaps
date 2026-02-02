package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans for crypt structures in dungeons.
 * Crypts are identified by Mossy Stone Bricks which are distinctive to crypt structures.
 */
public class CryptScanner {
    // Cached crypt positions (center of each crypt)
    private static final List<Vec3d> cryptPositions = new ArrayList<>();

    // Track which chunks we've scanned
    private static final Set<Long> scannedChunks = new HashSet<>();

    // Last scan time
    private static long lastScanTime = 0;
    private static final long SCAN_COOLDOWN = 2000; // 2 seconds between full scans

    // Minimum distance between crypts (to avoid counting same crypt twice)
    private static final double MIN_CRYPT_DISTANCE = 3.0;

    /**
     * Get the total number of crypts found in the dungeon.
     */
    public static int getCryptCount() {
        return cryptPositions.size();
    }

    /**
     * Get all crypt positions.
     */
    public static List<Vec3d> getCryptPositions() {
        return cryptPositions;
    }

    /**
     * Reset scanner (call on dungeon exit).
     */
    public static void reset() {
        cryptPositions.clear();
        scannedChunks.clear();
        lastScanTime = 0;
    }

    /**
     * Scan for crypts in loaded chunks around the player.
     * Called periodically from tick.
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            if (!cryptPositions.isEmpty()) {
                reset();
            }
            return;
        }

        // Rate limit scanning
        long now = System.currentTimeMillis();
        if (now - lastScanTime < SCAN_COOLDOWN) {
            return;
        }
        lastScanTime = now;

        // Scan chunks around player
        int playerChunkX = (int) mc.player.getX() >> 4;
        int playerChunkZ = (int) mc.player.getZ() >> 4;

        // Scan 5x5 chunk area around player
        for (int cx = playerChunkX - 2; cx <= playerChunkX + 2; cx++) {
            for (int cz = playerChunkZ - 2; cz <= playerChunkZ + 2; cz++) {
                long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);

                if (scannedChunks.contains(chunkKey)) {
                    continue;
                }

                // Check if chunk is loaded
                if (!mc.world.isChunkLoaded(cx, cz)) {
                    continue;
                }

                scanChunkForCrypts(mc, cx, cz);
                scannedChunks.add(chunkKey);
            }
        }
    }

    /**
     * Scan a single chunk for crypt structures.
     * Pattern: Stone Brick + Mossy Stone Brick + Stone Brick in a row,
     * with 2 Smooth Stone Slabs on top of each.
     */
    private static void scanChunkForCrypts(MinecraftClient mc, int chunkX, int chunkZ) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        // Dungeon Y levels (crypts are typically around y=66-70)
        int minY = 64;
        int maxY = 72;

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    // Look for mossy stone brick as the center
                    if (block == Blocks.MOSSY_STONE_BRICKS) {
                        // Check if this is a valid crypt pattern
                        if (isValidCryptPattern(mc, x, y, z)) {
                            Vec3d cryptPos = new Vec3d(x + 0.5, y + 0.5, z + 0.5);

                            // Check if we already have a crypt nearby
                            boolean alreadyFound = false;
                            for (Vec3d existing : cryptPositions) {
                                if (existing.squaredDistanceTo(cryptPos) < MIN_CRYPT_DISTANCE * MIN_CRYPT_DISTANCE) {
                                    alreadyFound = true;
                                    break;
                                }
                            }

                            if (!alreadyFound) {
                                cryptPositions.add(cryptPos);
                                TeslaMaps.LOGGER.info("[CryptScanner] Found crypt at {}, {}, {} (total: {})",
                                        x, y, z, cryptPositions.size());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if the mossy stone brick at (x, y, z) is part of a valid crypt pattern.
     * Pattern: Stone Brick + Mossy Stone Brick + Stone Brick with smooth stone slabs on top.
     */
    private static boolean isValidCryptPattern(MinecraftClient mc, int x, int y, int z) {
        // Check X-axis pattern: stone_brick, mossy_stone_brick, stone_brick
        if (checkCryptLine(mc, x, y, z, 1, 0)) {
            return true;
        }
        // Check Z-axis pattern
        if (checkCryptLine(mc, x, y, z, 0, 1)) {
            return true;
        }
        return false;
    }

    /**
     * Check if there's a crypt pattern along the given axis.
     * @param dx 1 for X-axis, 0 for Z-axis
     * @param dz 0 for X-axis, 1 for Z-axis
     */
    private static boolean checkCryptLine(MinecraftClient mc, int x, int y, int z, int dx, int dz) {
        // Center is mossy stone brick (already checked)
        // Check left neighbor: stone brick
        Block left = mc.world.getBlockState(new BlockPos(x - dx, y, z - dz)).getBlock();
        if (left != Blocks.STONE_BRICKS) return false;

        // Check right neighbor: stone brick
        Block right = mc.world.getBlockState(new BlockPos(x + dx, y, z + dz)).getBlock();
        if (right != Blocks.STONE_BRICKS) return false;

        // Check smooth stone slabs on top (y+1 and y+2)
        // At least check y+1 for smooth stone slab
        Block topCenter = mc.world.getBlockState(new BlockPos(x, y + 1, z)).getBlock();
        Block topLeft = mc.world.getBlockState(new BlockPos(x - dx, y + 1, z - dz)).getBlock();
        Block topRight = mc.world.getBlockState(new BlockPos(x + dx, y + 1, z + dz)).getBlock();

        // Check if at least the center has a smooth stone slab on top
        // Smooth stone slab block is SMOOTH_STONE_SLAB
        boolean hasSlabOnTop = (topCenter == Blocks.SMOOTH_STONE_SLAB || topCenter == Blocks.SMOOTH_STONE);

        return hasSlabOnTop;
    }

    /**
     * Force a full rescan of the dungeon area.
     */
    public static void forceScan() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        cryptPositions.clear();
        scannedChunks.clear();

        // Scan entire dungeon area (-200 to -8 in both X and Z)
        TeslaMaps.LOGGER.info("[CryptScanner] Starting full dungeon scan...");

        int minY = 64;
        int maxY = 72;

        for (int x = -200; x <= -8; x++) {
            for (int z = -200; z <= -8; z++) {
                // Check if chunk is loaded
                int cx = x >> 4;
                int cz = z >> 4;
                if (!mc.world.isChunkLoaded(cx, cz)) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (block == Blocks.MOSSY_STONE_BRICKS) {
                        // Check if valid crypt pattern
                        if (isValidCryptPattern(mc, x, y, z)) {
                            Vec3d cryptPos = new Vec3d(x + 0.5, y + 0.5, z + 0.5);

                            boolean alreadyFound = false;
                            for (Vec3d existing : cryptPositions) {
                                if (existing.squaredDistanceTo(cryptPos) < MIN_CRYPT_DISTANCE * MIN_CRYPT_DISTANCE) {
                                    alreadyFound = true;
                                    break;
                                }
                            }

                            if (!alreadyFound) {
                                cryptPositions.add(cryptPos);
                            }
                        }
                    }
                }
            }
        }

        TeslaMaps.LOGGER.info("[CryptScanner] Full scan complete. Found {} crypts.", cryptPositions.size());
    }
}
