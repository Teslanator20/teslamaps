package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Blaze puzzle solver - highlights which blaze to kill.
 * Blaze puzzle solver - shows kill order.
 */
public class DungeonBlaze {
    private static ArmorStand highestBlaze = null;
    private static ArmorStand lowestBlaze = null;
    private static ArmorStand nextHighestBlaze = null;
    private static ArmorStand nextLowestBlaze = null;

    private static boolean blazeDoneMessageSent = false;
    private static int previousBlazeCount = 0;

    public static void tick() {
        if (!DungeonManager.isInDungeon() || !TeslaMapsConfig.get().solveBlaze) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        List<ObjectIntPair<ArmorStand>> blazes = getBlazesInWorld();
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

    public static void render(PoseStack matrices, Vec3 cameraPos) {
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

    private static List<ObjectIntPair<ArmorStand>> getBlazesInWorld() {
        Minecraft mc = Minecraft.getInstance();
        List<ObjectIntPair<ArmorStand>> blazes = new ArrayList<>();

        if (mc.level == null || mc.player == null) return blazes;

        AABB searchBox = mc.player.getBoundingBox().inflate(500);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                ArmorStand.class,
                searchBox,
                entity -> !entity.isVehicle()
        );

        for (ArmorStand blaze : armorStands) {
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

    private static void sortBlazes(List<ObjectIntPair<ArmorStand>> blazes) {
        blazes.sort(Comparator.comparingInt(ObjectIntPair::rightInt));
    }

    private static void updateBlazeEntities(List<ObjectIntPair<ArmorStand>> blazes) {
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

    private static void renderBlazeOutline(PoseStack matrices, ArmorStand blaze, ArmorStand nextBlaze, Vec3 cameraPos) {
        // Green box for target blaze
        AABB blazeBox = blaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
        ESPRenderer.drawBoxOutline(matrices, blazeBox, 0xFF00FF00, 5.0f, cameraPos); // Green

        if (nextBlaze != null && nextBlaze.isAlive() && nextBlaze != blaze) {
            // White box for next blaze
            AABB nextBlazeBox = nextBlaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
            ESPRenderer.drawBoxOutline(matrices, nextBlazeBox, 0xFFFFFFFF, 5.0f, cameraPos); // White

            // Line connecting them
            Vec3 blazeCenter = blazeBox.getCenter();
            Vec3 nextBlazeCenter = nextBlazeBox.getCenter();
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.connection.sendCommand("pc Blaze Done!");
        TeslaMaps.LOGGER.info("[DungeonBlaze] Sent Blaze Done message to party");
    }

    public static boolean isBlazeDone() {
        return blazeDoneMessageSent;
    }
}
