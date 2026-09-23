package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class MobVisualsCategory {

    private MobVisualsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Mob Visuals");
        b.toggle("Mob Visuals", () -> config.mobVisuals, v -> config.mobVisuals = v);
        b.info("Glow, tracer and nameplate for the mobs you pick, per area");
        b.button("Pick Mobs (/tmap mobs)",
                () -> Minecraft.getInstance().setScreen(new MobVisualScreen(b.screen())));
        b.toggle("  Glow", () -> config.mobVisualGlow, v -> config.mobVisualGlow = v);
        b.toggle("  Tracers", () -> config.mobVisualTracer, v -> config.mobVisualTracer = v);
        b.toggle("  Tracer to Every Mob", () -> config.mobVisualTracerAll, v -> config.mobVisualTracerAll = v);
        b.info("Off draws one line per species, to the nearest one");
        b.toggle("  Nameplates", () -> config.mobVisualNameplate, v -> config.mobVisualNameplate = v);
        b.toggle("    Show Health", () -> config.mobVisualNameplateHealth, v -> config.mobVisualNameplateHealth = v);
        b.slider("    Nameplate Size", 0.5f, 3.0f,
                () -> config.mobVisualNameplateScale, v -> config.mobVisualNameplateScale = v);
    }
}
