package com.teslamaps.features.events;

// Ported from SkyHanni's BeachBallTracker.kt and BeachBallCatchHelper.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BeachBall {

    private static final Pattern NORMAL = Pattern.compile(
            ".*You kept the Bouncy Beach Ball in the air for \\d+ bounces and earned (\\d+) Fishy Treats?.*");
    private static final Pattern GIANT = Pattern.compile(".*The Giant Bouncy Beach Ball was kept in the air for \\d+ bounces and "
            + "\\d+ unique players have participated, rewarding you with (\\d+) extra Fishy Treats.*");
    private static final Pattern START = Pattern.compile(".*BOUNCE BONANZA(?: EX)?! Keep the (?:Giant )?Bouncy Beach Ball in the air .*");
    private static final List<String> SAMPLE = List.of("§6§lBeach Ball Tracker §7(Session)", "§e12x §fBeach Balls",
            "§e140 §dFishy Treats", "§e11.7 §7Treats per Ball", "§e320 §7Treats per Hour");

    private static Tracker tracker;
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static boolean show;
    private static long lastActivity, armedUntil;
    private static final Map<Integer, Predictor> predictors = new HashMap<>();
    private static Object lastLevel;

    private BeachBall() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        tracker = Tracker.create("beach_ball", "§6§lBeach Ball Tracker");
        hud = HudEditElement.of("beachBallTracker", "Beach Ball Tracker").section("Events")
                .enabled(() -> cfg().beachBallTracker)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("BeachBall").section("Events").when(() -> cfg().beachBallTracker || cfg().beachBallLine)
                .tick(BeachBall::tick)
                .serverChat(BeachBall::onChat)
                .world(BeachBall::render)
                .hudHiddenInInventory("beachballtracker", BeachBall::renderHud)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Year of the Seal");
                    b.toggle("Beach Ball Tracker", () -> c.beachBallTracker, v -> c.beachBallTracker = v)
                            .desc("Balls used, Fishy Treats earned, treats per ball and per hour. Shows while holding a "
                                    + "beach ball or right after one. Move it in /tmap gui.")
                            .tags("beach ball", "fishy treat", "seal", "bounce");
                    b.slider("  Hide After (min)", 1f, 10f, () -> (float) c.beachBallHideMinutes,
                            v -> c.beachBallHideMinutes = Math.round(v), 0);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{tracker::resetSession, tracker::resetTotal, tracker::toggleView});
                    b.toggle("Beach Ball Catch Helper", () -> c.beachBallLine, v -> c.beachBallLine = v)
                            .desc("Predicted flight path of your thrown Bouncy Beach Ball (not the giant one), "
                                    + "the landing spot at head height (green = right under it) and a bounce counter.")
                            .tags("beach ball", "bounce", "seal", "trajectory");
                    b.color("  Line Color", () -> c.beachBallLineColor, v -> c.beachBallLineColor = v);
                    b.toggle("  Landing Spot + Counter", () -> c.beachBallLanding, v -> c.beachBallLanding = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.contains("Beach Ball")) return;
        if (START.matcher(t).matches()) {
            lastActivity = System.currentTimeMillis();
            if (!t.contains("BONANZA EX")) armedUntil = lastActivity + 4000;
            tracker.markActivity();
            return;
        }
        if (!cfg().beachBallTracker) return;
        Matcher m = NORMAL.matcher(t);
        String key = "balls_normal";
        if (!m.matches()) {
            m = GIANT.matcher(t);
            key = "balls_giant";
            if (!m.matches()) return;
        }
        lastActivity = System.currentTimeMillis();
        tracker.add(key, 1);
        tracker.add("treats", Long.parseLong(m.group(1)));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            predictors.clear();
        }
        if (cfg().beachBallLine) tickPredictors(mc);
        else if (!predictors.isEmpty()) predictors.clear();

        if (!cfg().beachBallTracker) {
            show = false;
            return;
        }
        tracker.tick();
        if (++ticks % 10 != 0) return;
        String held = ItemUtil.skyblockId(mc.player.getMainHandItem());
        boolean holding = held.equals("BOUNCY_BEACH_BALL") || held.equals("GIANT_BOUNCY_BEACH_BALL");
        show = holding || System.currentTimeMillis() - lastActivity < cfg().beachBallHideMinutes * 60_000L;
        if (show) build();
    }

    private static void build() {
        Tracker.View v = tracker.view();
        List<String> out = new ArrayList<>();
        out.add("§6§lBeach Ball Tracker §7(" + (v == Tracker.View.SESSION ? "Session" : "Total") + ")");
        long normal = tracker.count("balls_normal", v), giant = tracker.count("balls_giant", v);
        long treats = tracker.count("treats", v);
        if (normal > 0) out.add("§e" + String.format(Locale.ROOT, "%,d", normal) + "x §fBeach Balls");
        if (giant > 0) out.add("§e" + String.format(Locale.ROOT, "%,d", giant) + "x §fGiant Beach Balls");
        out.add("§e" + String.format(Locale.ROOT, "%,d", treats) + " §dFishy Treats");
        if (normal + giant > 0) out.add("§e" + String.format(Locale.ROOT, "%.1f", treats / (double) (normal + giant)) + " §7Treats per Ball");
        long ms = tracker.activeMillis();
        if (v == Tracker.View.SESSION && ms >= 60_000) {
            out.add("§e" + String.format(Locale.ROOT, "%,d", Math.round(treats * 3_600_000.0 / ms)) + " §7Treats per Hour");
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void tickPredictors(Minecraft mc) {
        predictors.values().removeIf(p -> {
            if (p.stand.isRemoved()) return true;
            p.add(p.stand.position(), mc.player.position());
            return false;
        });
        if (System.currentTimeMillis() > armedUntil || !predictors.isEmpty()) return;
        ArmorStand best = null;
        double bestD = 36;
        for (ArmorStand s : EntityCache.armorStands()) {
            if (s.isRemoved() || s.tickCount > 40 || !s.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) continue;
            double d = s.distanceToSqr(mc.player);
            if (d < bestD) { bestD = d; best = s; }
        }
        if (best != null) {
            predictors.put(best.getId(), new Predictor(best));
            armedUntil = 0;
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!cfg().beachBallLine || predictors.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int color = 0xFF000000 | TeslaMapsConfig.parseColor(cfg().beachBallLineColor);
        int dark = 0xFF000000 | ((color >> 1) & 0x7F7F7F);
        Vec3 head = mc.player.getPosition(pt).add(0, 1, 0);
        for (Predictor p : predictors.values()) {
            List<Vec3> pre = p.prePath, path = p.predicted;
            if (pre.size() >= 2) ESPRenderer.drawPolyline(pose, pre, widths(pre.size()), dark, cam, true);
            if (path.size() >= 2) ESPRenderer.drawPolyline(pose, path, widths(path.size()), color, cam, true);
            if (!cfg().beachBallLanding) continue;
            Vec3 at = p.stand.getPosition(pt);
            Vec3 spot = new Vec3(at.x, head.y, at.z);
            double dist = spot.distanceTo(head);
            int boxColor = dist < 0.3 ? 0x8055FF55 : dist < 0.9 ? 0x80FFAA00 : 0x80FF5555;
            ESPRenderer.drawFilledBox(pose, new AABB(spot.x - 0.3, spot.y - 0.3, spot.z - 0.3, spot.x + 0.3, spot.y + 0.3, spot.z + 0.3),
                    boxColor, cam, true);
            ESPRenderer.drawText(pose, p.label, spot.add(0, 0.7, 0), 1f, cam);
        }
    }

    private static float[] widthBuf = new float[0];

    private static float[] widths(int n) {
        if (widthBuf.length < n) {
            widthBuf = new float[Math.max(n, widthBuf.length * 2)];
            java.util.Arrays.fill(widthBuf, 3f);
        }
        return widthBuf;
    }

    private static final class Predictor {
        final ArmorStand stand;
        final List<Vec3> data = new ArrayList<>();
        List<Vec3> prePath = List.of(), predicted = List.of();
        String label = "§c§l0";
        int start, updated, bounces;
        double minY;
        Vec3 last;
        boolean rising = true;
        long lastChange;

        Predictor(ArmorStand stand) {
            this.stand = stand;
            last = stand.position();
            minY = last.y;
        }

        void add(Vec3 pos, Vec3 player) {
            direction(pos);
            data.add(pos);
            if (data.size() > 1200) {
                data.subList(0, 600).clear();
                start = Math.max(0, start - 600);
            }
            if (pos.distanceTo(player) < 2.1) {
                start = data.size() - 1;
                minY = pos.y;
            }
            prePath = new ArrayList<>(data.subList(start, data.size()));
            if (!predicted.isEmpty()) predicted = predicted.subList(1, predicted.size());
            if (++updated <= 3) return;
            updated = 0;
            predicted = predict();
        }

        private void direction(Vec3 pos) {
            if (last.distanceTo(pos) < 0.3) return;
            long now = System.currentTimeMillis();
            if (now - lastChange < 800) return;
            boolean up = pos.y - last.y > 0;
            if (up && !rising) {
                bounces++;
                lastChange = now;
                label = quality(bounces);
            }
            rising = up;
            last = pos;
        }

        private static String quality(int n) {
            if (n <= 1) return "§c§l" + n;
            String c, q;
            if (n <= 5) { c = "§f"; q = "DECENT"; }
            else if (n <= 15) { c = "§a"; q = "GOOD"; }
            else if (n <= 25) { c = "§5"; q = "AMAZING"; }
            else if (n <= 39) { c = "§6"; q = "IMPRESSIVE"; }
            else { c = "§d"; q = "INSANE"; }
            return c + "§l" + n + " §8- " + c + "§l" + q + "!";
        }

        private List<Vec3> predict() {
            int cur = data.size() - 1;
            int present = cur - start;
            List<List<Vec3>> results = new ArrayList<>();
            if (present >= 3) results.add(poly(cur, cur - 1, cur - 2, cur));
            if (present >= 5) results.add(poly(cur - 1, (cur - start) / 2 + start, start + 1, cur));
            List<Vec3> best = null;
            double bestD = Double.MAX_VALUE, ax = 0, az = 0;
            int n = 0;
            for (List<Vec3> r : results) {
                Vec3 end = r.get(r.size() - 1);
                if (end.y <= minY - 1 || end.y >= minY + 1) continue;
                ax += end.x;
                az += end.z;
                n++;
            }
            if (n == 0) return List.of(data.get(cur));
            ax /= n;
            az /= n;
            for (List<Vec3> r : results) {
                Vec3 end = r.get(r.size() - 1);
                if (end.y <= minY - 1 || end.y >= minY + 1) continue;
                double d = (end.x - ax) * (end.x - ax) + (end.z - az) * (end.z - az);
                if (d < bestD) { bestD = d; best = r; }
            }
            return best;
        }

        private List<Vec3> poly(int t1, int t2, int t3, int cur) {
            double y1 = data.get(t1).y, y2 = data.get(t2).y, y3 = data.get(t3).y;
            double a = ((y3 - y1) * (t2 - t1) + (y2 - y1) * (t1 - t3))
                    / ((double) (t3 * t3 - t1 * t1) * (t2 - t1) + (double) (t2 * t2 - t1 * t1) * (t1 - t3));
            double b = ((y2 - y1) - a * (t2 * t2 - t1 * t1)) / (t2 - t1);
            double c = y1 - b * t1 - a * t1 * t1;
            double dx = data.get(cur).x - data.get(cur - 1).x, dz = data.get(cur).z - data.get(cur - 1).z;
            List<Vec3> out = new ArrayList<>();
            Vec3 p = data.get(t1);
            out.add(p);
            for (int t = cur + 1; t <= cur + 300; t++) {
                double y = a * t * t + b * t + c;
                p = new Vec3(p.x + dx, y, p.z + dz);
                out.add(p);
                if (y <= minY) break;
            }
            return out;
        }
    }
}
