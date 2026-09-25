package com.teslamaps.features.waypoints;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Waypoints a feature owns: beam, box, label and distance, optional expiry and
 * remove-on-arrival. The label text is rebuilt in {@link #tick()}, render only draws.
 */
public final class WaypointSet {

    public static final class Waypoint {
        public final BlockPos pos;
        public final String label;
        public final int color;
        final long expiresAt;
        String text;

        Waypoint(BlockPos pos, String label, int color, long expiresAt) {
            this.pos = pos;
            this.label = label;
            this.color = color;
            this.expiresAt = expiresAt;
            this.text = label;
        }
    }

    private static final Pattern XYZ =
            Pattern.compile("x:\\s*(-?\\d+)[,.]?\\s*y:\\s*(-?\\d+)[,.]?\\s*z:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRIPLE = Pattern.compile("(?<![\\w.-])(-?\\d{1,7})[ ,]+(-?\\d{1,3})[ ,]+(-?\\d{1,7})(?![\\w.])");

    private final List<Waypoint> list = new ArrayList<>();
    private boolean beam = true, box = true, label = true, distance = true, throughWalls = true;
    private double removeWithin = -1;

    public WaypointSet beam(boolean on) { beam = on; return this; }
    public WaypointSet box(boolean on) { box = on; return this; }
    public WaypointSet label(boolean on) { label = on; return this; }
    public WaypointSet distance(boolean on) { distance = on; return this; }
    public WaypointSet throughWalls(boolean on) { throughWalls = on; return this; }
    /** Drops a waypoint once the player is this close. */
    public WaypointSet removeWithin(double blocks) { removeWithin = blocks; return this; }

    /** "x: 1, y: 2, z: 3" (Patcher / SkyHanni / our /tmap coords share format). */
    public static BlockPos fromChat(String message) {
        Matcher m = XYZ.matcher(StringUtil.strip(message));
        return m.find() ? pos(m) : null;
    }

    /** Also accepts a bare "12 70 -3". Only for lines that are known to carry coordinates. */
    public static BlockPos fromChatLoose(String message) {
        BlockPos p = fromChat(message);
        if (p != null) return p;
        Matcher m = TRIPLE.matcher(StringUtil.strip(message));
        return m.find() ? pos(m) : null;
    }

    private static BlockPos pos(Matcher m) {
        try {
            return new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code durationMs <= 0} = stays until removed. A second waypoint on the same block replaces the first. */
    public Waypoint add(BlockPos pos, String label, int color, long durationMs) {
        if (pos == null) return null;
        remove(pos);
        Waypoint w = new Waypoint(pos, label, color, durationMs > 0 ? System.currentTimeMillis() + durationMs : 0);
        list.add(w);
        return w;
    }

    public void remove(BlockPos pos) {
        list.removeIf(w -> w.pos.equals(pos));
    }

    public void clear() {
        list.clear();
    }

    public List<Waypoint> all() {
        return list;
    }

    public void tick() {
        if (list.isEmpty()) return;
        long now = System.currentTimeMillis();
        var player = Minecraft.getInstance().player;
        list.removeIf(w -> {
            if (w.expiresAt > 0 && now >= w.expiresAt) return true;
            if (player == null) return false;
            double d = Math.sqrt(player.distanceToSqr(w.pos.getX() + 0.5, w.pos.getY() + 0.5, w.pos.getZ() + 0.5));
            if (removeWithin > 0 && d <= removeWithin) return true;
            w.text = distance ? w.label + " §7(" + Math.round(d) + "m)" : w.label;
            return false;
        });
    }

    public void render(PoseStack pose, Vec3 cam) {
        for (int i = 0, n = list.size(); i < n; i++) {
            Waypoint w = list.get(i);
            draw(pose, cam, w.pos, w.color, label ? w.text : null, beam, box, throughWalls);
        }
    }

    /** One waypoint: beam, box outline and label, the look every waypoint feature shares. */
    public static void draw(PoseStack pose, Vec3 cam, BlockPos pos, int color, String text,
                            boolean beam, boolean box, boolean throughWalls) {
        if (beam) ESPRenderer.drawBeaconBeam(pose, pos, color, cam);
        if (box) ESPRenderer.drawBoxOutline(pose, new AABB(pos), color | 0xFF000000, 2f, cam, throughWalls);
        if (text != null && !text.isEmpty()) {
            drawLabel(pose, cam, text, new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5), 1.0f, throughWalls);
        }
    }

    /** Waypoint label; gets the shared background box when "Waypoint Label Background" is on. */
    public static void drawLabel(PoseStack pose, Vec3 cam, String text, Vec3 at, float scale, boolean throughWalls) {
        ESPRenderer.drawText(pose, text, at, scale, cam, true, throughWalls, 0xFFFFFFFF,
                com.teslamaps.config.TeslaMapsConfig.get().waypointLabelBackground ? 0x66000000 : 0);
    }

    public static void drawLabel(PoseStack pose, Vec3 cam, String text, Vec3 at, float scale) {
        drawLabel(pose, cam, text, at, scale, true);
    }
}
