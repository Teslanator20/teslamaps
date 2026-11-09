package com.teslamaps.features;

// Ported from Devonian's DragonStackAimer.kt + Projectile.kt (paths from M7Dragon.kt)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class DragonStackAimer {

    private static final double[] RED = {27.0, 14.0, 59.0, 27.46875, 14.59375, 58.75, 27.6875, 14.875, 58.625, 27.9375, 15.1875, 58.5, 28.1875, 15.46875, 58.375, 28.40625, 15.78125, 58.28125, 28.65625, 16.0625, 58.15625, 28.90625, 16.375, 58.03125, 29.125, 16.65625, 57.90625, 29.375, 16.96875, 57.78125, 29.59375, 17.25, 57.6875, 30.09375, 17.875, 57.4375, 30.5625, 18.46875, 57.1875, 30.84375, 18.46875, 56.90625, 31.09375, 18.5, 56.59375, 31.375, 18.5, 56.3125, 31.65625, 18.53125, 56.03125, 31.90625, 18.53125, 55.71875, 32.1875, 18.5625, 55.4375, 32.4375, 18.5625, 55.125, 32.71875, 18.59375, 54.84375, 33.0, 18.59375, 54.53125, 33.25, 18.625, 54.25, 33.53125, 18.65625, 53.9375, 33.8125, 18.65625, 53.65625, 34.0625, 18.6875, 53.375, 34.34375, 18.6875, 53.0625, 34.59375, 18.71875, 52.78125, 34.875, 18.71875, 52.46875, 35.15625, 18.75, 52.1875};
    private static final double[] GREEN = {27.0, 14.0, 94.0, 26.8125, 14.25, 93.25, 26.71875, 14.40625, 92.875, 26.65625, 14.53125, 92.53125, 26.5625, 14.6875, 92.15625, 26.46875, 14.8125, 91.78125, 26.40625, 14.96875, 91.4375, 26.3125, 15.09375, 91.0625, 26.21875, 15.25, 90.6875, 26.15625, 15.375, 90.34375, 26.0625, 15.53125, 89.96875, 25.96875, 15.65625, 89.59375, 25.875, 15.8125, 89.25, 25.8125, 15.9375, 88.875, 25.71875, 16.09375, 88.5, 25.625, 16.21875, 88.15625, 25.5625, 16.375, 87.78125, 25.46875, 16.5, 87.40625, 25.375, 16.65625, 87.0625, 25.3125, 16.78125, 86.6875, 25.21875, 16.9375, 86.3125, 25.125, 17.0625, 85.96875, 25.0625, 17.21875, 85.59375, 24.96875, 17.34375, 85.21875, 24.875, 17.5, 84.875, 24.78125, 17.625, 84.5, 24.71875, 17.78125, 84.125, 24.625, 17.90625, 83.75, 24.53125, 18.0625, 83.40625, 24.46875, 18.1875, 83.03125};
    private static final double[] PURPLE = {56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0, 56.0, 14.0, 125.0};
    private static final double[] BLUE = {84.0, 14.0, 94.0, 83.84375, 14.125, 94.34375, 83.6875, 14.28125, 94.6875, 83.5625, 14.40625, 95.03125, 83.40625, 14.5625, 95.375, 83.28125, 14.6875, 95.71875, 83.125, 14.84375, 96.0625, 82.96875, 15.0, 96.40625, 82.84375, 15.125, 96.75, 82.6875, 15.28125, 97.09375, 82.5625, 15.40625, 97.4375, 82.40625, 15.5625, 97.78125, 82.25, 15.71875, 98.125, 82.125, 15.84375, 98.46875, 81.96875, 16.0, 98.8125, 81.84375, 16.125, 99.15625, 81.6875, 16.28125, 99.5, 81.53125, 16.4375, 99.84375, 81.40625, 16.5625, 100.1875, 81.25, 16.71875, 100.53125, 81.125, 16.84375, 100.875, 80.96875, 17.0, 101.21875, 80.8125, 17.15625, 101.5625, 80.6875, 17.28125, 101.90625, 80.53125, 17.4375, 102.25, 80.40625, 17.5625, 102.59375, 80.25, 17.71875, 102.9375, 80.09375, 17.875, 103.28125, 79.96875, 18.0, 103.625, 79.8125, 18.15625, 103.96875};
    private static final double[] ORANGE = {85.0, 14.0, 56.0, 84.6875, 14.21875, 56.125, 84.375, 14.4375, 56.25, 84.0625, 14.65625, 56.375, 83.75, 14.875, 56.5, 83.4375, 15.09375, 56.65625, 83.125, 15.3125, 56.78125, 82.84375, 15.53125, 56.90625, 82.53125, 15.75, 57.03125, 82.21875, 15.96875, 57.15625, 81.90625, 16.1875, 57.3125, 81.59375, 16.40625, 57.4375, 81.28125, 16.625, 57.5625, 81.0, 16.84375, 57.6875, 80.6875, 17.0625, 57.84375, 80.375, 17.28125, 57.96875, 80.0625, 17.5, 58.09375, 79.75, 17.71875, 58.21875, 79.4375, 17.9375, 58.34375, 79.15625, 18.15625, 58.5, 78.84375, 18.375, 58.625, 78.53125, 18.59375, 58.75, 78.625, 18.59375, 59.125, 78.75, 18.625, 59.53125, 78.84375, 18.625, 59.90625, 78.96875, 18.625, 60.28125, 79.0625, 18.65625, 60.6875, 79.15625, 18.65625, 61.0625, 79.28125, 18.65625, 61.4375, 79.375, 18.65625, 61.84375};

    private static final double G = -0.05, V = 3.0, DRAG = 0.99, EPS = 0.001;
    private static final String SAMPLE = "§a4250";

    private static WitherDragons.Dragon target;
    private static long spawnTick;
    private static int prevBest;
    private static boolean hasAim;
    private static double theta, phi, flightTicks;
    private static List<DungeonTimers.Row> row;

    private DragonStackAimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DragonStackAimer").when(() -> cfg().dragonStackAimer && cfg().witherDragons)
                .tick(DragonStackAimer::tick)
                .world(DragonStackAimer::render)
                .hud("dragonstackaimer", DragonStackAimer::renderHud);
        HudEditElement.of("dragonStackAimer", "Dragon Stack Aimer").ctx(HudCtx.DUNGEON)
                .enabled(() -> cfg().dragonStackAimer && cfg().dragonStackAimerHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, m, x, y, s) -> DungeonTimers.drawRows(g, m, x, y, s, List.of(new DungeonTimers.Row(SAMPLE, null))));
    }

    private static double[] path(WitherDragons.Dragon d) {
        return switch (d) {
            case RED -> RED;
            case GREEN -> GREEN;
            case PURPLE -> PURPLE;
            case BLUE -> BLUE;
            case ORANGE -> ORANGE;
        };
    }

    private static boolean classOk() {
        if (cfg().dragonStackAimerAnyClass) return true;
        String c = PlayerTracker.getLocalClass();
        return c == null || c.isEmpty() || "Archer".equals(c) || "Berserk".equals(c) || "Unknown".equals(c);
    }

    private static double pingTicks() {
        if (!cfg().dragonStackAimerPing) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency() / 50.0;
    }

    private static void clear() {
        target = null;
        hasAim = false;
        row = null;
        prevBest = 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        WitherDragons.Dragon prio = WitherDragons.priorityDragon;
        if (prio != null && prio.state == WitherDragons.State.SPAWNING && prio.timeToSpawn > 0) {
            if (prio != target) { target = prio; prevBest = 0; }
            spawnTick = WitherDragons.currentTick + prio.timeToSpawn;
        }
        if (target == null || mc.player == null || !classOk()) { hasAim = false; row = null; return; }

        long remaining = spawnTick - WitherDragons.currentTick;
        if (remaining < -30) { clear(); return; }

        double ping = pingTicks();
        double ttl = remaining - ping;
        double yo = target.high ? 8.0 : 0.0;
        double px = mc.player.getX();
        double py = mc.player.getY() - yo + (mc.player.getPose() == Pose.CROUCHING ? 1.54 : 1.62);
        double pz = mc.player.getZ();
        if (!aim(ttl, path(target), px, py, pz)) { hasAim = false; row = null; return; }
        hasAim = true;

        if (!cfg().dragonStackAimerHud || remaining <= 0) { row = null; return; }
        int ms = (int) ((remaining - flightTicks) * 50 - ping * 50);
        if (ms < -1000) { row = null; return; }
        String text = ms < 0 ? "§b§lNOW" : (ms > 2000 ? "§a" : ms > 1000 ? "§e" : "§c") + ms;
        row = List.of(new DungeonTimers.Row(text, null));
    }

    private static boolean aim(double ttl, double[] p, double x, double y, double z) {
        int n = p.length / 3;
        if (prevBest >= n) prevBest = 0;
        boolean[] visited = new boolean[n];
        visited[prevBest] = true;
        int bestI = prevBest;
        double[] best = solve(p[prevBest * 3] - x, p[prevBest * 3 + 1] - y, p[prevBest * 3 + 2] - z);
        double bestO = Math.abs(best[2] - ttl - prevBest);
        if (Double.isNaN(bestO)) bestO = Double.POSITIVE_INFINITY;

        int dir = 1, i = prevBest;
        boolean swapped = false;
        while (true) {
            int next = i + dir;
            boolean swap = false;
            if (next >= 0 && next < n && !visited[next]) {
                visited[next] = true;
                double[] d = solve(p[next * 3] - x, p[next * 3 + 1] - y, p[next * 3 + 2] - z);
                double o = Math.abs(d[2] - ttl - next);
                if (o < bestO) { bestI = next; bestO = o; best = d; i = next; }
                else swap = true;
            } else swap = true;
            if (swap) {
                if (swapped) break;
                swapped = true;
                dir = -dir;
            }
        }
        prevBest = Double.isFinite(bestO) ? bestI : 0;
        if (Double.isNaN(best[0]) || Double.isNaN(best[1]) || Double.isNaN(best[2])) return false;
        theta = best[0];
        phi = best[1];
        flightTicks = best[2];
        return true;
    }

    private static final double NEG_INV_E = -1.0 / Math.E;

    /** {theta, phi, ticks}; phi measured from straight up. NaN when out of reach. */
    private static double[] solve(double dx, double dy, double dz) {
        double a = G, v = V, d = DRAG;
        double th = Math.atan2(dz, dx);
        double R = Math.hypot(dx, dz);
        double f = 1.0 / (d - 1.0);
        double lnd = Math.log(d);
        double invA = 1.0 / a;
        double invLnd = 1.0 / lnd;

        double A = v + a * f;
        double r = -(dy * (d - 1.0) + A) * invA;
        double B = -A * lnd * Math.exp(lnd * r) * invA;
        double[] nan = {Double.NaN, Double.NaN, Double.NaN};
        if (B < NEG_INV_E) return nan;

        if (dy > 0.0) {
            double searchL = 0.0, searchR = Math.PI / 2.0, t = 0.0;
            while (searchR - searchL > EPS) {
                double m = (searchL + searchR) * 0.5;
                A = v * Math.cos(m) + a * f;
                r = -(dy * (d - 1.0) + A) * invA;
                B = -A * lnd * Math.exp(lnd * r) * invA;
                if (B < NEG_INV_E) searchR = m;
                else {
                    t = r - invLnd * lambertWm1(B);
                    double xx = v * Math.sin(m) * (Math.exp(lnd * t) - 1.0) * f;
                    if (xx < R) searchL = m;
                    else searchR = m;
                }
            }
            return new double[]{th, (searchL + searchR) * 0.5, t};
        }

        double maxDomain = Math.PI;
        double searchL = 0.0, searchR = maxDomain, x1 = 0.0, x2 = 0.0;
        while (true) {
            double window = searchR - searchL;
            if (window < EPS) break;
            double m1 = window / 3.0 + searchL;
            A = v * Math.cos(m1) + a * f;
            r = -(dy * (d - 1.0) + A) * invA;
            B = -A * lnd * Math.exp(lnd * r) * invA;
            if (B < NEG_INV_E) {
                searchR = m1;
                maxDomain = m1;
            } else {
                x1 = v * Math.sin(m1) * (Math.exp(lnd * (r - invLnd * lambertW0(B))) - 1.0) * f;
                double m2 = window * 2.0 / 3.0 + searchL;
                A = v * Math.cos(m2) + a * f;
                r = -(dy * (d - 1.0) + A) * invA;
                B = -A * lnd * Math.exp(lnd * r) * invA;
                if (B < NEG_INV_E) {
                    searchR = m2;
                    maxDomain = m2;
                } else {
                    x2 = v * Math.sin(m2) * (Math.exp(lnd * (r - invLnd * lambertW0(B))) - 1.0) * f;
                    if (x1 < x2) searchL = m1;
                    else searchR = m2;
                }
            }
        }
        if (R > (x1 + x2) * 0.5) return nan;

        searchL = (searchL + searchR) * 0.5;
        searchR = maxDomain;
        double t = Double.NaN;
        while (searchR - searchL > EPS) {
            double m = (searchL + searchR) * 0.5;
            A = v * Math.cos(m) + a * f;
            r = -(dy * (d - 1.0) + A) * invA;
            B = -A * lnd * Math.exp(lnd * r) * invA;
            if (B < NEG_INV_E) searchR = m;
            else {
                t = r - invLnd * lambertW0(B);
                x1 = v * Math.sin(m) * (Math.exp(lnd * t) - 1.0) * f;
                if (x1 >= R) searchL = m;
                else searchR = m;
            }
        }
        return new double[]{th, (searchL + searchR) * 0.5, t};
    }

    private static double lambertW0(double x) {
        if (x < NEG_INV_E) return Double.NaN;
        if (x == 0.0) return 0.0;
        double w;
        if (x < -0.25) {
            double p = Math.sqrt(2.0 * (Math.E * x + 1.0));
            w = -1.0 + p - p * p / 3.0 + 11.0 / 72.0 * p * p * p;
        } else {
            w = Math.log1p(x);
            if (x > 3) w -= Math.log(w);
        }
        return halley(x, w);
    }

    private static double lambertWm1(double x) {
        if (x < NEG_INV_E || x >= 0.0) return Double.NaN;
        double w;
        if (x < -0.25) {
            double p = -Math.sqrt(2.0 * (Math.E * x + 1.0));
            w = -1.0 + p - p * p / 3.0 + 11.0 / 72.0 * p * p * p;
        } else {
            double l1 = Math.log(-x), l2 = Math.log(-l1);
            w = l1 - l2 + l2 / l1;
        }
        return halley(x, w);
    }

    private static double halley(double x, double w) {
        for (int i = 0; i < 20; i++) {
            double ew = Math.exp(w);
            double fw = w * ew - x;
            double wp1 = w + 1.0;
            if (Math.abs(wp1) < 1e-12) break;
            double step = fw / (ew * wp1 - (w + 2.0) * fw / (2.0 * wp1));
            if (!Double.isFinite(step)) break;
            w -= step;
            if (Math.abs(step) < 1e-12 * (1.0 + Math.abs(w))) break;
        }
        return w;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!hasAim || target == null) return;
        double sp = Math.sin(phi);
        double x = cam.x + 50.0 * sp * Math.cos(theta);
        double y = cam.y + 50.0 * Math.cos(phi);
        double z = cam.z + 50.0 * sp * Math.sin(theta);
        ESPRenderer.drawFilledBox(pose, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                color(cfg().dragonStackAimerColor), cam, true);
        ESPRenderer.drawTracerFromCamera(pose, new Vec3(x, y, z), color(cfg().dragonStackAimerTracerColor), cam, true);
    }

    private static int color(String hex) {
        int c = TeslaMapsConfig.parseColor(hex);
        return (c >>> 24) == 0 ? c | 0xFF000000 : c;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<DungeonTimers.Row> r = row;
        if (r == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        TeslaMapsConfig c = cfg();
        DungeonTimers.drawRows(g, mc, c.dragonStackAimerX, c.dragonStackAimerY, c.dragonStackAimerScale, r);
    }
}
