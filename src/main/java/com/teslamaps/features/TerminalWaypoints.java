package com.teslamaps.features;

// Ported from Odin's InactiveWaypoints.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TerminalWaypoints {

    private static final Pattern COMPLETED = Pattern.compile("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$");
    private static final String GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE = "The Core entrance is opening!";
    private static final String GATE = "The gate has been destroyed!";

    private static final List<DungeonTimers.Row> SAMPLE = List.of(
            new DungeonTimers.Row("§6Levers §c1§8/§a2", null),
            new DungeonTimers.Row("§6Terms §c2§8/§a4", null),
            new DungeonTimers.Row("§6Device §a✔", null),
            new DungeonTimers.Row("§6Gate §c✘", null));

    private record Point(ArmorStand stand, int kind) {}

    private static final String[] LABELS = {"Terminal", "Device", "Lever"};

    private static final List<Point> points = new ArrayList<>();
    private static final List<ArmorStand> hidden = new ArrayList<>();
    private static List<DungeonTimers.Row> rows;
    private static Object lastLevel;
    private static int scanTick;

    private static boolean p3, firstInSection, complete, device, gate;
    private static int lastCompleted, terminals, section = 1, levers;

    private TerminalWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TerminalWaypoints").when(() -> cfg().terminalWaypoints || cfg().termInfoHud)
                .serverChat(TerminalWaypoints::onChat)
                .tick(TerminalWaypoints::tick);
        Feature.of("TerminalWaypoints.render").when(() -> cfg().terminalWaypoints)
                .world(TerminalWaypoints::render);
        Feature.of("TermInfoHud").when(() -> cfg().termInfoHud)
                .hud("terminfo", TerminalWaypoints::renderHud);
        HudEditElement.of("termInfo", "Term Info").ctx(HudCtx.DUNGEON)
                .enabled(() -> cfg().termInfoHud)
                .size(() -> Minecraft.getInstance().font.width("§6Levers §c1§8/§a2"), () -> 36)
                .scaleRange(0.5f, 5.0f)
                .preview((g, m, x, y, s) -> DungeonTimers.drawRows(g, m, x, y, s, SAMPLE));
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
            section = 1;
            return;
        }
        if (!p3) return;
        if (text.equals(CORE)) {
            reset();
            return;
        }
        if (text.equals(GATE)) {
            gate = true;
            if (complete) newSection();
            return;
        }
        Matcher m = COMPLETED.matcher(text);
        if (!m.matches()) return;
        int done = Integer.parseInt(m.group(4));
        int max = Integer.parseInt(m.group(5));
        if (done == 1) firstInSection = true;
        if (done == max) {
            if (gate) newSection(); else complete = true;
            return;
        }
        switch (m.group(3)) {
            case "lever" -> levers++;
            case "terminal" -> terminals++;
            default -> { if (!firstInSection || lastCompleted != done) device = true; }
        }
        lastCompleted = done;
    }

    private static void newSection() {
        firstInSection = false;
        complete = false;
        device = false;
        terminals = 0;
        gate = false;
        levers = 0;
        section++;
    }

    private static void reset() {
        p3 = false;
        firstInSection = false;
        lastCompleted = 0;
        complete = false;
        device = false;
        terminals = 0;
        gate = false;
        section = 1;
        levers = 0;
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
            rows = null;
            return;
        }
        TeslaMapsConfig c = cfg();

        if (c.termInfoHud && p3) {
            int need = section == 2 ? 5 : 4;
            rows = List.of(
                    new DungeonTimers.Row("§6Levers " + (levers == 2 ? "§a" : "§c") + levers + "§8/§a2", null),
                    new DungeonTimers.Row("§6Terms " + (terminals == need ? "§a" : "§c") + terminals + "§8/§a" + need, null),
                    new DungeonTimers.Row("§6Device " + (device ? "§a✔" : "§c✘"), null),
                    new DungeonTimers.Row("§6Gate " + (gate ? "§a✔" : "§c✘"), null));
        } else {
            rows = null;
        }

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

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<DungeonTimers.Row> r = rows;
        if (r == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        TeslaMapsConfig c = cfg();
        DungeonTimers.drawRows(g, mc, c.termInfoX, c.termInfoY, c.termInfoScale, r);
    }
}
