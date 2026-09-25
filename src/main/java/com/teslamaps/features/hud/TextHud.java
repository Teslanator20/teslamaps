package com.teslamaps.features.hud;

import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Panel with plain text lines, shared by the small w07 HUDs. */
public final class TextHud {

    private TextHud() {}

    public static int width(List<String> ls) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    public static int height(int lines) {
        return lines * 10 + 6;
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        if (ls.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, height(ls.size()));
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
