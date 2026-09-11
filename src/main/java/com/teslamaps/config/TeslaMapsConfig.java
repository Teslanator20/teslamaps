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
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(net.minecraft.resources.Identifier.class, new IdentifierAdapter())
            .registerTypeHierarchyAdapter(net.minecraft.network.chat.Component.class, new ComponentAdapter())
            .create();

    /** Identifiers round-trip as their plain string form. */
    private static class IdentifierAdapter
            implements com.google.gson.JsonSerializer<net.minecraft.resources.Identifier>,
                       com.google.gson.JsonDeserializer<net.minecraft.resources.Identifier> {
        @Override
        public com.google.gson.JsonElement serialize(net.minecraft.resources.Identifier src,
                java.lang.reflect.Type type, com.google.gson.JsonSerializationContext ctx) {
            return new com.google.gson.JsonPrimitive(src.toString());
        }

        @Override
        public net.minecraft.resources.Identifier deserialize(com.google.gson.JsonElement json,
                java.lang.reflect.Type type, com.google.gson.JsonDeserializationContext ctx) {
            return net.minecraft.resources.Identifier.tryParse(json.getAsString());
        }
    }

    /** Text components go through the vanilla codec so styling survives a restart. */
    private static class ComponentAdapter
            implements com.google.gson.JsonSerializer<net.minecraft.network.chat.Component>,
                       com.google.gson.JsonDeserializer<net.minecraft.network.chat.Component> {
        @Override
        public com.google.gson.JsonElement serialize(net.minecraft.network.chat.Component src,
                java.lang.reflect.Type type, com.google.gson.JsonSerializationContext ctx) {
            return net.minecraft.network.chat.ComponentSerialization.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, src)
                    .result().orElseGet(com.google.gson.JsonObject::new);
        }

        @Override
        public net.minecraft.network.chat.Component deserialize(com.google.gson.JsonElement json,
                java.lang.reflect.Type type, com.google.gson.JsonDeserializationContext ctx) {
            return net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                    .result().orElse(null);
        }
    }
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("config.json");

    private static TeslaMapsConfig instance = new TeslaMapsConfig();

    public String activeProfile = ConfigProfiles.DEFAULT;

    public boolean mapEnabled = true;
    public boolean onlyShowInDungeon = true;  // Only show map while in dungeon
    public boolean mapOnlyInBoss = false;     // Only show map during the boss fight
    public boolean mapHideInBoss = true;      // Hide the map during the boss fight
    public boolean legitMode = false;         // Only show explored (revealed) rooms, like the vanilla map
    public boolean legitMapOnly = false;      // Legit Mode affects only the map; don't block ESP/auto/cheats
    @Global
    public int legitPeekKey = -1;             // Hold to reveal everything while legit mode is on (GLFW code, -1 = unbound)
    public int mapX = 23;
    public int mapY = 172;
    public float mapScale = 1.5f;
    public boolean showMapBackground = true;

    public boolean showRoomNames = true;
    public boolean hideEntranceBloodFairyNames = true; // Don't render names for entrance, blood, fairy rooms
    public boolean showNamesOnlyForPuzzles = false;     // Only show room names for puzzle rooms
    public boolean roomNamesOnlyWithLeap = false;       // Only show room names while holding a Spirit Leap
    public boolean playerNamesOnlyWithLeap = false;     // Only show player names while holding a Spirit Leap
    public boolean mapHeadBorder = false;               // Class-colored border around map player heads
    public boolean showSecretCount = true;
    public boolean hideOneSecretCount = true;           // Don't show secret count if room has only 1 secret
    public boolean showCrypts = true;           // Show crypts found below map
    public boolean showTotalCrypts = false;     // Show total crypts (from rooms.json) next to crypts found
    public boolean hideSecretsWhenDone = true;  // Hide secret count when room is green
    public boolean showMimicStatus = true;      // Show mimic status (checkmark/cross) below map
    public boolean showPrinceIcon = true;        // Show a small crown icon on rooms that can contain a Prince (+1 score)
    public int princeCrownVariant = 5;           // 0 = drawn crown, 1-5 = bundled crown images (/tmap prince crown N)
    public boolean showPrinceStatus = true;      // Show Prince status (checkmark/cross) below map, like Mimic
    public boolean princeESP = true;             // World ESP marking the superboom structure that spawns a Prince
    public boolean showDungeonScore = true;     // Show estimated dungeon score below map
    public boolean assumePaulMayor = false;    // Always add +10 Paul bonus to score
    public boolean announce300 = true;         // Announce "300 Score" in party chat (/pc)
    public boolean announce270 = false;         // Announce "270 Score" in party chat (/pc)
    public boolean cryptReminder = true;        // Warn at boss entry if fewer than 5 crypts were done
    public String cryptReminderSound = "BELL";  // Sound for the crypt reminder (see SoundOptions)
    public boolean watcherKillAlert = true;     // Title + sound when the Watcher is cleared (blood done)
    public float roomNameScale = 1.0f;          // Scale of room name text (0.5 - 2.0)
    public float infoTextScale = 1.0f;          // Scale of the info box below the map (Secrets/Crypts/Score) (0.5 - 2.0)

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
    public int felESPRange = 30;                // Max range for Fel ESP (blocks)
    public boolean sniperESP = true;            // Highlight snipers (skeleton mobs)
    public boolean shadowAssassinESP = true;    // Highlight invisible shadow assassins
    public boolean dungeonBatESP = true;        // Highlight invisible bats (secrets)
    public boolean dungeonBatTracers = false;   // Draw tracers to dungeon bats
    public boolean invisibleArmorStandESP = false; // Highlight invisible armor stands (decorations like skulls)
    public boolean witherKeyESP = true;         // Highlight wither/blood keys
    public boolean keyTracers = true;           // Draw tracers to wither/blood keys
    public boolean bossESP = true;             // Box the F7 wither bosses (Maxor/Storm/Goldor/Necron)
    public boolean bossESPFilled = true;       // Filled box instead of outline for Boss ESP
    public boolean bossESPDepthCheck = false;   // Depth check on = respect walls; off = through walls
    public boolean corpseESP = true;            // Box + beacon on unlooted Glacite Mineshaft corpses
    public boolean corpseESPTypeColors = true;  // Colour per corpse type (Lapis/Umber/Tungsten) instead of one colour
    public boolean lividFinder = true;          // Highlight the correct Livid in F5/M5
    public boolean lividTracer = true;          // Draw tracer to correct Livid
    public boolean lividDeathMessage = true;    // Send "/pc Livid Dead!" when Livid dies
    public boolean doorESP = true;              // Highlight wither/blood doors through walls
    public boolean fairyDoorESP = true;         // Highlight the closed fairy-room doors (open when the wither door behind falls)
    public boolean doorTracers = false;         // Draw tracers to wither/blood doors
    public boolean doorColorBasedOnKey = true;  // Color doors red if key not picked up, green if picked up
    public boolean onlyShowNextDoor = true;     // Only show tracer/ESP for the nearest door
    public boolean mimicChestESP = true;        // Highlight trapped chests (potential mimic)
    public boolean mimicChestTracers = false;   // Draw tracers to trapped chests
    public boolean mimicDeadMessage = true;     // Send "/pc Mimic Dead!" when mimic dies
    public boolean princeDeadMessage = true;    // Send "/pc Prince Dead!" when a Prince is killed
    public boolean showGlow = true;             // Show glow effect on entities
    public boolean filledESP = false;           // Fill entire entity hitbox instead of just outlines
    public boolean boxESP = false;              // Outline hitbox (F3+H style) around ESP mobs, through walls
    public float espAlpha = 0.4f;               // Transparency for filled ESP (0.0 = invisible, 1.0 = solid)

    public boolean pestESP = true;             // Highlight pests in garden (invisible silverfish)
    public boolean pestTracers = true;         // Draw tracers to pests
    public boolean hoppityEggESP = false;       // Box + label on Hoppity chocolate eggs
    public boolean hoppityEggTracers = false;   // Draw tracers to Hoppity eggs
    public boolean droppedItemESP = true;       // Highlight all dropped items
    public boolean secretItemReadiness = true;   // Color dropped items in dungeons by pickup readiness
    public int secretItemPickupTicks = 50;       // Ticks after a drop spawns before it's pickable (tune in-game)
    public boolean hideSoulweaverSkull = true; // Hide the floating skull spawned by Soulweaver Gloves
    public boolean hideSkeletonSkull = true;   // Hide the floating "Skeleton Skull" armor stands (bone shield / piling skulls)
    public boolean hideThrownBones = true;     // Hide thrown/dropped Bone item entities in dungeons
    public boolean hideSuperboomTnt = true;    // Hide Superboom TNT drops
    public boolean hideBlessing = true;        // Hide Blessing drops
    public boolean hideReviveStone = true;     // Hide Revive Stone drops
    public boolean hidePremiumFlesh = true;    // Hide Premium Flesh drops
    public boolean hideJournalEntry = true;    // Hide Journal Entry item drops
    public boolean hideHealerOrbs = true;      // Hide healer DAMAGE/ABILITY/DEFENSE orbs
    public boolean hideHealerFairy = true;     // Hide the healer fairy companion skull
    public List<String> customESPMobs = new ArrayList<>();  // Custom mob names to highlight
    public List<String> customESPTracers = new ArrayList<>();  // Of those, which also get a tracer
    // per-entry colour, keyed by the lowercased entry; absent = colorESPCustom
    public java.util.Map<String, String> customESPColors = new java.util.HashMap<>();

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
    public boolean secretItemPickupSound = true; // Play the secret sound when picking up dungeon item drops
    public boolean secretClickHighlight = true;  // Flash clicked secret blocks green (worked) / red (locked chest)
    public boolean salvageHelper = true;        // Highlight cheap (<100k) dungeon-salvageable gear in the Salvage menu
    public boolean sellableHighlighter = true;  // Highlight common junk to sell in Ophelia/Booster Cookie menus
    public boolean secretChime = false;          // Ascending chime per secret found in the room
    public String secretChimeSound = "NOTE_CHIME"; // Chime sound (see SoundOptions.keys())
    public float secretChimeVolume = 1.0f;       // Volume for the secret chime (0.0 - 4.0)
    public boolean bearSpawnWarning = true;      // Enable the bear spawn warning
    public boolean bearSpawnWardenSound = true;  // Play warden emerge sound on alert
    public boolean bearSpawnWitherSound = true;  // Play wither death sound on alert
    public float bearSpawnVolume = 0.5f;         // Alert sound volume (0.0 - 20.0)
    public int bearSpawnX = 598;                 // "STOP!" overlay position X (draggable in HUD edit)
    public int bearSpawnY = 482;                  // "STOP!" overlay position Y
    public float bearSpawnScale = 5.0f;          // "STOP!" overlay text scale (0.5 - 10.0)

    public boolean commandSuggestions = true;
    public boolean commandSuggestionsPlayers = true;    // Tab-complete the commands that take a player name
    @Global
    public boolean updateCheck = true;          // Look for a new signed release on launch and offer it
    public String hudStyle = "Modern";          // Look of every HUD panel: Modern / Glass / Vanilla / Tooltip
    @Global
    public String guiStyle = "Default";         // Look of the mod's screens: Default / Vanilla / Tooltip / Glass
    @Global
    public String guiSoundTheme = "Off";        // Menu sound set: Off / Clicky / Chime / Soft / Digital
    @Global
    public boolean guiHoverSounds = false;      // The quiet tick when the cursor moves onto a row
    @Global
    public float guiSoundVolume = 1.0f;         // Scales every interface sound (0.0 - 2.0)
    @Global
    public String lastConfigCategory = "Profiles"; // Category the settings screen reopens on
    public boolean sprintingOverlay = false;    // Show a "Sprinting / Not Sprinting" HUD indicator
    public int sprintingX = 10;                  // Sprinting overlay position X (draggable in HUD edit)
    public int sprintingY = 40;                  // Sprinting overlay position Y
    public float sprintingScale = 1.0f;          // Sprinting overlay text scale (0.5 - 10.0)
    public boolean autoSprint = true;            // Always sprint without holding the sprint key

    public boolean auctionPriceInput = true;     // Replace the auction starting-bid sign with a textbox
    public String auctionDefaultMode = "Normal"; // Mode the input opens in, follows the last picked mode

    public boolean auctionPriceCompare = false;  // tint auction slots by listing price vs estimated value
    public boolean auctionCompareOnlyGood = false; // only the green side, no red/yellow
    public float auctionCompareThreshold = 5f;   // percent off the value before a slot is tinted
    public float auctionCompareOpacity = 0.45f;

    public boolean searchOverlay = true;         // Replace the auction/bazaar search sign with a textbox
    public int searchOverlaySuggestions = 7;     // How many item names the search overlay suggests (0 - 12)
    public boolean searchOverlayKeepText = true; // Start with whatever the sign already carried
    public boolean searchOverlayHistory = true;  // Remember and list previous searches
    @Global
    public java.util.List<String> auctionSearchHistory = new java.util.ArrayList<>();
    @Global
    public java.util.List<String> bazaarSearchHistory = new java.util.ArrayList<>();
    public boolean middleClickCopyName = true;   // Middle click an item in a container copies its name

    public boolean darkMode = false;             // Darken the whole screen with a black tint
    public int darkModeOpacity = 25;             // Dark tint strength (1 - 100)
    public boolean darkModeTintHud = false;      // Also tint HUD elements (else HUD stays bright)

    public boolean splitsEnabled = true;         // Dungeon run splits HUD + chat output
    public boolean splitsShowPb = true;          // Show personal-best time per split (and color vs PB)
    @Global
    public java.util.Map<String, Long> splitPbs = new java.util.HashMap<>(); // "floor:splitName" -> best duration ms
    public int splitsX = 936;                       // Splits HUD position X (draggable in HUD edit)
    public int splitsY = 59;                     // Splits HUD position Y
    public float splitsScale = 1.7000002f;              // Splits HUD scale (0.5 - 2.0)
    public boolean watcherSpeedGrade = true;     // Announce Watcher (blood camp) clear time with a fast/normal/slow grade
    public float watcherGradeVolume = 1.0f;      // Volume for the Watcher grade sound (0.0 - 20.0)
    public boolean watcherHud = true;            // Live Watcher Stats HUD (Dialogue / Move / Camp) during blood camp
    public int watcherHudX = 5;                  // Watcher HUD position X
    public int watcherHudY = 80;                 // Watcher HUD position Y
    public float watcherHudScale = 1.0f;         // Watcher HUD scale
    public boolean instaClearAlert = false;      // Alert when a teammate rush/insta-clears a room you never entered
    public float instaClearVolume = 1.0f;        // Volume for the insta-clear alert sound (0.0 - 20.0)
    public boolean rareRoomAlert = false;        // Title + chat when Trinity is scanned
    public float rareRoomVolume = 1.0f;          // Volume for the rare room alert sound (0.0 - 20.0)
    public boolean autoExtraStats = false;       // Run /showextrastats automatically at the end of a run
    public boolean itemProtection = false;       // Block dropping items worth more than itemProtectionValue
    public int itemProtectionValue = 10;         // Protection threshold in millions of coins
    public boolean itemProtectionSell = true;    // Also block salvaging and shift-click selling
    public boolean roomSplits = false;           // Live per-room pace HUD (current room timer + recent rooms)
    public int roomSplitsX = 5;                  // Room Splits HUD position X
    public int roomSplitsY = 120;                // Room Splits HUD position Y
    public float roomSplitsScale = 1.0f;         // Room Splits HUD scale

    public boolean bloodCampMoveTimer = true;     // Watcher move prediction timer HUD
    public boolean bloodCampMoveMessage = true;   // Chat message with predicted move time
    public boolean bloodCampPartyMessage = true; // Send predicted move time to party chat
    public boolean bloodCampKillTitle = true;     // "Kill Mobs" title when the watcher moves
    public boolean bloodCampHpBar = true;         // Show the Watcher's remaining HP/mob count
    public int bloodCampX = 586;                   // Move Timer HUD position X
    public int bloodCampY = 66;                   // Move Timer HUD position Y
    public float bloodCampScale = 1.5000001f;           // Move Timer HUD scale
    public boolean bloodCampAssist = true;       // Master toggle for the spawn-box guess
    public String colorBloodSpawn = "FF5555";     // predicted spawn box (red)
    public String colorBloodPosition = "55FF55";  // current ping-adjusted position box (green)
    public String colorBloodFinal = "00AAAA";     // merged spawn+position box (aqua)
    public float bloodAssistBoxSize = 1.0f;       // box side length (0.1 - 1.0)
    public boolean bloodAssistLine = true;        // line from position to spawn box
    public boolean bloodAssistTime = true;        // per-mob spawn countdown text
    public boolean bloodAssistInterpolation = true; // smooth box jitter between ticks
    public boolean bloodAssistPingOffset = true;  // offset the position box by your ping
    public float bloodAssistManualOffset = 0.0f;    // manual ms offset when ping offset is off (0 - 300)
    public int bloodAssistOffset = 40;            // advanced: tick offset to fine-tune timing (-100 - 100)
    public int bloodAssistTick = 38;              // advanced: assumed spawn tick (35 - 41)
    public boolean bloodCampMoveAlert = true;    // title + sound at the moment the Watcher moves
    public String bloodCampMoveSound = "NOTE_BELL";
    public float bloodCampMoveVolume = 1.5f;
    public boolean bloodReturnTimer = true;      // show the "Return to Blood" HUD countdown
    public int bloodReturnEstimate = 24;          // early seed countdown until SkyHanni-style prediction refines it (10 - 60)

    public boolean starredTracerWhenFew = false;  // Tracer to starred mobs when few are left
    public int starredTracerThreshold = 3;        // Show tracers when this many or fewer are alive

    public boolean rarityBackgrounds = true;     // Colored background behind items by rarity
    public float rarityBgOpacity = 0.5f;          // 0.0 - 1.0
    public String rarityBgShape = "Square";       // Square, Rounded, Circle, Diamond, Corners, Underline
    public String rarityBgStyle = "Filled";       // Filled, Outline, Gradient

    public boolean dungeonWaypoints = true;       // Render Odin-format dungeon waypoints from config/teslamaps/dungeon_waypoints.json
    @Global
    public int waypointAddKey = -1;               // Slot 1 keybind: add waypoint at looked-at block (-1 = unbound)
    public String waypointAddColor = "FFFF55";    // Slot 1 color
    public boolean waypointAddFilled = false;    // Slot 1 filled box instead of outline
    public boolean waypointAddThroughWalls = false; // Slot 1 render through walls (depth off)

    @Global
    public int waypointAdd2Key = -1;              // Slot 2 keybind
    public String waypointAdd2Color = "5555FF";   // Slot 2 color
    public boolean waypointAdd2Filled = true;    // Slot 2 filled
    public boolean waypointAdd2ThroughWalls = false; // Slot 2 through walls

    @Global
    public int waypointAdd3Key = -1;              // Slot 3 keybind
    public String waypointAdd3Color = "FF5555";   // Slot 3 color
    public boolean waypointAdd3Filled = false;   // Slot 3 filled
    public boolean waypointAdd3ThroughWalls = true; // Slot 3 through walls

    @Global
    public int waypointRemoveKey = -1;            // Keybind: remove nearest waypoint in current room
    @Global
    public int waypointClearKey = -1;             // Keybind: clear all waypoints in current room

    // Crystal Hollows scanner (Golden Dragon nest etc.)
    public boolean chScanner = false;             // Master toggle: scan CH chunks for structures
    public boolean chScanDragon = true;           // Golden Dragon nest
    public boolean chScanCrystals = true;         // King, Queen, Divan, City, Temple, Bal
    public boolean chScanMobSpots = true;         // Corleone, Key Guardians, Xalx, Pete, Odawa
    public boolean chScanGrottos = true;          // Fairy Grottos
    public boolean chScanWorm = false;            // Worm fishing spots
    public boolean chScanTracers = false;         // Tracer lines to found structures
    public boolean chScanChat = true;             // Chat alert with coords when found

    // Drill / pickaxe ability cooldown, from the "Pickaxe Ability:" tab widget
    public boolean pickaxeAbilityDisplay = false;
    public boolean pickaxeAbilityMiningOnly = true;    // only on the mining islands
    public boolean pickaxeAbilityOnlyOnCooldown = false;
    public int pickaxeAbilityX = 5;
    public int pickaxeAbilityY = 120;
    public float pickaxeAbilityScale = 1.0f;

    // Glacite Mineshaft pity, from the "Pity:" tab widget
    public boolean mineshaftPityDisplay = false;
    public boolean mineshaftFoundChat = true;          // chat line with the pity value on spawn
    public float mineshaftChanceBonus = 0f;            // Surveyor + Core of the Mountain mineshaft chance %
    public int mineshaftSurveyorLevel = 0;             // runtime: scanned off the HOTM menu
    public int mineshaftCoreLevel = 0;                 // runtime: scanned off the HOTM menu
    public int mineshaftTotalFound = 0;                // runtime: mineshafts found overall
    public int mineshaftPityX = 5;
    public int mineshaftPityY = 140;
    public float mineshaftPityScale = 1.0f;

    // Head ESP (skull blocks + armor-stand heads, matched on texture hash)
    public static class HeadTarget {
        public String name = "";
        public String hash = "";
        public String color = "#FFAA00";
        public HeadTarget() {}
        public HeadTarget(String name, String hash, String color) {
            this.name = name;
            this.hash = hash;
            this.color = color;
        }
    }
    public boolean headESP = false;
    public boolean headGoldenDragonEgg = false;    // built-in target: Golden Dragon egg heads (CH nest)
    public String headGoldenDragonColor = "#FFD700";
    public boolean headESPName = true;
    public boolean headESPTracers = false;
    public boolean headESPBeam = false;
    public String headESPColor = "#FFAA00";        // color used for newly added heads
    @Global
    public List<HeadTarget> headTargets = new ArrayList<>();

    // Tiki ESP (3 stacked skull blocks)
    public boolean tikiESP = false;
    public boolean tikiESPName = true;
    public String tikiESPColor = "#00FF06";

    public boolean etherwarp = true;              // Show etherwarp guess box
    public boolean etherwarpShowFail = true;      // Show the box even when the guess failed
    public boolean etherwarpFilled = true;       // Filled box instead of outline
    public float etherwarpEyeOffset = -0.0f;      // Fine-tune the ray's eye height (blocks) if the guess sits too high/low
    public boolean etherwarpSkullFail = true;     // Mark as failed if the target block (or above it) is a skull
    public String colorEtherwarp = "00FF8C";      // Etherwarp guess box color (gold)
    public boolean etherwarpCustomSound = true;  // Replace the etherwarp sound with a custom one
    public String etherwarpSound = "NOTE_PLING"; // see SoundOptions for all options
    public float etherwarpSoundVolume = 1.0f;     // Custom etherwarp sound volume (0.0 - 20.0)
    public float etherwarpSoundPitch = 1.0f;      // Custom etherwarp sound pitch (0.5 - 2.0)

    public boolean hidePlayers = false;           // Hide nearby players
    public boolean hideDeadMobs = true;          // Hide mobs playing their death animation
    public boolean hidePlayersOnlyDungeon = true; // Only hide while in a dungeon
    public boolean hidePlayersAll = false;        // Hide all players regardless of distance
    public float hidePlayersDistance = 3.0f;      // Hide players within this many blocks
    public boolean hidePlayersAfterLeap = false;  // Hide players briefly after a spirit leap
    public float hidePlayersAfterLeapSeconds = 3.0f;

    public boolean croesusProfitOverlay = true; // Profit overlay in the Croesus run menu (prices via public bazaar/BIN, no API key)
    public boolean croesusHighlightBest = true;     // Green highlight on the most profitable chest
    public boolean croesusDimOthers = true;         // Gray out every chest that isn't the best one
    public boolean croesusHideOpened = true;       // Gray out fully-opened runs in the Croesus menu
    public boolean croesusHighlightUnopened = true; // Green-highlight runs with openable chests (orange = kismet used)
    public boolean croesusCompact = false;          // Compact profit panel: only totals, no item breakdown
    public boolean croesusDebug = false;            // Log chest lore to latest.log (to calibrate the parser)

    public boolean chatCopyEnabled = true;       // Right-click a chat line to copy its text

    public boolean chatFilterEnabled = true;     // Master switch for the chat filter
    public boolean chatFilterWatcher = true;    // Hide "[BOSS] The Watcher:" lines
    public boolean chatFilterF4Boss = true;     // Hide F4/M4 boss taunt spam ("[BOSS] Thorn: ...")
    public boolean chatFilterBlessings = true;  // Hide "DUNGEON BUFF! ... Blessing of ..." + blessing pickups
    public boolean chatFilterEssence = true;    // Hide essence pickup spam ("ESSENCE! ...", "found a Wither Essence!")
    public boolean chatFilterKeys = true;       // Hide "A Wither/Blood Key was picked up!" / "has obtained ... Key"
    public boolean chatFilterReviveStone = true; // Hide "<player> has obtained Revive Stone!"
    public boolean chatFilterDoors = true;      // Hide "<player> opened a WITHER/BLOOD door!"
    public boolean chatFilterWish = true;       // Hide "<player>'s Wish healed you for ..."
    public boolean chatFilterPickups = true;    // Hide all "... was picked up!" messages
    public boolean chatFilterBlocksInWay = true; // Hide "There are blocks in the way!"
    public boolean chatFilterUltReady = true;    // Hide "<ult> is ready to use! Press DROP to activate it!"
    public boolean chatFilterAoeDamage = true;   // Hide "Your <ability> hit N enemies for X damage."
    public boolean chatFilterGuildXp = true;     // Hide "You earned N GEXP from playing SkyBlock!"
    public boolean chatFilterKillCombo = true;   // Hide "+N Kill Combo ..." lines
    public boolean chatFilterStash = true;       // Hide "... items/materials stashed away!" + "CLICK HERE to pick them up!"
    public boolean chatFilterServerMsgs = true;  // Hide "Sending to server...", "Warping...", "Queuing..."
    public boolean chatFilterProfileInfo = true; // Hide "You are playing on profile: ..." + "Profile ID: ..."
    public boolean chatFilterPerkBuffs = true;   // Hide HOTF/HOTM daily perk buffs (Lottery / Sky Mall "buff changed!")
    public boolean chatFilterOruo = false;        // Hide "[STATUE] Oruo the Omniscient:" quiz flavor/question lines
    public boolean chatFilterWitherKingHeroes = true; // Hide the Wither King "Heroes I could never be" line (M7)
    public boolean chatFilterSacks = true;       // Hide "[Sacks] +N items. (Last 5s.)" sack pickup spam
    public boolean chatFilterGfs = true;         // Hide "Moved N <item> from your Sacks to your inventory." (GFS)
    public boolean chatFilterWatchdog = true;    // Hide "[WATCHDOG ANNOUNCEMENT] Watchdog has banned ..." spam
    public boolean chatFilterMort = true;        // Hide "[NPC] Mort: ..." dungeon entrance flavor lines
    public boolean chatFilterEmpty = true;        // Hide blank/whitespace-only chat lines (leftover from filtered multi-line msgs)
    public boolean chatFilterBonePlating = true; // Hide "Your bone plating reduced the damage ..." spam
    public boolean chatFilterMilestone = true;   // Hide dungeon milestone spam ("Healer/Class Milestone ❹: ... so far!")

    public boolean infiniteChat = true;          // Keep full chat history (scroll back through everything)
    public boolean chromaPrefix = true;          // [TeslaMaps] chat prefix with animated fade
    public String prefixFadeColorA = "B300FF";   // fade start colour
    public String prefixFadeColorB = "3355FF";   // fade end colour
    public int chatHistoryLimit = 16384;         // Hard cap so memory stays bounded (effectively infinite)
    public boolean chatStacking = true;          // Stack identical messages with a (Nx) counter
    public int chatStackWindowMinutes = 5;       // Stack any identical message from the last N minutes (1-30)

    public boolean pbOnJoin = true;               // Show a player's dungeon PBs when they join the party (needs API key)
    public boolean chatCommands = true;           // Party chat commands (!8ball, !cf, !warp, !pt, !kick, ...)

    public boolean autoRequeue = true;           // Send /instancerequeue at the end of a dungeon
    public boolean requeueOnPartyR = true;       // Requeue when a party member types "r"
    public int requeueDelaySeconds = 0;           // Delay before requeuing

    // Per-class feature gates. true = feature active when playing that class.
    public static class ClassFilter {
        public boolean mage = true, archer = true, berserk = true, healer = true, tank = true;
        public ClassFilter() {}
        public ClassFilter(boolean mage, boolean archer, boolean berserk, boolean healer, boolean tank) {
            this.mage = mage; this.archer = archer; this.berserk = berserk; this.healer = healer; this.tank = tank;
        }
        public boolean allows(String dungeonClass) {
            if (dungeonClass == null) return true; // class unknown -> don't block
            return switch (dungeonClass) {
                case "Mage" -> mage;
                case "Archer" -> archer;
                case "Berserk" -> berserk;
                case "Healer" -> healer;
                case "Tank" -> tank;
                default -> true;
            };
        }
        public boolean allowsLocal() {
            return allows(com.teslamaps.player.PlayerTracker.getLocalClass());
        }
        public String summary() {
            List<String> on = new ArrayList<>();
            if (mage) on.add("Mage");
            if (archer) on.add("Archer");
            if (berserk) on.add("Berserk");
            if (healer) on.add("Healer");
            if (tank) on.add("Tank");
            if (on.size() == 5) return "All Classes";
            if (on.isEmpty()) return "None";
            return String.join(", ", on);
        }
    }
    public ClassFilter doorEspClasses = new ClassFilter();                                  // all classes
    public ClassFilter bloodCampClasses = new ClassFilter(true, false, false, false, false); // mage only
    public ClassFilter cryptReminderClasses = new ClassFilter(false, true, true, true, true); // all but mage
    public ClassFilter keySoundClasses = new ClassFilter(true, true, false, false, false);    // mage + archer
    public ClassFilter watcherGradeClasses = new ClassFilter(true, false, false, false, false); // mage only

    public static class Keybind {
        public int key = -1;          // GLFW key code, -1 = unbound
        public String message = "";
        public Keybind() {}
        public Keybind(int key, String message) { this.key = key; this.message = message; }
    }
    @Global
    public List<Keybind> keybinds = defaultKeybinds();   // ships 3 unbound hotkeys (/eq /pets /wd) ready to bind
    private static List<Keybind> defaultKeybinds() {
        List<Keybind> l = new ArrayList<>();
        l.add(new Keybind(-1, "/eq"));
        l.add(new Keybind(-1, "/pets"));
        l.add(new Keybind(-1, "/wd"));
        return l;
    }
    public int hotkeyChatDelay = 250;            // ms to suppress hotkeys after closing a screen/chat (stops typed keys firing)
    @Global
    public int configScreenKey = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;  // opens /tmap, -1 = unbound
    @Global
    public boolean configScreenKeyExclusive = true;  // eat the key so other mods bound to it stay quiet

    // Section masters: a section must be on for its features to function (AND-gated). Absent = on.
    public java.util.Map<String, Boolean> sectionEnabled = new java.util.HashMap<>();

    /**
     * Config-screen categories that have a working master switch. The names have to match the
     * category keys in MapConfigScreen AND the section(...) call sites - a typo used to mean the
     * gate silently never matched. section() reports unknown names in dev so that cannot recur.
     */
    public static final java.util.List<String> SECTIONS = java.util.List.of(
            "Auto", "Blood Camp", "Chat", "Combat", "Contest Timers", "Critter Safari", "ESP", "Fishing",
            "Foraging", "Garden", "Inventory", "Leap", "Map", "Mining",
            "Ordered Waypoints", "Party", "Puzzles & Terminals", "Render", "Score & Splits",
            "Secrets & Waypoints", "Shards", "Skills", "Sounds", "Timers", "Torrhus Canyon");

    private static final java.util.Set<String> WARNED_SECTIONS = new java.util.HashSet<>();

    public boolean section(String name) {
        if (!SECTIONS.contains(name) && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                && WARNED_SECTIONS.add(name)) {
            TeslaMaps.LOGGER.warn("section(\"{}\") is not a known category - the gate never matches", name);
        }
        return sectionEnabled.getOrDefault(name, Boolean.TRUE);
    }

    // --- /tmap customize --- keyed by the Hypixel per-item uuid, so they follow the item, not the slot
    @Global
    public java.util.Map<String, com.teslamaps.features.customize.ArmorTrimId> customArmorTrims = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, Integer> customDyeColors = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, com.teslamaps.features.customize.AnimatedDye> customAnimatedDyes = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, String> customHelmetTextures = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, String> customAnimatedHelmetTextures = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, net.minecraft.network.chat.Component> customItemNames = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, Boolean> customGlint = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, net.minecraft.resources.Identifier> customItemModel = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, net.minecraft.resources.Identifier> customArmorModel = new java.util.HashMap<>();
    public boolean showCustomizeButton = true;   // Small button in the inventory that opens /tmap customize

    public String keybindChatMessage = "";

    public static class Shortcut {
        public String alias = "";     // typed without the leading slash, e.g. "pk"
        public String command = "";   // expansion without leading slash, e.g. "party kick"
        public Shortcut() {}
        public Shortcut(String alias, String command) { this.alias = alias; this.command = command; }
    }
    @Global
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
    public boolean noCreeperHurtSound = true; // Mute creeper hurt sounds
    public boolean soundDebug = false;       // Print every incoming sound (id/vol/pitch) to chat
    public boolean muteBonzoStaff = false;   // Mute Bonzo's Staff impact (firework blast/twinkle sounds)
    public boolean muteLightning = false;    // Mute lightning/thunder sounds (Storm phase)
    public java.util.List<String> mutedSounds = new java.util.ArrayList<>(); // sound ids to mute ("id" or "id@pitch")
    public boolean noArrows = true;          // Hide arrow entities
    public boolean noStuckArrows = true;     // Hide arrows stuck in entities
    public boolean noWaterOverlay = true;    // Hide water overlay on screen
    public boolean noVignette = true;        // Disable vignette (darkness around screen edges)
    public boolean noDeathAnimation = true;  // Hide death animation from entities dying
    public boolean hideInventoryEffects = true;  // Hide potion effects in inventory
    public boolean noEffects = true;              // Hide status effect icons on HUD (top right)
    public boolean hideArmorBar = false;     // Hide vanilla armor bar
    public boolean hideHungerBar = false;    // Hide vanilla hunger bar
    public boolean actionBarFilter = false;  // Master toggle for the action bar segment filter
    public boolean abHideHealth = false;     // Action bar: health segment
    public boolean abHideDefense = false;    // Action bar: defense segment
    public boolean abHideMana = false;       // Action bar: mana segment
    public boolean abHideAbilityMana = false;// Action bar: "-100 Mana (Ability)"
    public boolean abHideVitality = false;   // Action bar: vitality segment
    public boolean abHideOverflowMana = false;// Action bar: overflow mana
    public boolean abHideArmorStacks = false;// Action bar: armor stacks (10 Ѫ)
    public boolean abHideLocation = false;   // Action bar: ⏣ area name
    public boolean abHideRiftTime = false;   // Action bar: rift time segment
    public boolean abHideDrillFuel = false;  // Action bar: drill fuel segment
    public boolean abHideSecrets = false;    // Action bar: dungeon secrets segment
    public boolean abHideSkillXp = false;    // Action bar: "+12.5 Farming (1/2)"
    public java.util.List<String> actionBarHidden = new java.util.ArrayList<>(); // custom substrings to drop
    public boolean noLightning = true;       // Remove lightning bolts
    public boolean noBlockBreaking = true;   // Remove block breaking particles
    public boolean noFallingBlocks = true;   // Hide falling block entities
    public boolean fullbright = true;        // Maximum brightness (see in the dark)
    @Global
    public int dumpGuiKey = -1;                     // Dumps the open container to gui_dump.txt
    @Global
    public int pestRoutineKey = -1;                  // Advances the pest routine (GLFW code, -1 = unbound)
    public String pestChanceLoadout = "Loadout 7";  // Equipped after the cooldown message, to farm for spawns
    public String roseDragLoadout = "Loadout 6";    // Equipped once the pests have spawned
    public float pestWarpDelay = 1.5f;              // Seconds to block /warp garden after the previous step
    public boolean sprayRoutine = false;            // Sprayonator running out queues a trip back
    public boolean pestTimer = true;                // Time each pest round and report a PB
    public float pestPersonalBest = 0;              // Best round in seconds, 0 = none yet
    public boolean hudDragInInventory = true;       // Drag and scale garden panels from the inventory
    public boolean farmProfit = true;               // Farming + pest income panel
    public int farmProfitX = 10;
    public int farmProfitY = 200;
    public float farmProfitScale = 1.0f;
    public int farmProfitRows = 4;                  // Items listed in the profit panel
    public boolean farmingSensitivity = false;      // Drop sensitivity while holding a farming tool
    public float farmingSensitivityValue = 0.0f;    // Sensitivity to use while it applies
    public boolean pestOverlay = true;              // Cooldown / alive / infested plots panel
    public int pestOverlayX = 10;
    public int pestOverlayY = 60;
    public float pestOverlayScale = 1.0f;
    public boolean pestCustomCooldown = false;      // Count the cooldown ourselves instead of trusting tab
    public int pestCustomCooldownSeconds = 129;     // How long your cooldown actually is, in seconds
    public int pestCooldownWarnAt = 5;              // Warn this many seconds before spawns are ready
    public boolean pestCooldownTitle = false;       // Title when the spawn cooldown is nearly up
    public boolean pestCooldownSound = false;
    public String pestCooldownSoundName = "NOTE_CHIME";
    public float pestCooldownVolume = 1.0f;
    public boolean pestCooldownArmsRoutine = false; // Cooldown ready arms the pest routine
    public int farmIdleSeconds = 150;               // Quiet time before the profit session counts as a break
    public boolean pestGearReminder = false;        // Keep warning while the pest chance gear is off and spawn is due
    public boolean pestTpReminder = false;          // Keep warning while pests are alive on another plot

    public boolean farmBar = true;                  // Lane progress bar while farming the garden
    public boolean farmLaneSet = false;             // Whether both lane points are set
    public double farmX1 = 0, farmZ1 = 0;
    public double farmX2 = 0, farmZ2 = 0;
    public float farmWarnDistance = 5.0f;           // Warn this many blocks before the lane end
    public String farmWarnSound = "NOTE_PLING";
    public float farmWarnVolume = 0.8f;
    public int farmBarX = 10;
    public int farmBarY = 150;
    public float farmBarScale = 1.0f;

    public String vacuumBagItem = "Empty Vacuum Bag";  // Slot to click in the Pesthunter menu
    public int philipWarnSeconds = 60;              // Warn this long before the Philip buff runs out
    public boolean philipArmsRoutine = false;       // Buff running out queues the Philip refill
    public boolean pestBuffWarning = false;         // Title + sound when the pest fortune buff expires
    public String pestBuffSound = "NOTE_PLING";     // Sound for that warning
    public float pestBuffVolume = 1.5f;
    public boolean pestResumeAttack = false;        // After closing the loadout, tap the attack key
    @Global
    public int pestAttackKey = -1;                  // Which key to tap (-1 = the vanilla attack bind)
    public boolean pestRoutineHud = false;          // Show the next routine step on screen
    public int pestRoutineX = 10;
    public int pestRoutineY = 120;
    public float pestRoutineScale = 1.0f;

    public boolean charmedVisitorList = true;   // List of visitors charmed with the Gift Vinyl Set
    public int charmedVisitorX = 180;
    public int charmedVisitorY = 200;
    public float charmedVisitorScale = 1.0f;
    public List<String> charmedVisitorNames = new ArrayList<>();

    public boolean huntingBoxValue = true;      // Bazaar value of the shards in the hunting box
    public boolean shardOverlay = true;         // What is still missing to max every attribute
    public String shardSort = "Price to Max";   // ShardOverlay.SORTS
    public String shardPriceSource = "Instant Buy";  // ShardOverlay.PRICE_SOURCES
    public boolean shardHideMaxed = true;       // Skip tier 10 shards in the list
    public boolean shardOnlyMissing = false;    // Only shards that are not unlocked yet
    public boolean shardIncludeBox = true;      // Count what is already in the hunting box
    public boolean shardIncludeOrders = true;   // Count what is already on order in the bazaar
    public boolean shardTierStack = true;       // Tier as the stack size in the attribute menu
    public boolean shardHighlightDisabled = true;   // Red tint over switched-off attributes
    @Global
    public java.util.Map<String, Integer> shardsSyphoned = new java.util.HashMap<>(); // bazaar id -> total syphoned
    @Global
    public java.util.Map<String, Integer> shardsInBox = new java.util.HashMap<>();    // bazaar id -> amount in the box
    @Global
    public java.util.Map<String, Integer> shardOrders = new java.util.HashMap<>();    // bazaar id -> still owed by open buy orders

    public boolean gardenPlotsOverlay = true;   // Plot teleport grid beside the inventory in the Garden
    public boolean gardenPlotsCloseOnClick = true;  // Close the inventory after a plot teleport
    public int gardenPlotsX = 0;                // Panel offset from the inventory's right edge
    public int gardenPlotsY = 0;

    public boolean hideClouds = true;        // Skip the clouds render pass
    public boolean noFog = true;             // Disable fog rendering (boss/water/blindness fog)
    public boolean noHurtCam = true;         // Disable damage camera tilt
    public boolean noViewBobbing = true;    // Disable view bobbing
    public boolean noXpOrbs = true;          // Hide experience orb entities
    public boolean packetHiders = true;      // Drop the spawn packet for hidden decorative entities instead of skipping the render
    public boolean noEnchantGlint = true;   // Remove enchantment glint/shimmer on items
    public boolean zoomEnabled = true;       // Enable hold-key zoom
    @Global
    public int zoomKey = -1;                 // Zoom keybind (GLFW code, -1 = unbound)
    public int zoomFov = 30;                 // FOV while zoomed (1-110)
    public boolean zoomSmooth = true;        // Ease the FOV in and out instead of snapping
    public float zoomSmoothSpeed = 14f;      // Higher = snappier (1-30)

    public boolean motionBlur = false;       // Blend the previous frame over the current one
    public float motionBlurStrength = 0.35f; // How much of the old frame survives (0-0.95)
    public boolean motionBlurWorldOnly = true;   // Stop blurring while a menu is open
    public boolean smoothEntities = false;   // Stretch entity interpolation past vanilla's 3 ticks
    public int smoothEntitySteps = 4;        // Ticks other entities take to reach a new position

    // cosmetic and client side only, so it follows the player and not the profile
    @Global
    public boolean capeEnabled = false;      // Wear a custom cape (only you see it)
    @Global
    public String capeSelected = "";         // Cape id, see features/capes/CapeManager
    @Global
    public float capeWaviness = 0.5f;        // Cloth wiggle: 0 rigid vanilla board, 1 full flag
    // supabase project url + anon key (public by design, RLS guards the data)
    public static final String CAPE_SERVER_URL = "https://sceaxjzzflqcihrcchki.supabase.co";
    public static final String CAPE_SERVER_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNjZWF4anp6ZmxxY2locmNjaGtpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczMDQyNDUsImV4cCI6MjEwMjg4MDI0NX0.oAOvbrdPZlIK6NSDzdfVDC6E4Cshxse4we6AJk4Sba8";
    @Global
    public String capeServerUrl = CAPE_SERVER_URL;
    @Global
    public String capeServerKey = CAPE_SERVER_KEY;


    // deliberately NOT @Global: it restyles every vanilla button, so a profile has to be able
    // to switch it off without dragging the choice into the other profiles
    public boolean modernVanillaButtons = true;  // glass style on vanilla buttons (pause menu, options, ...)
    @Global
    public boolean customTitleScreen = true;      // Replace the vanilla main menu
    public boolean customLoadingScreen = true;   // Same background on the loading and connect screens
    @Global
    public String titleBackground = "Liquid Metal";   // Animated background preset
    @Global
    public float titleBackgroundSpeed = 0.61f;        // Menu animation speed (0.05-2.0)
    @Global
    public String titleBackgroundColor = "3E0E95"; // Tint applied to the background shader
    @Global
    public boolean titleAccentAuto = true;           // Pick the brand title color per background
    @Global
    public String titleAccentColor = "FF5AC8FA";     // Brand title color when auto is off

    @Global
    public float titleImageDim = 0.25f;           // Darkening over image backgrounds, keeps text readable
    public boolean customSkybox = false;          // Replace the in-world sky with a shader
    @Global
    public boolean skyboxOnlySkyblock = true;     // Only while on Skyblock
    @Global
    public String skyboxPreset = "Shimmer";       // Which skybox shader
    @Global
    public String skyboxColor = "FFFFFFFF";       // Tint applied to the skybox
    @Global
    public float skyboxScale = 1.0f;              // Pattern scale (0.2-3.0)
    @Global
    public float skyboxSpeed = 0.3f;              // Skybox animation speed (0.05-2.0)

    public boolean autoCloseChests = false;       // Automatically close chests after opening
    public int autoCloseDelay = 7;                // Delay in ticks before auto-closing (0-20)
    public int autoCloseRandomization = 3;        // Random ticks added to delay (0-10)
    public boolean closeChestOnInput = false;     // Close chest when any key is pressed

    public boolean slayerOnlyOwnBoss = false;  // Only count/track your own boss
    public boolean slayerBossESP = false;      // Glow on boss (color based on phase)
    public boolean slayerMinibossESP = false;  // Glow on minibosses (Burningsoul=red, Kindleheart=white)
    public boolean slayerMinibossTracer = false; // Tracer lines to slayer minibosses
    public boolean slayerAlerts = true;        // Title alerts: miniboss spawn, cocoon, boss spawned
    public boolean bestiaryTracker = true;     // Bestiary HUD: kills/hour and tier ETA out of the tab list
    public int bestiaryTrackerX = 10;          // X position of bestiary HUD
    public int bestiaryTrackerY = 200;         // Y position of bestiary HUD
    public float bestiaryTrackerScale = 1.0f;  // Scale of bestiary HUD
    @Global
    public java.util.List<String> bestiaryTracked = new java.util.ArrayList<>(); // mobs picked with /tmap be add
    @Global
    public java.util.Map<String, Long> bestiaryKillsToMax = new java.util.HashMap<>(); // read from the Bestiary menu

    public boolean skillTracker = true;        // Tracker HUD: skill XP/hour plus the rates that fit the skill
    public int skillTrackerX = 10;             // X position of tracker HUD
    public int skillTrackerY = 120;            // Y position of tracker HUD
    public int fishingCatchSource = 0;         // what counts as a catch: 0 = auto, 1 = sea creatures + trophies (chat), 2 = every catch (xp)
    public float skillTrackerScale = 1.0f;     // Scale of skill tracker HUD
    public boolean skillTrackerHidden = false; // keep tracking, but only show the panel in the inventory
    public boolean fixDoubleClicks = true;     // Drop the duplicate use-on-block Hypixel sends for blaze daggers / autopet rods
    public boolean skillLevelStackSize = true;  // draw the skill level as the stack size in the skills menu
    public boolean skillAvgOverflow = true;     // add the overflow skill average to the "Your Skills" tooltip
    public boolean skillOverflow = true;       // Overflow levels in the skill menu and on level up
    public java.util.Map<String, Long> skillTotalXp = new java.util.HashMap<>(); // total xp per skill, read from the skill menu

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

    public boolean autoWish = false;            // Auto use healer wish at key boss moments


    public boolean solveBlaze = true;              // Show which blaze to hit
    public boolean blazeDoneMessage = true;        // Send "/pc Blaze Done!" when last blaze killed
    public boolean modernBlazeSolver = true;      // Fill the solver boxes + hide the vanilla blaze mobs (cleaner look)
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
    public boolean tpMazeTracer = true;            // Draw a tracer line to the suggested next portal
    public boolean tpMazePrioritizeDiagonal = true; // Prefer diagonal portals when suggesting the next one
    public boolean terminalSolver = false;            // Normal-mode solver: highlight correct slots in the vanilla terminal GUI
    public boolean lastBreathSound = true;         // Rising charge sound while pulling a Last Breath
    public float lastBreathVolume = 1.0f;           // Last Breath sound volume (0-5)
    public int lastBreathThreshold = 9;             // Tick threshold to swap to the second sound (0 always, 21 never)
    public boolean splitsSendAllOnEnd = true;      // Print a recap of all splits + total at run end
    public boolean partyDuplicateAlert = true;     // Alert when the party has duplicate dungeon classes
    public boolean partyDuplicateSound = true;      // Play a sound on the duplicate-class alert
    public boolean partyDuplicateMessage = true;   // Announce duplicate classes to party chat
    public boolean hideCheapCoins = true;          // Hide the spinning coin item entities from coin talismans
    public boolean warpCooldown = true;            // 30s warp/re-queue cooldown timer after a floor starts
    public boolean witherShieldTimer = false;       // Hyperion/wither-blade Wither Impact cooldown timer
    public boolean tacticalInsertionTimer = false;  // 3s Tactical Insertion timer on right-click
    public boolean invincibilityTimer = true;       // Bonzo / Spirit / Phoenix immunity + cooldown block
    public boolean invincibilityAnnounce = true;    // Announce mask/pet procs to party chat ("Bonzo Procced!")
    public boolean spiritPetReminder = true;        // Remind to swap off the Spirit pet when the boss starts
    public boolean purplePadTimer = true;          // F7 purple-pad countdown after Storm's call
    public boolean necronDropTimer = false;        // F7 Necron drop countdown ("your journey ends now")
    public boolean goldorTickTimer = false;        // F7 P3 device/terminal 60-tick pulse (+104t start timer)
    public boolean stormPadTimer = false;          // F7 Storm pad 20-tick pulse
    public boolean lightningTimer = false;         // F7 Storm lightning countdown (560t)
    public boolean secretTickTimer = false;        // Secret spawn tick (20-tick cycle, clear only)
    public String tickTimerFormat = "Seconds";     // Seconds, Ticks, Both — display format for tick timers
    public boolean colorPortal = false;             // Recolor the blood portal by score (red/gold/green)
    public boolean highlightTeammates = false;      // ESP box around party members (through walls), colored by class
    public boolean relicTimer = true;              // M7 relic spawn countdown after Necron's line
    public boolean creeperBeamsDing = true;        // Ding when toggling a lantern in Creeper Beams
    public int dungeonTimersX = 404;                 // Dungeon-timers block (warp/purple/leaps/ss/relic/death) X
    public int dungeonTimersY = 60;                 // Dungeon-timers block Y
    public float dungeonTimersScale = 1.0f;         // Dungeon-timers block scale
    public int invincibilityX = 404;                 // Invincibility block (Bonzo/Spirit/Phoenix) X
    public int invincibilityY = 110;                // Invincibility block Y
    public float invincibilityScale = 1.0f;         // Invincibility block scale
    public boolean disableWorldLoadingScreen = true; // Skip the world loading / downloading-terrain screen
    public boolean noReloadScreen = true;          // Skip the resource/texture reload (dirt) overlay (F3+T etc.)
    public boolean blockOverlay = true;            // Outline the block you're looking at
    public String blockOverlayColor = "FFFFFF";     // Block overlay outline colour
    public boolean chatWaypoint = true;            // Render a waypoint beam from coords in chat

    // Ordered Waypoints (routes outside dungeons, Coleweight/Skytils format)
    public boolean orderedWaypoints = false;        // Master toggle
    public String orderedColorCurrent = "55FF55";   // Current waypoint
    public String orderedColorPrevious = "5555FF";  // Previous waypoint
    public String orderedColorNext = "FFFF55";      // Upcoming waypoints
    public String orderedColorAll = "00FF00";       // Colour used in show-all mode
    public String orderedColorSetup = "FF0000";     // Extra waypoints shown by setup mode
    public String orderedColorTraceLine = "55FF55"; // Trace line to the next waypoint
    public String orderedColorSetupLine = "FF0000"; // Setup-mode line of sight
    public int orderedNextCount = 2;                // How many waypoints ahead to render
    public float orderedOutlineThickness = 2.0f;    // Box outline thickness
    public boolean orderedFillBlock = false;        // Filled box instead of outline
    public float orderedWaypointRange = 3.0f;       // How close counts as reached
    public boolean orderedTraceLine = true;         // Draw a line from the crosshair to the next waypoint
    public boolean orderedShowDistance = true;      // Show the distance above a waypoint
    public boolean orderedShowName = true;          // Show the waypoint number
    public boolean orderedShowAll = false;          // Render the whole route at once
    public boolean orderedSetupMode = false;        // Show nearby waypoints + line of sight while building a route
    public float orderedSetupRange = 16.0f;         // Radius for setup mode
    public float orderedSetupLineThickness = 2.0f;  // Setup-mode line thickness
    public boolean orderedSneaking = true;          // Eye height used for the setup line (sneaking = AOTV)
    public boolean orderedAutoSkipForward = false;  // Jump ahead when reaching a later waypoint
    public boolean orderedAutoUnload = false;       // Unload the route on world change
    public boolean orderedAutoLoadByArea = false;   // Load a saved route named like the current area
    public boolean rareDropEffects = true;         // Item animation + particles on RARE DROP! lines
    public boolean rareDungeonDropEffects = true;  // Same for dungeon chest rewards
    public boolean rareDyeDropEffects = true;      // Same for your own dye drops
    public boolean rareDropText = true;            // Drop line + item name on screen during the animation
    public boolean rareDropPrice = true;           // Append what the drop is worth to that line
    public boolean showSelectedPet = true;         // Highlight the spawned pet in the Pets menu
    public boolean simonSaysProgress = true;       // Show Simon Says progress (X/Y) in the timer HUD
    public boolean customHypeSound = false;         // Replace the wither-blade explosion sound
    public String customHypeSoundType = "NOTE_IRON_XYLO"; // which sound replaces the wither-blade explosion
    public float customHypeSoundVolume = 1.0f;     // Volume for the custom hype sound (0.0 - 20.0)
    public float customHypeSoundPitch = 1.0f;      // Pitch for the custom hype sound (0.5 - 2.0)
    public boolean wardrobeKeybinds = true;        // Hotbar keys 1-9 switch wardrobe slots in the Wardrobe GUI
    public boolean leapCounter = false;            // F7: count teammates standing at your spot
    public boolean bossbarHealth = true;           // Append boss HP numbers to the bossbar name (Watcher/Thorn/F7)

    public boolean enchantColors = true;           // Recolor enchantments in item lore by level
    public boolean enchantLevelsAsNumbers = false; // Show enchant levels as numbers instead of roman numerals
    public boolean enchantBoldMax = true;          // Bold max-level enchants
    public boolean enchantChromaMax = true;        // moving rainbow on max-level enchants
    public boolean enchantChromaBold = true;       // bold the chroma max-level enchants
    // Chroma look, same knobs and defaults as SkyHanni's shader
    public float chromaSize = 30f;                 // gui units per full colour cycle
    public float chromaSpeed = 6f;                 // hue per tick = speed / 360
    public float chromaSaturation = 0.75f;
    public boolean chromaForward = true;           // true = x-y slant, false = x+y       // moving rainbow on max-level enchants
    public String enchantColorMax = "FFAA00";      // Max level (gold), ignored while chroma is on
    public String enchantColorHigh = "FFAA00";     // Above good level (gold)
    public String enchantColorNormal = "00AAAA";   // Good level (cyan)
    public String enchantColorBad = "AAAAAA";      // Below good level (gray)
    public boolean translucentLava = false;        // render lava with water textures + tint
    public String colorTranslucentLava = "3F76E4";
    public boolean tintedWater = false;            // custom tint on water
    public String colorTintedWater = "3F76E4";
    public boolean hotspotRadar = true;            // guess the hotspot from the radar particles
    public boolean hotspotLine = true;             // tracer from the crosshair to the guess
    public boolean hotspotBeam = false;            // beacon beam at the guess
    public String colorHotspot = "#FF55FF";
    public boolean hotspotGoneWarning = false;     // warn when the whirlpool you fish at stops
    public boolean hotspotGoneTitle = true;
    public String hotspotGoneSound = "NOTE_PLING";
    public float hotspotGoneVolume = 1.0f;         // 0 = silent
    public boolean explodingFrogESP = false;       // box the lily pad display an exploding frog leaves
    public boolean explodingFrogTracer = false;    // tracer to that lily pad
    public boolean explodingFrogName = true;       // "Exploding Frog" label over the lily pad
    public String colorExplodingFrog = "#55FF55";
    public boolean hookDisplay = true;             // Hypixel's bite countdown, big on the HUD
    public boolean hookHideArmorStand = true;      // hide Hypixel's own tiny label over the bobber
    public String hookAlertText = "&c&lPULL";      // replaces Hypixel's "!!!"
    public String hookAlertSound = "NOTE_PLING";   // plays once when the hook is ready
    public float hookAlertVolume = 0f;             // 0 = silent
    public float hookAlertPitch = 2.0f;
    public int hookDisplayX = 200;
    public int hookDisplayY = 120;
    public float hookDisplayScale = 3.0f;
    public boolean bobberTimer = false;            // time the fishing bobber has been out
    public boolean bobberTimerOnLiquid = true;     // start on the liquid touch instead of the cast
    public int bobberTimerX = 10;
    public int bobberTimerY = 10;
    public float bobberTimerScale = 1.0f;

    public boolean gfsQueued = true;               // space /gfs commands out so Hypixel does not drop them
    public int gfsDefaultAmount = 1;               // amount used when /gfs is called without one
    public boolean gfsSuggestions = true;          // tab-complete /gfs with the sack contents
    public boolean gfsBazaar = false;              // offer the missing rest from the bazaar
    public boolean gfsSuperCraft = true;           // clickable grab-from-sacks after supercrafting
    @Global
    public int gfsKeybind = -1;                    // key over a slot to pull that item from the sacks

    public boolean pageKeybinds = true;            // A/D click Previous/Next Page, W/S Scroll Up/Down in any GUI
    public int[] wardrobeKeys = {-1, -1, -1, -1, -1, -1, -1, -1, -1}; // custom per-slot wardrobe keys (-1 = unbound)
    // Loadouts hold 12 per page, the wardrobe 9 - sharing one 9-key array left 10-12 unreachable
    public int[] loadoutKeys = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    public int wardrobeNextPageKey = -1;
    public int wardrobePrevPageKey = -1;
    public boolean signEnterKey = true;            // Enter confirms/closes a sign edit screen
    public boolean noCursorReset = true;           // keep cursor position when navigating between GUIs
    public float noCursorResetHold = 2f;           // seconds the saved cursor position stays valid
    public boolean autoCopyScreenshot = true;      // copy taken screenshots to the system clipboard
    public boolean screenshotChatActions = true;    // clickable Copy/Browse/Delete under the save message
    public boolean screenshotHideVanillaMessage = true; // hide "Saved screenshot as ..."
    public boolean imageShareReceive = true;       // turn shared links in chat into a click-to-view line
    public String imageShareExpiry = "24h";        // 1h / 12h / 24h / 72h / permanent
    public int imageShareChatLines = 12;            // chat rows reserved for an inline image
    @Global
    public int imagePeekKey = -1;                  // hold to blow up the newest shared image
    @Global
    public java.util.List<String> imageShareBlocked = new java.util.ArrayList<>(); // senders whose images are ignored
    public boolean ownNameTag = true;             // render your own player nametag in third person
    public boolean searchbar = true;               // type to search/highlight items in any container
    public boolean scrollableTooltip = true;       // scroll long item tooltips with the mouse wheel
    public boolean compactorPreview = true;        // list personal compactor/deletor contents in the tooltip
    public boolean estimatedValue = true;          // show estimated item value (bazaar) in tooltips
    public boolean itemValueFixedPos = false;       // drag it in /tmap gui instead of pinning it to the container
    public int itemValueX = 5;
    public int itemValueY = 60;
    public float itemValueScale = 1.0f;
    public boolean itemValueGui = false;            // show a value-breakdown panel beside the inventory when hovering an item
    public int itemValueHoverDelay = 500;           // ms on the same slot before the first breakdown of an inventory shows
    public int itemValueEnchantmentsCap = 7;        // only list the top # most expensive enchantments
    public int itemValueStarMaterialCap = 3;        // only list the top # most expensive star materials
    public boolean itemValueExactPrice = false;     // exact total instead of the compact number
    public boolean itemValueIgnoreHelmetSkins = false; // leave helmet skins out of the total
    public boolean itemValueIgnoreArmorDyes = false;   // leave armor dyes out of the total
    public boolean itemValueIgnoreRunes = false;       // leave runes out of the total
    public String itemValuePriceSource = "BZ Instant Sell"; // BZ Instant Buy / BZ Instant Sell / Lowest BIN / NPC Sell
    public boolean itemValueShowBin = true;         // extra "Lowest BIN" line in the value breakdown
    public boolean containerValue = false;          // show total container value + top 5 items in a side panel
    public boolean backpackPreview = true;         // cache + show backpack/ender chest contents on hover (data in storage.json)
    public boolean itemSearchIndex = true;          // remember opened containers so /tmap search can find items anywhere
    @Global
    public int itemSearchKey = 79;                  // opens /tmap search (default O), -1 = unbound
    public boolean customStorageOverlay = false;    // replace the vanilla "Storage" menu with a custom all-in-one view
    public boolean storageShowNames = false;         // show page/backpack name headers in the custom storage view
    public float storageOverlayScale = 1.0f;        // overall size multiplier for the custom storage overlay (0.5 - 1.5)
    public boolean critterESP = false;              // Torrhus Canyon critter ESP (master)
    public boolean critterFilled = true;            // also fill the critter boxes
    public boolean critterTracers = false;          // tracer to every boxed critter
    public boolean critterBee = true;               // Beeheemoth & Honeybuzz (Bee)
    public boolean critterBlueJay = true;           // Blue Jay (Parrot)
    public boolean critterBunbun = true;            // Bunbun (Rabbit)
    public boolean critterDrybark = true;           // Drybark (Creaking)
    public boolean critterFirefox = true;           // Firefox (Fox)
    public boolean critterGrizzly = true;           // Grizzly Bear (by name)
    public boolean critterGroundhog = true;         // Groundhog (Hoglin)
    public boolean critterHideonsun = true;         // Hideonsun (Shulker)
    public boolean critterMountainGoat = true;      // Mountain Goat (Goat)
    public boolean critterSepialot = true;          // Sepialot (Axolotl)
    public boolean critterDustybit = true;          // Dustybit (Frog)
    public boolean critterPuck = true;              // Puck (Vex)
    public String colorCritterBee = "#FFFF55";
    public String colorCritterBlueJay = "#5599FF";
    public String colorCritterBunbun = "#2A8B7C";
    public String colorCritterDrybark = "#A08050";
    public String colorCritterFirefox = "#FF7F11";
    public String colorCritterGrizzly = "#8A7A6A";
    public String colorCritterGroundhog = "#CC5500";
    public String colorCritterHideonsun = "#AA66CC";
    public String colorCritterMountainGoat = "#D8D8D8";
    public String colorCritterSepialot = "#FF09F8";
    public String colorCritterDustybit = "#55DD55";
    public String colorCritterPuck = "#FF1313";
    public boolean critterSpotESP = false;           // floor spots (item_display clusters holding string)
    public String colorCritterSpot = "#55FFFF";

    // Critter Safari
    public boolean safariTracker = true;            // parse the run from chat
    public boolean safariHud = true;                // draw the run HUD
    public int safariHudX = 5;
    public int safariHudY = 120;
    public float safariHudScale = 1.0f;
    public boolean safariChecklist = true;          // per-biome critter checklist under the HUD
    public String safariChecklistBiome = "Auto";    // Auto = the biome you are standing in
    public boolean safariChecklistIcons = true;     // check/cross icons, off = colour the name only
    public boolean safariCritterESP = false;         // box critters, coloured by shard rarity
    public boolean safariNames = false;             // name label over every critter, not just the rare ones
    public boolean safariTracers = false;           // tracer to labelled critters
    public boolean safariFilled = true;             // also fill the safari boxes
    public boolean safariOnlyMissing = false;       // only box critters nobody caught yet this run
    public boolean safariHideyhoTracer = true;      // tracer to Hideyho even with the general ones off
    public boolean safariNestESP = false;            // box the bee nests that spawn Honeybugs
    public boolean safariRecatchSpot = true;        // mark where an escaped critter comes back
    public boolean safariWallMarkers = false;       // mark the intact Snoozle / Troodon walls
    public boolean safariSparklingAlert = true;     // alert on the WAX_ON particles of a sparkling critter
    public boolean safariSparklingParty = false;    // also post the sparkling coords to party chat
    public boolean safariSparkleForce = true;       // render WAX_ON sparkles at any distance/particle setting
    public boolean safariAnnounceBiomeDone = true;  // alert when every critter of a biome is caught
    public boolean safariAnnounceUniqueDone = true; // alert when the whole dex is complete
    public boolean safariWarnNotFull = true;        // warn shortly after entry when fewer than 4 players are in
    public boolean safariShareTimeParty = false;    // post those completion times to party chat
    public boolean safariAutoHideyho = false;       // click the [Sure] the server put on the Hideyho question
    public int safariAutoHideyhoMinMs = 300;        // never answer faster than this
    public int safariAutoHideyhoJitterMs = 400;     // extra 0..n ms on top, so the delay varies
    public boolean safariHideCold = false;          // drop the frost overlay in the Icy Biome
    public boolean safariShowPb = true;             // PB line in the run HUD
    public boolean safariShowHotspot = false;       // "Hotspot: <Biome>" line
    public boolean safariShowHeadStart = false;     // "Head Start: <Critter>" line
    public float safariAlertVolume = 1.0f;          // 0 = silent
    public String colorSafariSparkling = "#FF55FF";
    public String colorSafariNest = "#FFAA00";
    public String colorSafariRecatch = "#FFFF55";
    public String colorSafariWall = "#FF8800";
    // Foraging (Galatea / Moonglade Marsh + Torrhus Canyon)
    public boolean foragingMutePhantoms = false;    // silence Phantoms in Moonglade Marsh
    public boolean foragingMuteFusion = false;      // silence the Fusion Machine in Moonglade Marsh
    public boolean foragingMuteTreeBreak = false;   // silence the tree-break (creaking death) sound
    public boolean foragingMuteTreeBreakGalatea = false; // also mute it on the foraging islands
    public boolean foragingHideLottery = false;     // hide Lottery/Beekeeper buff messages off the foraging islands
    public boolean foragingCompactGifts = true;     // squash the whole TREE GIFT block into one line
    public boolean foragingHideSweepDetails = false; // hide the Sweep Details / Toughness / penalty lines
    public boolean foragingCleanTreeView = false;   // hide the floating blocks from chopping
    public boolean foragingHideRuneEffects = false; // hide the floating blocks from rune effects
    public boolean umbrellaWarning = false;         // alert before and when the Umberella runs out
    public int umbrellaWarnBefore = 30;             // seconds of warning, 0 = only on despawn
    public boolean umbrellaWarningTitle = false;
    public String umbrellaWarningSound = "NOTE_PLING";
    public float umbrellaWarningVolume = 1.0f;      // 0 = silent
    public boolean deployableOverlay = false;       // HUD with the time left on active deployables
    public boolean deployableHighestTierOnly = false; // hide a Radiant while an Overflux is up
    public boolean deployableFlux = true;           // power orbs
    public boolean deployableLantern = true;
    public boolean deployableUmbrella = true;
    public boolean deployableBlackHole = true;
    public boolean deployableFlare = true;          // warning/alert/sos flares, matched by head texture
    // an overlay with every type filtered out shows nothing, which reads as broken
    public boolean deployableTypesRepaired = false;
    public boolean deployableOthers = true;         // flares, Willow, anything else with a timer nametag
    public int deployableX = 5;
    public int deployableY = 240;
    public float deployableScale = 1.0f;
    public boolean latherReminder = false;          // remind when the honeycomb lather ran out (12 min)
    public boolean latherReminderTitle = true;      // also show a title
    public String latherReminderSound = "NOTE_PLING";
    public float latherReminderVolume = 1.0f;       // 0 = silent
    public boolean latherOverlay = false;           // HUD with the lather time per tree
    public boolean latherOverlayForagingOnly = true; // only on Moonglade Marsh / Torrhus Canyon
    public boolean latherOverlayOnlyWhenActive = false; // hide it while no tree is lathered
    public int latherOverlayX = 5;
    public int latherOverlayY = 200;
    public float latherOverlayScale = 1.0f;
    public boolean honeyhiveReminder = false;       // remind when honeyhives are lootable again
    public boolean honeyhiveQueenBeeTitle = true;   // title on a Queen Bee instant refill
    public boolean honeyhiveRemindOutside = false;  // also remind outside Torrhus Canyon
    @Global
    public long honeyhiveReadyAt = 0L;              // runtime: when the hives are lootable again
    public boolean hotfHighlight = true;            // colour the perks in the Heart of the Forest
    public boolean hotfLevelStack = true;           // perk level as a stack size
    public boolean hotfTokenStack = true;           // unused tokens on the heart
    public boolean hotfPerkDisplay = false;         // HUD with the current Lottery/Beekeeper perk
    public boolean hotfPerkDisplayForagingOnly = true; // only draw it on the foraging islands
    public int hotfPerkDisplayX = 5;
    public int hotfPerkDisplayY = 160;
    public float hotfPerkDisplayScale = 1.0f;
    public boolean lassoDisplay = true;             // lasso progress bar / REEL prompt on the HUD
    public String lassoReelText = "&e&l          REEL          ";
    public String lassoReelSound = "NOTE_PLING";    // plays once when REEL shows up
    public float lassoReelVolume = 0f;              // 0 = silent
    public float lassoReelPitch = 2.0f;
    public int lassoDisplayX = 200;
    public int lassoDisplayY = 150;
    public float lassoDisplayScale = 2.0f;
    @Global
    public String hotfLotteryPerk = "";             // runtime: last seen Lottery perk
    @Global
    public String hotfBeekeeperPerk = "";           // runtime: last seen Beekeeper perk
    public boolean greenhousePlanner = true;        // ghost overlay for a pasted greenhouse layout
    @Global
    public String greenhouseCode = "";              // last pasted planner code
    public int greenhousePlot = 0;                  // which plot of the code to show
    public int greenhousePhase = 0;                 // 0 auto (floor until done, then plants), 1 floor, 2 plants
    public boolean greenhouseManual = false;        // use the corner1/corner2 anchor instead of scanning
    public int greenhouseAnchorX = 0;               // manual plot min corner, y = floor level
    public int greenhouseAnchorY = 0;
    public int greenhouseAnchorZ = 0;
    @Global
    public java.util.Map<String, String> greenhouseSoil = new java.util.HashMap<>(); // plant id -> floor block id
    @Global
    public java.util.Map<String, String> greenhousePlants = new java.util.HashMap<>(); // head identity -> plant id
    @Global
    public java.util.Map<String, String> greenhouseRotations = new java.util.HashMap<>(); // "x,y,z" -> "rot,mirror"
    public int greenhouseRotation = 0;              // 0..3, x90 degrees
    public boolean greenhouseMirror = false;
    public String greenhouseFocus = "";              // plant to highlight, "" = rarest in the layout, "all" = no dimming
    public boolean greenhouseDimOthers = true;       // draw everything but the focus plant faint
    public boolean greenhouseStages = true;          // colour grown focus plants by growth stage
    public int greenhouseStageMax = 0;               // 0 = take the highest stage seen in the plot
    public boolean greenhouseStageInverted = false;  // stage 1 is the ripe one
    public boolean greenhouseLabels = true;         // plant names over the cells
    public boolean greenhouseHideDone = true;       // hide cells that already hold a plant
    public boolean greenhouseThroughWalls = true;
    public float greenhouseAlpha = 0.35f;

    public String colorHotfEnabled = "#55FF55";
    public String colorHotfDisabled = "#FF5555";
    public String colorHotfLocked = "#555555";

    // SkyBlock contests (real-time schedule: Foraging every 20 min, Farming hourly)
    public boolean contestTimer = false;
    public boolean contestShowForaging = true;
    public boolean contestShowFarming = true;
    public boolean contestTimerSkyblockOnly = true;
    public boolean contestTimerButton = true;       // hide/show button in the player inventory
    public int contestTimerX = 5;
    public int contestTimerY = 120;
    public float contestTimerScale = 1.0f;
    public boolean contestAlerts = false;
    public int contestWarnBefore = 60;
    public boolean contestAlertChat = true;
    public boolean contestAlertTitle = false;
    public String contestAlertSound = "NOTE_PLING";
    public float contestAlertVolume = 1.0f;

    public boolean hoveredSlotOutline = true;       // outline the slot under the cursor in every container
    public String colorHoveredSlot = "#FF00FF";     // hovered slot outline color
    public int hoveredSlotThickness = 1;            // outline thickness in pixels
    public float hoveredSlotFill = 0f;              // extra fill over the hovered slot (0 = off)
    public boolean slotLock = true;                // lock inventory slots from being moved/dropped/sold
    @Global
    public int slotLockKey = 76;                    // keybind (over a slot) to toggle its lock
    public String colorSlotLock = "#FF5555";        // locked slot outline color
    @Global
    public java.util.List<Integer> lockedSlots = new java.util.ArrayList<>(); // locked player-inventory slot indices
    public boolean slotBinds = false;               // bind slots together for quick swapping
    @Global
    public int slotBindsKey = -1;                   // keybind (over a slot) to set/remove a bind
    public String colorSlotBinds = "#55FF55";       // line color between bound slots
    public float slotBindsLineWidth = 0.5f;         // line thickness
    public String slotBindsLineDisplay = "Hover";   // Hover / On Hover + Shift / None
    public String slotBindsProfile = "Profile 1";   // active bind profile
    @Global
    public java.util.Map<String, java.util.Map<Integer, Integer>> slotBindsData = new java.util.HashMap<>();
    public boolean solveWaterBoard = true;          // Show solution for Water Board puzzle
    public boolean waterBoardTracers = true;        // Draw tracers to next lever
    public boolean solveIceFill = true;             // Show Ice Fill (F7) path solution
    public boolean iceFillOptimized = true;         // Use optimized (harder/faster) ice fill paths
    public boolean icePathSolver = true;            // Draw the silverfish path in the Ice Path puzzle
    public boolean puzzleTimers = true;            // Report how long each puzzle took to solve
    @Global
    public java.util.Map<String, Double> puzzlePbs = new java.util.HashMap<>(); // per-puzzle personal bests (seconds)
    public String colorIceFill = "55FFFF";          // Ice Fill path color (aqua)
    public boolean witherDragons = true;            // M7 Wither Dragons helper (master toggle)
    public boolean witherDragonTimer = true;        // Spawn countdown timer at each dragon's spawn
    public boolean witherDragonBoxes = true;        // Boxes around spawning/alive dragons
    public boolean witherDragonTitle = true;        // Title when a dragon starts spawning
    public boolean witherDragonMsg = true;          // Chat message when a dragon has spawned
    public int witherDragonTimerStyle = 0;          // 0 = milliseconds, 1 = seconds, 2 = ticks
    public boolean witherDragonTimerSymbol = true;  // Append ms/s/t to the timer
    public boolean witherDragonHealth = true;       // Draw each live dragon's health above it
    public boolean witherDragonConfirm = true;      // "X dragon counts." when the Wither King confirms a kill
    public boolean witherDragonSendTime = true;     // Message with how long a dragon was alive (with PB)
    public boolean witherDragonSendSpray = true;    // Message when a dragon gets ice sprayed
    public boolean witherDragonHud = true;          // Priority-dragon timer as a HUD element
    public int witherDragonHudX = 10;               // Dragon timer HUD X
    public int witherDragonHudY = 100;              // Dragon timer HUD Y
    public float witherDragonHudScale = 1.0f;       // Dragon timer HUD scale
    @Global
    public java.util.Map<String, Float> dragonPbs = new java.util.HashMap<>(); // per-dragon best time alive (seconds)
    public boolean witherDragonPriority = true;      // Split: pick your priority dragon by class+power
    public boolean witherDragonPriorityTracer = true; // Tracer to your priority dragon
    public int witherDragonNormalPower = 0;          // Power needed to split (0-32)
    public int witherDragonEasyPower = 0;            // Power needed to split when Purple + another (0-32)
    public int witherDragonSoloDebuff = 0;           // 0 = Tank solos purple, 1 = Healer solos purple
    public boolean witherDragonSoloDebuffAll = true; // Solo-debuff split applies to all dragons, not just purple
    public boolean witherDragonPaulBuff = false;     // Multiply power by 1.25 (Paul mayor)
    public boolean dragonBoxes = true;             // ESP box around each live wither dragon (by colour)
    public boolean dragonHealth = true;            // Show each dragon's health % above it
    public boolean hideDyingDragons = true;        // Hide a dragon once it's dying (health <= 0)
    public boolean witherHighlight = false;        // Highlight F7/M7 device-phase withers
    public boolean witherHighlightBox = true;       // Filled box (vs outline) for the wither highlight
    public String witherHighlightColor = "00FFFF";  // Wither highlight colour

    public boolean solveSimonSays = false;         // Show button sequence for Simon Says
    public boolean simonSaysBlockWrongClicks = false; // Block clicks on wrong SS buttons (sneak to override)
    public boolean simonSaysAlerts = true;         // SS broke/restart alerts (title + sound)
    public boolean simonSaysAlertChat = true;      // Send "SS Broke!"/"SS Started Again!" to party chat
    public boolean solveArrowAlign = false;        // Show clicks needed for Arrow Align

    public boolean terracottaTimer = true;        // Show spawn timers for F6/M6 terracotta
    public boolean spiritBearTimer = true;        // Show spawn timer for F4/M4 spirit bear
    public int spiritBearX = 300;                  // Spirit bear timer position X (draggable in HUD edit)
    public int spiritBearY = 180;                  // Spirit bear timer position Y
    public float spiritBearScale = 1.0f;           // Spirit bear timer text scale (0.5 - 5.0)
    // M4 visual helpers (ported from BirdAddon)
    public boolean m4Highlight = true;             // Highlight M4 animals/thorn/bear
    public String m4HighlightStyle = "Outline";    // Filled, Outline, Filled Outline
    public boolean m4HideNametags = true;          // Hide the ❤ health nametags in M4 boss
    public boolean m4HlThorn = true;
    public boolean m4ThornDmgFlash = true;         // Flash thorn's box when it takes damage
    public boolean m4HlBear = true;
    public boolean m4BearOnlyMage = false;         // Only show the bear box on Mage
    public boolean m4HlCow = true, m4HlSheep = true, m4HlChicken = true, m4HlBat = true, m4HlWolf = true, m4HlRabbit = true;
    public String m4ColorThorn = "FFFFFF";
    public String m4ColorThornDmg = "FF5555";
    public String m4ColorBear = "AA00AA";
    public String m4ColorCow = "55FFFF";
    public String m4ColorSheep = "FFFF55";
    public String m4ColorChicken = "FF5555";
    public String m4ColorBat = "0000AA";
    public String m4ColorWolf = "FFAA00";
    public String m4ColorRabbit = "00AA00";
    public boolean m4Decoy = true;                 // Highlight the best decoy spot
    public boolean m4Trajectories = true;          // Predicted landing spots of cows/sheep/chickens
    public boolean m4TrajCow = true, m4TrajSheep = true, m4TrajChicken = true;

    public boolean overkillCounter = true;         // F4/M4 Thorn overkill counter HUD
    public boolean overkillHideZero = false;       // Hide per-mob counters that are 0
    public int overkillX = 5;                      // Overkill counter position X (draggable in HUD edit)
    public int overkillY = 140;                    // Overkill counter position Y
    public float overkillScale = 1.0f;             // Overkill counter scale (0.5 - 5.0)
    public boolean kickedTimer = true;             // "Kicked" HUD timer + /pc kicked when kicked while joining
    public boolean kickedPartyMessage = true;      // Auto-send "/pc kicked" when a join kick happens
    public int kickedX = 300;                      // Kicked timer position X (draggable in HUD edit)
    public int kickedY = 100;                      // Kicked timer position Y
    public float kickedScale = 1.0f;               // Kicked timer text scale (0.5 - 5.0)
    public boolean thornStunTimer = true;         // Show how long Thorn is stunned (F4/M4)
    public boolean thornStunHealerOnly = true;     // Only show the Thorn stun timer on healer
    public int thornStunX = 10;                    // Thorn stun timer position X (draggable in HUD edit)
    public int thornStunY = 120;                   // Thorn stun timer position Y
    public float thornStunScale = 1.0f;            // Thorn stun timer text scale (0.5 - 5.0)
    public boolean titleBowMiss = true;           // Title when you miss a Thorn bow shot
    public boolean titleBowPickup = true;         // Title when the Spirit Bow is picked up
    public boolean titleBearEvents = true;        // Titles for bear spawn-start / spawned / killed
    public boolean hideDefaultTitles = true;      // Hide Hypixel's default bow/bear titles in F4/M4 boss

    public String colorBoulder = "55FF55";           // Boulder click highlight (green)
    public String colorQuiz = "55FF55";              // Quiz correct answer (green)
    public String colorTPMazeOne = "55FF55";         // TP Maze single correct (green)
    public String colorTPMazeMultiple = "FFFF55";    // TP Maze multiple correct (yellow)
    public String colorTPMazeVisited = "FF5555";     // TP Maze visited (red)
    public String colorTPMazeTracer = "55FFFF";      // TP Maze tracer to next portal (cyan)
    public String colorPrinceESP = "FFD700";         // Prince structure ESP marker (gold)
    public String colorBossESP = "FF5555";           // Boss ESP box (red)
    public String colorCorpseESP = "55FFFF";         // Corpse ESP box/beacon (cyan)
    public String colorSimonSaysFirst = "8055FF55";  // Simon Says first (green, semi-transparent)
    public String colorSimonSaysSecond = "80FFAA00"; // Simon Says second (gold, semi-transparent)
    public String colorSimonSaysThird = "80FF5555";  // Simon Says third+ (red, semi-transparent)
    public String colorWaterFirst = "FF55FF55";       // Water Board first lever (green)
    public String colorWaterSecond = "FFFFAA00";      // Water Board second lever (gold)

    public boolean melodyMessage = true;           // Announce "/pc <text>" when the melody terminal opens
    public String melodyMessageText = "Melody Terminal start!"; // message sent on melody open
    public boolean melodyProgress = true;          // Send 25/50/75% melody progress to party chat

    public int terminalRenderType = 0;             // 0 = Odin, 1 = Normal, 2 = Custom GUI
    public int terminalNormalSize = 3;             // GUI scale for the vanilla terminal window (6 = auto)
    public float terminalCustomSize = 2.0f;        // Size of the custom terminal GUI (1 - 3)
    public float terminalRoundness = 5f;           // Corner roundness of the custom terminal GUI (0 - 15)
    public int terminalSlotGap = 2;                // Gap between slots in the custom terminal GUI (0 - 8)
    public boolean terminalCancelMelodySolver = false; // Stop rendering the melody solver
    public float terminalMelodySize = 1.5f;        // Size of the melody terminal GUI (1 - 3)
    public boolean terminalShowNumbers = true;     // Show numbers in the order terminal
    public int terminalRubixMode = 1;              // 0 = Fewest Clicks, 1 = Left Clicks Only
    public boolean terminalHideClicked = true;     // Predict the server state before the gui update arrives
    public int terminalReloadThreshold = 600;      // ms before the terminal reloads after an unregistered click
    public int terminalFirstClickProt = 500;       // ms after opening where clicks are blocked (500 minus your ping)
    public boolean terminalLagProtection = false;  // Also block clicks until the server has ticked
    public int terminalLagProtectionTicks = 8;     // Ticks to wait when lag protection is on (1 tick = 50ms)
    public boolean terminalTimes = true;           // Announce terminal solve times
    public boolean terminalSolverDebug = false;    // Draw the terminal solver debug overlay

    public String terminalBackgroundColor = "FF1A1A1A";       // Background of the terminal solver
    public String terminalPanesColor = "FF55FF55";            // Panes terminal
    public String terminalRubixColor1 = "FF55FF55";           // Rubix, 1 click
    public String terminalRubixColor2 = "FF2B802B";           // Rubix, 2 clicks
    public String terminalRubixColorNeg1 = "FFAA0000";        // Rubix, -1 click
    public String terminalRubixColorNeg2 = "FF550000";        // Rubix, -2 clicks
    public String terminalOrderColor1 = "FF55FF55";           // Order terminal, 1st item
    public String terminalOrderColor2 = "FF2B802B";           // Order terminal, 2nd item
    public String terminalOrderColor3 = "FF154015";           // Order terminal, 3rd item
    public String terminalStartsWithColor = "FF55FF55";       // Starts with terminal
    public String terminalSelectColor = "FF55FF55";           // Select all terminal
    public String terminalMelodyColumnColor = "FFAA00AA";     // Melody column indicator
    public String terminalMelodyPointerColor = "FF55FF55";    // Melody press location
    public String terminalMelodyBackgroundColor = "FF262626"; // Melody background slot

    public int termSimPing = 0;                    // Simulated ping of the terminal simulator
    public boolean termSimDisableFirstClickProt = false; // Skip first click protection inside termsim
    public java.util.Map<String, Float> termSimPbs = new java.util.HashMap<>();
    public java.util.Map<String, Float> terminalPbs = new java.util.HashMap<>();

    public boolean secretWaypoints = true;            // Master toggle for secret waypoints
    public boolean secretWaypointChests = true;       // Show chest secrets
    public boolean secretWaypointItems = true;        // Show item secrets
    public boolean secretWaypointBats = true;         // Show bat secrets
    public boolean secretWaypointEssence = true;      // Show essence secrets
    public boolean secretWaypointRedstoneKey = true;  // Show redstone key secrets
    public boolean kickedCenterWarning = true;        // Big center-screen "wait Xs" warning for 60s after a kick
    public boolean bazaarOwnOrders = true;            // Highlight your own orders ("By: <you>") green in bazaar order books
    public boolean bazaarOrderFilled = true;          // Green check mark on bazaar orders that are 100% filled / claimable
    public boolean progressHighlight = true;          // Highlight menu items whose lore shows a progress line at 100%
    public String colorProgressDone = "#55FF55";      // Progress Highlight color
    public boolean commissionHighlight = true;        // Highlight finished commissions in the Commissions menu
    public String colorCommissionDone = "#55FF55";    // Commission Highlight color
    public boolean secretWaypointTracers = false;     // Draw tracers to waypoints
    public boolean secretWaypointHideCollected = true; // Hide waypoints when secret is collected

    public String colorSecretChest = "55FF55";        // Chest (green)
    public String colorSecretItem = "55FFFF";         // Item (cyan)
    public String colorItemReady = "55FF55";          // Dropped item ready to pick up (green)
    public String colorItemSoon = "FFFF55";           // Dropped item nearly pickable (yellow)
    public String colorItemNotReady = "FF5555";       // Dropped item not yet pickable (red)
    public String colorSecretBat = "AAAAAA";          // Bat (gray)
    public String colorSecretEssence = "AA00AA";      // Essence (purple)
    public String colorSecretRedstone = "FF5555";     // Redstone (red)

    public boolean leapOverlay = true;               // Enhanced Spirit Leap menu
    public boolean leapShowHeads = true;              // Show player heads/skins in the leap menu
    public boolean leapAnnounce = true;              // Announce "/pc Leaped to X!" when you leap
    public boolean leapShowMap = true;                // Show the dungeon map atop the leap menu (click to leap)
    public float leapScale = 1.0f;                    // Size of the leap menu boxes
    public String leapSortMode = "Odin";              // Odin, Class A-Z, Name A-Z, Custom, None
    public List<String> leapCustomOrder = new ArrayList<>(); // player names (lowercase), for Custom sort
    public String leapKeybindMode = "Corners";        // Corners (TL/TR/BL/BR) or Class (per-class)
    @Global
    public int leapKeyTL = -1, leapKeyTR = -1, leapKeyBL = -1, leapKeyBR = -1; // corner keybinds
    @Global
    public int leapKeyArcher = -1, leapKeyBerserk = -1, leapKeyHealer = -1, leapKeyMage = -1, leapKeyTank = -1; // class keybinds
    @Global
    public int leapKeyLastDoor = 32; // leap to the player who opened the most recent wither door
    @Global
    public int fastLeapKey = -1;     // Fast Leap: context-aware (door opener / boss phase / arena zone)

    public boolean debugMode = false;

    @Global
    public String hypixelApiKey = "";
    @Global
    public List<String> recentPlayers = new ArrayList<>();
    @Global
    public List<String> guildNames = new ArrayList<>();   // captured from /g list, for the name suggestions
    @Global
    public List<String> friendNames = new ArrayList<>();  // captured from /f list
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
    public String colorESPCustom = "00FFFF";         // Custom entries without their own colour (cyan)

    public static TeslaMapsConfig get() {
        return instance;
    }

    // Called per room, per door and per text in the map render path, so the parse
    // is cached. Colour pickers can churn values, hence the size cap.
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> COLOR_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static int parseColor(String hex) {
        if (hex == null) return 0xFFFFFFFF;

        Integer cached = COLOR_CACHE.get(hex);
        if (cached != null) return cached;

        int result;
        try {
            String s = hex;
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) s = "FF" + s; // Add full alpha if not specified
            result = (int) Long.parseLong(s, 16);
        } catch (Exception e) {
            result = 0xFFFFFFFF; // Default to white on error
        }

        if (COLOR_CACHE.size() > 512) COLOR_CACHE.clear();
        COLOR_CACHE.put(hex, result);
        return result;
    }

    /** Gson leaves collections null when the stored json predates the field. */
    static void normalize(TeslaMapsConfig c) {
        if (c.customESPMobs == null) c.customESPMobs = new ArrayList<>();
        if (c.customESPTracers == null) c.customESPTracers = new ArrayList<>();
        if (c.customESPColors == null) c.customESPColors = new java.util.HashMap<>();
        if (c.colorESPCustom == null || c.colorESPCustom.isBlank()) c.colorESPCustom = "00FFFF";
        if (c.recentPlayers == null) c.recentPlayers = new ArrayList<>();
        if (c.guildNames == null) c.guildNames = new ArrayList<>();
        if (c.friendNames == null) c.friendNames = new ArrayList<>();
        if (c.splitPbs == null) c.splitPbs = new java.util.HashMap<>();
        if (c.lockedSlots == null) c.lockedSlots = new java.util.ArrayList<>();
        if (c.slotBindsData == null) c.slotBindsData = new java.util.HashMap<>();
        if (c.puzzlePbs == null) c.puzzlePbs = new java.util.HashMap<>();
        if (c.termSimPbs == null) c.termSimPbs = new java.util.HashMap<>();
        if (c.terminalPbs == null) c.terminalPbs = new java.util.HashMap<>();
        if (c.sectionEnabled == null) c.sectionEnabled = new java.util.HashMap<>();
        Boolean oldGeneral = c.sectionEnabled.remove("General"); // category renamed to Skills
        if (oldGeneral != null) c.sectionEnabled.putIfAbsent("Skills", oldGeneral);
        Boolean oldDragons = c.sectionEnabled.remove("Dragons"); // category merged into F7 / M7
        if (Boolean.FALSE.equals(oldDragons)) c.witherDragons = false;
        if (c.mutedSounds == null) c.mutedSounds = new java.util.ArrayList<>();
        if (c.actionBarHidden == null) c.actionBarHidden = new java.util.ArrayList<>();
        if (c.skillTotalXp == null) c.skillTotalXp = new java.util.HashMap<>();
        if (c.bestiaryTracked == null) c.bestiaryTracked = new java.util.ArrayList<>();
        if (c.bestiaryKillsToMax == null) c.bestiaryKillsToMax = new java.util.HashMap<>();
        if (c.imageShareBlocked == null) c.imageShareBlocked = new java.util.ArrayList<>();
        if (c.dragonPbs == null) c.dragonPbs = new java.util.HashMap<>();
        if (c.keybinds == null) c.keybinds = defaultKeybinds();
        if (c.shortcuts == null) c.shortcuts = new ArrayList<>();
        if (c.leapCustomOrder == null) c.leapCustomOrder = new ArrayList<>();
        if (c.charmedVisitorNames == null) c.charmedVisitorNames = new ArrayList<>();
        if (c.headTargets == null) c.headTargets = new ArrayList<>();
        if (c.headESPColor == null || c.headESPColor.isBlank()) c.headESPColor = "#FFAA00";
        if (c.headGoldenDragonColor == null || c.headGoldenDragonColor.isBlank()) c.headGoldenDragonColor = "#FFD700";
        if (c.shardsSyphoned == null) c.shardsSyphoned = new java.util.HashMap<>();
        if (c.shardsInBox == null) c.shardsInBox = new java.util.HashMap<>();
        if (c.shardOrders == null) c.shardOrders = new java.util.HashMap<>();
        if (c.auctionSearchHistory == null) c.auctionSearchHistory = new ArrayList<>();
        if (c.bazaarSearchHistory == null) c.bazaarSearchHistory = new ArrayList<>();
        if (c.capeSelected == null) c.capeSelected = "";
        if (c.capeServerUrl == null || c.capeServerUrl.isBlank() || c.capeServerUrl.contains("wynnextras.com")) {
            c.capeServerUrl = CAPE_SERVER_URL;
        }
        if (c.capeServerKey == null || c.capeServerKey.isBlank()) c.capeServerKey = CAPE_SERVER_KEY;
        if (c.greenhouseCode == null) c.greenhouseCode = "";
        if (c.greenhouseSoil == null) c.greenhouseSoil = new java.util.HashMap<>();
        if (c.greenhousePlants == null) c.greenhousePlants = new java.util.HashMap<>();
        if (c.greenhouseRotations == null) c.greenhouseRotations = new java.util.HashMap<>();
        if (c.greenhouseFocus == null) c.greenhouseFocus = "";
        if (c.shardSort == null || c.shardSort.isBlank()) c.shardSort = "Price to Max";
        if (c.shardPriceSource == null || c.shardPriceSource.isBlank()) c.shardPriceSource = "Instant Buy";
        // indexed straight through, so a short array out of an older config would throw
        c.wardrobeKeys = fitKeys(c.wardrobeKeys, 9);
        c.loadoutKeys = fitKeys(c.loadoutKeys, 12);
        if (c.customArmorTrims == null) c.customArmorTrims = new java.util.HashMap<>();
        if (c.customDyeColors == null) c.customDyeColors = new java.util.HashMap<>();
        if (c.customAnimatedDyes == null) c.customAnimatedDyes = new java.util.HashMap<>();
        if (c.customHelmetTextures == null) c.customHelmetTextures = new java.util.HashMap<>();
        if (c.customAnimatedHelmetTextures == null) c.customAnimatedHelmetTextures = new java.util.HashMap<>();
        if (c.customItemNames == null) c.customItemNames = new java.util.HashMap<>();
        if (c.customGlint == null) c.customGlint = new java.util.HashMap<>();
        if (c.customItemModel == null) c.customItemModel = new java.util.HashMap<>();
        if (c.customArmorModel == null) c.customArmorModel = new java.util.HashMap<>();
        // a component or identifier that failed to decode lands as null and would NPE on use
        c.customItemNames.values().removeIf(java.util.Objects::isNull);
        c.customItemModel.values().removeIf(java.util.Objects::isNull);
        c.customArmorModel.values().removeIf(java.util.Objects::isNull);
        if (c.activeProfile == null || c.activeProfile.isBlank()) c.activeProfile = ConfigProfiles.DEFAULT;
        if (c.itemValuePriceSource == null || c.itemValuePriceSource.isBlank()) c.itemValuePriceSource = "BZ Instant Sell";
        if (!c.deployableTypesRepaired) {
            c.deployableTypesRepaired = true;
            if (!c.deployableFlux && !c.deployableLantern && !c.deployableUmbrella && !c.deployableBlackHole) {
                c.deployableFlux = c.deployableLantern = c.deployableUmbrella = c.deployableBlackHole = true;
            }
        }
    }

    private static int[] fitKeys(int[] keys, int size) {
        if (keys != null && keys.length == size) return keys;
        int[] out = new int[size];
        java.util.Arrays.fill(out, -1);
        if (keys != null) System.arraycopy(keys, 0, out, 0, Math.min(keys.length, size));
        return out;
    }

    static void setInstance(TeslaMapsConfig c) {
        instance = c;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = GSON.fromJson(json, TeslaMapsConfig.class);
                if (instance == null) {
                    instance = new TeslaMapsConfig();
                }
                normalize(instance);
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
