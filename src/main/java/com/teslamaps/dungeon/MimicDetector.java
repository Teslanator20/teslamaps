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
package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.scanner.ComponentGrid;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

public class MimicDetector {

    private static final Map<String, Integer> EXPECTED_CHESTS = new HashMap<>();
    static {
        EXPECTED_CHESTS.put("Buttons", 1);
        EXPECTED_CHESTS.put("Slime", 1);
        EXPECTED_CHESTS.put("Dueces", 1);
        EXPECTED_CHESTS.put("Redstone Key", 1);
    }

    private static boolean mimicKilled = false;
    private static final Set<DungeonRoom> mimicRooms = new HashSet<>();  // All potential mimic rooms
    private static final List<BlockPos> trappedChestPositions = new ArrayList<>();  // For ESP
    private static BlockPos mimicChestPos = null;
    private static long mimicOpenTime = 0L;

    private static int scanTickCounter = 0;
    private static boolean fullScanComplete = false;
    private static boolean loggedNoMimic = false;  // Prevent log spam

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

    public static void tick() {
        if (!DungeonManager.isInDungeon() || mimicKilled) return;

        if (!DungeonScore.floorHasMimics()) {
            if (!loggedNoMimic) {
                loggedNoMimic = true;
                TeslaMaps.LOGGER.info("[MimicDetector] Floor does not have mimics, detection disabled");
            }
            return;
        }

        scanTickCounter++;

        if (scanTickCounter % 20 == 0) {
            checkForMimicEntity();
        }

        if (scanTickCounter % 100 == 0 && !fullScanComplete && mimicRooms.isEmpty()) {
            scanForMimicRoom();
        }

        if (mimicOpenTime > 0 && System.currentTimeMillis() - mimicOpenTime > 750) {
            checkMimicDead();
        }
    }

    private static void checkForMimicEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                String name = entity.getName().getString();
                if (name.contains("Mimic")) {
                    if (name.contains("0") && !mimicKilled) {
                        TeslaMaps.LOGGER.info("[MimicDetector] Detected Mimic death via armor stand: {}", name);
                        setMimicKilled();
                        return;
                    }

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

    public static void onBlockChange(BlockPos pos, boolean wasTrappedChest, boolean isNowAir) {
        if (!DungeonManager.isInDungeon() || mimicKilled) return;

        if (wasTrappedChest && isNowAir) {
            mimicOpenTime = System.currentTimeMillis();
            mimicChestPos = pos;
            TeslaMaps.LOGGER.info("[MimicDetector] Trapped chest opened at {}", pos);
        }
    }

    private static int getExpectedChestCount(String roomName) {
        return EXPECTED_CHESTS.getOrDefault(roomName, 0);
    }

    private static void scanForMimicRoom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        long startTime = System.currentTimeMillis();
        int chunksChecked = 0;
        int chunksLoaded = 0;

        trappedChestPositions.clear();
        Map<DungeonRoom, List<BlockPos>> chestsPerRoom = new HashMap<>();

        int minChunkX = -200 >> 4;
        int maxChunkX = -10 >> 4;
        int minChunkZ = -200 >> 4;
        int maxChunkZ = -10 >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunksChecked++;
                var chunk = mc.level.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                if (chunk.getBlockEntities().isEmpty()) continue;
                chunksLoaded++;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();

                    if (pos.getX() < -200 || pos.getX() > -10 || pos.getZ() < -200 || pos.getZ() > -10) {
                        continue;
                    }

                    if (mc.level.getBlockState(pos).getBlock() == Blocks.TRAPPED_CHEST) {
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

        int totalChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (chunksLoaded > totalChunks * 0.8) {
            fullScanComplete = true;
        }

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

        for (DungeonRoom room : mimicRooms) {
            sendMimicFoundMessage(room.getName());
        }
    }

    private static void checkMimicDead() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mimicChestPos == null) return;

        if (mc.player.distanceToSqr(mimicChestPos.getX(), mimicChestPos.getY(), mimicChestPos.getZ()) > 400) {
            return;
        }

        boolean mimicFound = false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Zombie zombie && zombie.isBaby()) {
                if (zombie.distanceToSqr(mimicChestPos.getX(), mimicChestPos.getY(), mimicChestPos.getZ()) < 100) {
                    mimicFound = true;
                    break;
                }
            }
        }

        if (!mimicFound) {
            setMimicKilled();
        }
    }

    private static void setMimicKilled() {
        if (mimicKilled) return;
        mimicKilled = true;
        mimicRooms.clear();
        trappedChestPositions.clear();
        TeslaMaps.LOGGER.info("[MimicDetector] Mimic killed!");

        DungeonScore.onMimicKilled();

        sendMimicDeadMessage();
    }

    public static void onChatMessage(String message) {
        if (!DungeonManager.isInDungeon() || mimicKilled) return;

        String lower = message.toLowerCase();
        if (lower.contains("mimic") && (lower.contains("dead") || lower.contains("killed") || lower.contains("done"))) {
            TeslaMaps.LOGGER.info("[MimicDetector] Detected mimic dead message from chat: {}", message);
            mimicKilled = true;
            mimicRooms.clear();
            trappedChestPositions.clear();
        }
    }

    private static void sendMimicDeadMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!com.teslamaps.config.TeslaMapsConfig.get().mimicDeadMessage) return;

        mc.player.connection.sendCommand("pc Mimic Dead!");
        TeslaMaps.LOGGER.info("[MimicDetector] Announced Mimic death to party");
    }

    public static boolean isMimic(Zombie zombie) {
        if (!zombie.isBaby()) return false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!zombie.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static Set<DungeonRoom> getMimicRooms() {
        return mimicRooms;
    }

    public static boolean isMimicKilled() {
        return mimicKilled;
    }

    public static boolean isMimicRoom(DungeonRoom room) {
        return mimicRooms.contains(room);
    }

    public static List<BlockPos> getTrappedChestPositions() {
        return trappedChestPositions;
    }

    private static void sendMimicFoundMessage(String roomName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component message = Component.literal("[TeslaMaps] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Mimic found in ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(roomName)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("!")
                        .withStyle(ChatFormatting.YELLOW));

        mc.player.sendSystemMessage(message);
    }
}
