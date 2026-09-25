package com.teslamaps.features.mining;

// Ported from SkyHanni's CrystalHollowsNamesInCore.kt + NucleusBarriersBox.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NucleusOverlay {

    private static final Vec3[] NAME_POS = {
            new Vec3(550.5, 116.5, 550.5), new Vec3(552.5, 116.5, 474.5),
            new Vec3(477.5, 116.5, 476.5), new Vec3(474.5, 116.5, 554.5)};
    private static final String[] NAMES = {"§8Precursor Remnants", "§bMithril Deposits", "§aJungle", "§6Goblin Holdout"};

    private static final AABB[] BARRIERS = {
            new AABB(474, 111, 524, 486, 125, 536), new AABB(474, 111, 492, 486, 125, 504),
            new AABB(508, 111, 473, 520, 125, 485), new AABB(542, 111, 492, 554, 125, 504),
            new AABB(542, 111, 524, 554, 125, 536)};
    // amber, amethyst, topaz, jade, sapphire
    private static final int[] COLORS = {0xFFFFAA00, 0xFFAA00AA, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF};

    private static boolean inNucleus;
    private static int ticks;

    private NucleusOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NucleusOverlay").section("Mining").when(() -> cfg().nucleusNames || cfg().nucleusBarriers)
                .tick(() -> {
                    if (++ticks < 20) return;
                    ticks = 0;
                    inNucleus = Mining.inNucleus();
                })
                .world(NucleusOverlay::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Crystal Nucleus");
                    b.toggle("Nucleus Exit Names", () -> c.nucleusNames, v -> c.nucleusNames = v)
                            .desc("Big labels at the four Crystal Nucleus exits naming the quarter behind them.")
                            .tags("crystal hollows", "nucleus", "labels");
                    b.toggle("Nucleus Crystal Barriers", () -> c.nucleusBarriers, v -> c.nucleusBarriers = v)
                            .desc("Boxes around the five colored crystal barriers in the Nucleus.");
                    b.toggle("  Filled Boxes", () -> c.nucleusBarriersFilled, v -> c.nucleusBarriersFilled = v);
                });
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inNucleus) return;
        TeslaMapsConfig c = cfg();
        if (c.nucleusNames) {
            for (int i = 0; i < NAME_POS.length; i++) {
                if (cam.distanceToSqr(NAME_POS[i]) > 50) WaypointSet.drawLabel(pose, cam, NAMES[i], NAME_POS[i], 2.5f);
            }
        }
        if (c.nucleusBarriers) {
            for (int i = 0; i < BARRIERS.length; i++) {
                if (c.nucleusBarriersFilled) ESPRenderer.drawFilledBox(pose, BARRIERS[i], (COLORS[i] & 0xFFFFFF) | 0x50000000, cam, false);
                else ESPRenderer.drawBoxOutline(pose, BARRIERS[i], COLORS[i], 2f, cam, false);
            }
        }
    }
}
