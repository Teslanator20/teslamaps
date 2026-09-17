package com.teslamaps.features;

// Ported from Skysoft's SkillExpDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.GuiAccessor;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts the skill-xp segment out of the action bar and re-renders it as its own movable
 * HUD element. Lives and dies with the vanilla overlay message timer.
 */
public class SkillExpDisplay {

    private static final Pattern GAIN = Pattern.compile(
            "\\+(?<gained>[\\d.,]+) (?<skill>Combat|Farming|Fishing|Mining|Foraging|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social|Hunting) "
                    + "\\((?<progress>(?:[\\d.,]+%)|(?:[\\d.,]+/[\\d,.]+[kmbKMB]?))\\)");

    private static String line = null;

    /** Called with the raw action bar text before filtering. Returns true when the segment should be cut. */
    public static boolean onActionBar(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.skillExpDisplay || !c.section("Skills")) { line = null; return false; }

        Matcher m = GAIN.matcher(StringUtil.strip(raw));
        if (!m.find()) return false;
        line = "§3+" + m.group("gained") + " " + m.group("skill") + " (" + m.group("progress") + ")";
        return true;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.skillExpDisplay || line == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SkyblockUtils.isInSkyblock()) return;
        if (((GuiAccessor) mc.gui).teslamaps$overlayMessageTime() <= 0) { line = null; return; }

        draw(g, mc, x(mc, c), y(mc, c), c.skillExpDisplayScale, line);
    }

    public static void drawPreview(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        draw(g, mc, x, y, scale, "§3+152.6 Foraging (52.7%)");
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    public static int x(Minecraft mc, TeslaMapsConfig c) {
        if (c.skillExpDisplayX >= 0) return c.skillExpDisplayX;
        String preview = line != null ? line : "§3+152.6 Foraging (52.7%)";
        int w = (int) (mc.font.width(preview) * c.skillExpDisplayScale);
        return (mc.getWindow().getGuiScaledWidth() - w) / 2;
    }

    public static int y(Minecraft mc, TeslaMapsConfig c) {
        if (c.skillExpDisplayY >= 0) return c.skillExpDisplayY;
        return mc.getWindow().getGuiScaledHeight() - 76;
    }

    public static int width(Minecraft mc, float scale) {
        String preview = line != null ? line : "§3+152.6 Foraging (52.7%)";
        return (int) (mc.font.width(preview) * scale);
    }

    public static int height(float scale) {
        return (int) (9 * scale);
    }
}
