package com.teslamaps.features;

// Ported from Odin's InactiveWaypoints.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class TerminalWaypoints {

    private static final String GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE = "The Core entrance is opening!";

    private record Point(ArmorStand stand, int kind) {}

    private static final String[] LABELS = {"Terminal", "Device", "Lever"};

    private static final List<Point> points = new ArrayList<>();
    private static final List<ArmorStand> hidden = new ArrayList<>();
    private static Object lastLevel;
    private static int scanTick;

    private static boolean p3;

    private TerminalWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TerminalWaypoints").when(() -> cfg().terminalWaypoints)
                .serverChat(TerminalWaypoints::onChat)
                .tick(TerminalWaypoints::tick);
        Feature.of("TerminalWaypoints.render").when(() -> cfg().terminalWaypoints)
                .world(TerminalWaypoints::render);
    }

    private static boolean floor7() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == 7 && DungeonManager.isInDungeon();
    }

    private static void onChat(String raw) {
        if (!floor7()) return;
        String text = StringUtil.stripTrim(raw);
        if (text.equals(GOLDOR)) {
            reset();
            p3 = true;
        } else if (p3 && text.equals(CORE)) {
            reset();
        }
    }

    private static void reset() {
        p3 = false;
        points.clear();
        unhide();
    }

    private static void unhide() {
        for (ArmorStand s : hidden) s.setCustomNameVisible(true);
        hidden.clear();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            hidden.clear();
            reset();
        }
        if (!floor7()) {
            if (p3) reset();
            return;
        }
        TeslaMapsConfig c = cfg();

        if (!c.terminalWaypoints || !p3) {
            points.clear();
            unhide();
            return;
        }
        if (++scanTick >= 10) {
            scanTick = 0;
            points.clear();
            for (ArmorStand s : com.teslamaps.utils.EntityCache.namedArmorStands()) {
                String name = StringUtil.stripTrim(s.getName().getString());
                int kind = switch (name) {
                    case "Inactive Terminal" -> 0;
                    case "Inactive" -> 1;
                    case "Not Activated" -> 2;
                    default -> -1;
                };
                if (kind >= 0) points.add(new Point(s, kind));
            }
        }
        if (c.terminalWaypointsHideNames) {
            for (Point p : points) {
                if (p.stand.isRemoved() || !p.stand.isCustomNameVisible()) continue;
                p.stand.setCustomNameVisible(false);
                hidden.add(p.stand);
            }
        } else {
            unhide();
        }
    }

    private static boolean shown(int kind, TeslaMapsConfig c) {
        return switch (kind) {
            case 0 -> c.terminalWaypointsTerms;
            case 1 -> c.terminalWaypointsDevices;
            default -> c.terminalWaypointsLevers;
        };
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (points.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        int color = TeslaMapsConfig.parseColor(c.terminalWaypointsColor);
        boolean walls = c.terminalWaypointsThroughWalls;
        for (Point p : points) {
            ArmorStand s = p.stand;
            if (s.isRemoved() || !shown(p.kind, c)) continue;
            double x = s.getX(), y = s.getY(), z = s.getZ();
            if (c.terminalWaypointsBox)
                ESPRenderer.drawBoxOutline(pose, new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5), color | 0xFF000000, 2f, cam, walls);
            if (c.terminalWaypointsText)
                ESPRenderer.drawText(pose, LABELS[p.kind], new Vec3(x, y + 2, z), 1.5f, cam, true, walls);
            if (c.terminalWaypointsBeacon)
                ESPRenderer.drawBeaconBeam(pose, s.blockPosition(), color, cam);
        }
    }

}
