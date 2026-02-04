package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Three Weirdos puzzle solver - highlights the correct chest.
 * Adapted from Skyblocker's ThreeWeirdos.java
 */
public class ThreeWeirdos {
    private static final Pattern PATTERN = Pattern.compile("^\\[NPC] ([A-Z][a-z]+): (?:The reward is(?: not in my chest!|n't in any of our chests\\.)|My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)|At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!|Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!)$");

    private static BlockPos correctChestPos = null;
    private static Box correctChestBox = null;
    private static DungeonRoom currentRoom = null;
    private static boolean disabled = false;

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }
        if (!TeslaMapsConfig.get().solveThreeWeirdos || disabled) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            reset();
            return;
        }

        // Update chest box if we have a position
        if (correctChestPos != null) {
            correctChestBox = new Box(correctChestPos);
        }
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveThreeWeirdos || correctChestBox == null || disabled) {
            return;
        }

        try {
            // Green filled box + outline for correct chest
            ESPRenderer.drawFilledBox(matrices, correctChestBox, 0x8000FF00, cameraPos); // Semi-transparent green
            ESPRenderer.drawBoxOutline(matrices, correctChestBox, 0xFF00FF00, 5.0f, cameraPos); // Bright green outline

            // Draw tracer to chest
            Vec3d chestCenter = correctChestBox.getCenter();
            ESPRenderer.drawTracerFromCamera(matrices, chestCenter, 0xFF00FF00, cameraPos); // Green tracer
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[ThreeWeirdos] Error rendering solution", e);
        }
    }

    /**
     * Handle chat messages to find the correct chest.
     */
    public static void onChatMessage(String message) {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveThreeWeirdos) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // Strip formatting and match pattern
        String stripped = Formatting.strip(message);
        if (stripped == null) return;

        Matcher matcher = PATTERN.matcher(stripped);
        if (!matcher.matches()) return;

        String npcName = matcher.group(1);
        TeslaMaps.LOGGER.info("[ThreeWeirdos] Detected NPC message from: {}", npcName);

        // Find the Three Weirdos room
        currentRoom = findThreeWeirdosRoom();
        if (currentRoom == null) {
            TeslaMaps.LOGGER.warn("[ThreeWeirdos] Could not find Three Weirdos room!");
            return;
        }

        // Check all three NPC positions
        checkForNPC(npcName, new BlockPos(13, 69, 24));
        checkForNPC(npcName, new BlockPos(15, 69, 25));
        checkForNPC(npcName, new BlockPos(17, 69, 24));
    }

    /**
     * Check if an NPC exists at the given room-relative position and matches the name.
     */
    private static void checkForNPC(String name, BlockPos relativePos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || currentRoom == null) return;

        // Convert relative position to world position
        BlockPos worldPos = currentRoom.relativeToActual(relativePos);

        // Search for armor stand at this position
        Box searchBox = new Box(worldPos).expand(1);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class,
                searchBox,
                entity -> entity.getName().getString().equals(name)
        );

        if (!armorStands.isEmpty()) {
            // Found the NPC - chest is 1 block to the east (+X)
            BlockPos relativeChestPos = relativePos.add(1, 0, 0);
            correctChestPos = currentRoom.relativeToActual(relativeChestPos);

            TeslaMaps.LOGGER.info("[ThreeWeirdos] Found NPC '{}' at relative {} (world: {})",
                name, relativePos, worldPos);
            TeslaMaps.LOGGER.info("[ThreeWeirdos] Chest at relative {} (world: {})",
                relativeChestPos, correctChestPos);

            // Update armor stand name to be green
            for (ArmorStandEntity stand : armorStands) {
                stand.setCustomName(Text.literal(name).formatted(Formatting.GREEN));
                stand.setCustomNameVisible(true);
            }
        }
    }

    /**
     * Find the Three Weirdos room based on player position or nearby rooms.
     */
    private static DungeonRoom findThreeWeirdosRoom() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        int[] gridPos = ComponentGrid.worldToGrid(playerX, playerZ);
        if (gridPos == null) return null;

        // Check current room
        DungeonRoom room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
        if (room != null && room.isIdentified()) {
            String roomName = room.getName().toLowerCase();
            if (roomName.contains("three") || roomName.contains("weirdos") || roomName.contains("chests")) {
                TeslaMaps.LOGGER.info("[ThreeWeirdos] Found room: {} at grid [{},{}]",
                    room.getName(), gridPos[0], gridPos[1]);
                return room;
            }
        }

        // Check adjacent rooms in case player is at edge
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int checkX = gridPos[0] + dx;
                int checkZ = gridPos[1] + dz;
                DungeonRoom adjacentRoom = DungeonManager.getRoomAt(checkX, checkZ);
                if (adjacentRoom != null && adjacentRoom.isIdentified()) {
                    String roomName = adjacentRoom.getName().toLowerCase();
                    if (roomName.contains("three") || roomName.contains("weirdos") || roomName.contains("chests")) {
                        return adjacentRoom;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Disable solver when any chest is clicked.
     */
    public static void onChestClick(BlockPos pos) {
        // Disable solver when any chest in the puzzle is clicked
        if (correctChestPos != null) {
            disabled = true;
            TeslaMaps.LOGGER.info("[ThreeWeirdos] Chest clicked, solver disabled");
        }
    }

    public static void reset() {
        correctChestPos = null;
        correctChestBox = null;
        currentRoom = null;
        disabled = false;
    }
}
