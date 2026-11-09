package com.teslamaps.features.mining;

// Ported from Skyblocker's CrystalsHudWidget (map image from Skyblocker too)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.WorldScanner;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Crystal Hollows minimap: player arrow plus every structure the scanner found. */
public final class HollowsMap {

    private static final Identifier MAP = Identifier.fromNamespaceAndPath("teslamaps", "textures/gui/crystals_map.png");
    private static final Identifier ICON = Identifier.withDefaultNamespace("textures/map/decorations/player.png");
    private static final int SIZE = 62;

    private static HudEditElement hud;
    private static boolean show;
    private static int ticks;
    private static int count;
    private static int[] xs = new int[16], ys = new int[16], colors = new int[16];
    private static boolean[] small = new boolean[16];

    private HollowsMap() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("hollowsMap", "Crystal Hollows Map").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().hollowsMap)
                .size(() -> SIZE, () -> SIZE)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, false));
        Feature.of("HollowsMap").section("Mining").when(() -> cfg().hollowsMap)
                .tick(HollowsMap::tick)
                .hud("hollowsmap", HollowsMap::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Crystal Hollows Map", () -> c.hollowsMap, v -> c.hollowsMap = v)
                            .desc("Minimap of the Hollows with your position and the structures the Crystal Hollows Scanner found. Move and scale it in /tmap gui.")
                            .tags("minimap", "crystal hollows", "map", "hud");
                    b.toggle("  Show Locations", () -> c.hollowsMapLocations, v -> c.hollowsMapLocations = v);
                    b.slider("  Location Size", 2f, 12f, () -> (float) c.hollowsMapLocationSize,
                            v -> c.hollowsMapLocationSize = Math.round(v), 0);
                    b.info("Locations come from the Crystal Hollows Scanner above");
                });
    }

    private static void tick() {
        show = SkyblockUtils.isInArea("Crystal Hollows");
        if (!show || ++ticks % 20 != 0) return;
        count = 0;
        WorldScanner.visit((name, pos, color) -> {
            if (count == xs.length) {
                xs = java.util.Arrays.copyOf(xs, count * 2);
                ys = java.util.Arrays.copyOf(ys, count * 2);
                colors = java.util.Arrays.copyOf(colors, count * 2);
                small = java.util.Arrays.copyOf(small, count * 2);
            }
            xs[count] = toMap(pos.getX());
            ys[count] = toMap(pos.getZ());
            colors[count] = 0xFF000000 | (color & 0xFFFFFF);
            small[count] = !(name.contains("Crystal") || name.contains("Temple") || name.contains("City")
                    || name.contains("Divan") || name.contains("Den") || name.contains("Nest") || name.contains("Lair")
                    || name.contains("Khazad"));
            count++;
        });
    }

    private static int toMap(double v) {
        return Math.clamp((int) ((v - 202) / 621 * SIZE), 0, SIZE);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), true);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, boolean live) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, MAP, 0, 0, 0, 0, SIZE, SIZE, SIZE, SIZE);
        if (live && cfg().hollowsMapLocations) {
            int size = Math.max(2, cfg().hollowsMapLocationSize);
            for (int i = 0; i < count; i++) {
                int s = small[i] ? Math.max(1, size / 2) : size;
                g.fill(xs[i] - s / 2, ys[i] - s / 2, xs[i] + (s + 1) / 2, ys[i] + (s + 1) / 2, colors[i]);
            }
        }
        if (mc.player != null) {
            int px = live ? toMap(mc.player.getX()) : SIZE / 2;
            int py = live ? toMap(mc.player.getZ()) : SIZE / 2;
            pose.translate(px - 2, py - 3);
            pose.scale(0.75f, 0.75f);
            pose.rotateAbout(Mth.DEG_TO_RAD * cardinal(mc.player.getYRot()), 2.5f, 3.5f);
            g.blit(RenderPipelines.GUI_TEXTURED, ICON, 0, 0, 2, 0, 5, 7, 8, 8);
        }
        pose.popMatrix();
    }

    // 16 steps like a vanilla map marker
    private static float cardinal(float yaw) {
        yaw += 180;
        byte clipped = (byte) ((yaw + (yaw < 0.0 ? -8.0 : 8.0)) * 16.0 / 360.0);
        return clipped * 360f / 16f;
    }
}
