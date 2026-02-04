package com.teslamaps.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TeslaMapsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve(".cache").resolve("core.json");

    private static TeslaMapsConfig instance = new TeslaMapsConfig();

    // ===== MAP DISPLAY =====
    public boolean mapEnabled = true;
    public boolean onlyShowInDungeon = true;  // Only show map while in dungeon
    public int mapX = 10;
    public int mapY = 10;
    public float mapScale = 1.0f;
    public float mapOpacity = 0.8f;
    public boolean showMapBackground = true;

    // ===== ROOM INFO =====
    public boolean showRoomNames = true;
    public boolean hideEntranceBloodFairyNames = false; // Don't render names for entrance, blood, fairy rooms
    public boolean showNamesOnlyForPuzzles = false;     // Only show room names for puzzle rooms
    public boolean showSecretCount = true;
    public boolean hideOneSecretCount = false;          // Don't show secret count if room has only 1 secret
    public boolean showCrypts = true;           // Show crypts found below map
    public boolean showTotalCrypts = false;     // Show total crypts (from rooms.json) next to crypts found
    public boolean hideSecretsWhenDone = true;  // Hide secret count when room is green
    public boolean showMimicStatus = true;      // Show mimic status (checkmark/cross) below map
    public boolean showDungeonScore = true;     // Show estimated dungeon score below map
    public boolean assumePaulMayor = false;    // Always add +10 Paul bonus to score
    public float roomNameScale = 1.0f;          // Scale of room name text (0.5 - 2.0)

    // ===== CHECKMARKS =====
    public boolean showCheckmarks = true;
    public boolean showWhiteCheckmarks = true;   // Show white (cleared) checkmarks
    public boolean showGreenCheckmarks = true;   // Show green (all secrets) checkmarks
    public boolean showFailedCheckmarks = true;  // Show failed (red X) checkmarks
    public boolean useTextColorForCheckmarks = true;  // Use text color instead of symbols

    // ===== PLAYERS =====
    public boolean showPlayerMarker = true;
    public boolean showSelfMarker = true;        // Show your own marker/head
    public boolean showOtherPlayers = true;      // Show other players' markers/heads
    public boolean showPlayerNames = true;
    public boolean rotatePlayerHeads = true;
    public boolean useHeadsInsteadOfMarkers = true;  // True = player heads, False = arrow markers
    public float playerHeadScale = 1.0f;         // Scale of player heads (0.5 - 2.0)

    // ===== ESP: DUNGEON =====
    public boolean starredMobESP = true;        // Highlight starred mobs
    public boolean felESP = true;               // Highlight fels (Dinnerbone entities)
    public int felESPRange = 50;                // Max range for Fel ESP (blocks)
    public boolean sniperESP = true;            // Highlight snipers (skeleton mobs)
    public boolean shadowAssassinESP = true;    // Highlight invisible shadow assassins
    public boolean dungeonBatESP = true;        // Highlight invisible bats (secrets)
    public boolean dungeonBatTracers = true;    // Draw tracers to dungeon bats
    public boolean invisibleArmorStandESP = false; // Highlight invisible armor stands (decorations like skulls)
    public boolean witherKeyESP = true;         // Highlight wither/blood keys
    public boolean keyTracers = true;           // Draw tracers to wither/blood keys
    public boolean lividFinder = true;          // Highlight the correct Livid in F5/M5
    public boolean lividTracer = true;          // Draw tracer to correct Livid
    public boolean lividDeathMessage = true;    // Send "/pc Livid Dead!" when Livid dies
    public boolean doorESP = true;              // Highlight wither/blood doors through walls
    public boolean doorTracers = true;          // Draw tracers to wither/blood doors
    public boolean doorColorBasedOnKey = false; // Color doors red if key not picked up, green if picked up
    public boolean onlyShowNextDoor = true;     // Only show tracer/ESP for the nearest door
    public boolean mimicChestESP = true;        // Highlight trapped chests (potential mimic)
    public boolean mimicChestTracers = true;    // Draw tracers to trapped chests
    public boolean mimicDeadMessage = true;     // Send "/pc Mimic Dead!" when mimic dies
    public boolean showGlow = true;             // Show glow effect on entities
    public boolean filledESP = false;           // Fill entire entity hitbox instead of just outlines
    public float espAlpha = 0.4f;               // Transparency for filled ESP (0.0 = invisible, 1.0 = solid)

    // ===== ESP: OTHER =====
    public boolean pestESP = true;              // Highlight pests in garden (invisible silverfish)
    public boolean pestTracers = true;          // Draw tracers to pests
    public boolean droppedItemESP = false;      // Highlight all dropped items
    public List<String> customESPMobs = new ArrayList<>();  // Custom mob names to highlight

    // ===== SOUNDS =====
    public float keyPickupVolume = 1.0f;         // Volume multiplier for key sounds (0.0 - 4.0)
    public String keyPickupSound = "WITHER_SPAWN";   // Sound to play: LEVEL_UP, BLAZE_DEATH, GHAST_SHOOT, WITHER_SPAWN, ENDER_DRAGON_GROWL, NOTE_PLING
    public float keyOnGroundVolume = 1.0f;       // Volume for key on ground notification
    public String keyOnGroundSound = "NOTE_CHIME"; // Sound for key on ground: NOTE_CHIME, NOTE_PLING, EXPERIENCE_ORB, ANVIL_LAND

    // ===== RENDER OPTIONS =====
    public boolean noFire = false;           // Hide fire overlay on screen
    public boolean noBlind = false;          // Remove blindness effect
    public boolean noNausea = false;         // Remove nausea effect
    public boolean noExplosions = false;     // Hide explosion particles
    public boolean noArrows = false;         // Hide arrow entities
    public boolean noStuckArrows = false;    // Hide arrows stuck in entities
    public boolean noWaterOverlay = false;   // Hide water overlay on screen
    public boolean noVignette = false;       // Disable vignette (darkness around screen edges)
    public boolean noDeathAnimation = false; // Hide death animation from entities dying
    public boolean hideInventoryEffects = false; // Hide potion effects in inventory
    public boolean noLightning = false;      // Remove lightning bolts
    public boolean noBlockBreaking = false;  // Remove block breaking particles
    public boolean noFallingBlocks = false;  // Hide falling block entities

    // ===== SLAYER =====
    public boolean slayerHUD = true;           // Show slayer boss health/phase overlay
    public int slayerHudX = 10;                // X position of slayer HUD
    public int slayerHudY = 100;               // Y position of slayer HUD
    public float slayerHudScale = 1.0f;        // Scale of slayer HUD
    public boolean slayerOnlyOwnBoss = true;   // Only show HUD for your own boss
    public boolean slayerBossESP = true;       // Glow on boss (color based on phase)
    public boolean slayerMinibossESP = true;   // Glow on minibosses (Burningsoul=red, Kindleheart=white)

    // ===== SCANNING =====
    public boolean autoScan = true;
    public int scanTickInterval = 5;

    // ===== AUTO GFS =====
    public boolean autoGFS = true;              // Master toggle for Auto GFS
    public boolean autoGFSOnStart = true;       // Refill when dungeon starts
    public boolean autoGFSTimer = false;        // Refill on timer
    public int autoGFSInterval = 30;            // Timer interval in seconds
    public boolean autoGFSDungeonOnly = true;   // Only refill in dungeons
    public boolean autoGFSPearls = true;        // Refill ender pearls
    public boolean autoGFSJerry = true;         // Refill inflatable jerry
    public boolean autoGFSTNT = true;           // Refill superboom TNT
    public boolean autoGFSDraft = true;         // Auto get draft on puzzle fail

    // ===== PUZZLE SOLVERS =====
    public boolean solveBlaze = true;              // Show which blaze to hit
    public boolean blazeDoneMessage = true;        // Send "/pc Blaze Done!" when last blaze killed
    public boolean solveThreeWeirdos = true;       // Highlight correct chest in Three Weirdos
    public boolean solveTicTacToe = true;          // Show best move in Tic Tac Toe
    public boolean solveCreeperBeams = true;       // Show solution for Creeper Beams

    // ===== TERMINALS =====
    public boolean solveStartsWithTerminal = true; // Auto-click correct item in "What starts with" terminal
    public boolean solveSelectAllTerminal = true;  // Auto-click all items in "Select all the [color]" terminal
    public boolean solveClickInOrderTerminal = true; // Auto-click panes in order 1-14
    public boolean solveCorrectPanesTerminal = true; // Auto-click incorrect panes to fix them
    public boolean solveMelodyTerminal = true;     // Auto-click melody sequence (Click the button on time!)
    public boolean solveRubixTerminal = true;      // Auto-solve rubix cube (Change all to same color!)
    public int terminalClickDelay = 100;           // Delay in ms before first click (prevent too fast)
    public int terminalClickInterval = 50;         // Delay in ms between subsequent clicks
    public int melodyTerminalClickDelay = 150;     // Delay for melody terminal click (separate because timing is critical)

    // ===== CUSTOM TERMINAL GUI =====
    public boolean customTerminalGui = false;      // Enable custom terminal GUI overlay (replaces container screen)
    public boolean terminalClickAnywhere = false;  // Click anywhere to solve - redirects all clicks to correct slot
    public float terminalGuiSize = 1.0f;           // Size multiplier for custom terminal GUI (0.5 - 3.0)
    public float terminalGuiGap = 5.0f;            // Gap between slots in pixels (0 - 15)
    public int terminalGuiRoundness = 9;           // Corner roundness for slots (0 - 15)
    public boolean terminalGuiShowNumbers = true;  // Show numbers in Click in Order terminal
    public String terminalGuiBackgroundColor = "CC404040";  // Background color for custom GUI
    public String terminalGuiOrderColor1 = "55FF55";        // First item to click (green)
    public String terminalGuiOrderColor2 = "44DD44";        // Second item to click (darker green)
    public String terminalGuiOrderColor3 = "339933";        // Third item to click (darkest green)
    public String terminalGuiPanesColor = "55FF55";         // Incorrect panes to click (green)
    public String terminalGuiStartsWithColor = "00AAAA";    // Starts with item highlight (cyan)
    public String terminalGuiSelectColor = "00AAAA";        // Select all item highlight (cyan)
    public String terminalGuiRubixColor1 = "00AAAA";        // Rubix 1 click (cyan)
    public String terminalGuiRubixColor2 = "008888";        // Rubix 2 clicks (darker cyan)
    public String terminalGuiMelodyColor = "AA00AA";        // Melody current note (purple)

    // ===== DEBUG =====
    public boolean debugMode = false;

    // ===== PROFILE VIEWER =====
    public String hypixelApiKey = "";
    public List<String> recentPlayers = new ArrayList<>();
    public int pvCacheDurationSeconds = 300;  // 5 minute cache

    // ===== COLORS (hex format: "AARRGGBB" or "RRGGBB") =====
    // Room colors
    public String colorBackground = "CC1A1A1A";      // Map background
    public String colorUnexplored = "2A2A2A";        // Unexplored room
    public String colorNormal = "6B3A11";            // Normal explored room
    public String colorEntrance = "00AA00";          // Entrance/Start room
    public String colorBlood = "CC0000";             // Blood room
    public String colorTrap = "FF8800";              // Trap room
    public String colorPuzzle = "6600AA";            // Puzzle room
    public String colorFairy = "FF66CC";             // Fairy room
    public String colorMiniboss = "FFDD00";          // Miniboss/Yellow room

    // Door colors
    public String colorDoorNormal = "5C340E";        // Normal door (brown)
    public String colorDoorWither = "000000";        // Wither door (black)
    public String colorDoorBlood = "E70000";         // Blood door (red)
    public String colorDoorEntrance = "148500";      // Entrance door (green)

    // Text colors
    public String colorTextUnexplored = "888888";    // Gray - unexplored
    public String colorTextCleared = "FFFFFF";       // White - cleared
    public String colorTextGreen = "55FF55";         // Green - all secrets found
    public String colorSecretCount = "AAAAAA";       // Secret count number

    // Checkmark colors
    public String colorCheckWhite = "FFFFFF";        // White checkmark
    public String colorCheckGreen = "55FF55";        // Green checkmark
    public String colorCheckFailed = "FF5555";       // Failed/red X

    // ESP colors
    public String colorESPStarred = "FFFF00";        // Starred mobs (yellow)
    public String colorESPFel = "FF0000";            // Fels (red)
    public String colorESPSniper = "55FFFF";         // Snipers (light blue/cyan)
    public String colorESPShadowAssassin = "AA00AA"; // Shadow Assassins (purple)
    public String colorESPBat = "AAAAAA";            // Dungeon bats (gray)
    public String colorESPWitherKey = "000000";      // Wither key (black)
    public String colorESPBloodKey = "FF0000";       // Blood key (red)
    public String colorESPWitherDoor = "000000";     // Wither door (black)
    public String colorESPBloodDoor = "FF0000";      // Blood door (red)

    public static TeslaMapsConfig get() {
        return instance;
    }

    // Helper to parse hex color strings
    public static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) hex = "FF" + hex; // Add full alpha if not specified
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return 0xFFFFFFFF; // Default to white on error
        }
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = GSON.fromJson(json, TeslaMapsConfig.class);
                if (instance == null) {
                    instance = new TeslaMapsConfig();
                }
                // Ensure lists are never null (Gson might not initialize them)
                if (instance.customESPMobs == null) {
                    instance.customESPMobs = new ArrayList<>();
                }
                if (instance.recentPlayers == null) {
                    instance.recentPlayers = new ArrayList<>();
                }
                TeslaMaps.LOGGER.info("Config loaded from {}", CONFIG_PATH);
            } else {
                instance = new TeslaMapsConfig();
                save();
                TeslaMaps.LOGGER.info("Created default config at {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to load config", e);
            instance = new TeslaMapsConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(instance));
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to save config", e);
        }
    }
}
