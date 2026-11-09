package com.teslamaps.features;

// Hitboxes after Devonian's ArrowHitboxes.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ArrowVisuals {

    private static int hitboxColor, hitboxFill;

    private ArrowVisuals() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ArrowHitboxes").section("Render").when(() -> cfg().arrowHitboxes)
                .tick(ArrowVisuals::tickHitboxes)
                .world(ArrowVisuals::renderHitboxes)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Arrows");
                    b.toggle("Arrow Hitboxes", () -> c.arrowHitboxes, v -> c.arrowHitboxes = v)
                            .desc("Draws the hitbox of every arrow.");
                    b.color("  Hitbox Line Color", () -> c.arrowHitboxColor, v -> c.arrowHitboxColor = v);
                    b.color("  Hitbox Fill Color", () -> c.arrowHitboxFillColor, v -> c.arrowHitboxFillColor = v);
                    b.slider("  Hitbox Line Width", 0f, 10f, () -> c.arrowHitboxWidth, v -> c.arrowHitboxWidth = v, 1);
                });
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
