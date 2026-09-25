package com.teslamaps.features.theend;

// Ported from Zen's ZealotSpawnLocations
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ZealotSpawns {

    private static final int[][] ZEALOT = {
            {-646, 5, -274}, {-633, 5, -277}, {-639, 7, -305}, {-631, 5, -327}, {-619, 6, -313}, {-665, 10, -313},
            {-632, 5, -260}, {-630, 7, -229}, {-647, 5, -221}, {-684, 5, -261}, {-699, 5, -263}, {-683, 5, -292},
            {-698, 5, -319}, {-714, 5, -289}, {-732, 5, -295}, {-731, 5, -275}
    };
    private static final int[][] BRUISER = {
            {-595, 80, -190}, {-575, 72, -201}, {-560, 64, -220}, {-554, 56, -237}, {-571, 51, -240}, {-585, 52, -232},
            {-596, 55, -216}, {-578, 53, -214}, {-598, 55, -201}, {-532, 38, -223}, {-520, 38, -235}, {-530, 38, -246},
            {-515, 39, -250}, {-516, 39, -264}, {-513, 38, -279}, {-524, 44, -268}, {-536, 48, -252}, {-526, 38, -294},
            {-514, 39, -304}, {-526, 39, -317}
    };
    private static final double[] ZEALOT_CENTER = {-675, 5, -280};
    private static final double[] BRUISER_CENTER = {-550, 50, -250};

    private static final Vec3[] ZEALOT_LABELS = labels(ZEALOT);
    private static final Vec3[] BRUISER_LABELS = labels(BRUISER);
    private static final AABB[] ZEALOT_BOXES = boxes(ZEALOT);
    private static final AABB[] BRUISER_BOXES = boxes(BRUISER);

    private static int[][] active = null;
    private static boolean bruiser = false;
    private static long readyAt = 0;
    private static String text = "";
    private static IntOpenHashSet seen = new IntOpenHashSet();
    private static boolean primed = false;
    private static boolean inEnd = false;
    private static int ticks = 0;

    private ZealotSpawns() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ZealotSpawns").section("The End").when(() -> cfg().zealotSpawnPoints)
                .tick(ZealotSpawns::tick)
                .world(ZealotSpawns::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Zealot / Bruiser Spawn Points", () -> c.zealotSpawnPoints, v -> c.zealotSpawnPoints = v)
                            .desc("Dragon's Nest and the Zealot Bruiser Hideout: the fixed spawn points with a timer, "
                                    + "8 s after a new Zealot / Bruiser showed up, then \"Ready\".")
                            .tags("zealot", "bruiser", "spawn", "timer", "grind");
                    b.toggle("  Spawn Boxes", () -> c.zealotSpawnBoxes, v -> c.zealotSpawnBoxes = v);
                    b.color("  Box Color", () -> c.zealotSpawnColor, v -> c.zealotSpawnColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 1) inEnd = SkyblockUtils.isInArea("The End");
        int[][] now = null;
        boolean br = false;
        if (inEnd) {
            if (near(mc, ZEALOT_CENTER, 90)) now = ZEALOT;
            else if (near(mc, BRUISER_CENTER, 90)) {
                now = BRUISER;
                br = true;
            }
        }
        if (now != active) {
            active = now;
            bruiser = br;
            primed = false;
            seen = new IntOpenHashSet();
            readyAt = 0;
        }
        if (active == null) {
            text = "";
            return;
        }
        if (ticks % 5 == 0) scan(mc);
        long left = readyAt - System.currentTimeMillis();
        String who = bruiser ? "Bruiser" : "Zealot";
        text = left > 0 ? "§d" + who + " Spawn: §5" + (left / 1000) + "." + (left % 1000 / 100) + "s"
                : "§d" + who + " Spawn: §aReady";
    }

    private static void scan(Minecraft mc) {
        IntOpenHashSet next = new IntOpenHashSet();
        boolean spawned = false;
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            String name = s.getName().getString();
            if (!name.contains("Zealot") || !name.contains("❤")) continue;
            if (bruiser != name.contains("Bruiser")) continue;
            if (s.distanceToSqr(mc.player) > 100 * 100) continue;
            next.add(s.getId());
            if (primed && !seen.contains(s.getId())) spawned = true;
        }
        seen = next;
        primed = true;
        if (spawned) readyAt = System.currentTimeMillis() + 8000;
    }

    private static boolean near(Minecraft mc, double[] c, double r) {
        double dx = mc.player.getX() - c[0], dz = mc.player.getZ() - c[2];
        return dx * dx + dz * dz < r * r;
    }

    private static Vec3[] labels(int[][] pts) {
        Vec3[] out = new Vec3[pts.length];
        for (int i = 0; i < pts.length; i++) out[i] = new Vec3(pts[i][0] + 0.5, pts[i][1] + 1.5, pts[i][2] + 0.5);
        return out;
    }

    private static AABB[] boxes(int[][] pts) {
        AABB[] out = new AABB[pts.length];
        for (int i = 0; i < pts.length; i++) {
            int[] p = pts[i];
            out[i] = new AABB(p[0] - 4.5, p[1], p[2] - 4.5, p[0] + 5.5, p[1] + 3, p[2] + 5.5);
        }
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        int[][] pts = active;
        if (pts == null || text.isEmpty()) return;
        Vec3[] labels = pts == BRUISER ? BRUISER_LABELS : ZEALOT_LABELS;
        AABB[] boxes = pts == BRUISER ? BRUISER_BOXES : ZEALOT_BOXES;
        boolean drawBoxes = cfg().zealotSpawnBoxes;
        int color = drawBoxes ? TeslaMapsConfig.parseColor(cfg().zealotSpawnColor) : 0;
        for (int i = 0; i < labels.length; i++) {
            if (drawBoxes) {
                ESPRenderer.drawFilledBox(pose, boxes[i], color, cam, false);
                ESPRenderer.drawBoxOutline(pose, boxes[i], color | 0xFF000000, 2.0f, cam, false);
            }
            ESPRenderer.drawText(pose, text, labels[i], 1.0f, cam);
        }
    }
}
