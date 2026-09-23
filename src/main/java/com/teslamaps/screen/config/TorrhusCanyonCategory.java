package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class TorrhusCanyonCategory {

    private TorrhusCanyonCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Critter ESP");
        b.toggle("Critter ESP", () -> config.critterESP, v -> config.critterESP = v);
        b.info("Only active in Area: " + com.teslamaps.features.CritterESP.AREA);
        b.toggle("  Filled Boxes", () -> config.critterFilled, v -> config.critterFilled = v);
        b.toggle("  Tracers To All Critters", () -> config.critterTracers, v -> config.critterTracers = v);
        b.label("Tiki (3 stacked heads)");
        b.toggle("Tiki ESP", () -> config.tikiESP, v -> config.tikiESP = v);
        b.toggle("  Show Name", () -> config.tikiESPName, v -> config.tikiESPName = v);
        b.color("  Tiki Box Color", () -> config.tikiESPColor, v -> config.tikiESPColor = v);
        b.label("Queen Ant");
        b.toggle("Queen Ant Warning", () -> config.queenAntWarning, v -> config.queenAntWarning = v);
        b.info("Title when a Queen Ant spawns from a destroyed anthill");
        b.toggle("  Line To The Queen", () -> config.queenAntLine, v -> config.queenAntLine = v);
        b.soundDropdown("  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.queenAntSound, v -> config.queenAntSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.queenAntVolume, v -> config.queenAntVolume = v, 2);
        b.label("Critters");
        b.toggle("Beeheemoth & Honeybuzz ESP", () -> config.critterBee, v -> config.critterBee = v);
        b.color("  Color", () -> config.colorCritterBee, v -> config.colorCritterBee = v);
        b.toggle("Blue Jay ESP", () -> config.critterBlueJay, v -> config.critterBlueJay = v);
        b.color("  Color", () -> config.colorCritterBlueJay, v -> config.colorCritterBlueJay = v);
        b.toggle("Bunbun ESP", () -> config.critterBunbun, v -> config.critterBunbun = v);
        b.color("  Color", () -> config.colorCritterBunbun, v -> config.colorCritterBunbun = v);
        b.toggle("Drybark ESP", () -> config.critterDrybark, v -> config.critterDrybark = v);
        b.color("  Color", () -> config.colorCritterDrybark, v -> config.colorCritterDrybark = v);
        b.toggle("Firefox ESP", () -> config.critterFirefox, v -> config.critterFirefox = v);
        b.color("  Color", () -> config.colorCritterFirefox, v -> config.colorCritterFirefox = v);
        b.toggle("Grizzly Bear ESP", () -> config.critterGrizzly, v -> config.critterGrizzly = v);
        b.color("  Color", () -> config.colorCritterGrizzly, v -> config.colorCritterGrizzly = v);
        b.toggle("Groundhog ESP", () -> config.critterGroundhog, v -> config.critterGroundhog = v);
        b.color("  Color", () -> config.colorCritterGroundhog, v -> config.colorCritterGroundhog = v);
        b.toggle("Hideonsun ESP", () -> config.critterHideonsun, v -> config.critterHideonsun = v);
        b.color("  Color", () -> config.colorCritterHideonsun, v -> config.colorCritterHideonsun = v);
        b.toggle("Mountain Goat ESP", () -> config.critterMountainGoat, v -> config.critterMountainGoat = v);
        b.color("  Color", () -> config.colorCritterMountainGoat, v -> config.colorCritterMountainGoat = v);
        b.toggle("Sepialot ESP", () -> config.critterSepialot, v -> config.critterSepialot = v);
        b.color("  Color", () -> config.colorCritterSepialot, v -> config.colorCritterSepialot = v);
        b.toggle("Dustybit ESP", () -> config.critterDustybit, v -> config.critterDustybit = v);
        b.color("  Color", () -> config.colorCritterDustybit, v -> config.colorCritterDustybit = v);
        b.toggle("Puck ESP", () -> config.critterPuck, v -> config.critterPuck = v);
        b.color("  Color", () -> config.colorCritterPuck, v -> config.colorCritterPuck = v);
    }
}
