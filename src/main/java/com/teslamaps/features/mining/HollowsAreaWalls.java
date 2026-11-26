package com.teslamaps.features.mining;

// After SkyHanni's CrystalHollowsWalls and SkyOcean's AreaWalls
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.world.phys.Vec3;

/** See-through walls between the Crystal Hollows quarters, the Nucleus, and the heat line at Y 65. */
public final class HollowsAreaWalls {

    private static final float MID = 513f, MIN = 0f, MAX = 1024f;
    private static final float HEAT = 65f, TOP = 190f;
    // the nucleus is off-centre
    private static final float NX0 = 463f, NX1 = 560f, NZ0 = 460f, NZ1 = 563f;

    private static final int MITHRIL = 0x3C55FF55, PRECURSOR = 0x3C5555FF, JUNGLE = 0x3CFF55FF,
            GOBLIN = 0x3CFFAA00, HEAT_COLOR = 0x3CFF5555, NUCLEUS = 0x3CFFFFFF;

    // quarter index: 0 jungle (NW), 1 mithril (NE), 2 goblin (SW), 3 precursor (SE)
    private static final int[] COLORS = {JUNGLE, MITHRIL, GOBLIN, PRECURSOR};
    private static final float[][] X_WALL = new float[4][], Z_WALL = new float[4][];
    private static final float[][] NUC_X = new float[4][], NUC_Z = new float[4][];
    private static final float[][] PLANE = new float[4][];

    static {
        for (int q = 0; q < 4; q++) {
            boolean east = (q & 1) == 1, south = (q & 2) == 2;
            float zs = south ? NZ1 : MIN, ze = south ? MAX : NZ0;
            float xs = east ? NX1 : MIN, xe = east ? MAX : NX0;
            X_WALL[q] = wall(MID, zs, MID, ze);
            Z_WALL[q] = wall(xs, MID, xe, MID);
            float nx = east ? NX1 : NX0, nz = south ? NZ1 : NZ0;
            NUC_X[q] = wall(nx, south ? MID : NZ0, nx, south ? NZ1 : MID);
            NUC_Z[q] = wall(east ? MID : NX0, nz, east ? NX1 : MID, nz);
            PLANE[q] = plane(east ? MID : MIN, south ? MID : MIN, east ? MAX : MID, south ? MAX : MID);
        }
    }

    private HollowsAreaWalls() {}

    private static float[] wall(float x1, float z1, float x2, float z2) {
        return new float[] {x1, HEAT, z1, x2, HEAT, z2, x2, TOP, z2, x1, TOP, z1};
    }

    private static float[] plane(float x1, float z1, float x2, float z2) {
        return new float[] {x1, HEAT, z1, x2, HEAT, z1, x2, HEAT, z2, x1, HEAT, z2};
    }

    public static void register() {
        Feature.of("HollowsAreaWalls").section("Mining").when(() -> TeslaMapsConfig.get().chAreaWalls)
                .world(HollowsAreaWalls::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Crystal Hollows Walls");
                    b.toggle("Crystal Hollows Area Walls", () -> c.chAreaWalls, v -> c.chAreaWalls = v)
                            .desc("Coloured see-through walls at the borders of your current quarter (Jungle, Mithril Deposits, Goblin Holdout, Precursor Remnants)")
                            .tags("crystal hollows", "quarter", "zone", "border", "nucleus", "magma");
                    b.toggle("  Nucleus Walls", () -> c.chAreaWallsNucleus, v -> c.chAreaWallsNucleus = v);
                    b.toggle("  Heat Line (Y 65)", () -> c.chAreaWallsHeat, v -> c.chAreaWallsHeat = v)
                            .desc("Red plane at Y 65; below it you are in the Magma Fields and heat builds up");
                });
    }

    private static boolean inHollows() {
        return com.teslamaps.utils.SkyblockUtils.getArea().equalsIgnoreCase("Crystal Hollows");
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inHollows()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (cam.y < HEAT) {
            if (!c.chAreaWallsHeat) return;
            for (int q = 0; q < 4; q++) ESPRenderer.drawQuad(pose, PLANE[q], COLORS[q], cam);
            return;
        }
        boolean nucleus = cam.x >= NX0 && cam.x <= NX1 && cam.z >= NZ0 && cam.z <= NZ1;
        if (nucleus) {
            if (!c.chAreaWallsNucleus) return;
            for (int q = 0; q < 4; q++) {
                ESPRenderer.drawQuad(pose, NUC_X[q], COLORS[q], cam);
                ESPRenderer.drawQuad(pose, NUC_Z[q], COLORS[q], cam);
            }
            return;
        }
        int q = (cam.x > MID ? 1 : 0) | (cam.z > MID ? 2 : 0);
        ESPRenderer.drawQuad(pose, X_WALL[q], COLORS[q ^ 1], cam);
        ESPRenderer.drawQuad(pose, Z_WALL[q], COLORS[q ^ 2], cam);
        if (c.chAreaWallsNucleus) {
            ESPRenderer.drawQuad(pose, NUC_X[q], NUCLEUS, cam);
            ESPRenderer.drawQuad(pose, NUC_Z[q], NUCLEUS, cam);
        }
        if (c.chAreaWallsHeat) ESPRenderer.drawQuad(pose, PLANE[q], HEAT_COLOR, cam);
    }
}
