/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

    public boolean mapEnabled = true;
    public boolean onlyShowInDungeon = true;  // Only show map while in dungeon
    public int mapX = 54;
    public int mapY = 105;
    public float mapScale = 1.5f;
    public float mapOpacity = 0.8f;
    public boolean showMapBackground = true;

    public boolean showRoomNames = true;
    public boolean hideEntranceBloodFairyNames = true; // Don't render names for entrance, blood, fairy rooms
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

    public boolean showCheckmarks = true;
    public boolean showWhiteCheckmarks = true;   // Show white (cleared) checkmarks
    public boolean showGreenCheckmarks = true;   // Show green (all secrets) checkmarks
    public boolean showFailedCheckmarks = true;  // Show failed (red X) checkmarks

    public boolean showPlayerMarker = true;
    public boolean showSelfMarker = true;        // Show your own marker/head
    public boolean showOtherPlayers = true;      // Show other players' markers/heads
    public boolean showPlayerNames = true;
    public boolean rotatePlayerHeads = true;
    public boolean useHeadsInsteadOfMarkers = true;  // True = player heads, False = arrow markers
    public float playerHeadScale = 1.0f;         // Scale of player heads (0.5 - 2.0)

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

    public boolean pestESP = false;             // Highlight pests in garden (invisible silverfish)
    public boolean pestTracers = false;         // Draw tracers to pests
    public boolean droppedItemESP = true;       // Highlight all dropped items
    public boolean hideSoulweaverSkull = false; // Hide the floating skull spawned by Soulweaver Gloves
    public boolean hideSkeletonSkull = false;   // Hide the floating "Skeleton Skull" armor stands (bone shield / piling skulls)
    public boolean hideThrownBones = false;     // Hide thrown/dropped Bone item entities in dungeons
    public boolean hideSuperboomTnt = false;    // Hide Superboom TNT drops
    public boolean hideBlessing = false;        // Hide Blessing drops
    public boolean hideReviveStone = false;     // Hide Revive Stone drops
    public boolean hidePremiumFlesh = false;    // Hide Premium Flesh drops
    public boolean hideJournalEntry = false;    // Hide Journal Entry item drops
    public boolean hideHealerOrbs = false;      // Hide healer DAMAGE/ABILITY/DEFENSE orbs
    public boolean hideHealerFairy = false;     // Hide the healer fairy companion skull
    public List<String> customESPMobs = new ArrayList<>();  // Custom mob names to highlight

    public boolean keyPickupSoundEnabled = true;  // Enable sound when key is picked up
    public float keyPickupVolume = 1.0f;         // Volume multiplier for key sounds (0.0 - 4.0)
    public String keyPickupSound = "WITHER_SPAWN";   // Sound to play: LEVEL_UP, BLAZE_DEATH, GHAST_SHOOT, WITHER_SPAWN, ENDER_DRAGON_GROWL, NOTE_PLING
    public boolean keyOnGroundSoundEnabled = true; // Enable sound when key spawns on ground
    public float keyOnGroundVolume = 1.0f;       // Volume for key on ground notification
    public String keyOnGroundSound = "NOTE_CHIME"; // Sound for key on ground: NOTE_CHIME, NOTE_PLING, EXPERIENCE_ORB, ANVIL_LAND
    public boolean secretSound = true;          // Play sound when a secret is found
    public float secretSoundVolume = 1.75f;      // Volume for secret found sound (0.0 - 4.0)
    public String secretSoundType = "NOTE_PLING";  // see SoundOptions.keys() for all options
    public float secretSoundPitch = 1.0f;        // Pitch for secret found sound (0.5 - 2.0)
    public boolean secretClickHighlight = true;  // Flash clicked secret blocks green (worked) / red (locked chest)
    public boolean salvageHelper = false;        // Highlight cheap (<100k) dungeon-salvageable gear in the Salvage menu
    public boolean sellableHighlighter = false;  // Highlight common junk to sell in Ophelia/Booster Cookie menus
    public boolean secretChime = false;          // Ascending chime per secret found in the room
    public String secretChimeSound = "NOTE_CHIME"; // Chime sound (see SoundOptions.keys())
    public float secretChimeVolume = 1.0f;       // Volume for the secret chime (0.0 - 4.0)
    public boolean bearSpawnWarning = true;      // Enable the bear spawn warning
    public boolean bearSpawnWardenSound = true;  // Play warden emerge sound on alert
    public boolean bearSpawnWitherSound = true;  // Play wither death sound on alert
    public float bearSpawnVolume = 2.0f;         // Alert sound volume (0.0 - 20.0)
    public int bearSpawnX = 150;                 // "STOP!" overlay position X (draggable in HUD edit)
    public int bearSpawnY = 30;                  // "STOP!" overlay position Y
    public float bearSpawnScale = 4.0f;          // "STOP!" overlay text scale (0.5 - 10.0)

    public boolean sprintingOverlay = false;     // Show a "Sprinting / Not Sprinting" HUD indicator
    public int sprintingX = 10;                  // Sprinting overlay position X (draggable in HUD edit)
    public int sprintingY = 40;                  // Sprinting overlay position Y
    public float sprintingScale = 1.0f;          // Sprinting overlay text scale (0.5 - 10.0)

    public boolean splitsEnabled = true;         // Dungeon run splits HUD + chat output
    public boolean splitsShowPb = true;          // Show personal-best time per split (and color vs PB)
    public java.util.Map<String, Long> splitPbs = new java.util.HashMap<>(); // "floor:splitName" -> best duration ms
    public int splitsX = 5;                       // Splits HUD position X (draggable in HUD edit)
    public int splitsY = 100;                     // Splits HUD position Y
    public float splitsScale = 1.0f;              // Splits HUD scale (0.5 - 2.0)

    public boolean bloodCampMoveTimer = true;     // Watcher move prediction timer HUD
    public boolean bloodCampMoveMessage = true;   // Chat message with predicted move time
    public boolean bloodCampPartyMessage = true; // Send predicted move time to party chat
    public boolean bloodCampKillTitle = true;     // "Kill Mobs" title when the watcher moves
    public boolean bloodCampHpBar = true;         // Show the Watcher's remaining HP/mob count
    public int bloodCampX = 10;                   // Move Timer HUD position X
    public int bloodCampY = 80;                   // Move Timer HUD position Y
    public float bloodCampScale = 1.0f;           // Move Timer HUD scale
    public boolean bloodCampAssist = true;       // Master toggle for the spawn-box guess
    public String colorBloodSpawn = "FF5555";     // predicted spawn box (red)
    public String colorBloodPosition = "55FF55";  // current ping-adjusted position box (green)
    public String colorBloodFinal = "00AAAA";     // merged spawn+position box (aqua)
    public float bloodAssistBoxSize = 1.0f;       // box side length (0.1 - 1.0)
    public boolean bloodAssistLine = true;        // line from position to spawn box
    public boolean bloodAssistTime = true;        // per-mob spawn countdown text
    public boolean bloodAssistInterpolation = true; // smooth box jitter between ticks
    public boolean bloodAssistPingOffset = true;  // offset the position box by your ping
    public float bloodAssistManualOffset = 0f;    // manual ms offset when ping offset is off (0 - 300)
    public int bloodAssistOffset = 40;            // advanced: tick offset to fine-tune timing (-100 - 100)
    public int bloodAssistTick = 38;              // advanced: assumed spawn tick (35 - 41)
    public boolean bloodCampMoveAlert = true;    // title + sound at the moment the Watcher moves
    public String bloodCampMoveSound = "NOTE_PLING";
    public float bloodCampMoveVolume = 1.5f;
    public boolean bloodReturnTimer = false;      // show the "Return to Blood" HUD countdown
    public int bloodReturnEstimate = 27;          // early seed countdown until SkyHanni-style prediction refines it (10 - 60)

    public boolean starredTracerWhenFew = false;  // Tracer to starred mobs when few are left
    public int starredTracerThreshold = 3;        // Show tracers when this many or fewer are alive

    public boolean rarityBackgrounds = true;     // Colored background behind items by rarity
    public float rarityBgOpacity = 0.5f;          // 0.0 - 1.0
    public String rarityBgShape = "Square";       // Square or Circle
    public String rarityBgStyle = "Filled";       // Filled or Outline

    public boolean dungeonWaypoints = true;       // Render Odin-format dungeon waypoints from config/teslamaps/dungeon_waypoints.json
    public int waypointAddKey = -1;               // Keybind: add waypoint at looked-at block (-1 = unbound)
    public int waypointRemoveKey = -1;            // Keybind: remove nearest waypoint in current room
    public int waypointClearKey = -1;             // Keybind: clear all waypoints in current room
    public String waypointAddColor = "55FFFF";    // Color for waypoints added in-game
    public boolean waypointAddFilled = true;     // Add filled boxes instead of outlines
    public boolean waypointAddThroughWalls = false; // Render added waypoints through walls (depth off)

    public boolean etherwarp = true;              // Show etherwarp guess box
    public boolean etherwarpShowFail = true;      // Show the box even when the guess failed
    public boolean etherwarpFilled = true;       // Filled box instead of outline
    public float etherwarpEyeOffset = 0.0f;      // Fine-tune the ray's eye height (blocks) if the guess sits too high/low
    public String colorEtherwarp = "00FF8C";      // Etherwarp guess box color (gold)
    public boolean etherwarpCustomSound = true;  // Replace the etherwarp sound with a custom one
    public String etherwarpSound = "NOTE_PLING"; // see Etherwarp.soundKeys() for all options
    public float etherwarpSoundVolume = 1.0f;     // Custom etherwarp sound volume (0.0 - 20.0)
    public float etherwarpSoundPitch = 1.0f;      // Custom etherwarp sound pitch (0.5 - 2.0)

    public boolean hidePlayers = false;           // Hide nearby players
    public boolean hidePlayersOnlyDungeon = true; // Only hide while in a dungeon
    public boolean hidePlayersAll = false;        // Hide all players regardless of distance
    public float hidePlayersDistance = 3.0f;      // Hide players within this many blocks

    public boolean croesusProfitOverlay = false; // Profit overlay in the Croesus run menu (prices via public bazaar/BIN, no API key)
    public boolean croesusHighlightBest = true;     // Green highlight on the most profitable chest
    public boolean croesusDimOthers = true;         // Gray out every chest that isn't the best one
    public boolean croesusHideOpened = false;       // Gray out fully-opened runs in the Croesus menu
    public boolean croesusHighlightUnopened = true; // Green-highlight runs with openable chests (orange = kismet used)
    public boolean croesusDebug = false;            // Log chest lore to latest.log (to calibrate the parser)

    public boolean chatCopyEnabled = true;       // Right-click a chat line to copy its text

    public boolean chatFilterEnabled = true;     // Master switch for the chat filter
    public boolean chatFilterWatcher = false;    // Hide "[BOSS] The Watcher:" lines
    public boolean chatFilterF4Boss = false;     // Hide F4/M4 boss taunt spam ("[BOSS] Thorn: ...")
    public boolean chatFilterBlessings = false;  // Hide "DUNGEON BUFF! ... Blessing of ..." + blessing pickups
    public boolean chatFilterEssence = false;    // Hide essence pickup spam ("ESSENCE! ...", "found a Wither Essence!")
    public boolean chatFilterKeys = false;       // Hide "A Wither/Blood Key was picked up!" / "has obtained ... Key"
    public boolean chatFilterDoors = false;      // Hide "<player> opened a WITHER/BLOOD door!"
    public boolean chatFilterWish = false;       // Hide "<player>'s Wish healed you for ..."
    public boolean chatFilterPickups = false;    // Hide all "... was picked up!" messages
    public boolean chatFilterBlocksInWay = false; // Hide "There are blocks in the way!"
    public boolean chatFilterUltReady = false;    // Hide "<ult> is ready to use! Press DROP to activate it!"
    public boolean chatFilterAoeDamage = false;   // Hide "Your <ability> hit N enemies for X damage."
    public boolean chatFilterGuildXp = false;     // Hide "You earned N GEXP from playing SkyBlock!"
    public boolean chatFilterKillCombo = false;   // Hide "+N Kill Combo ..." lines
    public boolean chatFilterStash = false;       // Hide "... items/materials stashed away!" + "CLICK HERE to pick them up!"
    public boolean chatFilterServerMsgs = false;  // Hide "Sending to server...", "Warping...", "Queuing..."
    public boolean chatFilterProfileInfo = false; // Hide "You are playing on profile: ..." + "Profile ID: ..."
    public boolean chatFilterPerkBuffs = false;   // Hide HOTF/HOTM daily perk buffs (Lottery / Sky Mall "buff changed!")
    public boolean chatFilterOruo = false;        // Hide "[STATUE] Oruo the Omniscient:" quiz flavor/question lines
    public boolean chatFilterSacks = false;       // Hide "[Sacks] +N items. (Last 5s.)" sack pickup spam
    public boolean chatFilterEmpty = true;        // Hide blank/whitespace-only chat lines (leftover from filtered multi-line msgs)
    public boolean chatFilterBonePlating = false; // Hide "Your bone plating reduced the damage ..." spam

    public boolean infiniteChat = true;          // Keep full chat history (scroll back through everything)
    public int chatHistoryLimit = 16384;         // Hard cap so memory stays bounded (effectively infinite)
    public boolean chatStacking = true;          // Stack identical messages with a (Nx) counter
    public int chatStackWindowMinutes = 5;       // Stack any identical message from the last N minutes (1-30)

    public boolean pbOnJoin = true;               // Show a player's dungeon PBs when they join the party (needs API key)
    public boolean chatCommands = true;           // Party chat commands (!8ball, !cf, !warp, !pt, !kick, ...)

    public boolean autoRequeue = true;           // Send /instancerequeue at the end of a dungeon
    public boolean requeueOnPartyR = true;       // Requeue when a party member types "r"
    public int requeueDelaySeconds = 0;           // Delay before requeuing

    public static class Keybind {
        public int key = -1;          // GLFW key code, -1 = unbound
        public String message = "";
        public Keybind() {}
        public Keybind(int key, String message) { this.key = key; this.message = message; }
    }
    public List<Keybind> keybinds = new ArrayList<>();

    public String keybindChatMessage = "";

    public static class Shortcut {
        public String alias = "";     // typed without the leading slash, e.g. "pk"
        public String command = "";   // expansion without leading slash, e.g. "party kick"
        public Shortcut() {}
        public Shortcut(String alias, String command) { this.alias = alias; this.command = command; }
    }
    public List<Shortcut> shortcuts = new ArrayList<>(List.of(
            new Shortcut("pd", "party disband"),
            new Shortcut("pk", "party kick"),
            new Shortcut("pt", "party transfer"),
            new Shortcut("dh", "warp dhub")
    ));

    public boolean noFire = true;            // Hide fire overlay on screen
    public boolean noBlind = true;           // Remove blindness effect
    public boolean noNausea = true;          // Remove nausea effect
    public boolean noExplosions = true;      // Hide explosion particles
    public boolean noExplosionSound = false; // Mute explosion sounds (TNT/creeper)
    public boolean noCreeperHurtSound = false; // Mute creeper hurt sounds
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

    public boolean autoCloseChests = false;       // Automatically close chests after opening
    public int autoCloseDelay = 7;                // Delay in ticks before auto-closing (0-20)
    public int autoCloseRandomization = 3;        // Random ticks added to delay (0-10)
    public boolean closeChestOnInput = false;     // Close chest when any key is pressed

    public boolean slayerHUD = false;          // Show slayer boss health/phase overlay
    public int slayerHudX = 152;               // X position of slayer HUD
    public int slayerHudY = 32;                // Y position of slayer HUD
    public float slayerHudScale = 1.0f;        // Scale of slayer HUD
    public boolean slayerOnlyOwnBoss = false;  // Only show HUD for your own boss
    public boolean slayerBossESP = false;      // Glow on boss (color based on phase)
    public boolean slayerMinibossESP = false;  // Glow on minibosses (Burningsoul=red, Kindleheart=white)

    public boolean autoScan = true;
    public int scanTickInterval = 5;

    public boolean autoGFS = true;             // Master toggle for Auto GFS
    public boolean autoGFSOnStart = true;       // Refill when dungeon starts
    public boolean autoGFSTimer = true;        // Refill on timer
    public int autoGFSInterval = 30;            // Timer interval in seconds
    public boolean autoGFSDungeonOnly = true;   // Only refill in dungeons
    public boolean autoGFSPearls = true;        // Refill ender pearls
    public boolean autoGFSJerry = true;         // Refill inflatable jerry
    public boolean autoGFSTNT = true;           // Refill superboom TNT
    public boolean autoGFSDraft = true;         // Auto get draft on puzzle fail

    public boolean autoWish = true;             // Auto use healer wish at key boss moments

    public boolean secretClicker = false;       // Auto-click secrets when looking at them [DISABLED]
    public boolean secretClickerLevers = true;  // Click levers [DISABLED]
    public boolean secretClickerButtons = true; // Click buttons [DISABLED]
    public boolean secretClickerSkulls = true;  // Click skulls [DISABLED]
    public boolean secretClickerChests = true;  // Click regular chests [DISABLED]
    public boolean secretClickerTrappedChests = false; // Click trapped chests (mimic risk!) [DISABLED]
    public int secretClickerDelay = 200;        // Delay between clicks in ms [DISABLED]
    public int secretClickerRandomization = 50; // Random delay added (0 to this value) [DISABLED]

    public boolean solveBlaze = true;              // Show which blaze to hit
    public boolean blazeDoneMessage = true;        // Send "/pc Blaze Done!" when last blaze killed
    public boolean modernBlazeSolver = false;      // Fill the solver boxes + hide the vanilla blaze mobs (cleaner look)
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
    public boolean blockWrongTerminalClicks = true; // Swallow clicks on wrong terminal slots (can't misclick-fail)
    public boolean lastBreathSound = false;         // Rising charge sound while pulling a Last Breath
    public float lastBreathVolume = 1.0f;           // Last Breath sound volume (0-5)
    public int lastBreathThreshold = 9;             // Tick threshold to swap to the second sound (0 always, 21 never)
    public boolean splitsSendAllOnEnd = false;      // Print a recap of all splits + total at run end
    public boolean partyDuplicateAlert = false;     // Alert when the party has duplicate dungeon classes
    public boolean partyDuplicateSound = true;      // Play a sound on the duplicate-class alert
    public boolean partyDuplicateMessage = false;   // Announce duplicate classes to party chat
    public boolean hideCheapCoins = false;          // Hide the spinning coin item entities from coin talismans
    public boolean warpCooldown = false;            // 30s warp/re-queue cooldown timer after a floor starts
    public boolean bonzoTimer = false;              // Bonzo's Mask immunity + cooldown timer
    public boolean spiritMaskTimer = false;         // Spirit Mask immunity + cooldown timer
    public boolean phoenixTimer = false;            // Phoenix Pet immunity + cooldown timer
    public boolean purplePadTimer = false;          // F7 purple-pad countdown after Storm's call
    public boolean deathTickTimer = false;          // Entrance death-tick (40-tick i-frame cycle) countdown
    public boolean colorPortal = false;             // Recolor the blood portal by score (red/gold/green)
    public boolean relicTimer = false;              // M7 relic spawn countdown after Necron's line
    public boolean creeperBeamsDing = false;        // Ding when toggling a lantern in Creeper Beams
    public int dungeonTimersX = 10;                 // Shared dungeon-timers HUD position X
    public int dungeonTimersY = 80;                 // Shared dungeon-timers HUD position Y
    public float dungeonTimersScale = 1.0f;         // Shared dungeon-timers HUD scale
    public boolean disableWorldLoadingScreen = false; // Skip the world loading / downloading-terrain screen
    public boolean blockOverlay = false;            // Outline the block you're looking at
    public String blockOverlayColor = "FFFFFF";     // Block overlay outline colour
    public boolean chatWaypoint = false;            // Render a waypoint beam from coords in chat
    public boolean showSelectedPet = false;         // Highlight the spawned pet in the Pets menu
    public boolean simonSaysProgress = false;       // Show Simon Says progress (X/Y) in the timer HUD
    public boolean customHypeSound = false;         // Replace the wither-blade explosion sound
    public boolean wardrobeKeybinds = false;        // Hotbar keys 1-9 switch wardrobe slots in the Wardrobe GUI
    public boolean leapCounter = false;             // F7: count teammates standing at your spot
    public boolean solveWaterBoard = true;          // Show solution for Water Board puzzle
    public boolean waterBoardTracers = true;        // Draw tracers to next lever
    public boolean solveIceFill = true;             // Show Ice Fill (F7) path solution
    public boolean iceFillOptimized = true;         // Use optimized (harder/faster) ice fill paths
    public String colorIceFill = "55FFFF";          // Ice Fill path color (aqua)
    public boolean witherDragons = true;            // M7 Wither Dragons helper (master toggle)
    public boolean witherDragonTimer = true;        // Spawn countdown timer at each dragon's spawn
    public boolean witherDragonBoxes = true;        // Boxes around spawning/alive dragons
    public boolean witherDragonTitle = true;        // Title when a dragon starts spawning
    public boolean witherDragonMsg = true;          // Chat message when a dragon is spawning/spawned
    public boolean dragonBoxes = false;             // ESP box around each live wither dragon (by colour)
    public boolean dragonHealth = false;            // Show each dragon's health % above it
    public boolean hideDyingDragons = false;        // Hide a dragon once it's dying (health <= 0)
    public boolean witherHighlight = false;         // Highlight F7/M7 device-phase withers
    public boolean witherHighlightBox = true;       // Filled box (vs outline) for the wither highlight
    public String witherHighlightColor = "00FFFF";  // Wither highlight colour

    public boolean solveSimonSays = false;         // Show button sequence for Simon Says
    public boolean solveArrowAlign = false;        // Show clicks needed for Arrow Align

    public boolean terracottaTimer = false;        // Show spawn timers for F6/M6 terracotta
    public boolean spiritBearTimer = false;        // Show spawn timer for F4/M4 spirit bear

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

    public boolean solveChronomatron = false;      // Auto-solve Chronomatron (memory sequence) [DISABLED]
    public boolean solveSuperpairs = false;        // Auto-solve Superpairs (matching pairs) [DISABLED]
    public boolean solveUltrasequencer = false;    // Auto-solve Ultrasequencer (numbered sequence) [DISABLED]
    public int experimentClickDelay = 300;         // Delay in ms before first click in experiments [DISABLED]
    public int experimentClickInterval = 150;      // Delay in ms between clicks in experiments [DISABLED]

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

    public boolean secretWaypoints = true;            // Master toggle for secret waypoints
    public boolean secretWaypointChests = true;       // Show chest secrets
    public boolean secretWaypointItems = true;        // Show item secrets
    public boolean secretWaypointBats = true;         // Show bat secrets
    public boolean secretWaypointEssence = true;      // Show essence secrets
    public boolean secretWaypointRedstoneKey = true;  // Show redstone key secrets
    public boolean secretWaypointTracers = false;     // Draw tracers to waypoints
    public boolean secretWaypointHideCollected = true; // Hide waypoints when secret is collected

    public String colorSecretChest = "55FF55";        // Chest (green)
    public String colorSecretItem = "55FFFF";         // Item (cyan)
    public String colorSecretBat = "AAAAAA";          // Bat (gray)
    public String colorSecretEssence = "AA00AA";      // Essence (purple)
    public String colorSecretRedstone = "FF5555";     // Redstone (red)

    public boolean leapOverlay = true;               // Enhanced Spirit Leap menu
    public boolean leapShowHeads = true;              // Show player heads/skins in the leap menu
    public boolean leapAnnounce = true;              // Announce "/pc Leaped to X!" when you leap
    public boolean leapShowMap = true;                // Show the dungeon map atop the leap menu (click to leap)
    public String leapSortMode = "Odin";              // Odin, Class A-Z, Name A-Z, Custom, None
    public List<String> leapCustomOrder = new ArrayList<>(); // player names (lowercase), for Custom sort
    public String leapKeybindMode = "Corners";        // Corners (TL/TR/BL/BR) or Class (per-class)
    public int leapKeyTL = -1, leapKeyTR = -1, leapKeyBL = -1, leapKeyBR = -1; // corner keybinds
    public int leapKeyArcher = -1, leapKeyBerserk = -1, leapKeyHealer = -1, leapKeyMage = -1, leapKeyTank = -1; // class keybinds
    public int leapKeyLastDoor = -1; // leap to the player who opened the most recent wither door

    public boolean debugMode = false;

    public String hypixelApiKey = "";
    public List<String> recentPlayers = new ArrayList<>();
    public int pvCacheDurationSeconds = 300;  // 5 minute cache

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

    public String colorDoorNormal = "5C340E";        // Normal door (brown)
    public String colorDoorWither = "000000";        // Wither door (black)
    public String colorDoorBlood = "E70000";         // Blood door (red)
    public String colorDoorEntrance = "148500";      // Entrance door (green)

    public String colorTextUnexplored = "888888";    // Gray - unexplored
    public String colorTextCleared = "FFFFFF";       // White - cleared
    public String colorTextGreen = "55FF55";         // Green - all secrets found
    public String colorSecretCount = "AAAAAA";       // Secret count number

    public String colorCheckWhite = "FFFFFF";        // White checkmark
    public String colorCheckGreen = "55FF55";        // Green checkmark
    public String colorCheckFailed = "FF5555";       // Failed/red X

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
                if (instance.customESPMobs == null) {
                    instance.customESPMobs = new ArrayList<>();
                }
                if (instance.recentPlayers == null) {
                    instance.recentPlayers = new ArrayList<>();
                }
                if (instance.splitPbs == null) {
                    instance.splitPbs = new java.util.HashMap<>();
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
