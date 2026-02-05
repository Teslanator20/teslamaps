package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Blaze puzzle solver - highlights which blaze to kill.
 * Blaze puzzle solver - shows kill order.
 */
public class DungeonBlaze {
    private static ArmorStandEntity highestBlaze = null;
    private static ArmorStandEntity lowestBlaze = null;
    private static ArmorStandEntity nextHighestBlaze = null;
    private static ArmorStandEntity nextLowestBlaze = null;

    private static boolean blazeDoneMessageSent = false;
    private static int previousBlazeCount = 0;

    public static void tick() {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveBlaze) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            reset();
            return;
        }

        List<ObjectIntPair<ArmorStandEntity>> blazes = getBlazesInWorld();
        sortBlazes(blazes);
        updateBlazeEntities(blazes);

        // Check if blaze puzzle is complete (only 1 blaze left with 0 HP or no blazes)
        if (!blazeDoneMessageSent && TeslaMapsConfig.get().blazeDoneMessage) {
            if (blazes.isEmpty() || (blazes.size() == 1 && blazes.get(0).rightInt() == 0)) {
                // Only send if we previously had blazes (avoid sending on first scan)
                if (previousBlazeCount > 1) {
                    sendBlazeDoneMessage();
                    blazeDoneMessageSent = true;
                }
            }
        }

        previousBlazeCount = blazes.size();
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveBlaze || !DungeonManager.isInDungeon()) {
            return;
        }

        try {
            if (highestBlaze != null && lowestBlaze != null && highestBlaze.isAlive() && lowestBlaze.isAlive()) {
                // If highest blaze is below y=69, highlight it
                if (highestBlaze.getY() < 69) {
                    renderBlazeOutline(matrices, highestBlaze, nextHighestBlaze, cameraPos);
                }
                // If lowest blaze is above y=69, highlight it
                if (lowestBlaze.getY() > 69) {
                    renderBlazeOutline(matrices, lowestBlaze, nextLowestBlaze, cameraPos);
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[DungeonBlaze] Error rendering", e);
        }
    }

    private static List<ObjectIntPair<ArmorStandEntity>> getBlazesInWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<ObjectIntPair<ArmorStandEntity>> blazes = new ArrayList<>();

        if (mc.world == null || mc.player == null) return blazes;

        Box searchBox = mc.player.getBoundingBox().expand(500);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class,
                searchBox,
                entity -> !entity.hasPassengers()
        );

        for (ArmorStandEntity blaze : armorStands) {
            String blazeName = blaze.getName().getString();
            if (blazeName.contains("Blaze") && blazeName.contains("/")) {
                try {
                    String healthStr = blazeName.substring(blazeName.indexOf("/") + 1, blazeName.length() - 1);
                    int health = Integer.parseInt(healthStr.replaceAll(",", ""));
                    blazes.add(ObjectIntPair.of(blaze, health));
                } catch (NumberFormatException e) {
                    TeslaMaps.LOGGER.warn("[DungeonBlaze] Failed to parse blaze health: {}", blazeName);
                }
            }
        }
        return blazes;
    }

    private static void sortBlazes(List<ObjectIntPair<ArmorStandEntity>> blazes) {
        blazes.sort(Comparator.comparingInt(ObjectIntPair::rightInt));
    }

    private static void updateBlazeEntities(List<ObjectIntPair<ArmorStandEntity>> blazes) {
        if (!blazes.isEmpty()) {
            lowestBlaze = blazes.get(0).left();
            int highestIndex = blazes.size() - 1;
            highestBlaze = blazes.get(highestIndex).left();
            if (blazes.size() > 1) {
                nextLowestBlaze = blazes.get(1).left();
                nextHighestBlaze = blazes.get(highestIndex - 1).left();
            }
        }
    }

    private static void renderBlazeOutline(MatrixStack matrices, ArmorStandEntity blaze, ArmorStandEntity nextBlaze, Vec3d cameraPos) {
        // Green box for target blaze
        Box blazeBox = blaze.getBoundingBox().expand(0.3, 0.9, 0.3).offset(0, -1.1, 0);
        ESPRenderer.drawBoxOutline(matrices, blazeBox, 0xFF00FF00, 5.0f, cameraPos); // Green

        if (nextBlaze != null && nextBlaze.isAlive() && nextBlaze != blaze) {
            // White box for next blaze
            Box nextBlazeBox = nextBlaze.getBoundingBox().expand(0.3, 0.9, 0.3).offset(0, -1.1, 0);
            ESPRenderer.drawBoxOutline(matrices, nextBlazeBox, 0xFFFFFFFF, 5.0f, cameraPos); // White

            // Line connecting them
            Vec3d blazeCenter = blazeBox.getCenter();
            Vec3d nextBlazeCenter = nextBlazeBox.getCenter();
            ESPRenderer.drawLine(matrices, blazeCenter, nextBlazeCenter, 0xFFFFFFFF, 1.0f, cameraPos);
        }
    }

    public static void reset() {
        highestBlaze = null;
        lowestBlaze = null;
        nextHighestBlaze = null;
        nextLowestBlaze = null;
        blazeDoneMessageSent = false;
        previousBlazeCount = 0;
    }

    private static void sendBlazeDoneMessage() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        mc.player.networkHandler.sendChatCommand("pc Blaze Done!");
        TeslaMaps.LOGGER.info("[DungeonBlaze] Sent Blaze Done message to party");
    }

    public static boolean isBlazeDone() {
        return blazeDoneMessageSent;
    }
}
