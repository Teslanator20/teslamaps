package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Three Weirdos puzzle solver - highlights the correct chest.
 * Three Weirdos puzzle solver - highlights the correct NPC.
 */
public class ThreeWeirdos {

    // Pattern that matches the NPC with the correct chest
    private static final Pattern PATTERN = Pattern.compile(
        "^\\[NPC] ([A-Z][a-z]+): (?:" +
        "The reward is(?: not in my chest!|n't in any of our chests\\.)|" +
        "My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)|" +
        "At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!|" +
        "Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!" +
        ")$"
    );

    private static BlockPos correctChestPos = null;
    private static AABB correctChestBox = null;
    private static String targetNpcName = null;
    private static boolean disabled = false;
    private static long lastSearchTime = 0;

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }
        if (!TeslaMapsConfig.get().solveThreeWeirdos || disabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        // If we know the target NPC but haven't found the chest yet, search periodically
        if (targetNpcName != null && correctChestPos == null) {
            long now = System.currentTimeMillis();
            if (now - lastSearchTime > 500) {
                lastSearchTime = now;
                findNpcAndChest(targetNpcName);
            }
        }

        // Update chest box if we have a position
        if (correctChestPos != null) {
            correctChestBox = new AABB(correctChestPos);
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveThreeWeirdos || correctChestBox == null || disabled) {
            return;
        }

        try {
            ESPRenderer.drawFilledBox(matrices, correctChestBox, 0x8000FF00, cameraPos);
            ESPRenderer.drawBoxOutline(matrices, correctChestBox, 0xFF00FF00, 5.0f, cameraPos);
            Vec3 chestCenter = correctChestBox.getCenter();
            ESPRenderer.drawTracerFromCamera(matrices, chestCenter, 0xFF00FF00, cameraPos);
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[ThreeWeirdos] Error rendering solution", e);
        }
    }

    /**
     * Handle chat messages to find the correct NPC.
     * Matches specific NPC dialogue patterns.
     */
    public static void onChatMessage(String message) {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveThreeWeirdos || disabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        String stripped = ChatFormatting.stripFormatting(message);
        if (stripped == null) return;

        // Use regex pattern to match dialogue
        Matcher matcher = PATTERN.matcher(stripped);
        if (!matcher.matches()) {
            return;
        }

        // Extract NPC name from regex group
        String npcName = matcher.group(1);
        TeslaMaps.LOGGER.info("[ThreeWeirdos] Pattern matched! Message: {}", stripped);

        TeslaMaps.LOGGER.info("[ThreeWeirdos] NPC='{}', message='{}'", npcName, stripped);

        // Match dialogue patterns and highlight the correct chest
        // The pattern matching already validated this is a correct phrase
        // The NPC who says these specific phrases has the correct chest
        targetNpcName = npcName;
        TeslaMaps.LOGGER.info("[ThreeWeirdos] Pattern matched! NPC '{}' has the correct chest", targetNpcName);
        findNpcAndChest(targetNpcName);
    }

    /**
     * Find NPC by name and locate the chest next to them.
     * Find the armor stand entity, then find closest chest.
     */
    private static void findNpcAndChest(String npcName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Search for the NPC entity by name 
        ArmorStand targetNpc = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand armorStand) {
                String name = ChatFormatting.stripFormatting(armorStand.getName().getString());
                if (name != null && name.equals(npcName)) {
                    targetNpc = armorStand;
                    break;
                }
            }
        }

        if (targetNpc == null) {
            TeslaMaps.LOGGER.debug("[ThreeWeirdos] NPC '{}' not found", npcName);
            return;
        }

        // Get NPC position (floor to block pos)
        BlockPos npcBlockPos = new BlockPos(
            (int) Math.floor(targetNpc.getX()),
            69, // NPCs are always at Y=69 in this puzzle
            (int) Math.floor(targetNpc.getZ())
        );

        TeslaMaps.LOGGER.info("[ThreeWeirdos] Found NPC '{}' at entity pos ({}, {}, {}), block pos {}",
            npcName, targetNpc.getX(), targetNpc.getY(), targetNpc.getZ(), npcBlockPos);

        // Find the CLOSEST chest to the NPC within 3 blocks
        BlockPos closestChest = null;
        double closestDist = Double.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos checkPos = npcBlockPos.offset(dx, dy, dz);
                    if (mc.level.getBlockState(checkPos).getBlock() == Blocks.CHEST) {
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        TeslaMaps.LOGGER.info("[ThreeWeirdos] Found chest at {} distance {}", checkPos, dist);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = checkPos;
                        }
                    }
                }
            }
        }

        if (closestChest != null) {
            correctChestPos = closestChest;
            TeslaMaps.LOGGER.info("[ThreeWeirdos] Selected closest chest at {} (distance {})", correctChestPos, closestDist);

            // Color the NPC name green
            targetNpc.setCustomName(Component.literal(npcName).withStyle(ChatFormatting.GREEN));
            targetNpc.setCustomNameVisible(true);
        } else {
            TeslaMaps.LOGGER.warn("[ThreeWeirdos] No chest found near NPC '{}'", npcName);
        }
    }

    public static void onChestClick(BlockPos pos) {
        if (correctChestPos != null) {
            disabled = true;
            TeslaMaps.LOGGER.info("[ThreeWeirdos] Chest clicked, solver disabled");
        }
    }

    public static void reset() {
        correctChestPos = null;
        correctChestBox = null;
        targetNpcName = null;
        disabled = false;
        lastSearchTime = 0;
    }
}
