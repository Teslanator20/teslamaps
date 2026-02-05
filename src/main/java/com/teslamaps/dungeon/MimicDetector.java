package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Detects and tracks the Mimic in dungeons.
 * The Mimic spawns from a trapped chest and is a baby zombie.
 */
public class MimicDetector {

    // Rooms that normally have trapped chests (not mimic)
    // Key = room name, Value = expected trapped chest count
    private static final Map<String, Integer> EXPECTED_CHESTS = new HashMap<>();
    static {
        EXPECTED_CHESTS.put("Buttons", 1);
        EXPECTED_CHESTS.put("Slime", 1);
        EXPECTED_CHESTS.put("Dueces", 1);
        EXPECTED_CHESTS.put("Redstone Key", 1);
        // Add more rooms here as you discover them
    }

    private static boolean mimicKilled = false;
    private static final Set<DungeonRoom> mimicRooms = new HashSet<>();  // All potential mimic rooms
    private static final List<BlockPos> trappedChestPositions = new ArrayList<>();  // For ESP
    private static BlockPos mimicChestPos = null;
    private static long mimicOpenTime = 0L;

    private static int scanTickCounter = 0;
    private static boolean fullScanComplete = false;
    private static boolean loggedNoMimic = false;  // Prevent log spam

    /**
     * Reset state when entering a new dungeon.
     */
    public static void reset() {
        mimicKilled = false;
        mimicRooms.clear();
        trappedChestPositions.clear();
        mimicChestPos = null;
        mimicOpenTime = 0L;
        scanTickCounter = 0;
        fullScanComplete = false;
        loggedNoMimic = false;
    }

    /**
     * Called every tick to scan for mimic.
     */
    public static void tick() {
        if (!DungeonManager.isInDungeon() || mimicKilled) return;

        // Only scan for mimics on F6, F7, M6, M7 (floors that have mimics)
        if (!DungeonScore.floorHasMimics()) {
            if (!loggedNoMimic) {
                loggedNoMimic = true;
                TeslaMaps.LOGGER.info("[MimicDetector] Floor does not have mimics, detection disabled");
            }
            return;
        }

        scanTickCounter++;

        // Check for mimic entity every 20 ticks (1 second) - fast check
        if (scanTickCounter % 20 == 0) {
            checkForMimicEntity();
        }

        // Scan for trapped chests every 100 ticks (5 seconds) until complete or mimic found
        if (scanTickCounter % 100 == 0 && !fullScanComplete && mimicRooms.isEmpty()) {
            scanForMimicRoom();
        }

        // Check if mimic is dead
        if (mimicOpenTime > 0 && System.currentTimeMillis() - mimicOpenTime > 750) {
            checkMimicDead();
        }
    }

    /**
     * Check for Mimic entity (armor stand with "Mimic" in name).
     * Also detects mimic death when armor stand shows "0❤".
     */
    private static void checkForMimicEntity() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        for (var entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity) {
                String name = entity.getName().getString();
                if (name.contains("Mimic")) {
                    // Check if mimic is dead (shows 0❤)
                    if (name.contains("0❤") && !mimicKilled) {
                        TeslaMaps.LOGGER.info("[MimicDetector] Detected Mimic death via armor stand: {}", name);
                        setMimicKilled();
                        return;
                    }

                    // Otherwise, track mimic room location
                    int[] gridPos = ComponentGrid.worldToGrid(entity.getX(), entity.getZ());
                    if (gridPos != null) {
                        DungeonRoom room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
                        if (room != null && room.getType() != com.teslamaps.map.RoomType.TRAP) {
                            if (!mimicRooms.contains(room)) {
                                mimicRooms.add(room);
                                TeslaMaps.LOGGER.info("[MimicDetector] Found Mimic entity in room: {} at [{},{}]",
                                        room.getName(), gridPos[0], gridPos[1]);
                                sendMimicFoundMessage(room.getName());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when a block changes - detect trapped chest opening.
     */
    public static void onBlockChange(BlockPos pos, boolean wasTrappedChest, boolean isNowAir) {
        if (!DungeonManager.isInDungeon() || mimicKilled) return;

        if (wasTrappedChest && isNowAir) {
            mimicOpenTime = System.currentTimeMillis();
            mimicChestPos = pos;
            TeslaMaps.LOGGER.info("[MimicDetector] Trapped chest opened at {}", pos);
        }
    }

    /**
     * Get expected trapped chest count for a room.
     */
    private static int getExpectedChestCount(String roomName) {
        return EXPECTED_CHESTS.getOrDefault(roomName, 0);
    }

    /**
     * Scan for trapped chests by iterating through loaded chunks.
     */
    private static void scanForMimicRoom() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        long startTime = System.currentTimeMillis();
        int chunksChecked = 0;
        int chunksLoaded = 0;

        // Clear previous data for rescan
        trappedChestPositions.clear();
        Map<DungeonRoom, List<BlockPos>> chestsPerRoom = new HashMap<>();

        // Dungeon chunks: -13,-13 to -1,-1
        int minChunkX = -200 >> 4;
        int maxChunkX = -10 >> 4;
        int minChunkZ = -200 >> 4;
        int maxChunkZ = -10 >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunksChecked++;
                var chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                // Check if chunk is actually loaded (has block entities)
                if (chunk.getBlockEntities().isEmpty()) continue;
                chunksLoaded++;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();

                    if (pos.getX() < -200 || pos.getX() > -10 || pos.getZ() < -200 || pos.getZ() > -10) {
                        continue;
                    }

                    if (mc.world.getBlockState(pos).getBlock() == Blocks.TRAPPED_CHEST) {
                        trappedChestPositions.add(pos);

                        int[] gridPos = ComponentGrid.worldToGrid(pos.getX(), pos.getZ());
                        if (gridPos != null) {
                            DungeonRoom room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
                            if (room != null && room.getType() != com.teslamaps.map.RoomType.TRAP) {
                                chestsPerRoom.computeIfAbsent(room, k -> new ArrayList<>()).add(pos);
                            }
                        }
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // Check if we've loaded most chunks (consider scan complete if >80% loaded)
        int totalChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (chunksLoaded > totalChunks * 0.8) {
            fullScanComplete = true;
        }

        // Find rooms with more trapped chests than expected
        mimicRooms.clear();
        for (var entry : chestsPerRoom.entrySet()) {
            DungeonRoom room = entry.getKey();
            int chestCount = entry.getValue().size();
            int expected = getExpectedChestCount(room.getName());

            TeslaMaps.LOGGER.info("[MimicDetector] Room '{}' has {} trapped chest(s), expected {}",
                    room.getName(), chestCount, expected);

            if (chestCount > expected) {
                mimicRooms.add(room);
                TeslaMaps.LOGGER.info("[MimicDetector] -> Potential mimic room!");
            }
        }

        TeslaMaps.LOGGER.info("[MimicDetector] Scan: {}/{} chunks loaded, {} trapped chests, {} potential mimic rooms, {}ms (complete: {})",
                chunksLoaded, totalChunks, trappedChestPositions.size(), mimicRooms.size(), elapsed, fullScanComplete);

        // Send message for each new mimic room found
        for (DungeonRoom room : mimicRooms) {
            sendMimicFoundMessage(room.getName());
        }
    }

    /**
     * Check if the mimic entity is dead.
     */
    private static void checkMimicDead() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mimicChestPos == null) return;

        if (mc.player.squaredDistanceTo(mimicChestPos.getX(), mimicChestPos.getY(), mimicChestPos.getZ()) > 400) {
            return;
        }

        boolean mimicFound = false;
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof ZombieEntity zombie && zombie.isBaby()) {
                if (zombie.squaredDistanceTo(mimicChestPos.getX(), mimicChestPos.getY(), mimicChestPos.getZ()) < 100) {
                    mimicFound = true;
                    break;
                }
            }
        }

        if (!mimicFound) {
            setMimicKilled();
        }
    }

    /**
     * Mark mimic as killed.
     */
    private static void setMimicKilled() {
        if (mimicKilled) return;
        mimicKilled = true;
        mimicRooms.clear();
        trappedChestPositions.clear();
        TeslaMaps.LOGGER.info("[MimicDetector] Mimic killed!");

        // Notify DungeonScore
        DungeonScore.onMimicKilled();

        // Send party chat message
        sendMimicDeadMessage();
    }

    /**
     * Called when a chat message is received - check for mimic dead messages from other players.
     */
    public static void onChatMessage(String message) {
        if (!DungeonManager.isInDungeon() || mimicKilled) return;

        // Check for common mimic dead messages (case insensitive)
        String lower = message.toLowerCase();
        if (lower.contains("mimic") && (lower.contains("dead") || lower.contains("killed") || lower.contains("done"))) {
            TeslaMaps.LOGGER.info("[MimicDetector] Detected mimic dead message from chat: {}", message);
            mimicKilled = true;
            mimicRooms.clear();
            trappedChestPositions.clear();
        }
    }

    /**
     * Send mimic dead message to party chat.
     */
    private static void sendMimicDeadMessage() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!com.teslamaps.config.TeslaMapsConfig.get().mimicDeadMessage) return;

        mc.player.networkHandler.sendChatCommand("pc Mimic Dead!");
        TeslaMaps.LOGGER.info("[MimicDetector] Announced Mimic death to party");
    }

    /**
     * Check if an entity is likely a mimic.
     */
    public static boolean isMimic(ZombieEntity zombie) {
        if (!zombie.isBaby()) return false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!zombie.getEquippedStack(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get all potential mimic rooms.
     */
    public static Set<DungeonRoom> getMimicRooms() {
        return mimicRooms;
    }

    /**
     * Check if mimic has been killed.
     */
    public static boolean isMimicKilled() {
        return mimicKilled;
    }

    /**
     * Check if a room is a potential mimic room.
     */
    public static boolean isMimicRoom(DungeonRoom room) {
        return mimicRooms.contains(room);
    }

    /**
     * Get all trapped chest positions for ESP rendering.
     */
    public static List<BlockPos> getTrappedChestPositions() {
        return trappedChestPositions;
    }

    /**
     * Send chat message when mimic room is found.
     */
    private static void sendMimicFoundMessage(String roomName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Text message = Text.literal("[TeslaMaps] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Mimic found in ")
                        .formatted(Formatting.YELLOW))
                .append(Text.literal(roomName)
                        .formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal("!")
                        .formatted(Formatting.YELLOW));

        mc.player.sendMessage(message, false);
    }
}
