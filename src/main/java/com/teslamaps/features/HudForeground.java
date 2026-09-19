package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.MapRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * While the inventory is open the draggable panels move from the HUD pass
 * (under the screen) to the screen pass (over it), so they stay readable and
 * clickable. The HUD lambdas in TeslaMaps skip these features whenever
 * active(); RecipeBookOverlayMixin draws them here instead.
 */
public class HudForeground {

    public static boolean active() {
        return Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!active()) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        DeltaTracker d = Minecraft.getInstance().getDeltaTracker();
        com.teslamaps.debug.Perf.begin("HudForeground");
        if (cfg.section("Map")) MapRenderer.render(g, d);
        if (cfg.section("Skills")) SkillTracker.render(g, d);
        if (cfg.section("Skills")) com.teslamaps.features.hunting.FlareMode.render(g, d);
        if (cfg.section("Combat")) BestiaryTracker.render(g, d);
        if (cfg.section("Garden")) {
            PestRoutine.render(g, d);
            FarmProgress.render(g, d);
            PestOverlay.render(g, d);
            FarmProfitOverlay.render(g, d);
            CharmedVisitors.render(g, d);
        }
        if (cfg.section("Timers")) DungeonTimers.render(g, d);
        com.teslamaps.debug.Perf.end();
    }
}
