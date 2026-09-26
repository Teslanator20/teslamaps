package com.teslamaps.features;

// Ported from SkyHanni's TalbotCircles.kt + the theodolite patterns in TrevorFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Rings where Trevor's animal can be, from Talbot's Theodolite height + angle readings. */
public final class TalbotTheodolite {

    private static final Pattern READING = Pattern.compile(
            "The target is around (?<height>\\d+) blocks (?<dir>above|below), at a (?<angle>\\d+) degrees angle!");
    private static final double MAX_RADIUS = 500.0;
    private static final int COLOR = 0xFFFFAA00;

    private record Circle(double x, double y, double z, double radius) {}

    private static final List<Circle> circles = new ArrayList<>();
    private static Object lastLevel;

    private TalbotTheodolite() {}

    public static void register() {
        Feature.of("TalbotTheodolite").section("Garden")
                .when(() -> TeslaMapsConfig.get().theodoliteCircles)
                .tick(TalbotTheodolite::tick)
                .world(TalbotTheodolite::render)
                .serverChat(TalbotTheodolite::onChat)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Trevor the Trapper");
                    b.toggle("Theodolite Visualizer", () -> c.theodoliteCircles, v -> c.theodoliteCircles = v)
                            .desc("Draws the ring given by each Talbot's Theodolite reading, the animal sits on it. More height difference = more accurate. Cleared when the quest ends.")
                            .tags("trevor", "trapper", "talbot", "theodolite", "pelt");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            circles.clear();
        }
    }

    private static void onChat(String raw) {
        String text = StringUtil.strip(raw).trim();
        if (text.startsWith("[NPC] Trevor: You can find your")
                || text.equals("You ran out of time and the animal disappeared!")
                || text.equals("Return to the Trapper soon to get a new animal to hunt!")) {
            circles.clear();
            return;
        }
        Matcher m = READING.matcher(text);
        if (!m.matches()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int dy = Integer.parseInt(m.group("height"));
        if (m.group("dir").equals("below")) dy = -dy;
        int angle = Integer.parseInt(m.group("angle"));
        double radius = Math.tan(Math.toRadians(90.0 - angle)) * Math.abs(dy);
        if (radius < 0 || radius > MAX_RADIUS) return;
        Vec3 p = mc.player.position();
        circles.add(new Circle(Math.round(p.x * 100) / 100.0, p.y + dy, Math.round(p.z * 100) / 100.0, radius));
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        for (Circle c : circles) {
            ESPRenderer.drawCircle(matrices, c.x, c.y, c.z, c.radius, COLOR, 2f, cam, true);
        }
    }
}
