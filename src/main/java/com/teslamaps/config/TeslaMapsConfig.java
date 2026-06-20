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
    public boolean announce300 = false;         // Announce "300 Score" in party chat (/pc)
    public boolean announce270 = false;         // Announce "270 Score" in party chat (/pc)
    public boolean cryptReminder = true;        // Warn at boss entry if fewer than 5 crypts were done
    public boolean watcherKillAlert = true;     // Title + sound when the Watcher is cleared (blood done)
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
    public boolean princeDeadMessage = true;    // Send "/pc Prince Dead!" when a Prince is killed
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
    public String secretSoundType = "NOTE_PLING";  // see SoundOptions.keys() for all options
    public float secretSoundPitch = 1.0f;        // Pitch for secret found sound (0.5 - 2.0)
    public boolean secretChime = false;          // Ascending chime per secret found in the room
    public String secretChimeSound = "NOTE_CHIME"; // Chime sound (see SoundOptions.keys())
    public float secretChimeVolume = 1.0f;       // Volume for the secret chime (0.0 - 4.0)
    // Bear Spawn Warning: watch block (7,77,34); when it turns into a sea lantern, flash "STOP!" + play sounds
    public boolean bearSpawnWarning = true;      // Enable the bear spawn warning
    public boolean bearSpawnWardenSound = true;  // Play warden emerge sound on alert
    public boolean bearSpawnWitherSound = true;  // Play wither death sound on alert
    public float bearSpawnVolume = 2.0f;         // Alert sound volume (0.0 - 20.0)
    public int bearSpawnX = 150;                 // "STOP!" overlay position X (draggable in HUD edit)
    public int bearSpawnY = 30;                  // "STOP!" overlay position Y
    public float bearSpawnScale = 4.0f;          // "STOP!" overlay text scale (0.5 - 10.0)

    // ===== SPLITS =====
    public boolean splitsEnabled = true;         // Dungeon run splits HUD + chat output
    public int splitsX = 5;                       // Splits HUD position X (draggable in HUD edit)
    public int splitsY = 100;                     // Splits HUD position Y
    public float splitsScale = 1.0f;              // Splits HUD scale (0.5 - 2.0)

    // ===== BLOOD CAMP =====
    public boolean bloodCampMoveTimer = true;     // Watcher move prediction timer HUD
    public boolean bloodCampMoveMessage = true;   // Chat message with predicted move time
    public boolean bloodCampPartyMessage = false; // Send predicted move time to party chat
    public boolean bloodCampKillTitle = true;     // "Kill Mobs" title when the watcher moves
    public boolean bloodCampHpBar = true;         // Show the Watcher's remaining HP/mob count
    public int bloodCampX = 10;                   // Move Timer HUD position X
    public int bloodCampY = 80;                   // Move Timer HUD position Y
    public float bloodCampScale = 1.0f;           // Move Timer HUD scale

    // ===== DUNGEON WAYPOINTS =====
    public boolean dungeonWaypoints = true;       // Render Odin-format dungeon waypoints from config/teslamaps/dungeon_waypoints.json
    public int waypointAddKey = -1;               // Keybind: add waypoint at looked-at block (-1 = unbound)
    public int waypointRemoveKey = -1;            // Keybind: remove nearest waypoint in current room
    public int waypointClearKey = -1;             // Keybind: clear all waypoints in current room
    public String waypointAddColor = "55FFFF";    // Color for waypoints added in-game
    public boolean waypointAddFilled = false;     // Add filled boxes instead of outlines
    public boolean waypointAddThroughWalls = true; // Render added waypoints through walls (depth off)

    // ===== ETHERWARP =====
    public boolean etherwarp = true;              // Show etherwarp guess box
    public boolean etherwarpShowFail = true;      // Show the box even when the guess failed
    public boolean etherwarpFilled = false;       // Filled box instead of outline
    public String colorEtherwarp = "FFAA00";      // Etherwarp guess box color (gold)
    public boolean etherwarpCustomSound = false;  // Replace the etherwarp sound with a custom one
    public String etherwarpSound = "EXPERIENCE_ORB"; // see Etherwarp.soundKeys() for all options
    public float etherwarpSoundVolume = 1.0f;     // Custom etherwarp sound volume (0.0 - 20.0)
    public float etherwarpSoundPitch = 1.0f;      // Custom etherwarp sound pitch (0.5 - 2.0)

    // ===== HIDE PLAYERS =====
    public boolean hidePlayers = false;           // Hide nearby players
    public boolean hidePlayersOnlyDungeon = true; // Only hide while in a dungeon
    public boolean hidePlayersAll = false;        // Hide all players regardless of distance
    public float hidePlayersDistance = 3.0f;      // Hide players within this many blocks

    // ===== CHAT =====
    public boolean chatCopyEnabled = true;       // Right-click a chat line to copy its text

    // ===== PARTY =====
    public boolean pbOnJoin = true;               // Show a player's dungeon PBs when they join the party (needs API key)
    public boolean chatCommands = true;           // Party chat commands (!8ball, !cf, !warp, !pt, !kick, ...)

    // ===== AUTO REQUEUE =====
    public boolean autoRequeue = false;           // Send /instancerequeue at the end of a dungeon
    public boolean requeueOnPartyR = false;       // Requeue when a party member types "r"
    public int requeueDelaySeconds = 2;           // Delay before requeuing

    // ===== KEYBIND MESSAGES =====
    // Unlimited custom hotkeys -> chat messages, all configured in the GUI (/tmap msg).
    // Each entry binds a GLFW key code to a message; leading "/" sends it as a command.
    public static class Keybind {
        public int key = -1;          // GLFW key code, -1 = unbound
        public String message = "";
        public Keybind() {}
        public Keybind(int key, String message) { this.key = key; this.message = message; }
    }
    public List<Keybind> keybinds = new ArrayList<>();

    // Legacy single keybind (migrated into the list on first GUI open).
    public String keybindChatMessage = "";

    // ===== COMMAND SHORTCUTS =====
    // "/<alias> args" -> "<command> args", registered as client commands on join (/tmap shortcut GUI).
    public static class Shortcut {
        public String alias = "";     // typed without the leading slash, e.g. "pk"
        public String command = "";   // expansion without leading slash, e.g. "party kick"
        public Shortcut() {}
        public Shortcut(String alias, String command) { this.alias = alias; this.command = command; }
    }
    // Defaults kept for fresh configs (absent JSON key keeps these; existing configs override).
    public List<Shortcut> shortcuts = new ArrayList<>(List.of(
            new Shortcut("pd", "party disband"),
            new Shortcut("pk", "party kick"),
            new Shortcut("pt", "party transfer")
    ));

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
    public boolean quizChatHighlight = true;       // Recolor the correct answer line green in chat
    public boolean quizHideWrongAnswers = false;   // Hide the two wrong answer lines from chat
    public boolean solveTPMaze = true;             // Highlight correct portals in Teleport Maze
    public boolean solveWaterBoard = true;          // Show solution for Water Board puzzle
    public boolean waterBoardOptimized = true;      // Use optimized (faster) solutions
    public boolean waterBoardTracers = true;        // Draw tracers to next lever
    public boolean solveIceFill = true;             // Show Ice Fill (F7) path solution
    public boolean iceFillOptimized = true;         // Use optimized (harder/faster) ice fill paths
    public String colorIceFill = "55FFFF";          // Ice Fill path color (aqua)

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
    public boolean leapShowHeads = true;              // Show player heads/skins in the leap menu
    public boolean leapAnnounce = false;              // Announce "/pc Leaped to X!" when you leap
    public boolean leapShowMap = true;                // Show the dungeon map atop the leap menu (click to leap)
    public String leapSortMode = "Odin";              // Odin, Class A-Z, Name A-Z, Custom, None
    public List<String> leapCustomOrder = new ArrayList<>(); // player names (lowercase), for Custom sort
    public String leapKeybindMode = "Corners";        // Corners (TL/TR/BL/BR) or Class (per-class)
    public int leapKeyTL = -1, leapKeyTR = -1, leapKeyBL = -1, leapKeyBR = -1; // corner keybinds
    public int leapKeyArcher = -1, leapKeyBerserk = -1, leapKeyHealer = -1, leapKeyMage = -1, leapKeyTank = -1; // class keybinds
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
