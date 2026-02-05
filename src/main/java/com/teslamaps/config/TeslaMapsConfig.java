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
            .getConfigDir().resolve("teslamaps").resolve("config.json");

    private static TeslaMapsConfig instance = new TeslaMapsConfig();

    // ===== MAP DISPLAY =====
    public boolean mapEnabled = true;
    public boolean onlyShowInDungeon = true;  // Only show map while in dungeon
    public int mapX = 54;
    public int mapY = 105;
    public float mapScale = 1.5f;
    public float mapOpacity = 0.8f;
    public boolean showMapBackground = true;

    // ===== ROOM INFO =====
    public boolean showRoomNames = true;
    public boolean hideEntranceBloodFairyNames = false; // Don't render names for entrance, blood, fairy rooms
    public boolean showNamesOnlyForPuzzles = false;     // Only show room names for puzzle rooms
    public boolean showSecretCount = true;
    public boolean hideOneSecretCount = true;           // Don't show secret count if room has only 1 secret
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
    public boolean dungeonBatTracers = false;   // Draw tracers to dungeon bats
    public boolean invisibleArmorStandESP = false; // Highlight invisible armor stands (decorations like skulls)
    public boolean witherKeyESP = true;         // Highlight wither/blood keys
    public boolean keyTracers = true;           // Draw tracers to wither/blood keys
    public boolean lividFinder = true;          // Highlight the correct Livid in F5/M5
    public boolean lividTracer = true;          // Draw tracer to correct Livid
    public boolean lividDeathMessage = true;    // Send "/pc Livid Dead!" when Livid dies
    public boolean doorESP = true;              // Highlight wither/blood doors through walls
    public boolean doorTracers = false;         // Draw tracers to wither/blood doors
    public boolean doorColorBasedOnKey = true;  // Color doors red if key not picked up, green if picked up
    public boolean onlyShowNextDoor = true;     // Only show tracer/ESP for the nearest door
    public boolean mimicChestESP = true;        // Highlight trapped chests (potential mimic)
    public boolean mimicChestTracers = false;   // Draw tracers to trapped chests
    public boolean mimicDeadMessage = true;     // Send "/pc Mimic Dead!" when mimic dies
    public boolean showGlow = true;             // Show glow effect on entities
    public boolean filledESP = false;           // Fill entire entity hitbox instead of just outlines
    public float espAlpha = 0.4f;               // Transparency for filled ESP (0.0 = invisible, 1.0 = solid)

    // ===== ESP: OTHER =====
    public boolean pestESP = false;             // Highlight pests in garden (invisible silverfish)
    public boolean pestTracers = false;         // Draw tracers to pests
    public boolean droppedItemESP = true;       // Highlight all dropped items
    public List<String> customESPMobs = new ArrayList<>();  // Custom mob names to highlight

    // ===== SOUNDS =====
    public boolean keyPickupSoundEnabled = true;  // Enable sound when key is picked up
    public float keyPickupVolume = 1.0f;         // Volume multiplier for key sounds (0.0 - 4.0)
    public String keyPickupSound = "WITHER_SPAWN";   // Sound to play: LEVEL_UP, BLAZE_DEATH, GHAST_SHOOT, WITHER_SPAWN, ENDER_DRAGON_GROWL, NOTE_PLING
    public boolean keyOnGroundSoundEnabled = true; // Enable sound when key spawns on ground
    public float keyOnGroundVolume = 1.0f;       // Volume for key on ground notification
    public String keyOnGroundSound = "NOTE_CHIME"; // Sound for key on ground: NOTE_CHIME, NOTE_PLING, EXPERIENCE_ORB, ANVIL_LAND
    public boolean secretSound = false;          // Play sound when a secret is found
    public float secretSoundVolume = 1.75f;      // Volume for secret found sound (0.0 - 4.0)
    public String secretSoundType = "NOTE_PLING";  // Sound type: LEVEL_UP, NOTE_PLING, EXPERIENCE_ORB, AMETHYST_CHIME

    // ===== RENDER OPTIONS =====
    public boolean noFire = true;            // Hide fire overlay on screen
    public boolean noBlind = true;           // Remove blindness effect
    public boolean noNausea = true;          // Remove nausea effect
    public boolean noExplosions = true;      // Hide explosion particles
    public boolean noArrows = true;          // Hide arrow entities
    public boolean noStuckArrows = true;     // Hide arrows stuck in entities
    public boolean noWaterOverlay = true;    // Hide water overlay on screen
    public boolean noVignette = true;        // Disable vignette (darkness around screen edges)
    public boolean noDeathAnimation = true;  // Hide death animation from entities dying
    public boolean hideInventoryEffects = true;  // Hide potion effects in inventory
    public boolean noEffects = true;              // Hide status effect icons on HUD (top right)
    public boolean noLightning = true;       // Remove lightning bolts
    public boolean noBlockBreaking = true;   // Remove block breaking particles
    public boolean noFallingBlocks = true;   // Hide falling block entities
    public boolean fullbright = true;        // Maximum brightness (see in the dark)

    // ===== CHEST OPTIONS =====
    public boolean autoCloseChests = false;       // Automatically close chests after opening
    public int autoCloseDelay = 7;                // Delay in ticks before auto-closing (0-20)
    public int autoCloseRandomization = 3;        // Random ticks added to delay (0-10)
    public boolean closeChestOnInput = false;     // Close chest when any key is pressed

    // ===== SLAYER =====
    public boolean slayerHUD = false;          // Show slayer boss health/phase overlay
    public int slayerHudX = 152;               // X position of slayer HUD
    public int slayerHudY = 32;                // Y position of slayer HUD
    public float slayerHudScale = 1.0f;        // Scale of slayer HUD
    public boolean slayerOnlyOwnBoss = false;  // Only show HUD for your own boss
    public boolean slayerBossESP = false;      // Glow on boss (color based on phase)
    public boolean slayerMinibossESP = false;  // Glow on minibosses (Burningsoul=red, Kindleheart=white)

    // ===== SCANNING =====
    public boolean autoScan = true;
    public int scanTickInterval = 5;

    // ===== AUTO GFS =====
    public boolean autoGFS = false;             // Master toggle for Auto GFS
    public boolean autoGFSOnStart = true;       // Refill when dungeon starts
    public boolean autoGFSTimer = false;        // Refill on timer
    public int autoGFSInterval = 30;            // Timer interval in seconds
    public boolean autoGFSDungeonOnly = true;   // Only refill in dungeons
    public boolean autoGFSPearls = true;        // Refill ender pearls
    public boolean autoGFSJerry = true;         // Refill inflatable jerry
    public boolean autoGFSTNT = true;           // Refill superboom TNT
    public boolean autoGFSDraft = true;         // Auto get draft on puzzle fail

    // ===== AUTO WISH =====
    public boolean autoWish = true;             // Auto use healer wish at key boss moments

    // ===== SECRET CLICKER =====
    // DISABLED in code - these fields exist for compilation but feature is disabled
    public boolean secretClicker = false;       // Auto-click secrets when looking at them [DISABLED]
    public boolean secretClickerLevers = true;  // Click levers [DISABLED]
    public boolean secretClickerButtons = true; // Click buttons [DISABLED]
    public boolean secretClickerSkulls = true;  // Click skulls [DISABLED]
    public boolean secretClickerChests = true;  // Click regular chests [DISABLED]
    public boolean secretClickerTrappedChests = false; // Click trapped chests (mimic risk!) [DISABLED]
    public int secretClickerDelay = 200;        // Delay between clicks in ms [DISABLED]
    public int secretClickerRandomization = 50; // Random delay added (0 to this value) [DISABLED]

    // ===== PUZZLE SOLVERS =====
    public boolean solveBlaze = true;              // Show which blaze to hit
    public boolean blazeDoneMessage = true;        // Send "/pc Blaze Done!" when last blaze killed
    public boolean solveThreeWeirdos = true;       // Highlight correct chest in Three Weirdos
    public boolean solveTicTacToe = true;          // Show best move in Tic Tac Toe
    public boolean solveCreeperBeams = true;       // Show solution for Creeper Beams
    public boolean creeperBeamsTracers = true;     // Draw lines between lantern pairs
    public boolean solveBoulder = true;            // Show solution for Boulder puzzle
    public boolean showAllBoulderClicks = true;    // Show all clicks vs just next one
    public boolean solveQuiz = true;               // Highlight correct answer in Quiz (Trivia)
    public boolean quizBeacon = true;              // Draw beacon beam on correct answer
    public boolean solveTPMaze = true;             // Highlight correct portals in Teleport Maze
    public boolean solveWaterBoard = true;          // Show solution for Water Board puzzle
    public boolean waterBoardOptimized = true;      // Use optimized (faster) solutions
    public boolean waterBoardTracers = true;        // Draw tracers to next lever

    // ===== F7/M7 SOLVERS =====
    public boolean solveSimonSays = false;         // Show button sequence for Simon Says
    public boolean solveArrowAlign = false;        // Show clicks needed for Arrow Align

    // ===== BOSS TIMERS =====
    public boolean terracottaTimer = false;        // Show spawn timers for F6/M6 terracotta
    public boolean spiritBearTimer = false;        // Show spawn timer for F4/M4 spirit bear

    // ===== PUZZLE COLORS =====
    public String colorBoulder = "55FF55";           // Boulder click highlight (green)
    public String colorQuiz = "55FF55";              // Quiz correct answer (green)
    public String colorTPMazeOne = "55FF55";         // TP Maze single correct (green)
    public String colorTPMazeMultiple = "FFFF55";    // TP Maze multiple correct (yellow)
    public String colorTPMazeVisited = "FF5555";     // TP Maze visited (red)
    public String colorSimonSaysFirst = "8055FF55";  // Simon Says first (green, semi-transparent)
    public String colorSimonSaysSecond = "80FFAA00"; // Simon Says second (gold, semi-transparent)
    public String colorSimonSaysThird = "80FF5555";  // Simon Says third+ (red, semi-transparent)
    public String colorWaterFirst = "FF55FF55";       // Water Board first lever (green)
    public String colorWaterSecond = "FFFFAA00";      // Water Board second lever (gold)

    // ===== EXPERIMENT SOLVERS (Superpairs Table) =====
    // DISABLED in code - these fields exist for compilation but features are disabled in solver classes
    public boolean solveChronomatron = false;      // Auto-solve Chronomatron (memory sequence) [DISABLED]
    public boolean solveSuperpairs = false;        // Auto-solve Superpairs (matching pairs) [DISABLED]
    public boolean solveUltrasequencer = false;    // Auto-solve Ultrasequencer (numbered sequence) [DISABLED]
    public int experimentClickDelay = 300;         // Delay in ms before first click in experiments [DISABLED]
    public int experimentClickInterval = 150;      // Delay in ms between clicks in experiments [DISABLED]

    // ===== TERMINALS =====
    // DISABLED in code - these fields exist for compilation but features are disabled in solver classes
    public boolean solveStartsWithTerminal = false; // Auto-click correct item in "What starts with" terminal [DISABLED]
    public boolean solveSelectAllTerminal = false;  // Auto-click all items in "Select all the [color]" terminal [DISABLED]
    public boolean solveClickInOrderTerminal = false; // Auto-click panes in order 1-14 [DISABLED]
    public boolean solveCorrectPanesTerminal = false; // Auto-click incorrect panes to fix them [DISABLED]
    public boolean solveMelodyTerminal = false;    // Auto-click melody sequence (Click the button on time!) [DISABLED]
    public boolean solveRubixTerminal = false;     // Auto-solve rubix cube (Change all to same color!) [DISABLED]
    public int terminalClickDelay = 200;           // Delay in ms before first click (200-500 recommended) [DISABLED]
    public int terminalClickInterval = 171;        // Delay in ms between subsequent clicks (100-200 recommended) [DISABLED]
    public int terminalClickRandomization = 70;    // Random variance added to click delays (0-150ms, higher = more human-like) [DISABLED]
    public int terminalBreakThreshold = 500;       // Reset stuck state if no progress for this many ms (0 to disable) [DISABLED]
    public int melodyTerminalClickDelay = 25;      // Delay for melody terminal click (separate because timing is critical) [DISABLED]

    // ===== CUSTOM TERMINAL GUI =====
    public boolean customTerminalGui = false;      // Enable custom terminal GUI overlay (replaces container screen)
    public boolean terminalClickAnywhere = false;  // Click anywhere to solve - redirects all clicks to correct slot
    public float terminalGuiSize = 1.0f;           // Size multiplier for custom terminal GUI (0.5 - 3.0)
    public float terminalGuiGap = 5.0f;            // Gap between slots in pixels (0 - 15)
    public int terminalGuiRoundness = 9;           // Corner roundness for slots (0 - 15)
    public boolean terminalGuiShowNumbers = false; // Show numbers in Click in Order terminal
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

    // ===== SECRET WAYPOINTS =====
    public boolean secretWaypoints = true;            // Master toggle for secret waypoints
    public boolean secretWaypointChests = true;       // Show chest secrets
    public boolean secretWaypointItems = true;        // Show item secrets
    public boolean secretWaypointBats = true;         // Show bat secrets
    public boolean secretWaypointEssence = true;      // Show essence secrets
    public boolean secretWaypointRedstoneKey = true;  // Show redstone key secrets
    public boolean secretWaypointTracers = false;     // Draw tracers to waypoints
    public boolean secretWaypointHideCollected = true; // Hide waypoints when secret is collected

    // Secret waypoint colors
    public String colorSecretChest = "55FF55";        // Chest (green)
    public String colorSecretItem = "55FFFF";         // Item (cyan)
    public String colorSecretBat = "AAAAAA";          // Bat (gray)
    public String colorSecretEssence = "AA00AA";      // Essence (purple)
    public String colorSecretRedstone = "FF5555";     // Redstone (red)

    // ===== LEAP OVERLAY =====
    public boolean leapOverlay = false;               // Enhanced Spirit Leap menu
    public boolean leapShowClass = true;              // Show player class icon
    public boolean leapShowDistance = true;           // Show distance to player
    public boolean leapSortByDistance = false;        // Sort players by distance

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
    public String colorPuzzleUnexplored = "3D1866"; // Unexplored puzzle room (grayed purple)
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
