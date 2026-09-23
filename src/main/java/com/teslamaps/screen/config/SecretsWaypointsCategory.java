package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class SecretsWaypointsCategory {

    private SecretsWaypointsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Secret Waypoints");
        b.toggle("Enable Secret Waypoints", () -> config.secretWaypoints, v -> config.secretWaypoints = v);
        b.toggle("  Hide When Collected", () -> config.secretWaypointHideCollected, v -> config.secretWaypointHideCollected = v);
        b.toggle("  Tracers to Waypoints", () -> config.secretWaypointTracers, v -> config.secretWaypointTracers = v);
        b.toggle("  Secret Click Highlight", () -> config.secretClickHighlight, v -> config.secretClickHighlight = v);
        b.label("Secret Types");
        b.toggle("Show Chests", () -> config.secretWaypointChests, v -> config.secretWaypointChests = v);
        b.color("  Chest", () -> config.colorSecretChest, v -> config.colorSecretChest = v);
        b.toggle("Show Items", () -> config.secretWaypointItems, v -> config.secretWaypointItems = v);
        b.color("  Item", () -> config.colorSecretItem, v -> config.colorSecretItem = v);
        b.color("  Item Ready", () -> config.colorItemReady, v -> config.colorItemReady = v);
        b.color("  Item Nearly Ready", () -> config.colorItemSoon, v -> config.colorItemSoon = v);
        b.color("  Item Not Ready", () -> config.colorItemNotReady, v -> config.colorItemNotReady = v);
        b.toggle("Show Bats", () -> config.secretWaypointBats, v -> config.secretWaypointBats = v);
        b.color("  Bat", () -> config.colorSecretBat, v -> config.colorSecretBat = v);
        b.toggle("Show Essence", () -> config.secretWaypointEssence, v -> config.secretWaypointEssence = v);
        b.color("  Essence", () -> config.colorSecretEssence, v -> config.colorSecretEssence = v);
        b.toggle("Show Redstone Key", () -> config.secretWaypointRedstoneKey, v -> config.secretWaypointRedstoneKey = v);
        b.color("  Redstone", () -> config.colorSecretRedstone, v -> config.colorSecretRedstone = v);






        b.label("Dungeon Waypoints");
        b.toggle("Dungeon Waypoints", () -> config.dungeonWaypoints, v -> config.dungeonWaypoints = v);

        b.label("Add Waypoint #1");
        b.keybind("Keybind #1 (look at block)", () -> config.waypointAddKey, v -> config.waypointAddKey = v);
        b.color("Color #1", () -> config.waypointAddColor, v -> config.waypointAddColor = v);
        b.toggle("Filled Box #1", () -> config.waypointAddFilled, v -> config.waypointAddFilled = v);
        b.toggle("Through Walls #1", () -> config.waypointAddThroughWalls, v -> config.waypointAddThroughWalls = v);

        b.label("Add Waypoint #2");
        b.keybind("Keybind #2 (look at block)", () -> config.waypointAdd2Key, v -> config.waypointAdd2Key = v);
        b.color("Color #2", () -> config.waypointAdd2Color, v -> config.waypointAdd2Color = v);
        b.toggle("Filled Box #2", () -> config.waypointAdd2Filled, v -> config.waypointAdd2Filled = v);
        b.toggle("Through Walls #2", () -> config.waypointAdd2ThroughWalls, v -> config.waypointAdd2ThroughWalls = v);

        b.label("Add Waypoint #3");
        b.keybind("Keybind #3 (look at block)", () -> config.waypointAdd3Key, v -> config.waypointAdd3Key = v);
        b.color("Color #3", () -> config.waypointAdd3Color, v -> config.waypointAdd3Color = v);
        b.toggle("Filled Box #3", () -> config.waypointAdd3Filled, v -> config.waypointAdd3Filled = v);
        b.toggle("Through Walls #3", () -> config.waypointAdd3ThroughWalls, v -> config.waypointAdd3ThroughWalls = v);

        b.label("Remove / Clear Keybinds");
        b.keybind("Remove (nearest)", () -> config.waypointRemoveKey, v -> config.waypointRemoveKey = v);
        b.keybind("Clear (room)", () -> config.waypointClearKey, v -> config.waypointClearKey = v);
        b.info("File: config/teslamaps/dungeon_waypoints.json");
        b.info("Odin format. Reload: /tmap waypoints");
    }
}
