package com.teslamaps.features;

// Trail after SkyHanni's ArrowTrail.kt, hitboxes after Devonian's ArrowHitboxes.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class ArrowVisuals {

    private record Segment(Vec3 from, Vec3 to, long death, boolean own) {}

    private static final ArrayDeque<Segment> TRAIL = new ArrayDeque<>();
    private static Object lastLevel;
    private static int trailColor, ownColor, hitboxColor, hitboxFill;

    private ArrowVisuals() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ArrowTrail").section("Render").when(() -> cfg().arrowTrail)
                .tick(ArrowVisuals::tickTrail)
                .world(ArrowVisuals::renderTrail);
        Feature.of("ArrowHitboxes").section("Render").when(() -> cfg().arrowHitboxes)
                .tick(ArrowVisuals::tickHitboxes)
                .world(ArrowVisuals::renderHitboxes)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Arrows");
                    b.toggle("Arrow Trail", () -> c.arrowTrail, v -> c.arrowTrail = v)
                            .desc("Draws a short line behind flying arrows.");
                    b.toggle("  Only My Arrows", () -> c.arrowTrailOnlyOwn, v -> c.arrowTrailOnlyOwn = v);
                    b.color("  Trail Color", () -> c.arrowTrailColor, v -> c.arrowTrailColor = v);
                    b.toggle("  Own Arrows In Own Color", () -> c.arrowTrailOwnColor, v -> c.arrowTrailOwnColor = v);
                    b.color("    Own Trail Color", () -> c.arrowTrailOwnColorHex, v -> c.arrowTrailOwnColorHex = v);
                    b.slider("  Trail Time (s)", 0.1f, 5f, () -> c.arrowTrailSeconds, v -> c.arrowTrailSeconds = v, 1);
                    b.slider("  Trail Width", 1f, 10f, () -> c.arrowTrailWidth, v -> c.arrowTrailWidth = v, 1);
                    b.toggle("Arrow Hitboxes", () -> c.arrowHitboxes, v -> c.arrowHitboxes = v)
                            .desc("Draws the hitbox of every arrow.");
                    b.color("  Hitbox Line Color", () -> c.arrowHitboxColor, v -> c.arrowHitboxColor = v);
                    b.color("  Hitbox Fill Color", () -> c.arrowHitboxFillColor, v -> c.arrowHitboxFillColor = v);
                    b.slider("  Hitbox Line Width", 0f, 10f, () -> c.arrowHitboxWidth, v -> c.arrowHitboxWidth = v, 1);
                });
    }

    private static void tickTrail() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            TRAIL.clear();
        }
        TeslaMapsConfig c = cfg();
        trailColor = TeslaMapsConfig.parseColor(c.arrowTrailColor);
        ownColor = c.arrowTrailOwnColor ? TeslaMapsConfig.parseColor(c.arrowTrailOwnColorHex) : trailColor;
        long now = System.currentTimeMillis();
        while (!TRAIL.isEmpty() && TRAIL.peekFirst().death <= now) TRAIL.pollFirst();
        long death = now + (long) (c.arrowTrailSeconds * 1000);
        for (AbstractArrow a : EntityCache.byType(AbstractArrow.class)) {
            if (a.getX() == a.xo && a.getY() == a.yo && a.getZ() == a.zo) continue;
            boolean own = mc.player != null && a.getOwner() == mc.player;
            if (c.arrowTrailOnlyOwn && !own) continue;
            if (TRAIL.size() >= 4000) TRAIL.pollFirst();
            TRAIL.addLast(new Segment(new Vec3(a.xo, a.yo, a.zo), a.position(), death, own));
        }
    }

    private static void renderTrail(PoseStack pose, Vec3 cam) {
        if (TRAIL.isEmpty()) return;
        float w = cfg().arrowTrailWidth;
        for (Iterator<Segment> it = TRAIL.iterator(); it.hasNext(); ) {
            Segment s = it.next();
            ESPRenderer.drawLine(pose, s.from, s.to, s.own ? ownColor : trailColor, w, cam, false);
        }
    }

    private static void tickHitboxes() {
        TeslaMapsConfig c = cfg();
        hitboxColor = TeslaMapsConfig.parseColor(c.arrowHitboxColor);
        hitboxFill = TeslaMapsConfig.parseColor(c.arrowHitboxFillColor);
    }

    private static void renderHitboxes(PoseStack pose, Vec3 cam) {
        float w = cfg().arrowHitboxWidth;
        boolean line = w > 0 && (hitboxColor >>> 24) != 0;
        boolean fill = (hitboxFill >>> 24) != 0;
        if (!line && !fill) return;
        for (AbstractArrow a : EntityCache.byType(AbstractArrow.class)) {
            AABB box = ESPRenderer.lerpBox(a);
            if (fill) ESPRenderer.drawFilledBox(pose, box, hitboxFill, cam, false);
            if (line) ESPRenderer.drawBoxOutline(pose, box, hitboxColor, w, cam, false);
        }
    }
}
