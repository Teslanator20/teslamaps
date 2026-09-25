package com.teslamaps.features.waypoints;

// Ported (slimmed) from SkyHanni's utils/ParkourHelper.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Platforms walked in order: a platform only counts once the player lands on it. */
public final class ParkourHelper {

    public record Shortcut(int from, int to) {}

    private final List<Vec3> locations;
    private final List<Shortcut> shortcuts;
    private final double size;
    private final double range;
    private final boolean inOrder;
    private int current = -1;
    private boolean visible;

    public boolean rainbow = true;
    public int color = 0xB0FFFFFF;
    public int lookAhead = 3;
    public boolean showShortcuts = true;

    public ParkourHelper(List<Vec3> locations, List<Shortcut> shortcuts, double size, double range, boolean inOrder) {
        this.locations = locations;
        this.shortcuts = shortcuts;
        this.size = size;
        this.range = range;
        this.inOrder = inOrder;
    }

    public boolean inParkour() { return current != -1; }

    public void reset() {
        current = -1;
        visible = false;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || locations.isEmpty()) return;
        Vec3 p = mc.player.position();
        if (current == locations.size() - 1) visible = false;
        if (visible && mc.player.onGround()) {
            for (int i = 0; i < locations.size(); i++) {
                if (inOrder && (i < current - 1 || i > current + 1)) continue;
                if (center(locations.get(i)).distanceTo(p) < range) current = i;
            }
        }
        double start = center(locations.get(0)).distanceTo(p);
        if (start < range) visible = true;
        else if (start > 15 && current < 1) visible = false;
    }

    public void render(PoseStack pose, Vec3 cam) {
        if (!visible || current < 0) return;
        int end = Math.min(locations.size(), current + lookAhead);
        for (int i = current; i < end; i++) {
            Vec3 l = locations.get(i);
            ESPRenderer.drawFilledBox(pose, box(l), colorFor(i), cam, true);
            if (i + 1 < end) ESPRenderer.drawLine(pose, center(l), center(locations.get(i + 1)), colorFor(i) | 0xFF000000, 3f, cam, true);
        }
        if (!showShortcuts) return;
        for (Shortcut s : shortcuts) {
            if (s.from < current || s.from >= end || s.to < 0 || s.to >= locations.size()) continue;
            Vec3 from = center(locations.get(s.from)), to = center(locations.get(s.to));
            ESPRenderer.drawLine(pose, from, to, 0xFFFF5555, 3f, cam, true);
            Vec3 dir = to.subtract(from).normalize();
            ESPRenderer.drawText(pose, "§cShortcut", from.add(dir).add(0, 1, 0), 1.5f, cam);
            ESPRenderer.drawFilledBox(pose, box(locations.get(s.to)), 0xB0FF5555, cam, true);
        }
    }

    private AABB box(Vec3 l) {
        return new AABB(l.x, l.y, l.z, l.x + size, l.y + 1, l.z + size).inflate(0.05);
    }

    private Vec3 center(Vec3 l) {
        return l.add(size / 2, 1.0, size / 2);
    }

    private int colorFor(int index) {
        if (!rainbow) return color;
        float hue = ((-index / 12f) % 1f + 1f) % 1f;
        return 0xB0000000 | (java.awt.Color.HSBtoRGB(hue, 1f, 0.7f) & 0xFFFFFF);
    }
}
