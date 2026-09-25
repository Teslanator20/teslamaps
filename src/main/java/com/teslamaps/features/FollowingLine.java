package com.teslamaps.features;

// Ported from SkyHanni's features/cosmetics/CosmeticFollowingLine.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FollowingLine {

    private static final int MAX = 5000;
    private static final List<Vec3> points = new ArrayList<>();
    private static final List<Long> times = new ArrayList<>();
    private static final List<Vec3> drawPts = new ArrayList<>();
    private static float[] widths = new float[64];
    private static int ticks;
    private static Object lastLevel;

    private FollowingLine() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FollowingLine").section("Render").when(() -> cfg().followingLine)
                .tick(FollowingLine::tick)
                .world(FollowingLine::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Following Line");
                    b.toggle("Following Line", () -> c.followingLine, v -> c.followingLine = v)
                            .desc("Draws a line along the path you walked. It gets thinner with age and fades out.");
                    b.color("  Color", () -> c.followingLineColor, v -> c.followingLineColor = v);
                    b.slider("  Width", 1f, 10f, () -> c.followingLineWidth, v -> c.followingLineWidth = v, 1);
                    b.slider("  Seconds Alive", 1f, 120f, () -> (float) c.followingLineSeconds, v -> c.followingLineSeconds = Math.round(v), 0);
                    b.toggle("  Through Walls", () -> c.followingLineThroughWalls, v -> c.followingLineThroughWalls = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            points.clear();
            times.clear();
        }
        ticks++;
        long now = System.currentTimeMillis();
        if (ticks % 5 == 0) {
            long maxAge = cfg().followingLineSeconds * 1000L;
            int drop = 0;
            while (drop < times.size() && now - times.get(drop) > maxAge) drop++;
            drop = Math.max(drop, points.size() - MAX);
            if (drop > 0) {
                points.subList(0, drop).clear();
                times.subList(0, drop).clear();
            }
        }
        if (ticks % 2 != 0) return;
        Vec3 pos = mc.player.position().add(0, 0.3, 0);
        if (!points.isEmpty() && points.get(points.size() - 1).distanceToSqr(pos) < 0.01) return;
        points.add(pos);
        times.add(now);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || points.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        double alive = c.followingLineSeconds * 1000.0;
        float max = c.followingLineWidth;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        int n = points.size();
        drawPts.clear();
        drawPts.addAll(points);
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawPts.add(p.getPosition(pt).add(0, 0.3, 0));
        if (widths.length < n + 1) widths = new float[Math.max(n + 1, widths.length * 2)];
        for (int i = 1; i <= n; i++) {
            long age = now - (i < n ? times.get(i) : now);
            float w = (float) Math.max(1, (int) (1 + max - age / alive * max));
            if (firstPerson && (i == n || now - times.get(i) < 400) && !p.onGround()) w = 0;
            widths[i] = w;
        }
        widths[0] = 0;
        ESPRenderer.drawPolyline(pose, drawPts, widths, TeslaMapsConfig.parseColor(c.followingLineColor), cam, c.followingLineThroughWalls);
    }
}
