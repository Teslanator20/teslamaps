package com.teslamaps.safari;

// Ported from critterMod's Visibility.java (hasLineTo)
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Whether any part of an entity is unobstructed from the eyes, whichever way you face. */
public final class SafariSight {

    private static final double MAX_RANGE = 128.0;
    private static final long PAINTING_CACHE_MS = 1000;
    private static List<AABB> paintings = List.of();
    private static long paintingsAt;

    private SafariSight() {}

    public static boolean hasLineTo(Entity target) {
        if (target == null) return false;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return false;

        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        if (eye.distanceToSqr(box.getCenter()) > MAX_RANGE * MAX_RANGE) return false;

        for (Vec3 point : samples(box)) {
            HitResult hit = mc.level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) continue;
            if (behindPainting(mc, eye, point)) continue;
            return true;
        }
        return false;
    }

    // Hideonwalls sit behind paintings, and clip() goes straight through entities
    private static boolean behindPainting(Minecraft mc, Vec3 from, Vec3 to) {
        long now = System.currentTimeMillis();
        if (now - paintingsAt >= PAINTING_CACHE_MS) {
            paintingsAt = now;
            List<AABB> found = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Painting) found.add(e.getBoundingBox());
            }
            paintings = found;
        }
        for (AABB p : paintings) {
            if (p.clip(from, to).isPresent()) return true;
        }
        return false;
    }

    private static Vec3[] samples(AABB box) {
        Vec3 c = box.getCenter();
        double ix = box.getXsize() * 0.25, iy = box.getYsize() * 0.1, iz = box.getZsize() * 0.25;
        return new Vec3[] {
                c,
                new Vec3(c.x, box.maxY - iy, c.z),
                new Vec3(c.x, box.minY + iy, c.z),
                new Vec3(c.x - ix, c.y, c.z - iz),
                new Vec3(c.x + ix, c.y, c.z + iz),
        };
    }
}
