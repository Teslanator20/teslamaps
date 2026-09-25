package com.teslamaps.features;

// Ported from Devonian's features/misc/ChangeCrouchHeight.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

public final class ChangeCrouch {

    private static Feature feature;
    private static boolean wasCrouching;

    private ChangeCrouch() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ChangeCrouch").when(() -> cfg().changeCrouch)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Crouch");
                    b.toggle("Change Crouch", () -> c.changeCrouch, v -> c.changeCrouch = v)
                            .desc("Visual only: camera changes while sneaking.");
                    b.toggle("  Instant Crouch", () -> c.instantCrouch, v -> c.instantCrouch = v)
                            .desc("No camera animation when you start or stop sneaking");
                    b.toggle("  1.8.9 Crouch Height", () -> c.legacyCrouchHeight, v -> c.legacyCrouchHeight = v)
                            .desc("Sneak eye height 1.54, except in the Hub, Park and Galatea (Hypixel uses 1.27 there)");
                });
    }

    private static boolean modernArea() {
        String a = SkyblockUtils.getArea();
        return a.isEmpty() || a.equalsIgnoreCase("Hub") || a.equalsIgnoreCase("The Park") || a.equalsIgnoreCase("Galatea");
    }

    private static float eyeHeight(Entity e) {
        if (cfg().legacyCrouchHeight && e.getPose() == Pose.CROUCHING && !modernArea()) return 1.54f;
        return e.getEyeHeight();
    }

    /** From CameraCrouchMixin after Camera.tick; heights = {old, new}, false keeps vanilla. */
    public static boolean apply(Entity entity, float[] heights) {
        Minecraft mc = Minecraft.getInstance();
        if (feature == null || !cfg().changeCrouch || entity == null || entity != mc.player || !feature.active()) {
            wasCrouching = false;
            return false;
        }
        float eye = eyeHeight(entity);
        boolean crouching = entity.getPose() == Pose.CROUCHING;
        if (cfg().instantCrouch && (crouching || wasCrouching)) {
            heights[0] = eye;
            heights[1] = eye;
        } else {
            heights[1] = heights[0] + (eye - heights[0]) * 0.5f;
        }
        wasCrouching = crouching;
        return true;
    }
}
