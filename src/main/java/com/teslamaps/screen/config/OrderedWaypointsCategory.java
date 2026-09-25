package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class OrderedWaypointsCategory {

    private OrderedWaypointsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.info("Routes outside dungeons - Coleweight / Skytils format");
        b.toggle("Render Route",
                () -> config.orderedWaypoints, v -> config.orderedWaypoints = v);
        b.button("Manage Routes",
                () -> Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(new WaypointRoutesScreen())));
        b.button("Import From Clipboard",
                () -> MapConfigScreen.orderedFeedback(com.teslamaps.features.waypoints.OrderedWaypoints.importClipboard()));
        b.button("Unload Route",
                () -> MapConfigScreen.orderedFeedback(com.teslamaps.features.waypoints.OrderedWaypoints.unload()));
        b.info("/tmap waypoints for the GUI, /tmap route ... for commands");
        b.info("Import: Coleweight, Skytils V1, Skyblocker, Firmament, Waypointer (WP:), Soopy, JSON");
        b.button("Export All Routes (Skyblocker)",
                () -> MapConfigScreen.orderedFeedback(com.teslamaps.features.waypoints.OrderedWaypoints.exportAll("skyblocker")));
        b.button("Save All Routes As JSON File",
                () -> MapConfigScreen.orderedFeedback(com.teslamaps.features.waypoints.OrderedWaypoints.exportFile()));

        b.label("Display");
        b.color("Current Color", () -> config.orderedColorCurrent, v -> config.orderedColorCurrent = v);
        b.color("Previous Color", () -> config.orderedColorPrevious, v -> config.orderedColorPrevious = v);
        b.color("Next Color", () -> config.orderedColorNext, v -> config.orderedColorNext = v);
        b.slider("Next Waypoints", 1f, 5f,
                () -> (float) config.orderedNextCount, v -> config.orderedNextCount = Math.round(v));
        b.toggle("Filled Boxes", () -> config.orderedFillBlock, v -> config.orderedFillBlock = v);
        b.slider("Outline Thickness", 1f, 10f,
                () -> config.orderedOutlineThickness, v -> config.orderedOutlineThickness = v);
        b.toggle("Show Number", () -> config.orderedShowName, v -> config.orderedShowName = v);
        b.toggle("Show Distance", () -> config.orderedShowDistance, v -> config.orderedShowDistance = v);
        b.toggle("Show All Waypoints", () -> config.orderedShowAll, v -> config.orderedShowAll = v);
        b.color("  All Color", () -> config.orderedColorAll, v -> config.orderedColorAll = v);
        b.toggle("Use Imported Names & Colors", () -> config.orderedImportedStyle, v -> config.orderedImportedStyle = v);

        b.label("Trace Line");
        b.toggle("Trace Line", () -> config.orderedTraceLine, v -> config.orderedTraceLine = v);
        b.color("  Trace Line Color", () -> config.orderedColorTraceLine, v -> config.orderedColorTraceLine = v);
        b.slider("  Trace Line Thickness", 1f, 10f,
                () -> config.orderedTraceThickness, v -> config.orderedTraceThickness = v, 1);

        b.label("Progression");
        b.slider("Waypoint Range", 1f, 10f,
                () -> config.orderedWaypointRange, v -> config.orderedWaypointRange = v, 1);
        b.toggle("Auto-Skip Forward", () -> config.orderedAutoSkipForward, v -> config.orderedAutoSkipForward = v);
        b.toggle("Skip Air Waypoints", () -> config.orderedSkipAir, v -> config.orderedSkipAir = v);
        b.info("Waypoints on an air block are jumped over (19 -> 21 if 20 is air)");
        b.toggle("Advance Only On Ground", () -> config.orderedOnGround, v -> config.orderedOnGround = v)
                .desc("A waypoint only counts once you land near it, not while jumping past.");
        b.toggle("Unload On World Change", () -> config.orderedAutoUnload, v -> config.orderedAutoUnload = v);
        b.toggle("Auto-Load Route Named Like Area", () -> config.orderedAutoLoadByArea, v -> config.orderedAutoLoadByArea = v);
        b.info("Save a route as e.g. \"Crystal Hollows\" to load it there");
        b.toggle("Auto-Load Route Named Like Mineshaft", () -> config.orderedAutoLoadShaft, v -> config.orderedAutoLoadShaft = v)
                .desc("Loads the saved route named like the mineshaft type from the scoreboard, e.g. TOPA_1 or PERI_C.")
                .tags("mineshaft", "glacite", "shaft");
        b.toggle("  Unload On Leaving Mineshaft", () -> config.orderedAutoUnloadShaft, v -> config.orderedAutoUnloadShaft = v);

        b.label("Setup Mode");
        b.toggle("Setup Mode", () -> config.orderedSetupMode, v -> config.orderedSetupMode = v);
        b.info("Shows nearby waypoints + the line between them");
        b.color("  Setup Waypoint Color", () -> config.orderedColorSetup, v -> config.orderedColorSetup = v);
        b.color("  Setup Line Color", () -> config.orderedColorSetupLine, v -> config.orderedColorSetupLine = v);
        b.slider("  Setup Range", 1f, 100f,
                () -> config.orderedSetupRange, v -> config.orderedSetupRange = v);
        b.slider("  Setup Line Thickness", 1f, 10f,
                () -> config.orderedSetupLineThickness, v -> config.orderedSetupLineThickness = v);
        b.toggle("  Sneaking During Route", () -> config.orderedSneaking, v -> config.orderedSneaking = v);
    }
}
