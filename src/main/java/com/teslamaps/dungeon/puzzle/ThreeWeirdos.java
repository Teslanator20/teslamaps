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

public class ThreeWeirdos {

    private static final Pattern PATTERN = Pattern.compile(
        "^\\[NPC] ([A-Z][a-z]+): (?:" +
        "The reward is(?: not in my chest!|n't in any of our chests\\.)|" +
        "My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)|" +
        "At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!|" +
        "Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!" +
        ")$"
    );

    private static final Pattern NPC_LINE = Pattern.compile("^\\[NPC] ([A-Z][a-z]+): ");

    private static BlockPos correctChestPos = null;
    private static AABB correctChestBox = null;
    private static String targetNpcName = null;
    private static boolean disabled = false;
    private static long lastSearchTime = 0;
    private static final java.util.Set<BlockPos> clickedChests = new java.util.HashSet<>();
    private static final java.util.Set<String> npcNames = new java.util.LinkedHashSet<>();
    private static final java.util.Map<String, AABB> npcBoxes = new java.util.HashMap<>();

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

        if (targetNpcName != null && correctChestPos == null) {
            long now = System.currentTimeMillis();
            if (now - lastSearchTime > 500) {
                lastSearchTime = now;
                findNpcAndChest(targetNpcName);
            }
        }

        if (correctChestPos != null) {
            correctChestBox = new AABB(correctChestPos);
        }

        if (!npcNames.isEmpty()) {
            npcBoxes.clear();
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof ArmorStand as)) continue;
                String name = ChatFormatting.stripFormatting(as.getName().getString());
                if (name != null && npcNames.contains(name)) {
                    npcBoxes.put(name, new AABB(as.getX() - 0.4, as.getY(), as.getZ() - 0.4,
                            as.getX() + 0.4, as.getY() + 2.0, as.getZ() + 0.4));
                }
            }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveThreeWeirdos) return;

        try {
            for (var e : npcBoxes.entrySet()) {
                boolean isTarget = e.getKey().equals(targetNpcName);
                int fill = targetNpcName == null ? 0xFFFFFFFF : (isTarget ? 0xFF00FF00 : 0xFFFF0000);
                int outline = targetNpcName == null ? 0xFFFFFFFF : (isTarget ? 0xFF00FF00 : 0xFFFF0000);
                ESPRenderer.drawFilledBox(matrices, e.getValue(), fill, cameraPos, true);
                ESPRenderer.drawBoxOutline(matrices, e.getValue(), outline, 3.0f, cameraPos, true);
            }

            for (BlockPos pos : clickedChests) {
                AABB box = new AABB(pos);
                ESPRenderer.drawFilledBox(matrices, box, 0xB0000000, cameraPos);
                ESPRenderer.drawBoxOutline(matrices, box, 0xFF000000, 5.0f, cameraPos);
            }

            if (correctChestBox != null && !disabled && !clickedChests.contains(correctChestPos)) {
                ESPRenderer.drawFilledBox(matrices, correctChestBox, 0x8000FF00, cameraPos);
                ESPRenderer.drawBoxOutline(matrices, correctChestBox, 0xFF00FF00, 5.0f, cameraPos);
                ESPRenderer.drawTracerFromCamera(matrices, correctChestBox.getCenter(), 0xFF00FF00, cameraPos);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[ThreeWeirdos] Error rendering solution", e);
        }
    }

    public static void onChatMessage(String message) {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveThreeWeirdos || disabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        String stripped = ChatFormatting.stripFormatting(message);
        if (stripped == null) return;

        Matcher speaker = NPC_LINE.matcher(stripped);
        if (speaker.find()) npcNames.add(speaker.group(1));

        Matcher matcher = PATTERN.matcher(stripped);
        if (!matcher.matches()) {
            return;
        }

        String npcName = matcher.group(1);
        TeslaMaps.LOGGER.info("[ThreeWeirdos] Pattern matched! Message: {}", stripped);

        TeslaMaps.LOGGER.info("[ThreeWeirdos] NPC='{}', message='{}'", npcName, stripped);

        targetNpcName = npcName;
        TeslaMaps.LOGGER.info("[ThreeWeirdos] Pattern matched! NPC '{}' has the correct chest", targetNpcName);
        findNpcAndChest(targetNpcName);
    }

    private static void findNpcAndChest(String npcName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

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

        BlockPos npcBlockPos = new BlockPos(
            (int) Math.floor(targetNpc.getX()),
            69, // NPCs are always at Y=69 in this puzzle
            (int) Math.floor(targetNpc.getZ())
        );

        TeslaMaps.LOGGER.info("[ThreeWeirdos] Found NPC '{}' at entity pos ({}, {}, {}), block pos {}",
            npcName, targetNpc.getX(), targetNpc.getY(), targetNpc.getZ(), npcBlockPos);

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

            targetNpc.setCustomName(Component.literal(npcName).withStyle(ChatFormatting.GREEN));
            targetNpc.setCustomNameVisible(true);
        } else {
            TeslaMaps.LOGGER.warn("[ThreeWeirdos] No chest found near NPC '{}'", npcName);
        }
    }

    public static void onChestClick(BlockPos pos) {
        if (targetNpcName == null && correctChestPos == null) return;
        clickedChests.add(pos.immutable());
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
        clickedChests.clear();
        npcNames.clear();
        npcBoxes.clear();
    }
}
