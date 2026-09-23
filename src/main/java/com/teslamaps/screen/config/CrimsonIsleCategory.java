package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class CrimsonIsleCategory {

    private CrimsonIsleCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Rescue Mission");
        b.toggle("Rescue Mission Pathfinder", () -> config.rescueMission, v -> config.rescueMission = v);
        b.info("Open the Rescue quest on the Quest Board once, then talk to the");
        b.info("Undercover Agent - the hostage parkour path shows up in the world");
        b.slider("  Look Ahead (platforms)", 1f, 10f,
                () -> (float) config.rescueLookAhead, v -> config.rescueLookAhead = Math.round(v));
        b.dropdown("  Faction",
                new String[]{"Auto", "Barbarian", "Mage"},
                () -> config.rescueFaction, v -> config.rescueFaction = v);
        b.info("Auto reads the tab list - set it manually if that widget is off");
        b.dropdown("  Barbarian S Path Variant",
                new String[]{"1", "2"},
                () -> String.valueOf(config.rescueVariant), v -> config.rescueVariant = Integer.parseInt(v));
        b.info("Two S-rank layouts exist - switch if the path looks wrong");
    }
}
