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
        b.toggle("    Pests Green For Contest Crop", () -> config.pestGlowContest, v -> config.pestGlowContest = v).fresh()
                .desc("During a Jacob's contest, picked pests of the crop you are scoring glow green")
                .tags("pest", "jacob", "contest", "glow");
        b.toggle("    Pests Teal For Playing Vinyl", () -> config.pestGlowVinyl, v -> config.pestGlowVinyl = v).fresh()
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
        b.toggle("  Health List HUD", () -> config.mobHealthHud, v -> config.mobHealthHud = v).fresh()
                .desc("Up to 5 nearby tracked mobs with their health as a HUD list, read from the \"cur/max❤\" nametag. Works for any mob name, no pick needed. Position in /tmap gui")
                .tags("health", "hp", "inquisitor", "jawbus", "thunder", "elusive");
        b.searchList("    Tracked Mobs", () -> {
                    java.util.List<String> base = new java.util.ArrayList<>(com.teslamaps.features.mobvisuals.MobHealthHud.DEFAULTS);
                    for (String s : config.mobHealthHudMobs) if (!base.contains(s)) base.add(s);
                    for (String s : NearbyMobs.options(config.mobHealthHudMobs)) if (!base.contains(s)) base.add(s);
                    return base;
                }, id -> config.mobHealthHudMobs.contains(id),
                id -> { if (!config.mobHealthHudMobs.remove(id)) config.mobHealthHudMobs.add(id); }).fresh()
                .pick(NearbyMobs::looked);
        b.toggle("  Lotum: Confirm By Nametag", () -> config.mobVisualLotumConfirm, v -> config.mobVisualLotumConfirm = v).fresh()
                .desc("Only frogs with a Lotum nametag right above them count as Lotum");
        b.toggle("  Lotum: Line To Clicked", () -> config.mobVisualLotumLine, v -> config.mobVisualLotumLine = v).fresh()
                .desc("Green line to the nearest visible Lotum you clicked");
    }
}
