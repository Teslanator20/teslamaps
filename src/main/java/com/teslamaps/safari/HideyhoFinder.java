package com.teslamaps.safari;

// Ported from SkyHanni's HideyhoFinder.kt, spots from the SkyHanni repo SAFARI island graph
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** After "No peeking!" walks you through every known hiding spot until Hideyho is in sight. */
public final class HideyhoFinder {

    private static final int[][] SPOTS = {
            {37, 69, -16}, {1, 70, -54}, {11, 70, -62}, {-5, 70, -81}, {-4, 67, -64},
            {17, 78, -63}, {13, 78, -50}, {0, 78, -79}, {-8, 78, -83}, {-20, 78, -78},
            {-13, 78, -83}, {-21, 78, -50}, {-15, 78, -57}, {-17, 70, -59}, {28, 70, -55},
            {-20, 70, -80}, {19, 69, -24}, {13, 70, -88}, {-28, 71, -81}, {-7, 86, -63},
    };
    private static final double ARRIVE_SQ = 5.0 * 5.0;
    private static final double SEEN_RANGE = 5.0;
    private static final int COLOR = 0xFFAA55FF;

    private static Vec3 start;
    private static final List<BlockPos> route = new ArrayList<>();
    private static String label = "";
    private static int ticks;

    private HideyhoFinder() {}

    public static void register() {
        Feature.of("HideyhoFinder").section("Critter Safari").when(() -> TeslaMapsConfig.get().safariHideyhoFinder)
                .serverChat(HideyhoFinder::onChat)
                .tick(HideyhoFinder::tick)
                .world(HideyhoFinder::render);
    }

    private static void onChat(String raw) {
        if (!SafariData.inSafari()) return;
        String msg = StringUtil.strip(raw).trim();
        if (!msg.startsWith("[MOB] Hideyho:")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (msg.endsWith("Hehe, you found me!")) {
            start = mc.player.position();
        } else if (msg.endsWith("No peeking!")) {
            // it teleports first
            TickScheduler.schedule(40, HideyhoFinder::begin);
        } else if (msg.endsWith("Aah! You found me!")) {
            route.clear();
        }
    }

    private static void begin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SafariData.inSafari()) return;
        List<BlockPos> left = new ArrayList<>();
        for (int[] s : SPOTS) left.add(new BlockPos(s[0], s[1], s[2]));
        Vec3 from = start != null ? start : mc.player.position();
        left.remove(nearest(left, from));

        route.clear();
        Vec3 at = mc.player.position();
        while (!left.isEmpty()) {
            BlockPos next = nearest(left, at);
            left.remove(next);
            route.add(next);
            at = Vec3.atCenterOf(next);
        }
        ticks = 0;
        updateLabel();
    }

    private static BlockPos nearest(List<BlockPos> list, Vec3 to) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : list) {
            double d = Vec3.atCenterOf(p).distanceToSqr(to);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static void tick() {
        if (route.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SafariData.inSafari()) {
            route.clear();
            return;
        }
        if (++ticks < 10) return;
        ticks = 0;

        for (BlockPos p : route) {
            if (hideyhoNear(mc, p)) {
                route.clear();
                say(mc, "§aFound Hideyho!");
                return;
            }
        }
        if (mc.player.distanceToSqr(Vec3.atCenterOf(route.get(0))) <= ARRIVE_SQ) {
            route.remove(0);
            if (route.isEmpty()) {
                say(mc, "§7Could not find any hidden Hideyho, maybe someone else already found it.");
                return;
            }
        }
        updateLabel();
    }

    private static boolean hideyhoNear(Minecraft mc, BlockPos p) {
        AABB box = new AABB(p).inflate(SEEN_RANGE);
        for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (s.getCustomName() == null) continue;
            if (!StringUtil.strip(s.getCustomName().getString()).contains("Hideyho")) continue;
            if (mc.player.hasLineOfSight(s)) return true;
        }
        return false;
    }

    private static void updateLabel() {
        Minecraft mc = Minecraft.getInstance();
        if (route.isEmpty() || mc.player == null) return;
        double d = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(route.get(0))));
        label = "§dHideyho spot §7(" + Math.round(d) + "m, " + route.size() + " left)";
    }

    private static void say(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + msg));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (route.isEmpty()) return;
        BlockPos p = route.get(0);
        com.teslamaps.features.waypoints.WaypointSet.draw(pose, cam, p, COLOR, label, true, true, true);
        ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(p), COLOR, cam);
    }
}
