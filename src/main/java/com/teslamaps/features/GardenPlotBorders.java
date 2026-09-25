package com.teslamaps.features;

// Ported from SkyHanni's GardenPlotBorders.kt, SprayFeatures.kt and GardenPlotApi.renderPlot
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Walls of the current plot with the build limit, on a key or while holding the Sprayonator. */
public final class GardenPlotBorders {

    private static final int SIZE = 96;
    private static final int MIN_Y = 66, MAX_Y = 66 + 36, BUILD_LIMIT = MIN_Y + 11;
    private static final int LINE = 0xFFFFFF55, CORNER = 0xFF0000AA, LIMIT = 0xFFFF5555;

    private static boolean toggled, keyWasDown, show;
    private static int builtX = Integer.MIN_VALUE, builtZ = Integer.MIN_VALUE;
    private static final List<Vec3> lines = new ArrayList<>(), corners = new ArrayList<>(), limit = new ArrayList<>();
    private static float[] lineW = new float[0], cornerW = new float[0], limitW = new float[0];

    private GardenPlotBorders() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GardenPlotBorders").section("Garden").when(() -> cfg().plotBorders)
                .tick(GardenPlotBorders::tick)
                .world(GardenPlotBorders::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Plot Borders");
                    b.toggle("Plot Borders", () -> c.plotBorders, v -> c.plotBorders = v)
                            .desc("Walls of the plot you stand on, with the build limit in red")
                            .tags("plot", "border", "build limit", "sprayonator", "spray");
                    b.keybind("  Toggle Key", () -> c.plotBorderKey, v -> c.plotBorderKey = v);
                    b.toggle("  Show With Sprayonator", () -> c.plotBordersSprayonator, v -> c.plotBordersSprayonator = v)
                            .desc("Borders show on their own while the Sprayonator is in your hand");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean down = mc.screen == null && c.plotBorderKey != -1 && KeyNames.isDown(c.plotBorderKey);
        if (down && !keyWasDown && SkyblockUtils.isInGarden()) toggled = !toggled;
        keyWasDown = down;
        if (!SkyblockUtils.isInGarden()) {
            show = false;
            return;
        }
        boolean spray = c.plotBordersSprayonator && ItemUtil.skyblockId(mc.player.getMainHandItem()).equals("SPRAYONATOR");
        show = toggled || spray;
        if (!show) return;
        double px = Math.max(-240, Math.min(239.9, mc.player.getX()));
        double pz = Math.max(-240, Math.min(239.9, mc.player.getZ()));
        int minX = (int) Math.floor((px + 48) / SIZE) * SIZE - 48;
        int minZ = (int) Math.floor((pz + 48) / SIZE) * SIZE - 48;
        if (minX != builtX || minZ != builtZ) build(minX, minZ);
    }

    private static void build(int x0, int z0) {
        builtX = x0;
        builtZ = z0;
        lines.clear();
        corners.clear();
        limit.clear();
        for (int i = 0; i <= SIZE; i += SIZE) {
            for (int j = 0; j <= SIZE; j += SIZE) seg(corners, x0 + i, MIN_Y, z0 + j, x0 + i, MAX_Y, z0 + j);
        }
        for (int d = 4; d < SIZE; d += 4) {
            seg(lines, x0 + d, MIN_Y, z0, x0 + d, MAX_Y, z0);
            seg(lines, x0 + d, MIN_Y, z0 + SIZE, x0 + d, MAX_Y, z0 + SIZE);
            seg(lines, x0, MIN_Y, z0 + d, x0, MAX_Y, z0 + d);
            seg(lines, x0 + SIZE, MIN_Y, z0 + d, x0 + SIZE, MAX_Y, z0 + d);
        }
        for (int y = MIN_Y; y <= MAX_Y; y += 4) ring(lines, x0, y, z0);
        ring(limit, x0, BUILD_LIMIT, z0);
        lineW = widths(lines, 2f);
        cornerW = widths(corners, 3f);
        limitW = widths(limit, 3f);
    }

    private static void ring(List<Vec3> out, int x0, int y, int z0) {
        seg(out, x0, y, z0, x0, y, z0 + SIZE);
        seg(out, x0, y, z0 + SIZE, x0 + SIZE, y, z0 + SIZE);
        seg(out, x0 + SIZE, y, z0 + SIZE, x0 + SIZE, y, z0);
        seg(out, x0 + SIZE, y, z0, x0, y, z0);
    }

    private static void seg(List<Vec3> out, double x1, double y1, double z1, double x2, double y2, double z2) {
        out.add(new Vec3(x1, y1, z1));
        out.add(new Vec3(x2, y2, z2));
    }

    // pairs (a,b): segment a->b drawn, b->next a skipped
    private static float[] widths(List<Vec3> pts, float w) {
        float[] out = new float[pts.size()];
        for (int i = 1; i < out.length; i += 2) out[i] = w;
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!show || lines.isEmpty()) return;
        ESPRenderer.drawPolyline(pose, lines, lineW, LINE, cam, false);
        ESPRenderer.drawPolyline(pose, corners, cornerW, CORNER, cam, false);
        ESPRenderer.drawPolyline(pose, limit, limitW, LIMIT, cam, false);
    }
}
