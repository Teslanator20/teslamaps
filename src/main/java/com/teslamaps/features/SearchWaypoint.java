package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Waypoints set by clicking a chest hit in the item search. Gone on open or after 2 minutes. */
public class SearchWaypoint {

    private static final long DURATION_MS = 120_000;

    private record WP(BlockPos pos, String label, long until) {}
    private static final List<WP> waypoints = new ArrayList<>();

    public static void add(BlockPos pos, String label) {
        waypoints.removeIf(w -> w.pos.equals(pos));
        waypoints.add(new WP(pos, label, System.currentTimeMillis() + DURATION_MS));
    }

    public static void onChestClick(BlockPos pos) {
        waypoints.removeIf(w -> w.pos.equals(pos));
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (waypoints.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<WP> it = waypoints.iterator(); it.hasNext(); ) {
            WP w = it.next();
            if (now >= w.until) { it.remove(); continue; }
            AABB box = new AABB(w.pos);
            ESPRenderer.drawFilledBox(matrices, box, 0x40FFAA00, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, box, 0xFFFFAA00, 2.0f, cameraPos, true);
            Vec3 txt = new Vec3(w.pos.getX() + 0.5, w.pos.getY() + 1.5, w.pos.getZ() + 0.5);
            ESPRenderer.drawText(matrices, "§6" + w.label, txt, 1.0f, cameraPos);
        }
    }

    public static void reset() { waypoints.clear(); }
}
