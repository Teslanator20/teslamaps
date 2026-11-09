package com.teslamaps.features;

// Ported from Skysoft's NoCropRotation.kt + CropCoordinateVariationMixin.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NoCropRotation {

    private static volatile boolean active;
    private static int ticks;

    private NoCropRotation() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NoCropRotation").section("Garden").when(() -> cfg().noCropRotation)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("No Crop Rotation", () -> c.noCropRotation, v -> c.noCropRotation = v)
                            .desc("Plants lose their random position offset and model rotation, so rows look even. Garden only unless set below.")
                            .tags("crop", "rotation", "offset", "flower", "random");
                    b.toggle("  Everywhere", () -> c.noCropRotationEverywhere, v -> c.noCropRotationEverywhere = v);
                });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ticks++ % 10 != 0) return;
            TeslaMapsConfig c = cfg();
            boolean want = c.noCropRotation && c.section("Garden") && mc.level != null
                    && (c.noCropRotationEverywhere || SkyblockUtils.isInGarden());
            if (want == active) return;
            active = want;
            if (mc.levelExtractor != null && mc.level != null) mc.levelExtractor.allChanged();
        });
    }

    public static long seed(BlockState state, long seed) {
        return active && state.hasOffsetFunction() ? 0L : seed;
    }

    public static Vec3 offset(BlockState state, Vec3 offset) {
        return active && state.hasOffsetFunction() ? Vec3.ZERO : offset;
    }
}
