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
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DungeonBlaze {
    private static ArmorStand highestBlaze = null;
    private static ArmorStand lowestBlaze = null;
    private static ArmorStand nextHighestBlaze = null;
    private static ArmorStand nextLowestBlaze = null;
    private static final List<ArmorStand> allBlazes = new ArrayList<>(); // every blaze this tick (for red "wrong" boxes)

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

        if (!blazeDoneMessageSent && TeslaMapsConfig.get().blazeDoneMessage) {
            if (blazes.isEmpty() || (blazes.size() == 1 && blazes.get(0).rightInt() == 0)) {
                if (previousBlazeCount >= 1) {
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
            ArmorStand target = null, next = null;
            if (highestBlaze != null && lowestBlaze != null && highestBlaze.isAlive() && lowestBlaze.isAlive()) {
                if (highestBlaze.getY() < 69) { target = highestBlaze; next = nextHighestBlaze; }
                else if (lowestBlaze.getY() > 69) { target = lowestBlaze; next = nextLowestBlaze; }
            }

            for (ArmorStand blaze : allBlazes) {
                if (blaze == null || !blaze.isAlive() || blaze == target || blaze == next) continue;
                AABB box = blaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
                if (TeslaMapsConfig.get().modernBlazeSolver) ESPRenderer.drawFilledBox(matrices, box, 0x66FF0000, cameraPos);
                ESPRenderer.drawBoxOutline(matrices, box, 0xFFFF0000, 4.0f, cameraPos); // Red = do NOT shoot
            }

            if (target != null) renderBlazeOutline(matrices, target, next, cameraPos);
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
        allBlazes.clear();
        for (ObjectIntPair<ArmorStand> b : blazes) allBlazes.add(b.left());
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
        boolean modern = TeslaMapsConfig.get().modernBlazeSolver;
        AABB blazeBox = blaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
        if (modern) ESPRenderer.drawFilledBox(matrices, blazeBox, 0x6600FF00, cameraPos); // Green fill
        ESPRenderer.drawBoxOutline(matrices, blazeBox, 0xFF00FF00, 5.0f, cameraPos); // Green

        if (nextBlaze != null && nextBlaze.isAlive() && nextBlaze != blaze) {
            AABB nextBlazeBox = nextBlaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
            if (modern) ESPRenderer.drawFilledBox(matrices, nextBlazeBox, 0x66FFFFFF, cameraPos); // White fill
            ESPRenderer.drawBoxOutline(matrices, nextBlazeBox, 0xFFFFFFFF, 5.0f, cameraPos); // White

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
        allBlazes.clear();
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

    public static boolean shouldHideBlaze(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.monster.Blaze)) return false;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return cfg.solveBlaze && cfg.modernBlazeSolver && DungeonManager.isInDungeon();
    }
}
