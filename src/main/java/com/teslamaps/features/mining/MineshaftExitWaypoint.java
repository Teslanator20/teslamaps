package com.teslamaps.features.mining;

// After NEU's GlaciteMineshaftWaypoints.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Green Exit waypoint where you spawned into the Glacite Mineshaft. */
public final class MineshaftExitWaypoint {

    private static Object lastLevel;
    private static BlockPos spawn, exit;
    private static long joinedAt;
    private static int ticks;
    private static String label = "§aExit";

    private MineshaftExitWaypoint() {}

    public static void register() {
        Feature.of("MineshaftExitWaypoint").section("Mining").when(() -> TeslaMapsConfig.get().mineshaftExitWaypoint)
                .tick(MineshaftExitWaypoint::tick)
                .world(MineshaftExitWaypoint::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            spawn = mc.player.blockPosition();
            exit = null;
            joinedAt = System.currentTimeMillis();
        }
        if (++ticks % 10 != 0) return;
        boolean inShaft = SkyblockUtils.getArea().toLowerCase(Locale.ROOT).contains("mineshaft");
        if (!inShaft) {
            exit = null;
            return;
        }
        if (exit == null && spawn != null && System.currentTimeMillis() - joinedAt < 15_000) exit = spawn;
        if (exit != null) {
            double d = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(exit)));
            label = "§aExit §7(" + Math.round(d) + "m)";
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        BlockPos p = exit;
        if (p == null) return;
        ESPRenderer.drawFilledBox(pose, new AABB(p), 0x7020CC00, cam, true);
        WaypointSet.drawLabel(pose, cam, label, Vec3.atCenterOf(p.above()), 1f, true);
    }
}
