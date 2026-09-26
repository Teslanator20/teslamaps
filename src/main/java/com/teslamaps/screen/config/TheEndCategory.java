package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class TheEndCategory {

    private TheEndCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("End Stone Protector");
        b.toggle("Protector Tracker", () -> config.endProtector, v -> config.endProtector = v);
        b.info("Stage comes from the protector head and the tremor chat line");
        b.info("The Protector tab widget (/tab) is only a fallback");
        b.toggle("  Status HUD", () -> config.endProtectorStatusHud, v -> config.endProtectorStatusHud = v);
        b.toggle("  Loot Tracker HUD", () -> config.endProtectorLootHud, v -> config.endProtectorLootHud = v);
        b.toggle("  Spawn Beacon", () -> config.endProtectorBeacon, v -> config.endProtectorBeacon = v);
        b.toggle("  Spawn World Label", () -> config.endProtectorWorldText, v -> config.endProtectorWorldText = v);
        b.toggle("  Tracer To Spawn", () -> config.endProtectorTracer, v -> config.endProtectorTracer = v)
                .desc("Line from your crosshair to the spawn marker during stage 4/5");
        b.toggle("  Day 30 Restart Warning", () -> config.endProtectorDay30Warning, v -> config.endProtectorDay30Warning = v)
                .desc("Chat warning when stage 4 starts on a lobby at day 30 or later - it might reboot before the golem spawns")
                .tags("day", "restart", "reboot", "lobby");
        b.label("Stage Alerts");
        b.toggle("Stage 4 Title", () -> config.endProtectorStage4Title, v -> config.endProtectorStage4Title = v);
        b.toggle("Stage 4 Sound", () -> config.endProtectorStage4Sound, v -> config.endProtectorStage4Sound = v);
        b.toggle("Stage 5 Title", () -> config.endProtectorStage5Title, v -> config.endProtectorStage5Title = v);
        b.toggle("Stage 5 Sound", () -> config.endProtectorStage5Sound, v -> config.endProtectorStage5Sound = v);
        b.toggle("Stage 4 Duration in Chat", () -> config.endProtectorStage4Duration, v -> config.endProtectorStage4Duration = v);
        b.label("Fight Summary");
        b.toggle("DPS in Chat", () -> config.endProtectorDpsChat, v -> config.endProtectorDpsChat = v);
        b.info("Hover the line for the top 3 damagers' DPS");
        b.toggle("Loot Quality in Chat", () -> config.endProtectorLootQualityChat, v -> config.endProtectorLootQualityChat = v);
        b.info("Placement + damage share + zealots; hover shows which drops it reaches");
        b.toggle("Rare Drop Alert", () -> config.endProtectorDropAlert, v -> config.endProtectorDropAlert = v);
        b.toggle("  Skip Tier Boost Core", () -> config.endProtectorIgnoreTbc, v -> config.endProtectorIgnoreTbc = v)
                .desc("Still counted, just no alert");
        b.toggle("  Skip Legendary Golem Pet", () -> config.endProtectorIgnoreLegPet, v -> config.endProtectorIgnoreLegPet = v);
        b.toggle("  Skip Epic Golem Pet", () -> config.endProtectorIgnoreEpicPet, v -> config.endProtectorIgnoreEpicPet = v);
        b.button("Reset Loot Counts", () -> {
            config.protectorTierBoostCores = 0;
            config.protectorLegendaryPets = 0;
            config.protectorEpicPets = 0;
            TeslaMapsConfig.save();
        });
    }
}
