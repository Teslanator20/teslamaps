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
        b.toggle("    Event-Based Detection", () -> config.secretWaypointEvents, v -> config.secretWaypointEvents = v);
        b.toggle("  Tracers to Waypoints", () -> config.secretWaypointTracers, v -> config.secretWaypointTracers = v);
        b.dropdown("  Waypoint Style", com.teslamaps.features.SecretWaypoints.STYLES,
                () -> config.secretWaypointStyle, v -> config.secretWaypointStyle = v).fresh();
        b.toggle("  Fit Box To Essence / Key Skull", () -> config.secretWaypointAdaptiveBox, v -> config.secretWaypointAdaptiveBox = v).fresh();
        b.toggle("  Show Waypoint Names", () -> config.secretWaypointLabels, v -> config.secretWaypointLabels = v).fresh();
        b.toggle("    With Distance", () -> config.secretWaypointDistance, v -> config.secretWaypointDistance = v).fresh();
        b.toggle("  Route Waypoints (Stonk, Superboom, ...)", () -> config.secretWaypointRoutes, v -> config.secretWaypointRoutes = v).fresh();
        b.searchList("    Route Categories", () -> java.util.List.of(com.teslamaps.features.SecretWaypoints.ROUTE_CATEGORIES),
                id -> config.secretRouteCategories.contains(id),
                id -> { if (!config.secretRouteCategories.remove(id)) config.secretRouteCategories.add(id); }).fresh()
                .names(com.teslamaps.features.SecretWaypoints::routeName)
                .colors(id -> config.secretRouteColors.getOrDefault(id, com.teslamaps.features.SecretWaypoints.routeDefaultColor(id)),
                        (id, hex) -> config.secretRouteColors.put(id, hex));
        b.toggle("  Secret Click Highlight", () -> config.secretClickHighlight, v -> config.secretClickHighlight = v);
        b.toggle("    Highlight Bats", () -> config.secretClickBats, v -> config.secretClickBats = v);
        b.toggle("    Highlight Items", () -> config.secretClickItems, v -> config.secretClickItems = v);
        b.slider("    Highlight Duration (s)", 1f, 120f, () -> config.secretClickDuration, v -> config.secretClickDuration = v, 1);
        b.espStyle("  Secret Click", () -> config.secretClickStyle);
        b.color("    Secret Click Locked Color", () -> config.secretClickLockedColor, v -> config.secretClickLockedColor = v);
        b.label("Secret Types");
        b.toggle("Show Chests", () -> config.secretWaypointChests, v -> config.secretWaypointChests = v);
        b.color("  Chest", () -> config.colorSecretChest, v -> config.colorSecretChest = v);
        b.toggle("Show Items", () -> config.secretWaypointItems, v -> config.secretWaypointItems = v);
        b.color("  Item", () -> config.colorSecretItem, v -> config.colorSecretItem = v);
        b.color("Item Ready", () -> config.colorItemReady, v -> config.colorItemReady = v);
        b.color("Item Nearly Ready", () -> config.colorItemSoon, v -> config.colorItemSoon = v);
        b.color("Item Not Ready", () -> config.colorItemNotReady, v -> config.colorItemNotReady = v);
        b.toggle("Show Bats", () -> config.secretWaypointBats, v -> config.secretWaypointBats = v);
        b.color("  Bat", () -> config.colorSecretBat, v -> config.colorSecretBat = v);
        b.toggle("Show Essence", () -> config.secretWaypointEssence, v -> config.secretWaypointEssence = v);
        b.color("  Essence", () -> config.colorSecretEssence, v -> config.colorSecretEssence = v);
        b.toggle("Show Redstone Key", () -> config.secretWaypointRedstoneKey, v -> config.secretWaypointRedstoneKey = v);
        b.color("  Redstone", () -> config.colorSecretRedstone, v -> config.colorSecretRedstone = v);






        b.label("Dungeon Waypoints");
        b.toggle("Dungeon Waypoints", () -> config.dungeonWaypoints, v -> config.dungeonWaypoints = v);
        b.slider("  Title Scale", 0.1f, 4f, () -> config.waypointTitleScale, v -> config.waypointTitleScale = v, 1);
        b.toggle("  Editor HUD", () -> config.dungeonWaypointEditor, v -> config.dungeonWaypointEditor = v);
        b.toggle("  Edit Mode (Right-Click)", () -> config.waypointEditMode, v -> config.waypointEditMode = v).fresh()
                .desc("In dungeons right-click adds/removes a waypoint (Add Waypoint #1 style), also in mid-air");
        b.toggle("    Sneak + Right-Click Sets Title", () -> config.waypointEditTitles, v -> config.waypointEditTitles = v).fresh();
        b.keybind("    Edit Mode Key", () -> config.waypointEditModeKey, v -> config.waypointEditModeKey = v).fresh();
        b.button("  Waypoint Packs...", () -> Minecraft.getInstance().gui.setScreen(
                new com.teslamaps.gui.screen().WaypointPackScreen(Minecraft.getInstance().gui.screen()))).fresh();
        b.toggle("  New Waypoints Use Block Size", () -> config.waypointUseBlockSize, v -> config.waypointUseBlockSize = v);
        b.slider("  Size X", 0.1f, 5f, () -> config.waypointSizeX, v -> config.waypointSizeX = v, 2);
        b.slider("  Size Y", 0.1f, 5f, () -> config.waypointSizeY, v -> config.waypointSizeY = v, 2);
        b.slider("  Size Z", 0.1f, 5f, () -> config.waypointSizeZ, v -> config.waypointSizeZ = v, 2);
        b.info("Secret waypoints hide once collected, Etherwarp ones after warping there.");
        b.info("Title: /tmap waypoints title <text> | type: /tmap waypoints type <type>");
        b.info("Packs: /tmap waypoints packs | edit mode: /tmap waypoints edit").fresh();

        b.label("Add Waypoint #1");
        b.keybind("Keybind #1 (look at block)", () -> config.waypointAddKey, v -> config.waypointAddKey = v);
        b.color("Color #1", () -> config.waypointAddColor, v -> config.waypointAddColor = v);
        b.toggle("Filled Box #1", () -> config.waypointAddFilled, v -> config.waypointAddFilled = v);
        b.toggle("Through Walls #1", () -> config.waypointAddThroughWalls, v -> config.waypointAddThroughWalls = v);
        b.dropdown("Type #1", com.teslamaps.dungeon.DungeonWaypoints.TYPES, () -> config.waypointAddType, v -> config.waypointAddType = v);

        b.label("Add Waypoint #2");
        b.keybind("Keybind #2 (look at block)", () -> config.waypointAdd2Key, v -> config.waypointAdd2Key = v);
        b.color("Color #2", () -> config.waypointAdd2Color, v -> config.waypointAdd2Color = v);
        b.dropdown("Type #2", com.teslamaps.dungeon.DungeonWaypoints.TYPES, () -> config.waypointAdd2Type, v -> config.waypointAdd2Type = v);

        b.label("Add Waypoint #3");
        b.keybind("Keybind #3 (look at block)", () -> config.waypointAdd3Key, v -> config.waypointAdd3Key = v);
        b.color("Color #3", () -> config.waypointAdd3Color, v -> config.waypointAdd3Color = v);
        b.dropdown("Type #3", com.teslamaps.dungeon.DungeonWaypoints.TYPES, () -> config.waypointAdd3Type, v -> config.waypointAdd3Type = v);

        b.label("Remove / Clear Keybinds");
        b.keybind("Remove (nearest)", () -> config.waypointRemoveKey, v -> config.waypointRemoveKey = v);
        b.keybind("Clear (room)", () -> config.waypointClearKey, v -> config.waypointClearKey = v);
        b.info("File: config/teslamaps/dungeon_waypoints.json");
        b.info("Odin format. Reload: /tmap waypoints");
    }
}
