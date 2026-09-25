package com.teslamaps.features;

// Skyblocker TabHudWidget.updateContentMissing
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TabWidgetHint {

    private TabWidgetHint() {}

    public static void register() {
        Feature.of("TabWidgetHint")
                .config("Interface", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Tab Widgets");
                    b.toggle("Missing Tab Widget Hint", () -> c.tabWidgetHint, v -> c.tabWidgetHint = v)
                            .desc("Pet, Pickaxe Ability and Bestiary HUDs say which tab widget is missing instead of staying empty. Add widgets with /tab.")
                            .tags("widget", "tab list", "missing");
                    TabWidgetGuide.config(b, c);
                });
    }

    public static boolean on() {
        return TeslaMapsConfig.get().tabWidgetHint;
    }

    public static String[] lines(String widget) {
        return new String[] { "§c" + widget + " tab widget missing", "§7Add it with §c/tab" };
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String[] lines) {
        if (!SkyblockUtils.isInSkyblock()) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        for (int i = 0; i < lines.length; i++) g.text(mc.font, lines[i], 0, i * (mc.font.lineHeight + 1), 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
