package com.teslamaps.features.theend;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.HudVisibility;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Running count of the End Stone Protector rare drops. */
public class ProtectorLootHud {

    private static List<String> lines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return List.of(
                "§6§lProtector Loot",
                "§6Tier Boost Core§f: " + c.protectorTierBoostCores,
                "§7[Lvl 1] §6Golem§f: " + c.protectorLegendaryPets,
                "§7[Lvl 1] §5Golem§f: " + c.protectorEpicPets);
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.endProtector || !c.endProtectorLootHud) return;
        if (HudVisibility.suppressed()) return;
        if (!EndProtector.inArea()) return;

        draw(context, Minecraft.getInstance(), c.protectorLootX, c.protectorLootY, c.protectorLootScale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String line : lines()) {
            context.text(mc.font, line, 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
            row++;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(Font font) {
        List<String> lines = lines();
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
