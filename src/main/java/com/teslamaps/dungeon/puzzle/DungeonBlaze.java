package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DungeonBlaze {
    private static final List<ArmorStand> ordered = new ArrayList<>(); // shooting order, empty = direction unknown
    private static final List<ArmorStand> allBlazes = new ArrayList<>(); // every blaze this tick (for red "wrong" boxes)

    private static boolean blazeDoneMessageSent = false;
    private static boolean sameHpWarned = false;
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

        DungeonRoom room = DungeonManager.getCurrentRoom();
        String roomName = room != null ? room.getName() : null;
        if (roomName != null && !roomName.equals("Blaze") && !roomName.equals("Higher Blaze") && !roomName.equals("Lower Blaze")) {
            ordered.clear();
            allBlazes.clear();
            previousBlazeCount = 0;
            return;
        }

        List<ObjectIntPair<ArmorStand>> blazes = getBlazesInWorld();
        blazes.sort(Comparator.comparingInt(ObjectIntPair::rightInt));
        updateBlazeEntities(blazes, roomName);

        boolean done = previousBlazeCount >= 1 && (blazes.isEmpty() || (blazes.size() == 1 && blazes.get(0).rightInt() == 0));
        if (done && roomName != null) PuzzleTimers.onPuzzleComplete(roomName);
        if (done && !blazeDoneMessageSent && TeslaMapsConfig.get().blazeDoneMessage) {
            sendBlazeDoneMessage();
            blazeDoneMessageSent = true;
        }

        previousBlazeCount = blazes.size();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.solveBlaze || !DungeonManager.isInDungeon()) {
            return;
        }

        try {
            boolean modern = cfg.modernBlazeSolver;
            int rest = TeslaMapsConfig.parseColor(cfg.blazeColorRest);
            int shown = Math.min(3, ordered.size());

            for (ArmorStand blaze : allBlazes) {
                if (blaze == null || !blaze.isAlive()) continue;
                boolean highlighted = false;
                for (int i = 0; i < shown; i++) if (ordered.get(i) == blaze) highlighted = true;
                if (highlighted) continue;
                AABB box = boxOf(blaze);
                if (modern) ESPRenderer.drawFilledBox(matrices, box, fill(rest), cameraPos);
                ESPRenderer.drawBoxOutline(matrices, box, rest | 0xFF000000, 4.0f, cameraPos); // do NOT shoot
            }

            if (ordered.isEmpty() || !ordered.get(0).isAlive()) return;

            Vec3 prev = null;
            for (int i = 0; i < ordered.size(); i++) {
                ArmorStand blaze = ordered.get(i);
                if (!blaze.isAlive()) break;
                AABB box = boxOf(blaze);
                Vec3 center = box.getCenter();
                if (i < 3) {
                    int c = TeslaMapsConfig.parseColor(i == 0 ? cfg.blazeColorFirst : i == 1 ? cfg.blazeColorSecond : cfg.blazeColorThird);
                    if (modern) ESPRenderer.drawFilledBox(matrices, box, fill(c), cameraPos);
                    ESPRenderer.drawBoxOutline(matrices, box, c | 0xFF000000, 5.0f, cameraPos);
                }
                if (i == 0 && cfg.blazeTracer) {
                    ESPRenderer.drawTracerFromCamera(matrices, center, TeslaMapsConfig.parseColor(cfg.blazeColorFirst) | 0xFF000000, cameraPos);
                }
                if (i > 0 && i <= cfg.blazeLineAmount && prev != null) {
                    int c = i < 3 ? TeslaMapsConfig.parseColor(i == 1 ? cfg.blazeColorSecond : cfg.blazeColorThird) : rest;
                    ESPRenderer.drawLine(matrices, prev, center, c | 0xFF000000, cfg.blazeLineWidth, cameraPos);
                }
                if (i >= Math.max(2, cfg.blazeLineAmount)) break;
                prev = center;
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[DungeonBlaze] Error rendering", e);
        }
    }

    private static AABB boxOf(ArmorStand blaze) {
        return blaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
    }

    private static int fill(int c) {
        return (c & 0xFFFFFF) | 0x66000000;
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
                    int health = Integer.parseInt(healthStr.replace(",", ""));
                    blazes.add(ObjectIntPair.of(blaze, health));
                } catch (NumberFormatException e) {
                    TeslaMaps.LOGGER.warn("[DungeonBlaze] Failed to parse blaze health: {}", blazeName);
                }
            }
        }
        return blazes;
    }

    /** blazes sorted by HP ascending. */
    private static void updateBlazeEntities(List<ObjectIntPair<ArmorStand>> blazes, String roomName) {
        allBlazes.clear();
        ordered.clear();
        for (ObjectIntPair<ArmorStand> b : blazes) allBlazes.add(b.left());
        if (blazes.isEmpty()) return;

        ArmorStand lowest = blazes.get(0).left();
        ArmorStand highest = blazes.get(blazes.size() - 1).left();
        boolean descending;
        if ("Lower Blaze".equals(roomName)) descending = true;
        else if ("Higher Blaze".equals(roomName)) descending = false;
        else if (highest.getY() < 69) descending = true;
        else if (lowest.getY() > 69) descending = false;
        else return;

        if (descending) for (int i = blazes.size() - 1; i >= 0; i--) ordered.add(blazes.get(i).left());
        else ordered.addAll(allBlazes);

        if (TeslaMapsConfig.get().blazeSameHpWarning && !sameHpWarned) {
            for (int i = 1; i < blazes.size(); i++) {
                if (blazes.get(i).rightInt() == blazes.get(i - 1).rightInt()) {
                    sameHpWarned = true;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                            "§b[TeslaMaps] §cTwo blazes have the same HP (" + blazes.get(i).rightInt() + ") — order may be wrong"));
                    break;
                }
            }
        }
    }

    public static void reset() {
        ordered.clear();
        allBlazes.clear();
        blazeDoneMessageSent = false;
        sameHpWarned = false;
        previousBlazeCount = 0;
    }

    private static void sendBlazeDoneMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.connection.sendCommand("pc Blaze Done!");
        TeslaMaps.debug("[DungeonBlaze] Sent Blaze Done message to party");
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
