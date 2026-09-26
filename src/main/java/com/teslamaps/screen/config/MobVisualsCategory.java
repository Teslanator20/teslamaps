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
        b.toggle("    Pests Green For Contest Crop", () -> config.pestGlowContest, v -> config.pestGlowContest = v)
                .desc("During a Jacob's contest, picked pests of the crop you are scoring glow green")
                .tags("pest", "jacob", "contest", "glow");
        b.toggle("    Pests Teal For Playing Vinyl", () -> config.pestGlowVinyl, v -> config.pestGlowVinyl = v)
                .desc("Outside contests, picked pests the running Stereo Harmony vinyl targets glow dark aqua. Open Stereo Harmony once to read it")
                .tags("pest", "vinyl", "stereo", "glow");
        b.toggle("  Tracers", () -> config.mobVisualTracer, v -> config.mobVisualTracer = v);
        b.toggle("    Tracer to Every Mob", () -> config.mobVisualTracerAll, v -> config.mobVisualTracerAll = v);
        b.info("Off draws one line per species, to the nearest one");
        b.toggle("  Nameplates", () -> config.mobVisualNameplate, v -> config.mobVisualNameplate = v);
        b.toggle("    Show Health", () -> config.mobVisualNameplateHealth, v -> config.mobVisualNameplateHealth = v);
        b.toggle("    Draw As HUD (constant size)", () -> config.mobVisualNameplateHud, v -> config.mobVisualNameplateHud = v);
        b.slider("    Nameplate Size", 0.5f, 3.0f,
                () -> config.mobVisualNameplateScale, v -> config.mobVisualNameplateScale = v);
        b.toggle("  Lotum: Confirm By Nametag", () -> config.mobVisualLotumConfirm, v -> config.mobVisualLotumConfirm = v)
                .desc("Only frogs with a Lotum nametag right above them count as Lotum");
        b.toggle("  Lotum: Line To Clicked", () -> config.mobVisualLotumLine, v -> config.mobVisualLotumLine = v)
                .desc("Green line to the nearest visible Lotum you clicked");
    }
}
