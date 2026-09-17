package com.teslamaps.features.npc;

// Ported from bax's NpcFinder.java

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NpcFinder {

    private static final double CLEAR_DISTANCE = 5.0;
    private static final List<NpcData> targets = new ArrayList<>();
    private static String lastArea = "";

    public static void setTargets(List<NpcData> npcs) {
        targets.clear();
        targets.addAll(npcs);
        lastArea = SkyblockUtils.getArea();
    }

    public static void clearTargets() {
        targets.clear();
    }

    public static boolean hasTarget() {
        return !targets.isEmpty();
    }

    public static boolean isOnTargetIsland(NpcData npc) {
        return SkyblockUtils.getArea().equalsIgnoreCase(npc.island());
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().npcFinder || targets.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String island = targets.get(0).island();
        String area = SkyblockUtils.getArea();
        if (!area.isEmpty() && !area.equals(lastArea)) {
            lastArea = area;
            if (!area.equalsIgnoreCase(island)) {
                targets.clear();
                return;
            }
        }
        if (!area.isEmpty() && !area.equalsIgnoreCase(island)) return;

        NpcData closest = null;
        double closestDist = Double.MAX_VALUE;
        for (NpcData npc : targets) {
            double dist = mc.player.position().distanceTo(new Vec3(npc.x(), npc.y(), npc.z()));
            if (dist < CLEAR_DISTANCE) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Arrived at §f" + npc.name()));
                targets.clear();
                return;
            }
            if (dist < closestDist) {
                closestDist = dist;
                closest = npc;
            }
        }

        for (NpcData npc : targets) {
            if (targets.size() == 1 && npc == closest) {
                ESPRenderer.drawTracerFromCamera(matrices, new Vec3(npc.x(), npc.y() + 1.0, npc.z()), 0xFF00FFFF, cameraPos);
            }
            ESPRenderer.drawText(matrices, "§b" + npc.name(), new Vec3(npc.x(), npc.y() + 3.0, npc.z()), 1.0f, cameraPos);
        }
    }
}
