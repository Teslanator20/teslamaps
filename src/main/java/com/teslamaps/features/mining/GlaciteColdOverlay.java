package com.teslamaps.features.mining;

// Ported from Skyblocker's skyblock/dwarven/GlaciteColdOverlay.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Powder snow vignette at cold/100 opacity while Glacite cold builds up. */
public final class GlaciteColdOverlay {

    private static final Identifier OUTLINE = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    private static final Pattern COLD = Pattern.compile("Cold: -(\\d+)❄");
    private static final String CAMPFIRE = "The warmth of the campfire reduced your ❄ Cold to 0!";

    private static Feature feature;
    private static int cold, ticks;
    private static long resetAt;

    private GlaciteColdOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GlaciteColdOverlay").section("Mining").when(() -> cfg().glaciteColdOverlay)
                .tick(GlaciteColdOverlay::tick)
                .serverChat(GlaciteColdOverlay::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Glacite Cold Overlay");
                    b.toggle("Glacite Cold Overlay", () -> c.glaciteColdOverlay, v -> c.glaciteColdOverlay = v)
                            .desc("Frost vignette that gets stronger with the Cold value on the scoreboard, as a warning before you freeze")
                            .tags("cold", "glacite", "freeze", "vignette", "mineshaft", "frost");
                });
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("teslamaps", "glacite_cold"), (g, d) -> {
                    if (cold <= 0 || feature == null || !feature.active()) return;
                    Perf.begin("GlaciteColdOverlay");
                    draw(g);
                    Perf.end();
                });
    }

    /** Cold from the scoreboard, -1 when the line is missing. */
    public static int readCold() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (line.indexOf('❄') < 0) continue;
            Matcher m = COLD.matcher(StringUtil.stripTrim(line));
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        if (!Mining.onMiningIsland() || System.currentTimeMillis() - resetAt < 3000) {
            cold = 0;
            return;
        }
        cold = Math.max(0, readCold());
    }

    private static void onChat(String text) {
        if (StringUtil.stripTrim(text).equals(CAMPFIRE)) {
            cold = 0;
            resetAt = System.currentTimeMillis();
        }
    }

    private static void draw(GuiGraphicsExtractor g) {
        g.blit(RenderPipelines.GUI_TEXTURED, OUTLINE, 0, 0, 0f, 0f, g.guiWidth(), g.guiHeight(),
                g.guiWidth(), g.guiHeight(), ARGB.white(Math.min(1f, cold / 100f)));
    }
}
