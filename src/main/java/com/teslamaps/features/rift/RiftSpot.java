package com.teslamaps.features.rift;

import com.teslamaps.features.FishingHelpers;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Rift island + scoreboard sub-area, throttled to once a second; plus a plain line panel. */
public final class RiftSpot {

    private static long at;
    private static boolean rift;
    private static String sub = "";

    private RiftSpot() {}

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - at < 1000) return;
        at = now;
        rift = SkyblockUtils.isInArea("The Rift");
        sub = rift ? FishingHelpers.subArea() : "";
    }

    public static boolean inRift() {
        refresh();
        return rift;
    }

    public static String sub() {
        refresh();
        return sub;
    }

    public static boolean inMirrorverse() {
        return sub().equals("Mirrorverse");
    }

    public static boolean inStillgore() {
        return sub().startsWith("Stillgore Ch");
    }

    public static boolean inMountaintop() {
        return switch (sub()) {
            case "Continuum", "The Mountaintop", "Trial Grounds", "Time-Torn Isles", "Wizardman Bureau",
                 "Wizard Brawl", "Walk of Fame", "Time Chamber" -> true;
            default -> false;
        };
    }

    /** Lines with optional 16px item icons; icons may be null. */
    public static void drawLines(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale,
                                 String[] lines, ItemStack[] icons, int w) {
        if (lines.length == 0) return;
        int rowH = icons != null ? 17 : 10;
        int pad = HudStyle.padding();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, w + pad * 2, height(lines, icons) + pad * 2);
        for (int i = 0; i < lines.length; i++) {
            int ty = i * rowH;
            int tx = 0;
            if (icons != null && icons[i] != null) {
                g.item(icons[i], 0, ty);
                tx = 18;
            }
            g.text(mc.font, lines[i], tx, icons != null ? ty + 4 : ty, 0xFFFFFFFF, true);
        }
        g.pose().popMatrix();
    }

    public static int width(Minecraft mc, String[] lines, ItemStack[] icons) {
        int w = 0;
        for (int i = 0; i < lines.length; i++) {
            int lw = mc.font.width(lines[i]) + (icons != null && icons[i] != null ? 18 : 0);
            if (lw > w) w = lw;
        }
        return w;
    }

    public static int height(String[] lines, ItemStack[] icons) {
        return icons != null ? lines.length * 17 - 1 : Math.max(9, lines.length * 10 - 1);
    }
}
