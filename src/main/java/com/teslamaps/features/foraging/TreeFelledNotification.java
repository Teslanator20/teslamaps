package com.teslamaps.features.foraging;

// Ported from FloydAddons' FloydFellNotification
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Big TIMBER! / WOODPECKER! / PETALFALL! label above the crosshair when a whole tree falls. */
public final class TreeFelledNotification {

    private static final int DARK = 0x00AA00, BRIGHT = 0x55FF55;
    private static final long SHOW = 2500, FADE = 500;
    private static final String WIDEST = "WOODPECKER!";

    private static String label;
    private static int color;
    private static long started;
    private static Object lastLevel;
    private static HudEditElement hud;

    private TreeFelledNotification() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("treeFelled", "Tree Felled").section("Foraging")
                .enabled(() -> cfg().treeFelledNotification)
                .size(() -> Minecraft.getInstance().font.width(WIDEST), () -> 9)
                .scaleRange(0.5f, 6f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "TIMBER!", BRIGHT, 255));
        Feature.of("TreeFelledNotification").section("Foraging").when(() -> cfg().treeFelledNotification)
                .serverChat(TreeFelledNotification::onChat)
                .hudHiddenInInventory("treefelled", TreeFelledNotification::render)
                .config("Foraging", b -> b.toggle("Tree Felled Notification", () -> cfg().treeFelledNotification,
                                v -> cfg().treeFelledNotification = v)
                        .desc("TIMBER! / WOODPECKER! / PETALFALL! above the crosshair for 2.5s. Position in /tmap gui")
                        .tags("timber", "woodpecker", "petalfall", "felled"));
    }

    private static void onChat(String text) {
        String s = StringUtil.stripTrim(text);
        if (!s.contains("You felled the entire")) return;
        if (s.startsWith("TIMBER!")) set("TIMBER!", BRIGHT);
        else if (s.startsWith("WOODPECKER!")) set("WOODPECKER!", DARK);
        else if (s.startsWith("PETALFALL!")) set("PETALFALL!", DARK);
    }

    private static void set(String l, int c) {
        label = l;
        color = c;
        started = System.currentTimeMillis();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            label = null;
        }
        String l = label;
        if (l == null || hud == null || mc.options.hideGui) return;
        long el = System.currentTimeMillis() - started;
        if (el >= SHOW) {
            label = null;
            return;
        }
        int alpha = el < SHOW - FADE ? 255 : (int) Math.max(0, Math.min(255, (SHOW - el) * 255 / FADE));
        if (alpha < 5) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l, color, alpha);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String l, int rgb, int alpha) {
        int w = mc.font.width(WIDEST);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, l, (w - mc.font.width(l)) / 2, 0, (alpha << 24) | rgb, true);
        g.pose().popMatrix();
    }
}
