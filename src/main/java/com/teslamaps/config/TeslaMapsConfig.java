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
    public int configVersion = ConfigMigrator.CONFIG_VERSION;   // see ConfigMigrator; read from the raw json

    public boolean mapEnabled = true;
    public boolean onlyShowInDungeon = true;  // Only show map while in dungeon
    public boolean mapOnlyInBoss = false;     // Only show map during the boss fight
    public boolean mapHideInBoss = true;      // Hide the map during the boss fight
    public boolean legitMode = false;         // Only show explored (revealed) rooms, like the vanilla map
    public boolean legitMapOnly = false;      // Legit Mode affects only the map; don't block ESP/auto/cheats
    public boolean legitRoomGuess = false;    // guess puzzle/trap/miniboss behind unexplored doors (Odin special column)
    @Global
    public int legitPeekKey = -1;             // Hold to reveal everything while legit mode is on (GLFW code, -1 = unbound)
    public int mapX = 23;
    public int mapY = 172;
    public float mapScale = 1.5f;
    public boolean showMapBackground = true;
    public boolean mapCenterSmallFloors = false;  // F1-F3 rooms centered in the 6x6 frame
    public boolean mapHypixelFallback = false;    // draw Hypixel's map pixels where our scan has no room

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
    public boolean paulAutoDetect = false;     // +10 when the election API reports Paul EZPZ (mayor or minister)
    public boolean spiritPetCheck = false;     // Look up the first dead player: no legendary Spirit pet = -2 instead of -1
    public boolean announce300 = true;         // Announce "300 Score" in party chat (/pc)
    public boolean announce270 = false;         // Announce "270 Score" in party chat (/pc)
    public boolean cryptReminder = true;        // Warn at boss entry if fewer than 5 crypts were done
    public String cryptReminderSound = "BELL";  // Sound for the crypt reminder (see SoundOptions)
    public String cryptReminderTrigger = "Blood Door +30s"; // or "Run Start" (Mort's "Good luck." + delay)
    public float cryptReminderDelay = 2.0f;     // minutes after run start (Run Start trigger)
    public boolean cryptReminderTitle = false;  // also show a title
    public String cryptReminderChannel = "Nobody"; // Announce channel for "n/5 crypts"
    public boolean watcherKillAlert = true;     // Title + sound when the Watcher is cleared (blood done)
    public float roomNameScale = 1.0f;          // Scale of room name text (0.5 - 2.0)
    public float infoTextScale = 1.0f;          // Scale of the info box below the map (Secrets/Crypts/Score) (0.5 - 2.0)

    public boolean showCheckmarks = true;
    public boolean showWhiteCheckmarks = true;   // Show white (cleared) checkmarks
    public boolean showGreenCheckmarks = true;   // Show green (all secrets) checkmarks
    public boolean showFailedCheckmarks = true;  // Show failed (red X) checkmarks
    public boolean mapCheckmarkIcons = false;
    public String mapCheckmarkStyle = "IllegalMap"; // Symbol / IllegalMap / NEU / Vanilla New / Vanilla Old
    public float mapCheckmarkSize = 0.5f;           // fraction of a room tile
    public boolean mapCheckmarkCenter = true;
    public boolean mapCheckmarkUnopened = false;    // question mark on rooms not entered yet
    public boolean mapCheckmarkHideWithName = false;

    public boolean showPlayerMarker = true;
    public boolean showSelfMarker = true;        // Show your own marker/head
    public boolean showOtherPlayers = true;      // Show other players' markers/heads
    public boolean showPlayerNames = true;
    public boolean rotatePlayerHeads = true;
    public boolean useHeadsInsteadOfMarkers = true;  // True = player heads, False = arrow markers
    public float playerHeadScale = 1.0f;         // Scale of player heads (0.5 - 2.0)
    public boolean mapGlowArrow = false;         // pulsing glow arrow for self (and others when markers are used)

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
    public boolean corpseFinder = false;         // Legit corpse finder: only corpses seen in line of sight, no walls
    public boolean corpseFinderParseChat = false; // Party/all chat "x: y: z:" marks a corpse as seen
    public String corpseFinderChannel = "Nobody"; // Announce channel for the click-to-share line
    public boolean lividFinder = true;          // Highlight the correct Livid in F5/M5
    public boolean lividTracer = true;          // Draw tracer to correct Livid
    public boolean lividDeathMessage = true;    // Send "/pc Livid Dead!" when Livid dies
    public boolean lividShowWrong = false;      // keep the fake Livids visible instead of hiding them
    public boolean lividHideWrongNametags = false; // also hide the fake Livids' ﴾ Livid ﴿ nametags
    public boolean lividTimerHud = false;      // movable Livid invuln timer on server ticks, independent of the finder
    public boolean lividIceSprayAlert = false;  // title + sound when the invuln timer runs out
    public int lividTimerX = 10;
    public int lividTimerY = 120;
    public float lividTimerScale = 1.0f;
    public boolean doorESP = true;              // Highlight wither/blood doors through walls
    public boolean fairyDoorESP = true;         // Highlight the closed fairy-room doors (open when the wither door behind falls)
    public boolean doorTracers = false;         // Draw tracers to wither/blood doors
    public boolean doorColorBasedOnKey = true;  // Color doors red if key not picked up, green if picked up
    public boolean onlyShowNextDoor = true;     // Only show tracer/ESP for the nearest door
    public boolean doorEspRoute = false;        // next wither/blood doors along the route to blood instead of the nearest
    public float doorEspAhead = 1f;             // how many route doors ahead (0-10)
    public boolean doorEspKeyAtCurrentRoom = false; // green only when the key is there AND the door is at your room
    public boolean doorEspNormalDoors = false;  // box the normal doorways of the current room
    public boolean doorEspHideUseless = true;   // skip doorways that only lead to green rooms
    public com.teslamaps.render.EspStyle doorEspNormalStyle = new com.teslamaps.render.EspStyle("FF008080");
    public boolean mimicChestESP = true;        // Highlight trapped chests (potential mimic)
    public boolean mimicChestTracers = false;   // Draw tracers to trapped chests
    public boolean mimicDeadMessage = true;     // Send "/pc Mimic Dead!" when mimic dies
    public boolean princeDeadMessage = true;    // Send "/pc Prince Dead!" when a Prince is killed
    public boolean showGlow = true;             // Show glow effect on entities
    public boolean filledESP = false;           // Fill entire entity hitbox instead of just outlines
    public boolean boxESP = false;              // Outline hitbox (F3+H style) around ESP mobs, through walls
    public float espAlpha = 0.4f;               // Transparency for filled ESP (0.0 = invisible, 1.0 = solid)

    // Legacy pest ESP. Replaced by Mob Visuals, which tells the 15 pest species apart instead
    // of colouring every invisible hitbox the same. normalize() migrates these once and clears
    // them, so they only ever carry the old setting forward - nothing reads them at runtime.
    public boolean pestESP = true;
    public boolean pestTracers = true;
    public boolean hoppityEggESP = false;       // Box + label on Hoppity chocolate eggs
    public boolean hoppityEggTracers = false;   // Draw tracers to Hoppity eggs
    public boolean droppedItemESP = true;       // Highlight all dropped items
    public boolean droppedItemRarityColor = false; // Dropped items in their rarity / name colour
    public boolean droppedItemLabel = false;       // "x3 Name" above dropped items
    public boolean secretItemReadiness = true;   // Color dropped items in dungeons by pickup readiness
    public int secretItemPickupTicks = 50;       // Ticks after a drop spawns before it's pickable (tune in-game)
    public boolean hideSoulweaverSkull = true; // Hide the floating skull spawned by Soulweaver Gloves
    public boolean hideSkeletonSkull = true;   // Hide the floating "Skeleton Skull" armor stands (bone shield / piling skulls)
    public boolean showMovingSkulls = false;   // keep thrown (moving) skulls visible
    public boolean highlightMovingSkulls = false; // gold glow on a moving skull for 200 ms
    public boolean hideDungeonItemParticles = false; // firework/dust at hidden Superboom/Revive Stone/Flesh/orbs
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
    public boolean keyPickupTitle = true;        // Show WITHER/BLOOD KEY title (also gated by keySoundClasses)
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
    public boolean secretClickBats = false;      // also box killed secret bats
    public boolean secretClickItems = false;     // also box picked-up secret items
    public float secretClickDuration = 3.0f;     // seconds a box stays
    public com.teslamaps.render.EspStyle secretClickStyle = secretClickStyleDefault();
    public String secretClickLockedColor = "66FF0000";
    private static com.teslamaps.render.EspStyle secretClickStyleDefault() {
        var s = new com.teslamaps.render.EspStyle("6600FF00", com.teslamaps.render.EspStyle.BOTH);
        s.lineWidth = 3.0f;
        return s;
    }
    public boolean salvageHelper = true;        // Highlight cheap (<100k) dungeon-salvageable gear in the Salvage menu
    public boolean sellableHighlighter = true;  // Highlight common junk to sell in Ophelia/Booster Cookie menus
    public boolean sellableExtended = false;    // Trades menu + dungeon junk list + non-max quality gear
    public String colorSellable = "#80FFFF00";
    public boolean salvageQuality = false;      // mark dungeon gear by baseStatBoostPercentage in every container
    public String colorStatBoostLow = "#A000FFFF";
    public String colorStatBoostMax = "#A0FF0000";
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
    public String hudCustomBg = "C0101014";         // HUD Style "Custom": fill (AARRGGBB)
    public String hudCustomBorder = "FF5A5A6E";
    public String hudCustomBorderMode = "Solid";   // Solid / Chroma / Fade
    public String hudCustomBorderFade = "FF55FFFF"; // second colour of Fade
    public float hudCustomRadius = 5f;
    public float hudCustomBorderWidth = 1f;
    public float hudCustomPadding = 5f;
    public java.util.Map<String, String> hudPanelStyles = new java.util.HashMap<>(); // hud id -> own HUD Style
    public boolean hudClampToScreen = false;    // draw off-screen HUD panels at the screen edge, stored position kept
    public boolean hudEdgeAnchors = false;      // right/bottom panels keep their edge distance when the screen size changes
    public java.util.Map<String, int[]> hudAnchorRefs = new java.util.HashMap<>(); // element -> screen w/h it was placed at
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
    public boolean auctionCompareGradient = false; // fade relative to the best/worst deal on the page
    public String colorAuctionVeryGood = "#00AA00";
    public String colorAuctionGood = "#55FF55";
    public String colorAuctionBad = "#FFFF55";
    public String colorAuctionVeryBad = "#E12B1E";
    public boolean auctionCompareButton = true;  // toggle button above the auction browser

    public boolean searchOverlay = true;         // Replace the auction/bazaar search sign with a textbox
    public int searchOverlaySuggestions = 7;     // How many item names the search overlay suggests (0 - 12)
    public boolean searchOverlayKeepText = true; // Start with whatever the sign already carried
    public boolean searchOverlayHistory = true;  // Remember and list previous searches
    public boolean searchPasteCopied = true;     // Prefill the search with a name copied in the last 30s
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
    public boolean splitsShowDelta = false;      // "+1.23s" / "-0.50s" per finished split vs the PB before the run
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
    public boolean itemProtectionMenus = false;  // also trade, AH, dungeon chests, any NPC shop
    public boolean itemProtectionUuid = false;   // per-item protection by Hypixel uuid
    public int itemProtectionKey = 86;           // V over a slot toggles its protection
    @Global
    public java.util.Set<String> protectedItems = new java.util.HashSet<>();
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
    public String waypointAddType = "None";         // Slot 1-3 waypoint type: None, Normal, Secret, Etherwarp
    public String waypointAdd2Type = "None";
    public String waypointAdd3Type = "None";
    public boolean waypointUseBlockSize = true;     // new waypoints hug the block; off = size X/Y/Z below
    public float waypointSizeX = 1.0f;
    public float waypointSizeY = 1.0f;
    public float waypointSizeZ = 1.0f;
    public float waypointTitleScale = 1.0f;         // waypoint title text scale
    public boolean dungeonWaypointEditor = false;   // editor HUD: looked-at waypoint / next placement
    public int dungeonWaypointEditorX = 10;
    public int dungeonWaypointEditorY = 200;
    public float dungeonWaypointEditorScale = 1.0f;

    @Global
    public int waypointRemoveKey = -1;            // Keybind: remove nearest waypoint in current room
    @Global
    public int waypointClearKey = -1;             // Keybind: clear all waypoints in current room

    // Crystal Hollows scanner (Golden Dragon nest etc.)
    public boolean chScanner = false;             // Master toggle: scan CH chunks for structures
    public boolean chScanDragon = true;           // Golden Dragon nest
    public boolean chScanCrystals = true;         // King, Queen, Divan, City, Temple, Bal
    public boolean chScanMobSpots = true;         // Corleone, Key Guardians, Xalx, Pete, Odawa, Slime Tree
    public boolean chScanGrottos = true;          // Fairy Grottos
    public boolean chScanWorm = false;            // Worm fishing spots
    public boolean chScanTracers = false;         // Tracer lines to found structures
    public boolean chScanChat = true;             // Chat alert with coords when found

    // Drill / pickaxe ability cooldown, from the "Pickaxe Ability:" tab widget
    public boolean pickaxeAbilityDisplay = false;
    public boolean pickaxeAbilityMiningOnly = true;    // only on the mining islands
    public boolean pickaxeAbilityOnlyOnCooldown = false;
    public boolean pickaxeAbilityBar = false;          // animated bar instead of the text line
    public boolean pickaxeAbilityBarVertical = false;
    public String pickaxeAbilityReadyText = "Right";   // Off / Right / Left / Top / Bottom
    public String pickaxeAbilityReadyColor = "55FF55";
    public String pickaxeAbilityEmptyColor = "FF5555";
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
    public long mineshaftTotalBlocks = 0;              // runtime: pity blocks mined over all found mineshafts
    public boolean mineshaftPityShowNeeded = false;    // "Needed to pity" rows per block type
    public boolean mineshaftPityShowAverage = false;   // average blocks per mineshaft line
    public boolean mineshaftPityHideChance = false;
    public boolean mineshaftPityHideLast = false;
    public boolean mineshaftPityHideFound = false;
    public boolean mineshaftPityChatHover = false;     // block breakdown hover on the found line
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
    public String colorEtherwarpFail = "FF5555";
    public boolean etherwarpOutline = false;      // Outline on top of the filled box
    public float etherwarpLineWidth = 2.0f;
    public boolean etherwarpThroughWalls = false;
    public boolean etherwarpBlockShape = false;   // Box follows the block shape (slabs, stairs) instead of a full block
    public boolean etherwarpServerPos = false;    // Ray from the last position sent to the server
    public boolean etherwarpFailReason = false;   // HUD line: why the warp would fail
    public int etherwarpFailReasonX = 404;
    public int etherwarpFailReasonY = 150;
    public float etherwarpFailReasonScale = 1.0f;
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
    public int croesusPriceSource = 0;              // PriceManager.SOURCE_*; 0 = bazaar buy then BIN (old behaviour)
    public boolean croesusKismetCost = false;       // subtract a Kismet Feather when the chest was rerolled
    public float croesusKuudraPetBonus = 0f;        // Kuudra pet crimson essence bonus in %
    public boolean croesusKuudraSalvage = false;    // Kuudra armor at its crimson essence salvage value
    public boolean croesusKuudraNoKeyWarn = false;  // paid Kuudra chest: warn when the key is not in the inventory
    public String kuudraFaction = "";               // Mage / Barbarian, read from the faction shop
    public java.util.Map<String, Integer> kuudraKeyCoins = new java.util.HashMap<>(); // key name -> coin cost from the shop

    public boolean chatCopyEnabled = true;       // Right-click a chat line to copy its text

    public boolean chatFilterEnabled = true;     // Master switch for the chat filter
    public String chatFilterAction = "Hide";     // Hide / Grey / Compact / Compact + Grey
    public java.util.Map<String, String> chatFilterGroupActions = new java.util.HashMap<>(); // group -> action, absent = default
    public boolean chatFilterKeepClickable = false; // Never hide lines that carry a command button
    public java.util.List<ChatUserFilter> chatUserFilters = new java.util.ArrayList<>(); // /tmap chatfilter
    public String chatUserFilterAction = "Hide";
    public String chatRedirectTarget = "Spam HUD"; // "Redirect" action: Spam HUD / Action Bar
    public float chatRedirectSeconds = 4f;
    public int chatSpamHudX = 4;
    public int chatSpamHudY = 60;
    public float chatSpamHudScale = 1.0f;
    public int chatFilterLineKey = -1;           // In open chat: filter the hovered line (GLFW code, -1 = unbound)

    public static class ChatUserFilter {
        public String mode = "contains";         // contains / equals / startswith / endswith / regex
        public String text = "";
        public String action;                    // null = Own Filter Action, "Notify" = highlight + ping
        public String title;                     // alert: title, {0}/{1}/{name} = regex groups
        public String sound;                     // alert: SoundOptions key
        public boolean flash;                    // alert: screen flash
        public boolean keep;                     // alert: line stays visible
        public boolean systemOnly;               // alert: never on player chat
        public ChatUserFilter() {}
        public ChatUserFilter(String mode, String text) { this.mode = mode; this.text = text; }
    }
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
    public boolean stashCompact = false;         // Stash warning block -> one clickable line
    public boolean stashHideDuplicates = false;  // Skip a warning with the same count as the last one
    public boolean stashDuplicatesPerWorld = false; // ...but show it again once per world
    public boolean stashHideAdded = false;       // Hide "didn't fit ... added to your stash"
    public int stashMinCount = 0;                // Hide warnings at or below this count
    public boolean stashUseViewStash = false;    // Click runs /viewstash <type> instead of /pickupstash
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
    public boolean chatFilterPerkBuffsElsewhere = false; // Sky Mall / Lottery lines stay visible on Mining / Foraging islands
    public boolean chatFilterBossAll = false; // Every "[BOSS] " line (dungeon + slayer bosses)
    public boolean chatFilterLobby = false; // Lobby joins, mystery boxes/dust, tournaments
    public boolean chatFilterWelcome = false; // "Welcome to Hypixel SkyBlock!"
    public boolean chatFilterFriendJoin = false; // "Friend/Guild > X joined/left."
    public boolean chatFilterWarpMore = false; // Request join, Warped to, Finding player, visit request
    public boolean chatFilterBzAhMinis = false; // Bazaar/AH/bank "..." progress lines
    public boolean chatFilterSlayerSpam = false; // Slayer quest start/complete block + RING
    public boolean chatFilterUselessWarnings = false; // "sending commands too fast", "in combat" etc.
    public boolean chatFilterUselessNotifications = false; // Tips, speed boost, 0 coin interest
    public boolean chatFilterFireSale = false;
    public boolean chatFilterEventLevel = false; // Event level / silver / LEVEL UP
    public boolean chatFilterHoppity = false; // Egg appeared + hunt begun
    public boolean chatFilterChocolateFactory = false; // Factory upgrade lines
    public boolean chatFilterSacrifice = false; // Other players' dragon sacrifices
    public boolean chatFilterNoPests = false; // "There are not any Pests on your Garden"
    public boolean chatFilterLegacyItems = false; // Legacy items warning
    public boolean chatFilterParkour = false;
    public boolean chatFilterTeleportPads = false; // "Warped from the ... to the ..."
    public boolean chatFilterChefTed = false; // Feast Chef Ted donation line
    public boolean chatFilterRewardBundles = false; // Seasonal reward reminders
    public boolean chatFilterWinterGift = false; // Gift rewards
    public boolean chatFilterAnita = false; // Anita accessory contest line, outside the Garden
    public boolean chatFilterRareRewards = false; // Dungeon "RARE REWARD! X found ..."
    public boolean chatFilterSoloClass = false; // Solo class + class stat lines in dungeons
    public boolean chatFilterFairy = false; // Fairy lines in dungeons

    public boolean infiniteChat = true;          // Keep full chat history (scroll back through everything)
    public boolean chromaPrefix = true;          // [TeslaMaps] chat prefix with animated fade
    public String prefixFadeColorA = "B300FF";   // fade start colour
    public String prefixFadeColorB = "3355FF";   // fade end colour
    public int chatHistoryLimit = 16384;         // Hard cap so memory stays bounded (effectively infinite)
    public boolean chatStacking = true;          // Stack identical messages with a (Nx) counter
    public int chatStackWindowMinutes = 5;       // Stack any identical message from the last N minutes (1-30)
    public boolean chatStackBlocks = false;      // A repeated divider block drops the old one, dividers included
    public boolean chatStackCompareColors = false; // Only stack lines whose colours match too

    public boolean pbOnJoin = true;               // Show a player's dungeon PBs when they join via party finder
    public boolean pbOnJoinCard = false;          // full stats card (hover details, missing items, kick line) instead of the one-liner
    public boolean chatCommands = true;           // Party chat commands (!8ball, !cf, !warp, !pt, !kick, ...)
    public boolean chatCommandsGuild = false;     // harmless !commands in guild chat too
    public boolean chatCommandsDm = false;        // ... in private messages
    public boolean chatCommandsCoop = false;      // ... in co-op chat
    public boolean chatCommandsWhitelistOnly = false; // !pt/!kick/!promote/!demote/!allinv only from you + whitelist
    @Global public List<String> chatCommandsWhitelist = new ArrayList<>(); // lowercase names, /tmap pcmd <name>
    public List<String> chatCommandsDisabled = new ArrayList<>(); // switched-off command keys

    public boolean autoRequeue = true;           // Send /instancerequeue at the end of a dungeon
    public boolean requeueOnPartyR = true;       // Requeue when a party member types "r"
    public int requeueDelaySeconds = 0;           // Delay before requeuing
    public boolean requeueDowntimeList = false;   // dt/!dt list, requeue only once everyone said r
    public boolean requeueCheckParty = false;     // skip when the party size changed since the run started
    public boolean requeueFeedback = false;       // chat line when a requeue is skipped

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
        public int modifiers = 0;     // GLFW mod bits: 1 shift, 2 ctrl, 4 alt; 0 = any
        public boolean enabled = true;
        public String cond = "None";      // hotkeyConditions: None/Holding Item/Inventory Contains/Sneaking/On SkyBlock
        public String condMode = "Name";  // Name/Lore/SkyBlock ID
        public String condValue = "";
        public String islands = "";       // comma list, empty = any island
        public String failMessage = "";   // sent instead when the condition fails
        public boolean negate = false;
        public int seqKey = -1;           // hotkeyAdvanced: second key of a press sequence
        public int seqWindow = 500;
        public int cooldownMs = 0;
        public int delayMs = 0;
        public String actions = "";
        public int key2 = -1;             // hotkeyCombos: second key that must be held
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
            "Auto", "Blood Camp", "Chat", "Combat", "Contest Timers", "Crimson Isle", "Critter Safari", "ESP", "Fishing",
            "Foraging", "Garden", "Inventory", "Leap", "Map", "Mining", "Mob Visuals",
            "Ordered Waypoints", "Party", "Puzzles & Terminals", "Render", "Score & Splits",
            "Secrets & Waypoints", "Shards", "Skills", "Sounds", "The End", "Timers", "Torrhus Canyon");

    private static final java.util.Set<String> WARNED_SECTIONS = new java.util.HashSet<>();

    public boolean section(String name) {
        if (!SECTIONS.contains(name) && !com.teslamaps.registry.FeatureRegistry.isGatedCategory(name) && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
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
            new Shortcut("dh", "warp dhub"),
            new Shortcut("t1", "joinkuudra basic"),
            new Shortcut("t2", "joinkuudra hot"),
            new Shortcut("t3", "joinkuudra burning"),
            new Shortcut("t4", "joinkuudra fiery"),
            new Shortcut("t5", "joinkuudra infernal")
    ));
    @Global
    public boolean kuudraShortcutsAdded = false;

    public boolean noFire = true;            // Hide fire overlay on screen
    public boolean noBlind = true;           // Remove blindness effect
    public boolean noNausea = true;          // Remove nausea effect
    public boolean noExplosions = true;      // Hide explosion particles
    public boolean hideImplosions = false;   // Only wither blade implosion particles
    public boolean noExplosionSound = false; // Mute explosion sounds (TNT/creeper)
    public boolean noCreeperHurtSound = true; // Mute creeper hurt sounds
    public boolean soundDebug = false;       // Print every incoming sound (id/vol/pitch) to chat
    public int eventTrackerWindow = 3;       // /tmap tracksounds|trackparticles: seconds an event stays listed
    public int eventTrackerMaxLines = 10;
    @Global
    public int trackSoundsKey = -1;
    @Global
    public int trackParticlesKey = -1;
    public int eventTrackerX = 4;
    public int eventTrackerY = 120;
    public float eventTrackerScale = 1.0f;
    public boolean muteBonzoStaff = false;   // Mute Bonzo's Staff impact (firework blast/twinkle sounds)
    public boolean muteBonzoStaffPrecise = false; // only the Bonzo pitch window + its ghast moan, real fireworks stay
    public boolean muteLightning = false;    // Mute lightning/thunder sounds (Storm phase)
    public java.util.List<String> mutedSounds = new java.util.ArrayList<>(); // sound ids to mute ("id" or "id@pitch")
    public boolean soundManager = false;     // per-sound volume multipliers
    public java.util.Map<String, Float> soundVolumes = new java.util.HashMap<>(); // sound id -> multiplier (0-2)
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
    public String farmPriceMode = "Instant Sell";   // Instant Sell / Sell Offer / NPC
    public boolean farmProfitReplenish = false;     // subtract what Replenish eats
    public boolean farmProfitKernels = false;       // Ted kernels as coins
    public String farmKernelItem = "Feast I";
    public boolean farmKernelDiscount = false;      // 5% kernel discount
    public boolean farmProfitPestTypes = false;     // pests per species in the breakdown
    public List<String> farmProfitExcluded = new ArrayList<>();
    public boolean farmingSensitivity = false;      // Drop sensitivity while holding a farming tool
    public float farmingSensitivityValue = 0.0f;    // Sensitivity to use while it applies
    public boolean farmingSensitivityGroundOnly = false;
    public boolean farmingSensitivityPauseBarn = false;
    public boolean farmingMouseLockOnMousemat = false;
    public boolean farmingMouseUnlockOnTeleport = false;
    public boolean farmingSensitivityHud = false;
    public int farmingSensitivityHudX = 10;
    public int farmingSensitivityHudY = 120;
    public float farmingSensitivityHudScale = 1.0f;
    @Global
    public double sensitivityBackup = -1;           // Player's own sensitivity while overridden, -1 = nothing pending
    public boolean pestOverlay = true;              // Cooldown / alive / infested plots panel
    public boolean pestOverlayAlivePlots = false;   // alive count + infested plots rows
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
    public boolean farmLanePerCrop = false;         // one lane per crop, picked by the crop you break
    public java.util.Map<String, FarmLane> farmLanes = new java.util.LinkedHashMap<>();
    public boolean farmLaneTimeLeft = false;        // time until the lane end from walking speed
    public int farmLaneWarnSeconds = 0;             // title this many seconds before the end, 0 = off
    public boolean farmLaneCorners = false;         // beacons at both lane ends
    public static class FarmLane { public boolean alongX; public double min, max; }
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

    public boolean feastSeasonDisplay = true;   // Crops in-season during the Harvest / Grand Feast
    public boolean feastSeasonEverywhere = true;  // Also outside the garden
    public boolean feastAutoFetch = false;        // pull the rotation from EliteSkyblock when missing
    public boolean feastOutdatedHint = false;     // "Feast data is outdated!" while the feast runs without data
    public String feastShareChannel = "Nobody";   // /tmap feast share target
    public int feastSeasonX = 180;
    public int feastSeasonY = 240;
    public float feastSeasonScale = 1.0f;
    @Global
    public List<String> feastCrops = new ArrayList<>();        // Last read in-season crops
    @Global
    public java.util.Map<String, Long> feastNextSeason = new java.util.LinkedHashMap<>();  // Crop -> back in season, epoch millis
    @Global
    public int feastMonthIndex = -1;            // Skyblock month the data was read in

    public boolean sixthVisitorWarning = true;  // Title + sound when the 6th visitor is estimated ready
    public String sixthVisitorSound = "NOTE_PLING";
    public float sixthVisitorVolume = 1.0f;     // 0 = silent
    @Global
    public long visitorInterval = 15 * 60_000L; // Learned time between two visitors
    @Global
    public long sixthVisitorArrival = 0L;       // Estimated arrival, epoch millis, 0 = queue not full

    public boolean huntingBoxValue = true;      // Bazaar value of the shards in the hunting box
    public boolean shardOverlay = true;         // What is still missing to max every attribute
    public String shardSort = "Price to Max";   // ShardOverlay.SORTS
    public String shardPriceSource = "Instant Buy";  // ShardOverlay.PRICE_SOURCES
    public boolean shardHideMaxed = true;       // Skip tier 10 shards in the list
    public boolean shardOnlyMissing = false;    // Only shards that are not unlocked yet
    public boolean shardIncludeBox = true;      // Count what is already in the hunting box
    public boolean shardIncludeOrders = true;   // Count what is already on order in the bazaar
    public boolean shardOnlyCurrent = false;    // only shards on the open bazaar / attribute page
    public boolean shardPanelSearch = false;    // search box in the shard panel
    public boolean shardPanelButtons = false;   // option buttons under the shard panel
    public boolean shardFusionTracking = false; // FUSION! chat line moves the box amounts
    public boolean shardSyphonHint = false;     // clickable /attributemenu hint on "and N more..."
    public boolean shardTierStack = true;       // Tier as the stack size in the attribute menu
    public boolean shardHighlightDisabled = true;   // Red tint over switched-off attributes
    public boolean shardTierColors = false;
    public boolean shardHideMaxedTier = false;
    public boolean shardHighlightProgress = false;
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
    public boolean noNetherFog = true;       // Crimson Isle: push the nether fog away even when Hide Fog is off

    // --- Mob Visuals ---
    public boolean mobVisuals = false;             // Master toggle for glow/tracer/nameplate
    public boolean mobVisualGlow = true;           // Vanilla outline in the mob's colour
    public boolean mobVisualTracer = true;         // Line from the crosshair to the mob
    public boolean mobVisualNameplate = true;      // Name (and health) drawn on the mob
    public boolean mobVisualNameplateHealth = true;
    public float mobVisualNameplateScale = 1.0f;
    public boolean mobVisualNameplateHud = false;  // Nameplates drawn in the HUD over the projected mob, above glow
    public boolean mobVisualTracerAll = false;     // Off = one line per species, the nearest
    @Global
    public java.util.Set<String> mobVisualTargets = new java.util.HashSet<>();

    // --- The End: End Stone Protector ---
    public boolean endProtector = true;             // Master toggle for the protector tracker
    public boolean endProtectorStatusHud = true;    // Stage / timer / location panel
    public boolean endProtectorLootHud = true;      // Rare drop counter panel
    public boolean endProtectorBeacon = true;       // Beacon beam on the spawn site
    public boolean endProtectorWorldText = true;    // Label above the spawn site
    public boolean endProtectorStage4Title = true;  // Title when stage 4 starts
    public boolean endProtectorStage4Sound = true;
    public boolean endProtectorStage5Title = true;  // Title when the protector is summoned
    public boolean endProtectorStage5Sound = true;
    public boolean endProtectorStage4Duration = true;  // Chat line with how long stage 4 took
    public boolean endProtectorDpsChat = true;         // Your DPS + top 3 on hover
    public boolean endProtectorLootQualityChat = true; // Your loot quality + which drops it reaches
    public boolean endProtectorDropAlert = true;       // Title/sound/chat on a rare drop
    public int protectorStatusX = 230;
    public int protectorStatusY = 10;
    public float protectorStatusScale = 1.0f;
    public int protectorLootX = 230;
    public int protectorLootY = 60;
    public float protectorLootScale = 1.0f;
    @Global
    public int protectorTierBoostCores = 0;
    @Global
    public int protectorLegendaryPets = 0;
    @Global
    public int protectorEpicPets = 0;

    public boolean rescueMission = true;     // Crimson Isle rescue mission: hostage parkour waypoints
    public int rescueVariant = 1;            // Barbarian S-rank has two possible paths (1 or 2)
    public int rescueLookAhead = 3;          // how many parkour platforms are shown ahead
    public String rescueFaction = "Auto";    // Auto (tab list) / Barbarian / Mage
    // --- Inventory Buttons (NEU-compatible) ---
    public boolean inventoryButtons = false;       // Draw the command buttons around container screens
    public boolean invButtonsHideInDungeonMenus = true;  // Keep them off puzzle/leap menus
    public int invButtonClickType = 0;             // 0 = mouse down, 1 = mouse up
    public int invButtonTooltipDelay = 300;        // ms of hover before the command tooltip shows
    @Global
    public java.util.List<com.teslamaps.features.invbuttons.InventoryButton> invButtons = new ArrayList<>();
    @Global
    public java.util.LinkedHashMap<String, java.util.List<com.teslamaps.features.invbuttons.InventoryButton>>
            savedButtonPresets = new java.util.LinkedHashMap<>();

    public boolean hideRecipeBook = false;   // Hide the recipe book button in the inventory
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
    public boolean zoomToggle = false;       // Key toggles zoom instead of holding
    public boolean zoomRememberScroll = false;
    public boolean zoomRelativeSensitivity = false;
    public boolean zoomHideHand = false;

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
    public boolean slayerAlertSpawnTitle = true;
    public boolean slayerAlertCocoonTitle = true;
    public boolean slayerAlertMinibossTitle = true;
    public boolean slayerAlertSpawnSound = false;
    public boolean slayerAlertCocoonSound = false;
    public boolean slayerAlertMinibossSound = false;
    public boolean slayerAlertMinibossName = false; // miniboss title shows its name
    public boolean slayerAlertCustomColor = false;
    public String slayerAlertColor = "FF5555";
    public int slayerAlertTicks = 25;          // title stay time
    public boolean slayerKillChat = true;      // Chat line with kill/spawn time after each boss
    public boolean slayerKillPb = false;       // Persistent kill-time PB per slayer type and tier
    public boolean slayerKillTicks = false;    // Kill time in server ticks too
    public String slayerPbChannel = "Nobody";  // Where a new PB is posted
    public boolean bestiaryTracker = true;     // Bestiary HUD: kills/hour and tier ETA out of the tab list
    public int bestiaryTrackerX = 10;          // X position of bestiary HUD
    public int bestiaryTrackerY = 200;         // Y position of bestiary HUD
    public float bestiaryTrackerScale = 1.0f;  // Scale of bestiary HUD
    @Global
    public java.util.List<String> bestiaryTracked = new java.util.ArrayList<>(); // mobs picked with /tmap be add
    public boolean bestiaryAutoTrack = false;  // Keep tracking every non-maxed mob the tab shows
    public java.util.Map<String, Integer> bestiaryIdleMinutes = new java.util.HashMap<>(); // per-mob idle cap, /tmap be add <mob> <min>
    @Global
    public java.util.Map<String, Long> bestiaryKillsToMax = new java.util.HashMap<>(); // read from the Bestiary menu

    public boolean skillTracker = true;        // Tracker HUD: skill XP/hour plus the rates that fit the skill
    public int skillTrackerX = 10;             // X position of tracker HUD
    public int skillTrackerY = 120;            // Y position of tracker HUD
    public int fishingCatchSource = 0;         // what counts as a catch: 0 = auto, 1 = sea creatures + trophies (chat), 2 = every catch (xp)
    public float skillTrackerScale = 1.0f;     // Scale of skill tracker HUD
    public boolean skillTrackerHidden = false; // keep tracking, but only show the panel in the inventory
    public String skillTrackerPin = "";        // "" = follow the xp, otherwise stay on that skill
    public boolean skillExpDisplay = false;    // cut the skill xp out of the action bar into its own HUD element
    public boolean petOverlay = false;         // equipped pet HUD: level, xp progress, held item
    public int petOverlayX = 10;
    public int petOverlayY = 260;
    public float petOverlayScale = 1.0f;
    public boolean petOverlaySkinIcon = false;  // pet skin head from the Pets menu cache
    public boolean petOverlayXpRate = false;    // XP/h line
    public boolean petOverlayEta = false;       // time to next level and max level
    public boolean petOverlayLastPet = false;   // line with the pet before this one
    public boolean petOverlayRing = false;      // rarity disc + xp ring around the icon
    public int skillExpDisplayX = -1;          // -1 = centered above the hotbar
    public int skillExpDisplayY = -1;
    public float skillExpDisplayScale = 1.0f;
    public boolean skillExpIcon = false;       // skill item icon in front of the xp
    public boolean skillExpLevel = false;      // "[25]" level prefix, from the tab list
    public int skillExpFormat = 0;             // 0 = as sent, 1 = percent, 2 = xp/needed
    public boolean skillExpActionsLeft = false; // "340 left" = rest xp / last gain
    public boolean skillExpBar = false;        // progress bar under the text
    public int skillExpBarWidth = 120;
    public String skillExpBarColor = "30D158";
    public boolean fixDoubleClicks = true;     // Drop the duplicate use-on-block Hypixel sends for blaze daggers / autopet rods
    public boolean skillLevelStackSize = true;  // draw the skill level as the stack size in the skills menu
    public boolean skillAvgOverflow = true;     // add the overflow skill average to the "Your Skills" tooltip
    public boolean skillOverflow = true;       // Overflow levels in the skill menu and on level up
    public java.util.Map<String, Long> skillTotalXp = new java.util.HashMap<>(); // total xp per skill, read from the skill menu

    public boolean flareMode = false;          // Flare grind HUD: Hunting + Combat + flare bestiary and the 3x check
    public int flareModeX = 10;
    public int flareModeY = 60;
    public float flareModeScale = 1.0f;
    public String flareMob = "Flare";          // bestiary family the panel counts
    public boolean flareWarnChat = true;       // chat warning while the kill counter climbs in ones
    public int flareWarnSeconds = 20;          // how often that warning may repeat
    public boolean flareWarnSound = true;

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
    public boolean autoGFSDraftAlert = false;

    public boolean autoWish = false;            // Auto use healer wish at key boss moments


    public boolean solveBlaze = true;              // Show which blaze to hit
    public boolean blazeDoneMessage = true;        // Send "/pc Blaze Done!" when last blaze killed
    public boolean modernBlazeSolver = true;      // Fill the solver boxes + hide the vanilla blaze mobs (cleaner look)
    public boolean blazeTracer = false;
    public boolean blazeSameHpWarning = false;
    public int blazeLineAmount = 2;
    public float blazeLineWidth = 1.0f;
    public String blazeColorFirst = "00FF00";
    public String blazeColorSecond = "FFFFFF";
    public String blazeColorThird = "0055FF";
    public String blazeColorRest = "FF0000";
    public boolean solveThreeWeirdos = true;       // Highlight correct chest in Three Weirdos
    public boolean solveTicTacToe = true;          // Show best move in Tic Tac Toe
    public boolean solveCreeperBeams = true;       // Show solution for Creeper Beams
    public boolean creeperBeamsTracers = true;     // Draw lines between lantern pairs
    public boolean solveBoulder = true;            // Show solution for Boulder puzzle
    public boolean showAllBoulderClicks = true;    // Show all clicks vs just next one
    public boolean boulderClickBlock = false;
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
    public boolean partyDuplicatePreStart = false; // check once at "Starting in 4 seconds." with class picks from chat + a title
    public boolean hideCheapCoins = true;          // Hide the spinning coin item entities from coin talismans
    public boolean warpCooldown = true;            // 30s warp/re-queue cooldown timer after a floor starts
    public boolean warpCooldownPerPlayer = false;   // one warp cooldown line per player who entered
    public boolean warpCooldownOwnOnly = false;     // per-player mode: only your own cooldown
    public boolean warpCooldownSound = false;       // ding when a warp cooldown ends
    public float warpCooldownVolume = 1.0f;
    public boolean witherShieldTimer = false;       // Hyperion/wither-blade Wither Impact cooldown timer
    public boolean witherShieldHud = false;         // own movable HUD with READY instead of the timers line
    public boolean witherShieldHideReady = false;
    public boolean witherShieldCompact = false;     // numbers only, no "Shield:"
    public boolean witherShieldChime = false;       // chime when the shield is ready again
    public int witherShieldHudX = 200;
    public int witherShieldHudY = 150;
    public float witherShieldHudScale = 1.0f;
    public boolean tacticalInsertionTimer = false;  // 3s Tactical Insertion timer on right-click
    public boolean tacticalHud = false;             // own movable HUD, green/yellow/red
    public int tacticalHudX = 200;
    public int tacticalHudY = 162;
    public float tacticalHudScale = 1.0f;
    public boolean tacticalWaypoint = false;        // box where the tactical sends you back to
    public com.teslamaps.render.EspStyle tacticalWaypointEsp = new com.teslamaps.render.EspStyle("50AA55FF", com.teslamaps.render.EspStyle.BOTH);
    public boolean invincibilityTimer = true;       // Bonzo / Spirit / Phoenix immunity + cooldown block
    public boolean invincibilityAnnounce = true;    // Announce mask/pet procs to party chat ("Bonzo Procced!")
    public boolean invincibilityAnnounceCount = false; // add "(2/3)" used-masks counter to the announce
    public boolean invincibilityTitle = false;       // title alert on a mask/pet proc
    public String invincibilityShow = "Default";     // Default, Always, Any, When Active, On Cooldown
    public boolean invincibilityHideBonzo = false;
    public boolean invincibilityHideSpirit = false;
    public boolean invincibilityHidePhoenix = false;
    public boolean invincibilityOnlyDungeon = false; // procs + HUD only inside dungeons
    public boolean invincibilityOnlyBoss = false;    // HUD only in the boss arena
    public boolean invincibilityEquippedMarker = false; // colored bar next to the worn mask
    public String invincibilityEquippedColor = "AA00AA";
    public boolean invincibilityItemOverlay = false; // cooldown drawn over the mask items
    public boolean invincibilityItemDurability = false; // overlay as durability bar instead of vertical fill
    public boolean invincibilityPhoenixSlot = false; // phoenix cooldown on the top-left inventory slot
    public String invincibilityItemColor = "AA262626";
    public boolean spiritPetReminder = true;        // Remind to swap off the Spirit pet when the boss starts
    public boolean purplePadTimer = true;          // F7 purple-pad countdown after Storm's call
    public boolean necronDropTimer = false;        // F7 Necron drop countdown ("your journey ends now")
    public boolean goldorTickTimer = false;        // F7 P3 device/terminal 60-tick pulse (+104t start timer)
    public boolean stormPadTimer = false;          // F7 Storm pad 20-tick pulse
    public boolean lightningTimer = false;         // F7 Storm lightning countdown (560t)
    public boolean lightningTimerTitle = false;    // replace Hypixel's 4/6 lightning title with a synced countdown
    public boolean secretTickTimer = false;        // Secret spawn tick (20-tick cycle, clear only)
    public String tickTimerFormat = "Seconds";     // Seconds, Ticks, Both — display format for tick timers
    public boolean tickTimerNoPrefix = false;      // tick timers without "Tick:"/"Pad:" prefix
    public boolean tickTimerNoSuffix = false;      // tick timers without s/t suffix
    public boolean bossStartTimers = false;        // F7 Maxor (167t) / Storm (120t) start countdowns
    public boolean deathTickTimer = false;         // 40-tick death cycle until 6s after run start
    public boolean tickTimersSeparate = false;     // each F7 tick timer as its own movable HUD
    public int goldorTickHudX = 10;
    public int goldorTickHudY = 150;
    public float goldorTickHudScale = 1.0f;
    public int stormPadHudX = 10;
    public int stormPadHudY = 162;
    public float stormPadHudScale = 1.0f;
    public int necronDropHudX = 10;
    public int necronDropHudY = 174;
    public float necronDropHudScale = 1.0f;
    public int lightningHudX = 10;
    public int lightningHudY = 186;
    public float lightningHudScale = 1.0f;
    public int purplePadHudX = 10;
    public int purplePadHudY = 198;
    public float purplePadHudScale = 1.0f;
    public int bossStartHudX = 10;
    public int bossStartHudY = 210;
    public float bossStartHudScale = 1.0f;
    public int deathTickHudX = 10;
    public int deathTickHudY = 222;
    public float deathTickHudScale = 1.0f;
    public boolean colorPortal = false;             // Recolor the blood portal by score (red/gold/green)
    public String colorPortalLow = "A0FF1010";      // portal color below 270
    public String colorPortalS = "A0FFD700";        // portal color for S
    public String colorPortalSPlus = "A040FF00";    // portal color for S+
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
    public boolean realBeaconBeam = false;         // Vanilla textured beacon beam instead of a 2 px line
    public boolean glowRibbons = false;            // Tracers/lines as glowing camera-facing bands
    public float glowRibbonWidth = 1.0f;
    public boolean waypointLabelBackground = false;
    public boolean blockOverlay = true;            // Outline the block you're looking at
    public String blockOverlayColor = "FFFFFF";     // Block overlay outline colour
    public String blockOverlayStyle = "Outline";     // Outline / Filled / Filled + Outline
    public String blockOverlayFillColor = "40FFFFFF";
    public float blockOverlayLineWidth = 3.0f;
    public boolean blockOverlayThroughWalls = false;
    public boolean blockOverlayExactShape = false;   // Every box of the shape (stairs, fences) instead of its bounds
    public boolean blockOverlayHideVanilla = false;  // Cancel the vanilla black outline
    public boolean blockOverlayHideEtherwarp = false; // Hide while the etherwarp guess box shows
    public boolean chatWaypoint = true;            // Render a waypoint beam from coords in chat
    public boolean chatWaypointMoreFormats = false; // Also "x y z", "x, y, z" in player chat + Inquisitor lines
    public boolean chatWaypointIgnoreAllChat = false;
    public boolean chatWaypointIgnoreOwn = false;
    public boolean chatWaypointDetails = false;     // Sender, area and distance in the label
    public boolean chatWaypointRemoveOnArrival = false; // Drop the waypoint within 5 blocks
    public String chatWaypointColor = "AA00FFFF";
    public boolean chatWaypointShareArea = false;   // /tmap sendcoords appends "| <area>"
    public boolean npcFinder = true;               // /tmap npc - NPC search + warp/track waypoints
    public boolean npcNearestWarp = false;         // warp to the closest unlocked warp instead of the island default
    public boolean npcWarpTwice = false;
    @Global
    public int npcWaypointKey = -1;                // over an NPC item: track, Shift = warp; in world: open finder / Shift = warp
    @Global
    public java.util.Map<String, java.util.Set<String>> npcLockedWarps = new java.util.HashMap<>(); // sb profile -> warps not unlocked

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
    public boolean orderedImportedStyle = false;    // Use imported waypoint names/colors
    public boolean orderedSetupMode = false;        // Show nearby waypoints + line of sight while building a route
    public float orderedSetupRange = 16.0f;         // Radius for setup mode
    public float orderedSetupLineThickness = 2.0f;  // Setup-mode line thickness
    public boolean orderedSneaking = true;          // Eye height used for the setup line (sneaking = AOTV)
    public boolean orderedAutoSkipForward = false;  // Jump ahead when reaching a later waypoint
    public boolean orderedSkipAir = false;          // Skip waypoints whose block is air
    public boolean orderedAutoUnload = false;       // Unload the route on world change
    public boolean orderedAutoLoadByArea = false;   // Load a saved route named like the current area
    public boolean rareDropEffects = true;         // Item animation + particles on RARE DROP! lines
    public boolean rareDungeonDropEffects = true;  // Same for dungeon chest rewards
    public boolean rareDyeDropEffects = true;      // Same for your own dye drops
    public float rareDropMinValue = 0f;            // also play for any parsed drop worth this many million coins (0 = off)
    public boolean rareDropText = true;            // Drop line + item name on screen during the animation
    public boolean rareDropPrice = true;           // Append what the drop is worth to that line
    public boolean showSelectedPet = true;         // Highlight the spawned pet in the Pets menu
    public boolean petShiftTakeOut = true;         // Pets menu: right-click needs shift
    public boolean simonSaysProgress = true;       // Show Simon Says progress (X/Y) in the timer HUD
    public boolean customHypeSound = false;         // Replace the wither-blade explosion sound
    public String customHypeSoundType = "NOTE_IRON_XYLO"; // which sound replaces the wither-blade explosion
    public float customHypeSoundVolume = 1.0f;     // Volume for the custom hype sound (0.0 - 20.0)
    public float customHypeSoundPitch = 1.0f;      // Pitch for the custom hype sound (0.5 - 2.0)
    public boolean wardrobeKeybinds = true;        // Hotbar keys 1-9 switch wardrobe slots in the Wardrobe GUI
    public boolean wardrobeKeepEquipped = false;   // pressing the key of the worn set does nothing
    public boolean wardrobeShowKeys = false;       // key name on each wardrobe / loadout button
    public boolean leapCounter = false;            // F7: count teammates standing at your spot
    public boolean leapCounterSpots = false;       // count only at the known leap spots, with target count
    public boolean leapCounterAlert = false;       // title + sound once every expected player leaped
    public boolean bossbarHealth = true;           // Append boss HP numbers to the bossbar name (Watcher/Thorn/F7)

    public boolean tooltipFitScreen = true;        // Shrink tooltips taller than the screen instead of cutting the top off
    public boolean enchantAbbrev = true;           // Write enchant + level onto enchanted book icons (Leg I)
    public boolean enchantColors = true;           // Recolor enchantments in item lore by level
    public boolean enchantLevelsAsNumbers = false; // Show enchant levels as numbers instead of roman numerals
    public String enchantLayout = "Normal";        // Normal / Compressed / Stacked
    public boolean enchantHideDescriptions = false;
    public boolean enchantStackingProgress = false;
    public String colorEnchantAbbrev = "55FFFF";          // Book abbreviation colour
    public String colorEnchantAbbrevUltimate = "FF55FF";  // Book abbreviation colour for ultimates
    public String colorEnchantAbbrevLevel = "FFFFFF";     // Book abbreviation level colour
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
    public boolean enchantCustomRules = false;
    public List<String> enchantColorRules = new ArrayList<>();   // NEU format name:cmp:level:colour:mods
    public boolean translucentLava = false;        // render lava with water textures + tint
    public String colorTranslucentLava = "3F76E4";
    public boolean tintedWater = false;            // custom tint on water
    public String colorTintedWater = "3F76E4";
    public boolean hotspotRadar = true;            // guess the hotspot from the radar particles
    public boolean hotspotLine = true;             // tracer from the crosshair to the guess
    public boolean hotspotBeam = false;            // beacon beam at the guess
    public String colorHotspot = "#FF55FF";
    public boolean hotspotShare = false;           // announce new hotspots to the party (needs a rod in hand)
    public boolean hotspotShareWaypoints = true;   // waypoints for hotspots shared by party members
    public boolean hotspotClosedTitle = false;     // title + bell when the nearest hotspot despawns
    public String hotspotShareChannel = "Party";
    public boolean hotspotShareManual = false;
    public boolean hotspotShareXyz = false;
    public boolean hotspotShareAllIslands = false;
    public boolean hotspotShareFilter = false;
    public boolean hotspotPerkSeaCreature = true;
    public boolean hotspotPerkFishingSpeed = true;
    public boolean hotspotPerkDoubleHook = true;
    public boolean hotspotPerkTreasure = true;
    public boolean hotspotPerkTrophy = true;
    public boolean hotspotPerkShard = true;
    public boolean hotspotFoundAlert = false;
    public boolean hotspotShareTracer = false;
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
    public boolean bobberTimerWorld = false;
    public boolean bobberTimerThreshold = false;
    public String colorBobberTimer = "#FFFFFF";
    public String colorBobberTimerLate = "#FF5555";
    public boolean bobberTimerPing = false;
    public boolean bobberTimerSlug = false;

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
    public boolean noCursorResetForever = false;   // ignore hold time, restore until the next menu
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
    public boolean searchbarCtrlF = false;         // ctrl+f / ctrl+k focuses the searchbar
    public boolean searchbarHidden = false;        // box only appears after ctrl+f
    public String searchbarMode = "Fill";          // Fill / Outline
    public String colorSearchbarMatch = "#6655FF55";
    public boolean searchbarNameOnly = false;      // skip the lore when searching
    public boolean scrollableTooltip = true;       // scroll long item tooltips with the mouse wheel
    public boolean tooltipScrollHorizontal = false; // shift + wheel moves sideways
    public boolean tooltipScrollZoom = false;       // ctrl + wheel scales the tooltip
    public boolean tooltipScrollInvert = false;
    public boolean tooltipScrollKeep = false;       // keep the offset when hovering another item
    public int tooltipScrollStep = 20;
    public float tooltipZoom = 1f;
    public boolean compactorPreview = true;        // list personal compactor/deletor contents in the tooltip
    public boolean estimatedValue = true;          // show estimated item value (bazaar) in tooltips
    public boolean itemValueFixedPos = false;       // drag it in /tmap gui instead of pinning it to the container
    public int itemValueX = 5;
    public int itemValueY = 60;
    public float itemValueScale = 1.0f;
    public boolean itemValueGui = false;            // show a value-breakdown panel beside the inventory when hovering an item
    public int itemValueScreenKey = -1;              // over a slot: pinned, scrollable value breakdown screen
    public int itemValueHoverDelay = 500;           // ms on the same slot before the first breakdown of an inventory shows
    public int itemValueEnchantmentsCap = 7;        // only list the top # most expensive enchantments
    public int itemValueStarMaterialCap = 3;        // only list the top # most expensive star materials
    public boolean itemValueExactPrice = false;     // exact total instead of the compact number
    public boolean itemValueIgnoreHelmetSkins = false; // leave helmet skins out of the total
    public boolean itemValueIgnoreArmorDyes = false;   // leave armor dyes out of the total
    public boolean itemValueIgnoreRunes = false;       // leave runes out of the total
    public String itemValuePriceSource = "BZ Instant Sell"; // BZ Instant Buy / BZ Instant Sell / Lowest BIN / NPC Sell
    public boolean priceCraftFallback = false;      // price unknown items by their NEU raw craft cost
    public boolean itemValueShowBin = true;         // extra "Lowest BIN" line in the value breakdown
    public boolean priceLineHideInstant = false;    // drop the BZ insta buy/sell lines
    public boolean priceLineOrders = false;         // best buy order / sell offer lines
    public boolean priceLineNpc = false;            // NPC sell price line
    public boolean priceLineCraft = false;          // raw craft cost line (NEU recipes)
    public boolean priceLineEach = false;           // "total coins (each)" in aligned columns
    public boolean priceLineRequireKey = false;     // price lines only while priceLineKey is held
    public int priceLineKey = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
    public boolean containerValue = false;          // show total container value + top 5 items in a side panel
    public boolean containerValueGroup = false;     // same items in one row
    public boolean containerValueHover = false;     // hover a row: highlight its slots + value breakdown
    public int containerValueTop = 5;
    public int containerValueHideBelow = 0;         // k coins, 0 = off
    public boolean containerValueFreePos = false;   // drag it in /tmap gui instead of pinning it to the container
    public int containerValueX = 5;
    public int containerValueY = 60;
    public float containerValueScale = 1.0f;
    public boolean backpackPreview = true;         // cache + show backpack/ender chest contents on hover (data in storage.json)
    public boolean backpackPreviewShift = false;   // only while shift is held
    @Global
    public int backpackPreviewFreezeKey = -1;      // pins the preview so its items can be hovered
    public boolean itemSearchIndex = true;          // remember opened containers so /tmap search can find items anywhere
    @Global
    public int itemSearchKey = 79;                  // opens /tmap search (default O), -1 = unbound
    public boolean islandChestTracker = false;      // private island only: real chests, large chests split, broken chests removed
    public boolean itemSearchGfsClick = false;      // right-click a sack hit in /tmap search -> /gfs prefilled
    public boolean customStorageOverlay = false;    // replace the vanilla "Storage" menu with a custom all-in-one view
    public boolean storageShowNames = false;         // show page/backpack name headers in the custom storage view
    public float storageOverlayScale = 1.0f;        // overall size multiplier for the custom storage overlay (0.5 - 1.5)
    public boolean storageVanillaAfterSwap = false;  // after placing/removing a backpack, open the next storage menu in vanilla once
    public boolean storageApiFill = false;           // empty storage cache: load pages from the skyblock api once
    public int storageColumns = 3;                   // page columns in the custom storage overlay (1 - 10)
    public int storageScrollSpeed = 20;              // px per wheel notch
    public boolean storageCompact = false;           // masonry: short backpacks leave no gaps
    public boolean storageCenterActive = false;      // scroll to the open page when the overlay opens
    public boolean storageSearchFocus = false;       // search box is focused on open
    public boolean storageRenamePages = false;       // click a page title to rename it (& color codes)
    @Global
    public java.util.Map<String, String> storagePageNames = new java.util.HashMap<>();
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
    public boolean safariHudPanel = false;          // run HUD as a panel with bars, also "ready" / "last run"
    public boolean safariMissingPanel = false;      // own panel: what the biome you stand in still needs
    public boolean safariMissingNear = false;       // "n near" = critters in line of sight, plus mounds in sight
    public int safariMissingX = 300;
    public int safariMissingY = 20;
    public float safariMissingScale = 1.0f;
    public boolean safariChecklist = true;          // per-biome critter checklist under the HUD
    public String safariChecklistBiome = "Auto";    // Auto = the biome you are standing in
    public boolean safariChecklistIcons = true;     // check/cross icons, off = colour the name only
    public boolean safariCritterESP = false;         // box critters, coloured by shard rarity
    public boolean safariNames = false;             // name label over every critter, not just the rare ones
    public boolean safariTracers = false;           // tracer to labelled critters
    public boolean safariFilled = true;             // also fill the safari boxes
    public boolean safariLegitMode = false;         // depth check: nothing drawn through walls
    public boolean safariOnlyMissing = false;       // only box critters nobody caught yet this run
    public boolean safariHideyhoTracer = true;      // tracer to Hideyho even with the general ones off
    public boolean safariNestESP = false;            // box the bee nests that spawn Honeybugs
    public boolean safariRecatchSpot = false;       // mark where an escaped critter comes back
    public boolean safariWallMarkers = false;       // mark the intact Snoozle / Troodon walls
    public boolean safariWallSight = false;         // a wall only counts as broken once seen gone
    public boolean safariWallLabels = false;        // "Snoozle wall 34m" over each marker
    public boolean safariEncounterBanner = false;   // big READY / STARTED / DONE banner per encounter
    public boolean safariBannerGemzie = true;
    public boolean safariBannerWumpa = true;
    public boolean safariBannerDoomspiral = true;
    public boolean safariBannerBiomeOnly = false;
    public String safariBannerChannel = "Nobody";
    public boolean safariSparklingAlert = true;     // alert on the WAX_ON particles of a sparkling critter
    public String safariSparklingChannel = "Nobody"; // post the sparkling coords: Nobody / Party / All
    public boolean safariSparklingScreen = false;   // full-screen SPARKLING! wash
    public boolean safariSparklingVisible = false;  // only sparklings with a free line of sight
    public boolean safariSparkleForce = true;       // render WAX_ON sparkles at any distance/particle setting
    public boolean safariAnnounceBiomeDone = true;  // alert when every critter of a biome is caught
    public boolean safariAnnounceUniqueDone = true; // alert when the whole dex is complete
    public boolean safariWarnNotFull = true;        // warn shortly after entry when fewer than 4 players are in
    public String safariShareTimeChannel = "Nobody"; // post those completion times: Nobody / Party / All
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
    public String colorSafariWallTroodon = null;    // null = same as colorSafariWall
    // Foraging (Galatea / Moonglade Marsh + Torrhus Canyon)
    public boolean foragingMutePhantoms = false;    // silence Phantoms in Moonglade Marsh
    public boolean foragingMuteFusion = false;      // silence the Fusion Machine in Moonglade Marsh
    public boolean foragingMuteTreeBreak = false;   // silence the tree-break (creaking death) sound
    public boolean foragingMuteTreeBreakGalatea = false; // also mute it on the foraging islands
    public boolean foragingHideLottery = false;     // hide Lottery/Beekeeper buff messages off the foraging islands
    public boolean foragingCompactGifts = true;     // squash the whole TREE GIFT block into one line
    public boolean foragingGiftHideUncommon = false; // compact gift line: leave out these bonus drop categories
    public boolean foragingGiftHideBooks = false;
    public boolean foragingGiftHideBoosters = false;
    public boolean foragingGiftHideShards = false;
    public boolean foragingGiftHideRunes = false;
    public boolean foragingGiftHideMisc = false;
    public boolean foragingGiftHideMobs = false;
    public boolean foragingHideSweepDetails = false; // hide the Sweep Details / Toughness / penalty lines
    public boolean foragingCompactSweep = false;     // one sweep line with the breakdown in its hover
    public boolean floorDrops = false;               // floor drops on Galatea / Torrhus / Safari, depth tested
    public boolean floorDropsSafariBiome = false;    // Safari: only the biome you stand in
    public String floorDropsColor = "55FF55";
    public boolean foragingCleanTreeView = false;   // hide the floating blocks from chopping
    public boolean foragingHideRuneEffects = false; // hide the floating blocks from rune effects
    public boolean rareCatchAlert = false;          // title + sound when a rare sea creature is caught
    public String rareCatchRarity = "Rare Flag";    // Rare Flag / Epic+ / Rare+
    public boolean rareCatchName = true;            // name the creature in the title
    public String rareCatchSound = "NOTE_PLING";
    public float rareCatchVolume = 1.0f;
    public boolean rareCatchAnnounce = false;       // "I caught a X!" to party chat
    public java.util.Map<String, Boolean> seaCreatureAlert = new java.util.HashMap<>();    // per-creature override of Counts As Rare
    public java.util.Map<String, Boolean> seaCreatureAnnounce = new java.util.HashMap<>();
    public boolean rareCatchKeySwap = false;        // attack->O, use->I for a moment after a rare catch
    public float rareCatchKeySwapSeconds = 2.0f;    // 0-5s swap window
    public boolean rareCatchKeySwapAll = false;     // also trigger on party members' catch announces

    public boolean umbrellaWarning = false;         // alert before and when the Umberella runs out
    public int umbrellaWarnBefore = 30;             // seconds of warning, 0 = only on despawn
    public boolean umbrellaWarningTitle = false;
    public String umbrellaWarningSound = "NOTE_PLING";
    public float umbrellaWarningVolume = 1.0f;      // 0 = silent
    public boolean deployableOverlay = false;       // HUD with the time left on active deployables
    public boolean deployableHighestTierOnly = false; // hide a Radiant while an Overflux is up
    public boolean deployableIconMode = false;      // one panel with the strongest deployable and its big head icon
    public boolean deployableHideParticles = false; // drop the orbs' own villager/dust/flame particles
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
    public boolean chatHistoryPersist = false;      // save the chat on quit and restore it on the next launch
    public boolean chatTabs = false;                // tab bar on the chat screen (All / Guild / DM / Party)
    public int chatTabPosition = 0;                 // 0 = above chat, 1 = under chat, 2 = right of chat
    public boolean queenAntWarning = true;          // title + sound when a Queen Ant spawns from an anthill
    public boolean queenAntLine = true;             // crosshair line to the queen
    public String queenAntSound = "TM_QUEEN_ANT";
    public float queenAntVolume = 1.0f;             // 0 = silent
    public boolean honeyhiveReminder = false;       // remind when honeyhives are lootable again
    public boolean honeyhiveQueenBeeTitle = true;   // title on a Queen Bee instant refill
    public boolean honeyhiveRemindOutside = false;  // also remind outside Torrhus Canyon
    public boolean honeyhiveHelper = false;         // per-hive refill tracking off the hive nametags
    public boolean honeyhiveDisplay = false;        // hive list HUD, click a row for its waypoint
    public boolean honeyhiveDisplayOutside = false;
    public float honeyhiveMaxLines = 12f;
    public boolean honeyhiveReadyWaypoints = false;
    public boolean honeyhiveReadySound = false;
    public int honeyhiveX = 8;
    public int honeyhiveY = 180;
    public float honeyhiveScale = 1.0f;
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
    public java.util.Map<String, String> greenhousePresets = new java.util.LinkedHashMap<>(); // name -> code or link
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
    @Global
    public java.util.List<String> greenhouseMutations =
            new java.util.ArrayList<>(java.util.List.of("TIMESTALK")); // plants that appear by mutation, not planted
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
    public boolean contestAlertStart = false;       // "has started!" on every contest start
    public boolean contestAlert5Min = false;        // no ticket yet and the contest ends in 5 min
    public boolean contestAlert90Sec = false;       // no ticket yet and the contest ends in 1:30
    public boolean contestAlertChat = true;
    public boolean contestAlertTitle = false;
    public String contestAlertSound = "NOTE_PLING";
    public float contestAlertVolume = 1.0f;
    public boolean contestAnnounceParty = false;    // "Contest Started" to party chat on every contest start
    public boolean contestTicketAnnounce = false;   // "Ticket Acquired!" once the Miria score clears the threshold
    public int contestTicketScore = 250;            // score that earns the ticket in Miria's Contest
    public boolean contestHideAtTicket = true;      // Drop the foraging line once the ticket score is in

    public boolean hoveredSlotOutline = true;       // outline the slot under the cursor in every container
    public String colorHoveredSlot = "#FF00FF";     // hovered slot outline color
    public int hoveredSlotThickness = 1;            // outline thickness in pixels
    public float hoveredSlotFill = 0f;              // extra fill over the hovered slot (0 = off)
    public boolean slotLock = true;                // lock inventory slots from being moved/dropped/sold
    @Global
    public int slotLockKey = 76;                    // keybind (over a slot) to toggle its lock
    public String slotLockStyle = "Box";            // Box = red fill + outline, Icon = padlock
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
    public boolean slotBindsAllContainers = false;  // binds also work in chests, storage, wardrobe ...
    public boolean slotBindsPerProfile = false;     // separate bind sets per skyblock profile
    public boolean slotBindsAreaSwitch = false;     // pick the bind profile by area
    @Global
    public java.util.Map<String, String> slotBindsAreaProfiles = new java.util.HashMap<>();
    public boolean solveWaterBoard = true;          // Show solution for Water Board puzzle
    public boolean waterBoardTracers = true;        // Draw tracers to next lever
    public boolean waterBoardColorStages = false;
    public int waterBoardSolutionSet = 0;
    public boolean solveIceFill = true;             // Show Ice Fill (F7) path solution
    public boolean iceFillOptimized = true;         // Use optimized (harder/faster) ice fill paths
    public boolean iceFillDynamic = false;
    public boolean iceFillGradient = false;
    public float iceFillLineWidth = 3f;
    public boolean icePathSolver = true;            // Draw the silverfish path in the Ice Path puzzle
    public boolean icePathDynamic = false;
    public String colorIcePathNext = "00FF00";
    public String colorIcePathRest = "FF0000";
    public boolean puzzleTimers = true;            // Report how long each puzzle took to solve
    @Global
    public java.util.Map<String, Double> puzzlePbs = new java.util.HashMap<>(); // per-puzzle personal bests (seconds)
    public String colorIceFill = "55FFFF";          // Ice Fill path color (aqua)
    public String colorIceFillEnd = "FF5555";
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
    public boolean witherDragonPaulAuto = false;     // Paul buff from the election API (Benediction perk)
    public boolean witherDragonCustomPrio = false;   // use the priority strings below instead of the built-in order
    public String witherDragonSplitPrio = "ogrbp";   // bers team <- ... -> arch team, split
    public String witherDragonNoSplitPrio = "robpg"; // no split
    public String witherDragonBersTeam = "bmh";      // classes that split with bers (a b h m t)
    public boolean witherDragonShowHigh = false;     // "HIGH" in the spawn title when the dragon spawns high
    public int witherDragonAlertTicks = 20;          // spawn title duration
    public boolean witherDragonAlertMute = false;    // no sound with the spawn title
    public boolean dragonBoxes = true;             // ESP box around each live wither dragon (by colour)
    public boolean dragonHealth = true;            // Show each dragon's health % above it
    public boolean hideDyingDragons = true;        // Hide a dragon once it's dying (health <= 0)
    public boolean witherHighlight = false;        // Highlight F7/M7 device-phase withers
    public boolean witherHighlightBox = true;       // Filled box (vs outline) for the wither highlight
    public String witherHighlightColor = "00FFFF";  // Wither highlight colour
    public boolean witherHighlightGlow = false;     // outline glow on the current boss wither instead of a box
    public boolean witherHighlightPerBoss = false;  // colour by the bossbar name (Maxor/Storm/Goldor/Necron)
    public String witherColorMaxor = "5804A4";
    public String witherColorStorm = "00D0FF";
    public String witherColorGoldor = "FFFFFF";
    public String witherColorNecron = "FF0000";

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
    public boolean kickedTimer = true;             // Kick handling: center wait warning + /pc kicked
    public boolean kickedPartyMessage = true;      // Auto-send "/pc kicked" when a join kick happens
    public int kickedWaitSeconds = 60;             // wait before rejoining after a kick
    public boolean kickedTitle = false;            // "Try rejoining SkyBlock now!" title when the wait is over
    public int kickedWarnX = 150;                  // Kick wait warning position X (draggable in HUD edit)
    public int kickedWarnY = 120;                  // Kick wait warning position Y
    public float kickedWarnScale = 2.0f;           // Kick wait warning scale (0.5 - 5.0)
    public boolean perfHud = false;                // Performance HUD (FPS/TPS/Ping), hidden while F3 is open
    public boolean perfHudFps = true;              // Show FPS in the performance HUD
    public boolean perfHudTps = true;              // Show server TPS in the performance HUD
    public boolean perfHudPing = true;             // Show ping in the performance HUD (silent ping every 2s)
    public boolean perfHudVertical = false;        // Stack the metrics vertically instead of one line
    public String perfHudLabelColor = "3296DC";    // Performance HUD label color (FPS:/TPS:/Ping:)
    public String perfHudValueColor = "FFFFFF";    // Performance HUD value color
    public int perfHudX = 5;                       // Performance HUD position X (draggable in HUD edit)
    public int perfHudY = 5;                       // Performance HUD position Y
    public float perfHudScale = 1.0f;              // Performance HUD text scale (0.5 - 5.0)
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
    public String colorBoulderClickBlock = "FFAA00";
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
    public boolean bazaarOrderStatus = false;         // Your/Co-op Bazaar Orders: filled / outbid / competitive / partial / expiry
    public String colorBazaarOrderFilled = "#5555FF";
    public String colorBazaarOrderOutbid = "#FF5555";
    public String colorBazaarOrderCompetitive = "#55FF55";
    public boolean progressHighlight = true;          // Highlight 100% progress lines in the Ways to Level Up menu

    // Guide Highlight: marks what is still missing in guide-style menus (ported from SkyHanni)
    public boolean guideHighlight = true;
    public String colorGuideMissing = "#FF5555";
    public boolean guideMissingTasks = true;          // SkyBlock Level Guide pages
    public boolean guideOneTime = true;               // bank, spooky, dojo, slaying, rock/dolphin, dungeons
    public boolean guideStory = true;                 // fast travels, story objectives, harp songs
    public boolean guideAbiphone = true;              // abiphone contacts
    public boolean guideJacob = true;                 // Jacob's Farming Contest guide
    public boolean guideEssence = true;               // essence shops
    public boolean guideMinion = true;                // uncrafted minions
    public boolean guideConsumable = true;            // consumable items
    public boolean guideMenu = true;                  // Tasks / Skill Related Tasks
    public boolean guideCollection = true;            // collections
    public boolean guideCentury = false;              // Century raffle tasks (event only)
    public boolean guideWikiClick = true;             // click a marked travel scroll / consumable for /wiki
    public String colorProgressDone = "#55FF55";      // Progress Highlight color
    public boolean commissionHighlight = true;        // Highlight finished commissions in the Commissions menu
    public String colorCommissionDone = "#55FF55";    // Commission Highlight color
    public boolean secretWaypointTracers = false;     // Draw tracers to waypoints
    public boolean secretWaypointHideCollected = true; // Hide waypoints when secret is collected
    public boolean secretWaypointEvents = false;      // hide only on real events (skull click, item pickup packet, bat sound)

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
    public boolean pvScrollPages = false;

    public String colorBackground = "CC1A1A1A";      // Map background
    public String colorUnexplored = "2A2A2A";        // Unexplored room
    public String colorNormal = "6B3A11";            // Normal explored room
    public String colorEntrance = "00AA00";          // Entrance/Start room
    public String colorBlood = "CC0000";             // Blood room
    public String colorTrap = "FF8800";              // Trap room
    public String colorPuzzle = "6600AA";            // Puzzle room
    public String colorPuzzleUnexplored = "3D1866"; // Unexplored puzzle room (grayed purple)
    public boolean mapDarkenUndiscovered = false;   // unentered rooms keep their type color, darkened
    public float mapDarkenPercent = 0.5f;
    public boolean mapGrayUndiscovered = false;
    public String colorFairy = "FF66CC";             // Fairy room
    public String colorMiniboss = "FFDD00";          // Miniboss/Yellow room
    public String mapMimicRoomStyle = "Bright Red";  // Bright Red / Tint / Custom
    public String colorMimicRoom = "BA4234";

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
    public String colorESPLostAdventurer = "FEE15C";
    public String colorESPDiamondGuy = "57C2F7";
    public boolean espStarSpecialColors = false;     // own colors for starred Fels / Withermancers
    public String colorESPStarredFel = "FF5555";
    public String colorESPStarredWithermancer = "555555";
    public float espLineWidthNormal = 2.0f;          // dungeon box ESP line widths per group
    public float espLineWidthSpecial = 2.0f;         // Fel, Withermancer
    public float espLineWidthMiniboss = 2.0f;        // LA, SA, Diamond Guy
    public String colorESPBat = "AAAAAA";            // Dungeon bats (gray)
    public String colorESPWitherKey = "000000";      // Wither key (black)
    public String colorESPBloodKey = "FF0000";       // Blood key (red)
    public String colorESPWitherDoor = "000000";     // Wither door (black)
    public String colorESPBloodDoor = "FF0000";      // Blood door (red)
    public String colorESPCustom = "00FFFF";         // Custom entries without their own colour (cyan)

    // feature gaps, waves 2+
    // w17-08-fis
    public boolean fishingChat = false;
    public boolean fishingChatShort = true;
    public boolean fishingChatMergeDh = true;
    public String fishingChatDhPosition = "Left";
    public boolean fishingChatHide = false;
    public boolean fishingChatDhSound = false;
    public String fishingChatDhSoundId = "NOTE_PLING";
    public float fishingChatDhVolume = 1.0f;
    public boolean fishedItemNames = false;
    public boolean fishedItemNamesBait = false;
    public boolean legionBobbin = false;
    public boolean legionBobbinOnlyEnchanted = false;
    public int legionBobbinX = 10;
    public int legionBobbinY = 200;
    public float legionBobbinScale = 1.0f;
    public boolean chargeBottleNotice = false;
    public boolean geyserBox = false;
    public String colorGeyserBox = "#80FF5555";
    public boolean geyserBoxLavaRod = true;
    public boolean geyserBoxHideParticles = false;
    public boolean lootshareSphere = false;
    public String lootshareSphereRarity = "Rare Flag";
    public boolean lotusPity = false;
    public int lotusPityX = 10;
    public int lotusPityY = 220;
    public float lotusPityScale = 1.0f;
    @Global
    public java.util.Map<String, int[]> lotusPityData = new java.util.HashMap<>();
    @Global
    public String lotusPityLast = "";
    // w16-03-sla
    public boolean slayerTierLock = false;
    public String slayerTierLockLast = "";
    public boolean slayerArmorProgress = false;
    public int slayerArmorProgressX = 10;
    public int slayerArmorProgressY = 160;
    public float slayerArmorProgressScale = 1.0f;
    public boolean voidgloomHud = false;
    public boolean voidgloomHudOnlyOwn = true;
    public int voidgloomHudX = 10;
    public int voidgloomHudY = 100;
    public float voidgloomHudScale = 1.0f;
    public boolean voidgloomNukekubiGlow = false;
    public com.teslamaps.render.EspStyle voidgloomNukekubiStyle = new com.teslamaps.render.EspStyle("FF00FF00", com.teslamaps.render.EspStyle.GLOW);
    public boolean voidgloomHideLaser = false;
    public String voidgloomHideLaserFor = "All bosses";
    public boolean voidgloomHideSummons = false;
    public boolean zealotSpawnPoints = false;
    public boolean zealotSpawnBoxes = false;
    public String zealotSpawnColor = "7F00FFFF";
    // w16-07-kuu
    public boolean kuudraDistance = false;
    public int kuudraDistanceX = 10;
    public int kuudraDistanceY = 230;
    public float kuudraDistanceScale = 1.0f;
    public boolean kuudraSplits = false;
    public boolean kuudraSplitsChat = true;
    public boolean kuudraSplitsPace = true;
    public boolean kuudraSplitsLag = true;
    public boolean kuudraSplitsPbTitle = true;
    public int kuudraSplitsX = 10;
    public int kuudraSplitsY = 60;
    public float kuudraSplitsScale = 1.0f;
    public java.util.Map<String, Long> kuudraSplitPbs = new java.util.HashMap<>(); // "T5:Build" -> best ms
    public boolean kuudraAlerts = false;
    public boolean kuudraAlertSound = true;
    public boolean kuudraAlertNoKey = true;
    public boolean kuudraAlertBuild = true;
    public boolean kuudraAlertUpgrades = true;
    public boolean kuudraAlertPickup = true;
    public boolean kuudraAlertIchor = true;
    public boolean kuudraAlertNoPre = true;
    public boolean kuudraAlertSos = true;
    public boolean kuudraAlertPhase = false;
    public boolean kuudraAlertSupplies = true;
    public boolean kuudraAlertCannonball = true;
    public boolean kuudraAlertSupplyState = true;
    public boolean kuudraSpawnDirection = false;
    public boolean kuudraTickTimers = false;
    public boolean kuudraTickTimersTicks = false;
    public int kuudraSupplyTimerX = 10;
    public int kuudraSupplyTimerY = 200;
    public float kuudraSupplyTimerScale = 1.0f;
    public int kuudraBuildTimerX = 10;
    public int kuudraBuildTimerY = 212;
    public float kuudraBuildTimerScale = 1.0f;
    public boolean kuudraNoPre = false;
    public boolean kuudraNoPreAnnounce = true;
    public boolean kuudraCratePriority = true;
    public boolean kuudraCratePriorityAdvanced = false;
    public boolean kuudraHider = false;
    public boolean kuudraHideTags = true;
    public boolean kuudraHideArmorStands = true;
    public boolean kuudraHideBossBar = true;
    public boolean kuudraTinyMobs = false;
    public float kuudraTinyMobsScale = 0.4f;
    public boolean kuudraHideTentacles = false;
    public String kuudraTentacleColor = "FFFFFF";
    // w16-02-sla
    public boolean slayerItemsOnGround = false;
    public int slayerItemsMinPrice = 0;
    public boolean slayerRewardsHighlight = false;
    public boolean tarantulaHighlights = false;
    public boolean tarantulaEggSacs = true;
    public String colorTarantulaEggSac = "FF55FF";
    public boolean tarantulaInvincible = true;
    public String colorTarantulaInvincible = "FF5555";
    public boolean soulflowDisplay = false;
    public int soulflowDisplayX = 5;
    public int soulflowDisplayY = 200;
    public float soulflowDisplayScale = 1.0f;
    public boolean lowSoulflowWarning = false;
    public int lowSoulflowThreshold = 100;
    public boolean hideSlayerSpawnParticles = false;
    public boolean hideSlayerParticlesEnchant = true;
    public boolean hideSlayerParticlesWitch = true;
    public boolean hideSlayerParticlesEffect = true;
    public boolean hideSvenPupNametags = false;
    public boolean compactSlayerChat = false;
    // w16-01-sla
    public boolean profitTrackerSlayerRng = false;
    public java.util.Map<String, Integer> slayerBossesSinceRng = new java.util.HashMap<>();
    public java.util.Map<String, Long> slayerLastRngMs = new java.util.HashMap<>();
    public boolean slayerBossSoon = false;
    public int slayerBossSoonPercent = 85;
    public boolean slayerBossSoonRepeat = false;
    public boolean slayerQuestWarning = false;
    public boolean slayerQuestWarningTitle = true;
    public boolean slayerLevelHud = false;
    public boolean slayerLevelHudKillTimes = false;
    public int slayerLevelHudX = 5;
    public int slayerLevelHudY = 200;
    public float slayerLevelHudScale = 1.0f;
    public boolean slayerRngDisplay = false;
    public boolean slayerRngDisplayWarnEmpty = false;
    public boolean slayerRngDisplayHideChat = false;
    public int slayerRngDisplayX = 5;
    public int slayerRngDisplayY = 240;
    public float slayerRngDisplayScale = 1.0f;
    public boolean voidgloomLaserTimer = false;
    public boolean slayerNametag = false;
    public String slayerNametagMode = "Own + Carries";
    public boolean slayerNametagShortName = true;
    public boolean slayerNametagCompact = false;
    public boolean slayerNametagHideOriginal = false;
    public boolean slayerNametagKillTimer = false;
    // w16-06-kuu
    public boolean kuuBuildHelper = false;
    public boolean kuuBuildHud = true;
    public int kuuBuildHudX = 10;
    public int kuuBuildHudY = 120;
    public float kuuBuildHudScale = 1.0f;
    public boolean kuuBuildCountdown = true;
    public boolean kuuBuildBeams = true;
    public boolean kuuBuildHideTags = false;
    public boolean kuuBuildBallistaText = false;
    public float kuuBuildStunPercent = 93f;
    public boolean kuuFreshTools = false;
    public boolean kuuFreshAnnounce = true;
    public boolean kuuFreshAnnouncePercent = false;
    public boolean kuuFreshHud = true;
    public int kuuFreshHudX = 10;
    public int kuuFreshHudY = 160;
    public float kuuFreshHudScale = 1.0f;
    public boolean kuuFreshOverhead = true;
    public boolean kuuFreshGlow = false;
    public String kuuFreshGlowColor = "55FF55";
    public boolean kuuDangerWarning = false;
    public boolean kuuDangerSound = true;
    public boolean kuuHideDamageTitle = false;
    public boolean kuuBlockPerks = false;
    public boolean kuuIchorPool = false;
    public String kuuIchorColor = "D260EEFF";
    public boolean kuuSupplyMessage = false;
    public boolean kuuElleHighlight = false;
    public com.teslamaps.render.EspStyle kuuElleEsp = new com.teslamaps.render.EspStyle("6655FFFF", com.teslamaps.render.EspStyle.BOTH);
    // w16-04-sla
    public boolean bloodfiendSpringLines = false;
    public boolean killerSpringSoundFix = false;
    public boolean slayerAttunementDemons = false;
    public boolean slayerBossTracer = false;
    public boolean slayerMinibossCocoon = false;
    public float slayerMinibossTracerWidth = 2.0f;
    public int slayerMinibossTracerRange = 0;
    public boolean slayerMinibossTracerSight = false;
    public boolean arachneQol = false;
    public boolean arachneHidePlayers = true;
    public boolean arachneWebClick = false;
    public boolean skillTrackerSlayerStats = false;
    // w16-00-sla
    public boolean slayerHideEndermanParticles = false;
    public boolean slayerHideMobNames = false;
    public boolean slayerHidePacifiedBlazes = false;
    public boolean slayerHideOthersRadiation = false;
    public boolean vengeanceTimer = false;
    public int vengeanceTimerX = 5;
    public int vengeanceTimerY = 215;
    public float vengeanceTimerScale = 1.0f;
    public float vengeanceProcSeconds = 6.0f;
    public boolean vengeanceDamageChat = false;
    public boolean slayerBlockNotSpawnable = false;
    public boolean slayerBossHud = false;
    public int slayerBossHudX = 5;
    public int slayerBossHudY = 240;
    public float slayerBossHudScale = 1.0f;
    public boolean slayerBossHudBar = true;
    // w16-05-sla
    public boolean muteBlazeSounds = false;
    public boolean muteWolfSounds = false;
    public boolean muteSpiderSounds = false;
    public boolean callMaddoxOnFail = false;
    public boolean voidgloomYangGlyphAlert = false;
    public boolean voidgloomBeaconHighlight = false;
    public boolean voidgloomBeaconPing = false;
    public boolean endermanNoHurtTint = false;
    public boolean endermanStageColors = false;
    public boolean endermanStopTeleport = false;
    // w15-01-min
    public boolean forgeReminder = false;
    public int forgeReminderDelay = 1;
    public String forgeReminderAction = "Warp";
    public boolean forgeReminderBusy = false;
    public boolean caveInTimer = false;
    public boolean caveInTimerShowTime = false;
    public boolean caveInTimerEstimate = false;
    public int caveInTimerX = 10;
    public int caveInTimerY = 150;
    public float caveInTimerScale = 1.0f;
    public boolean commissionScreen = false;
    public boolean commissionScreenHideBloat = true;
    public boolean flowstateHelper = false;
    public boolean flowstateCompact = false;
    public boolean flowstateColorTimer = true;
    public boolean flowstatePbMessage = true;
    public int flowstateAutoHide = -1;
    public int flowstateBest = 0;
    public int flowstateHelperX = 10;
    public int flowstateHelperY = 190;
    public float flowstateHelperScale = 1.0f;
    public boolean excavatorTracker = false;
    public boolean excavatorChat = false;
    public boolean excavatorFossilDust = true;
    public boolean excavatorPowder = true;
    public boolean excavatorPowderStack = false;
    public int excavatorPriceSource = 1;
    public int excavatorTrackerX = 10;
    public int excavatorTrackerY = 60;
    public float excavatorTrackerScale = 1.0f;
    public boolean gemstoneMoney = false;
    public String gemstoneMoneyTier = "Fine";
    public boolean gemstoneMoneyNpc = false;
    public boolean gemstoneMoneyPause = true;
    public int gemstoneMoneyTimeout = 30;
    public int gemstoneMoneyX = 10;
    public int gemstoneMoneyY = 230;
    public float gemstoneMoneyScale = 1.0f;
    public boolean glaciteColdOverlay = false;
    // w15-06-min
    public boolean carpetHighlight = false;
    public String carpetHighlightColor = "66FF5555";
    public boolean hotmPlanner = false;
    public java.util.Map<String, Integer> hotmPlanBase = new java.util.HashMap<>();
    public java.util.Map<String, Integer> hotfPlanBase = new java.util.HashMap<>();
    public java.util.Map<String, Integer> hotmPlanTarget = new java.util.HashMap<>();
    public java.util.Map<String, Integer> hotfPlanTarget = new java.util.HashMap<>();
    public boolean superprotectronHighlight = false;
    public com.teslamaps.render.EspStyle superprotectronEsp = new com.teslamaps.render.EspStyle("FFFF55FF", com.teslamaps.render.EspStyle.GLOW);
    public boolean vanguardGambling = false;
    public String vanguardGamblingMode = "Most Expensive";
    public boolean vanguardGamblingHideChat = true;
    public float vanguardGamblingTime = 3f;
    public boolean chWaypoints = false;
    public boolean chWaypointsFromChat = true;
    public boolean chWaypointsSubzone = true;
    public boolean chWaypointsPersist = true;
    public boolean bpsDisplay = false;
    public int bpsDisplayX = 10;
    public int bpsDisplayY = 120;
    public float bpsDisplayScale = 1.0f;
    public boolean commissionStatusIcons = false;
    // w15-03-min
    public boolean tunnelsMaps = false;
    public int tunnelsMapsX = 10;
    public int tunnelsMapsY = 120;
    public float tunnelsMapsScale = 1.0f;
    public boolean tunnelsMapsCommission = true;
    public int tunnelsMapsCampfireKey = -1;
    public boolean tunnelsMapsTravelScroll = false;
    public int tunnelsMapsNextKey = -1;
    public String tunnelsMapsColor = "55FFFF";
    public boolean tunnelsMapsDynamicColor = true;
    public int tunnelsMapsWidth = 4;
    public boolean tunnelsMapsLandmarks = true;
    public boolean tunnelsMapsDistanceFirst = false;
    public boolean tunnelsMapsNoFairy = false;
    public boolean dwarvenMinimap = false;
    public int dwarvenMinimapX = 10;
    public int dwarvenMinimapY = 10;
    public float dwarvenMinimapScale = 1.0f;
    public boolean dwarvenMinimapCommissions = true;
    public boolean effectivePowderChat = false;
    public int hotmPowderBuffLevel = 0;             // runtime: scanned off the HOTM menu
    public long skyMallPowderUntil = 0;             // runtime: Sky Mall powder buff seen in chat
    public boolean hollowsItemInfo = false;
    public boolean hollowsItemChambers = true;
    public boolean hollowsItemEggBlocks = true;
    public boolean powderTracker = false;
    public int powderTrackerX = 10;
    public int powderTrackerY = 90;
    public float powderTrackerScale = 1.0f;
    public boolean powderTrackerOnlyGrinding = false;
    public boolean powderTrackerNatural = false;
    public int powderTrackerPriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    public boolean powderHud = false;
    public int powderHudX = 10;
    public int powderHudY = 200;
    public float powderHudScale = 1.0f;
    public boolean powderHudDiff = true;
    public boolean npcSolvers = false;
    public boolean npcSolverPuzzler = true;
    public String npcSolverPuzzlerColor = "FF5555";
    public boolean npcSolverFetchur = true;
    public boolean npcSolverFossil = true;
    // w15-07-min
    public boolean hotmTokenStack = false;
    public boolean hotmPowderTooltip = false;
    public boolean hotmSkyMallDisplay = false;
    public boolean hotmSkyMallMiningOnly = true;
    public String hotmSkyMallPerk = "";
    public int hotmSkyMallDisplayX = 5;
    public int hotmSkyMallDisplayY = 175;
    public float hotmSkyMallDisplayScale = 1.0f;
    public boolean mineshaftWaypoints = false;
    public boolean mineshaftWpEntrance = true;
    public boolean mineshaftWpLadder = true;
    public boolean mineshaftWpCorpses = true;
    public boolean mineshaftAutoShareCorpses = false;
    public int mineshaftShareKey = -1;
    public boolean orderedAutoLoadShaft = false;
    public boolean orderedAutoUnloadShaft = false;
    public float orderedTraceThickness = 2.0f;
    public java.util.Map<String, Boolean> chScanStructures = new java.util.HashMap<>();
    public boolean chScanLandmarks = false;
    public boolean chScanOffThread = false;
    public boolean pigeonSwapper = false;
    public int pigeonSwapperDelay = 150;
    public int pigeonSwapperRandom = 150;
    public boolean ghostVisuals = false;
    public boolean ghostShow = true;
    public boolean ghostNametag = true;
    public boolean ghostHideCharged = true;
    // w15-08-sla
    public boolean slayerCarryMode = false;
    public int slayerCarryTier = 4;
    public boolean firePillarTitle = false;
    public boolean firePillarSound = false;
    public boolean blazeDaggerHud = false;
    public int blazeDaggerHudX = 5;
    public int blazeDaggerHudY = 190;
    public float blazeDaggerHudScale = 1.0f;
    public boolean blazeDaggerMarkShield = true;
    public boolean blazeDaggerHideTitle = false;
    public boolean infernoFirePitWarning = false;
    public boolean bloodfiendSteakTitle = false;
    public boolean bloodfiendTwinclawsTitle = false;
    public int bloodfiendTwinclawsDelay = 0;
    public boolean bloodfiendIchorHighlight = false;
    public String bloodfiendIchorColor = "AA0000";
    public String bloodfiendSpringColor = "FFAA00";
    public boolean slayerCarryTracker = false;
    public int slayerCarryTrackerX = 5;
    public int slayerCarryTrackerY = 60;
    public float slayerCarryTrackerScale = 1.0f;
    @Global
    public java.util.List<SlayerCarry> slayerCarries = new java.util.ArrayList<>();
    @Global
    public java.util.Map<String, Double> slayerCarryPrices = new java.util.HashMap<>();
    public boolean chatFilterHellion = false;

    public static class SlayerCarry {
        public String customer;
        public String type;
        public int requested;
        public int done;
        public double paid;
    }

    // w15-04-min
    public boolean mineshaftAnnounce = false;
    public boolean mineshaftAnnounceTitle = true;
    public boolean mineshaftAnnounceKeys = true;
    public boolean mineshaftAnnounceStats = true;
    public String mineshaftAnnounceChannel = "Nobody";
    public java.util.Map<String, Integer> mineshaftSinceType = new java.util.HashMap<>();
    public java.util.Map<String, Long> mineshaftLastType = new java.util.HashMap<>();
    public boolean miningNotify = false;
    public boolean miningNotifyMineshaft = true;
    public boolean miningNotifyScrap = true;
    public boolean miningNotifyGoblin = true;
    public boolean miningNotifyGhast = true;
    public boolean miningNotifyTitanium = false;
    public boolean miningNotifyTitaniumVisible = true;
    public boolean miningNotifyCold = true;
    public int miningNotifyColdAt = 50;
    public boolean miningNotifyRope = false;
    public int miningNotifyRopeAt = 90;
    public boolean miningNotifySkyMall = true;
    public boolean miningNotifySkyMallReset = false;
    public boolean miningNotifySound = true;
    public boolean miningEvents = false;
    public int miningEventsX = 10;
    public int miningEventsY = 140;
    public float miningEventsScale = 1.0f;
    public boolean miningEventsStarCult = true;
    public boolean miningEventsWarn = true;
    public int miningEventsWarnSeconds = 15;
    public boolean miningEventsRaffleBox = true;
    public boolean miningEventsMuteWind = false;
    public boolean corpseTracker = false;
    public int corpseTrackerX = 10;
    public int corpseTrackerY = 90;
    public float corpseTrackerScale = 1.0f;
    public String corpseTrackerShow = "All";
    public boolean corpseTrackerChat = true;
    public int corpseTrackerPriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    public boolean treasureChestTimer = false;
    public boolean treasureChestTimerColor = true;
    public int treasureChestTimerLines = 1;
    public int treasureChestTimerX = 10;
    public int treasureChestTimerY = 110;
    public float treasureChestTimerScale = 1.0f;
    public boolean powderChatFilter = false;
    public int powderChatFilterPowder = 1000;
    public int powderChatFilterEssence = 5;
    public boolean powderChatFilterCommon = true;
    public String powderChatFilterEggs = "Show All";
    public String powderChatFilterGems = "Flawed Up";
    public boolean powderChatFilterTool = true;
    public boolean powderChatFilterCompact = true;
    public boolean muteTreasureChest = false;
    public boolean muteTreasureChestDiscover = true;
    public boolean muteTreasureChestOpen = true;
    // w15-02-min
    public boolean kingTalismanHelper = false;
    public boolean kingTalismanOutsideMines = false;
    @Global
    public java.util.List<String> kingsTalkedTo = new java.util.ArrayList<>();
    public int kingTalismanX = 10;
    public int kingTalismanY = 140;
    public float kingTalismanScale = 1.0f;
    public boolean hotmPowderGoal = false;
    public boolean hotmPowderGoalTitle = false;
    public boolean hotmPowderGoalRepeat = false;
    public int hotmPowderGoalInterval = 5;
    @Global
    public java.util.Map<String, String> hotmPowderGoals = new java.util.HashMap<>();
    public boolean goldenGoblinHighlight = false;
    public com.teslamaps.render.EspStyle goldenGoblinEsp = new com.teslamaps.render.EspStyle("FF55FF55");
    public boolean enderNodeTracker = false;
    public boolean enderNodeOnlyTool = false;
    public int enderNodePriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    public int enderNodeTrackerX = 10;
    public int enderNodeTrackerY = 80;
    public float enderNodeTrackerScale = 1.0f;
    public boolean miningRedCarpets = false;
    public boolean miningDarkMist = false;
    public boolean muteHighHeat = false;
    public boolean excavatorHideDirtTooltip = false;
    public boolean excavatorHideAllTooltips = false;
    public boolean scrapGfsButton = false;
    public int scrapGfsAmount = 64;
    public boolean scrapGfsOnlyIfNone = true;
    public boolean scrapGfsBazaar = true;
    // w15-00-min
    public boolean donExpressoReminder = false;
    public boolean forgeSlotHud = false;
    public int forgeSlotHudX = 10;
    public int forgeSlotHudY = 230;
    public float forgeSlotHudScale = 1.0f;
    public boolean forgeSlotHudMiningOnly = false;
    public boolean forgeSlotHudShowLocked = false;
    public boolean forgeSlotReadyAlert = false;
    public java.util.List<String> forgeSlotData = new java.util.ArrayList<>();
    public boolean dwarvenOverlay = false;
    public int dwarvenOverlayX = 10;
    public int dwarvenOverlayY = 290;
    public float dwarvenOverlayScale = 1.0f;
    public boolean dwarvenOverlayCommissions = true;
    public boolean dwarvenOverlayPowders = true;
    public boolean dwarvenOverlayForge = true;
    public boolean dwarvenOverlayPickaxe = true;
    public boolean dwarvenOverlayStarCult = true;
    public boolean dwarvenOverlaySkyMall = true;
    public long skyMallDay = -1;
    public boolean metalDetectorSolver = false;
    public boolean metalDetectorAllTools = true;
    public boolean drillFuelDisplay = false;
    public boolean drillFuelBars = true;
    public boolean drillFuelHud = true;
    public int drillFuelHudX = 200;
    public int drillFuelHudY = 200;
    public float drillFuelHudScale = 1.0f;
    public boolean fossilSolver = false;
    public boolean commissionWaypointsGlaciteAlways = false;
    public boolean commissionWaypointsGlaciteNearest = false;
    public boolean mineshaftExitWaypoint = false;
    // w15-09-sla
    public boolean remainingSlayerKills = false;
    public int remainingSlayerKillsX = 5;
    public int remainingSlayerKillsY = 150;
    public float remainingSlayerKillsScale = 1.0f;
    public boolean slayerBossTransparency = false;
    public int slayerBossTransparencyLevel = 40;
    public boolean slayerBossTransparencyPlayers = false;
    public boolean slayerMagmaCubeTransparency = false;
    public int slayerMagmaCubeLevel = 40;
    public boolean blazeClearView = false;
    public boolean infernoTotemDisplay = false;
    public int infernoTotemPing = 0;
    public int infernoTotemX = 5;
    public int infernoTotemY = 190;
    public float infernoTotemScale = 1.0f;
    public boolean rev5TntPing = false;
    public boolean gummyWarning = false;
    public boolean gummyTimer = false;
    public boolean gummyTimerExpired = true;
    public long gummyTimerEnd = 0;
    public int gummyTimerX = 5;
    public int gummyTimerY = 205;
    public float gummyTimerScale = 1.0f;
    public boolean keeperWaypoints = false;
    public boolean keeperFocusMode = false;
    // w15-05-min
    public boolean muteMetalDetector = false;
    public boolean muteOrganDonor = false;
    public boolean wishingCompassSolver = false;
    public boolean pickobulusHelper = false;
    public boolean pickobulusOutline = true;
    public boolean pickobulusHud = true;
    public boolean pickobulusHideOnCooldown = false;
    public String colorPickobulus = "55FFFF";
    public int pickobulusHudX = 5;
    public int pickobulusHudY = 160;
    public float pickobulusHudScale = 1.0f;
    public boolean scathaAlerts = false;
    public boolean scathaWormTitle = true;
    public boolean scathaAliveSound = true;
    public boolean scathaCooldownTitle = true;
    public boolean scathaPetRewrite = true;
    public boolean scathaPetTitle = true;
    public boolean hotmLevelStack = false;
    public boolean precisionMiningHighlight = false;
    // w14-09-min
    public boolean darkMonolith = false;
    public boolean darkMonolithBox = true;
    public boolean darkMonolithBeacon = true;
    public boolean darkMonolithTitle = true;
    public boolean darkMonolithTracker = false;
    public int darkMonolithTrackerX = 10;
    public int darkMonolithTrackerY = 160;
    public float darkMonolithTrackerScale = 1f;
    public boolean nucleusChatFilter = false;
    public boolean nucleusFilterCrystalFound = true;
    public boolean nucleusFilterCrystalPlaced = true;
    public boolean nucleusFilterRunDone = true;
    public boolean nucleusFilterScavenge = true;
    public boolean nucleusFilterGuards = true;
    public boolean nucleusFilterRobot = true;
    public boolean nucleusFilterYolkar = true;
    public boolean nucleusFilterKeepers = true;
    public boolean hollowsPassHelper = false;
    public boolean hollowsPassTimer = false;
    public boolean hollowsPassAutoRenew = false;
    public long hollowsPassExpires = 0L;
    public int hollowsPassX = 10;
    public int hollowsPassY = 140;
    public float hollowsPassScale = 1f;
    public boolean chDeathWaypoint = false;
    public boolean commissionBlocks = false;
    public boolean commissionBlocksSneak = true;
    public String commissionBlocksColor = "Red";
    public boolean deepCavernsGuide = false;
    public int deepCavernsLookAhead = 3;
    public boolean nucleusNames = false;
    public boolean nucleusBarriers = false;
    public boolean nucleusBarriersFilled = false;
    // w14-08-min
    public String miningChestSummary = "Off";
    public boolean corleoneSpawnTitle = false;
    public boolean corpseKeyAnnounce = false;
    public boolean crystalHollowsOverlay = false;
    public boolean crystalHollowsOverlayOnlyHollows = true;
    public int crystalHollowsOverlayX = 10;
    public int crystalHollowsOverlayY = 220;
    public float crystalHollowsOverlayScale = 1.0f;
    public java.util.Map<String, Integer> chCrystalStates = new java.util.HashMap<>();
    public java.util.List<String> chPartsDelivered = new java.util.ArrayList<>();
    public boolean blockStrengthGuide = false;
    public boolean blockBreakPrediction = false;
    public boolean blockBreakPredictionSound = false;
    public boolean chAreaWalls = false;
    public boolean chAreaWallsNucleus = true;
    public boolean chAreaWallsHeat = true;
    // w14-05-gar
    public boolean contestCrops = false;
    public boolean contestCropsPlacement = true;
    public boolean contestCropsFetch = true;
    public boolean contestCropWarn = false;
    public int contestCropWarnSeconds = 120;
    public java.util.List<String> contestCropWarnFor = new java.util.ArrayList<>();
    public boolean contestCropWarnFlash = true;
    public boolean pestFinder = false;
    public boolean pestFinderBorders = false;
    public boolean pestFinderLabels = true;
    public boolean pestFinderHeldOnly = false;
    public boolean pestFinderNoPestTitle = false;
    public int pestFinderKey = -1;
    public int pestFinderX = 5;
    public int pestFinderY = 200;
    public float pestFinderScale = 1.0f;
    public boolean pestKillTracker = false;
    public boolean pestKillTrackerSprays = true;
    public boolean pestKillTrackerRates = true;
    public boolean pestKillTrackerCoins = true;
    public boolean pestKillTrackerHideDropChat = false;
    public int pestKillTrackerPriceSource = 1;
    public int pestKillTrackerX = 5;
    public int pestKillTrackerY = 260;
    public float pestKillTrackerScale = 1.0f;
    public boolean pestTrapWarn = false;
    public boolean pestTrapWarnFull = true;
    public boolean pestTrapWarnNoBait = true;
    public boolean pestTrapWarnUnplaced = true;
    public boolean pestTrapWarnChat = true;
    public boolean pestTrapWarnTitle = true;
    public int pestTrapWarnInterval = 60;
    public String pestTrapWarnSound = "NOTE_PLING";
    public float pestTrapWarnVolume = 1.0f;
    public boolean pestTrapRelease = false;
    public int pestTrapReleaseKey = -1;
    public boolean pestSpawnTitle = false;
    public boolean pestSpawnCompact = false;
    public boolean pestSpawnStats = false;
    public boolean pestSpawnChat = false;
    public boolean pestCountdownWardrobePause = false;
    public boolean pestFinneganCooldown = false;
    public int pestFinneganCooldownSeconds = 75;
    public boolean gardenWarpAliases = false;
    public int gardenWarpKey = -1;
    public int gardenSetspawnKey = -1;
    public int gardenBarnKey = -1;
    public boolean pestGlowContest = false;
    public boolean pestGlowVinyl = false;
    public String pestGlowVinylId = "";
    // w13-04-pv (master pvExtras lives in w13-05-pv)
    public boolean pvEliteRanks = true;
    public boolean pvGearScan = true;
    public boolean pvGreenhouse = true;
    public boolean pvFishingStats = true;
    public boolean pvMinions = true;
    public boolean pvMiscBags = true;
    public boolean pvDeepLinks = true;
    public boolean pvLevelHoppity = true;
    // w13-06-gar
    public boolean greenhouseModels = false;
    public boolean composterHud = false;
    public int composterHudX = 10;
    public int composterHudY = 200;
    public float composterHudScale = 1.0f;
    public boolean composterOutside = false;
    public int composterOutsideX = 10;
    public int composterOutsideY = 250;
    public float composterOutsideScale = 1.0f;
    public boolean composterLowWarn = false;
    public int composterLowMatter = 20000;
    public int composterLowFuel = 10000;
    public boolean composterEmptyWarn = false;
    public long composterEmptyAt = 0L;
    public java.util.Map<String, Integer> composterUpgrades = new java.util.HashMap<>();
    public boolean composterOverlay = false;
    public int composterPriceSource = 1;
    public String composterTimeUnit = "Hour";
    public String composterMatterItem = "";
    public String composterFuelItem = "";
    public boolean composterMenuNumbers = false;
    public boolean composterUpgradePrices = false;
    public boolean composterUpgradeHighlight = false;
    public boolean cropMilestoneHud = false;
    public int cropMilestoneHudX = 10;
    public int cropMilestoneHudY = 120;
    public float cropMilestoneHudScale = 1.0f;
    public int cropMilestoneGoal = 0;
    public boolean cropMilestoneOverflow = false;
    public boolean cropMilestoneWarn = false;
    public boolean mooshroomPerkHud = false;
    public int mooshroomPerkHudX = 10;
    public int mooshroomPerkHudY = 180;
    public float mooshroomPerkHudScale = 1.0f;
    public boolean farmingFortuneHud = false;
    public int farmingFortuneHudX = 10;
    public int farmingFortuneHudY = 100;
    public float farmingFortuneHudScale = 1.0f;
    public boolean farmingFortuneBonus = true;
    public boolean farmingFortunePhilipChat = false;
    public boolean cropFeverTimer = false;
    public boolean cropFeverAlert = false;
    public String cropFeverSound = "LEVEL_UP";
    public boolean visitorOfferColor = false;
    // w14-01-gar
    public boolean mutationsWebsite = false;
    public boolean gardenMuteComposter = false;
    public boolean plotPresetWarning = false;
    public boolean sprayonatorCooldownBar = false;
    public boolean hungryHikerSolver = false;
    public boolean treasureHunterSolver = false;
    public boolean visitorShoppingList = false;
    public boolean visitorShoppingPrices = true;
    public boolean visitorShoppingSacks = true;
    public boolean visitorShoppingBazaar = true;
    public boolean visitorShoppingIslands = false;
    public java.util.List<String> visitorIgnored = new java.util.ArrayList<>();
    public int visitorShoppingListX = 10;
    public int visitorShoppingListY = 160;
    public float visitorShoppingListScale = 1.0f;
    public boolean visitorOfferInfo = false;
    public boolean visitorOfferCopperRate = true;
    public boolean visitorOfferProfit = true;
    public boolean visitorRareRewardChat = true;
    public boolean visitorRewardGuard = false;
    public boolean visitorGuardRare = true;
    public boolean visitorGuardNew = true;
    public boolean visitorGuardCheapCopper = false;
    public boolean visitorGuardExpensiveCopper = false;
    public int visitorGuardCopperPrice = 6000;
    public boolean visitorGuardLowLoss = false;
    public boolean visitorGuardHighLoss = false;
    public int visitorGuardLossLimit = 300000;
    public int visitorGuardBypassKey = 341;
    public boolean visitorGuardOutline = true;
    public boolean visitorArrivalTitle = false;
    public boolean visitorArrivalWarp = false;
    public boolean visitorQueueFullTitle = false;
    public boolean visitorRareArrivalItem = false;
    public boolean visitorStatusHighlight = false;
    public boolean visitorStatusText = true;
    public boolean visitorStatusRare = true;
    // w14-07-min
    public boolean commissionHud = false;
    public int commissionHudX = 10;
    public int commissionHudY = 160;
    public float commissionHudScale = 1.0f;
    public boolean commissionHudBars = true;
    public boolean commissionHudHideDone = false;
    public boolean commissionAlert = true;
    public String commissionAlertSound = "NOTE_PLING";
    public boolean commissionWaypoints = false;
    public boolean commissionWaypointsDwarven = true;
    public boolean commissionWaypointsGlacite = true;
    public boolean commissionWaypointsBaseCamp = false;
    public boolean commissionWaypointsEmissary = true;
    public boolean commissionWaypointsPigeon = true;
    public String commissionTips = "Off";
    public boolean nucleusTracker = false;
    public int nucleusTrackerX = 10;
    public int nucleusTrackerY = 60;
    public float nucleusTrackerScale = 1.0f;
    public String nucleusProfessor = "Robot Parts";
    public boolean nucleusChat = true;
    public int nucleusChatMinimum = 20000;
    public int nucleusPriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    public boolean hollowsMap = false;
    public int hollowsMapX = 10;
    public int hollowsMapY = 10;
    public float hollowsMapScale = 1.5f;
    public boolean hollowsMapLocations = true;
    public int hollowsMapLocationSize = 8;
    public boolean treasureChestHelper = false;
    public boolean treasureChestLockpick = true;
    public String colorTreasureChest = "FFAA00";
    public boolean commissionMobHighlight = false;
    public com.teslamaps.render.EspStyle commissionMobEsp = new com.teslamaps.render.EspStyle("FFFFFF55");

    // w14-02-gar
    public boolean yawPitchHud = false;
    public boolean yawPitchShowPitch = true;
    public boolean yawPitchEverywhere = false;
    public int yawPitchYawDecimals = 4;
    public int yawPitchPitchDecimals = 4;
    public int yawPitchTimeout = 5;
    public int yawPitchHudX = 10;
    public int yawPitchHudY = 200;
    public float yawPitchHudScale = 1.0f;
    public boolean speedPresets = false;
    public java.util.Map<String, Integer> speedPresetValues = defaultSpeedPresets();
    public boolean rareCropTracker = false;
    public boolean rareCropOnlyWithTool = true;
    public boolean rareCropHideChat = false;
    public int rareCropPriceSource = 1;
    public int rareCropTrackerX = 10;
    public int rareCropTrackerY = 140;
    public float rareCropTrackerScale = 1.0f;
    public boolean stereoHarmonyHud = false;
    public boolean stereoHideWhenNone = false;
    public boolean stereoMenuIcons = false;
    public boolean stereoContestHighlight = false;
    public String stereoActiveVinyl = "";
    public int stereoHarmonyHudX = 10;
    public int stereoHarmonyHudY = 240;
    public float stereoHarmonyHudScale = 1.0f;
    public boolean plotBorders = false;
    public int plotBorderKey = -1;
    public boolean plotBordersSprayonator = true;
    public boolean visitorChat = false;
    public boolean visitorCompactRewards = true;
    public boolean visitorHideNpcChat = true;
    public boolean visitorHideArrival = false;
    public boolean visitorLogbookNav = false;
    public int visitorLogbookKey = 70;
    public boolean visitorStats = false;
    public boolean visitorStatsOnlyBarn = false;
    public long visitorStatAccepted = 0;
    public long visitorStatDenied = 0;
    public long visitorStatCopper = 0;
    public long visitorStatFarmingXp = 0;
    public long visitorStatGardenXp = 0;
    public long visitorStatCoinsSpent = 0;
    public long visitorStatBits = 0;
    public long visitorStatMithril = 0;
    public long visitorStatGemstone = 0;
    public java.util.Map<String, Long> visitorStatRarities = new java.util.HashMap<>();
    public java.util.Map<String, Long> visitorStatRewards = new java.util.HashMap<>();
    public int visitorStatsX = 10;
    public int visitorStatsY = 280;
    public float visitorStatsScale = 1.0f;

    public static java.util.Map<String, Integer> defaultSpeedPresets() {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        m.put("default", 100);
        m.put("crops", 93);
        m.put("cocoa", 155);
        m.put("mushroom", 233);
        m.put("cane", 327);
        m.put("squash", 327);
        m.put("cactus", 464);
        return m;
    }
    // w14-03-gar
    public boolean samScytheBlocks = false;
    public String samScytheColor = "FFFF5555";
    public boolean plotCleanupHelper = false;
    public String plotCleanupColor = "80FFE14D";
    public boolean zorroCapeProtection = false;
    public boolean phantomleafSolver = false;
    public boolean visitorsOutsideHighlight = false;
    public String visitorsOutsideBlock = "Don't";
    public boolean visitorLogbookStats = false;
    public boolean visitorSupercraft = false;
    public boolean visitorAcceptHotkey = false;
    public int visitorAcceptKey = -1;
    // w14-04-gar
    public boolean compactFarmingTooltips = false;
    public boolean gardenPlotMenuIcons = false;
    public boolean gardenPlotMenuHighlight = false;
    public boolean gardenPlotMenuPrice = false;
    public boolean farmingStatsHud = false;
    public boolean farmingStatsCounter = true;
    public boolean farmingStatsCropsMin = true;
    public boolean farmingStatsBps = false;
    public boolean farmingStatsYawPitch = true;
    public boolean farmingStatsContest = true;
    public boolean farmingStatsMouseLock = true;
    public int farmingStatsHudX = 10;
    public int farmingStatsHudY = 200;
    public float farmingStatsHudScale = 1.0f;
    public boolean gardenUptime = false;
    public int gardenUptimeAfkSeconds = 30;
    public int gardenUptimeHudX = 10;
    public int gardenUptimeHudY = 260;
    public float gardenUptimeHudScale = 1.0f;
    public boolean farmKeys = false;
    public int farmKeysAttack = -1;
    public int farmKeysUse = -1;
    public int farmKeysForward = -1;
    public int farmKeysBack = -1;
    public int farmKeysLeft = -1;
    public int farmKeysRight = -1;
    public int farmKeysJump = -1;
    public int farmKeysSneak = -1;
    public int farmKeysToggleKey = -1;
    public boolean farmKeysExcludeBarn = false;
    public boolean pestChanceAlways = false;
    public boolean carrolynHelper = false;
    // w13-01-eco
    public boolean starlynCouponProfit = false;
    public boolean supercraftWasteBlock = false;
    public float supercraftWasteThreshold = 10f;
    public float supercraftWasteBulkThreshold = 5f;
    public float supercraftWasteThresholdNoCookie = 20f;
    public float supercraftWasteBulkThresholdNoCookie = 10f;
    public boolean supercraftPresets = false;
    public java.util.List<Integer> supercraftPresetAmounts = new java.util.ArrayList<>(java.util.List.of(4, 8, 16, 32, 64, 128, 256, 512));
    public boolean npcTradeHelper = false;
    public boolean npcTradeHighlight = true;
    public boolean rngMeterHelper = false;
    public boolean rngMeterFractions = true;
    public boolean rngMeterRuns = true;
    public boolean rngMeterCoinsPer = true;
    public boolean searchOverlayMuseum = false;
    public java.util.List<String> museumSearchHistory = new java.util.ArrayList<>();
    public boolean tradeHistory = false;
    public boolean bazaarOrderTooltipClean = false;
    public boolean bazaarHideOrderOwner = true;
    public boolean bazaarHideTradePartners = true;
    // w13-07-gar
    public boolean bestCropTime = false;
    public String bestCropTimeType = "SkyBlock Level";
    public int bestCropTimeTop = 10;
    public boolean bestCropTimeCurrent = true;
    public boolean bestCropTimeCompact = false;
    public int bestCropTimeX = 10;
    public int bestCropTimeY = 220;
    public float bestCropTimeScale = 1.0f;
    public boolean dnaAnalyzerSolver = false;
    public boolean dnaMiddleClick = false;
    public boolean dnaBlockClose = false;
    public boolean dnaHideTooltips = false;
    public boolean cropStartLocation = false;
    public String cropStartLocationMode = "Both";
    public boolean gardenLevelHud = false;
    public boolean gardenLevelOverflow = true;
    public boolean gardenLevelChat = true;
    public int gardenLevelHudX = 10;
    public int gardenLevelHudY = 40;
    public float gardenLevelHudScale = 1.0f;
    public String burrowingSporesAlarm = "Off";
    public boolean cropFeverTracker = false;
    public boolean cropFeverOnlyTool = true;
    public boolean cropFeverOnlyDuring = false;
    public int cropFeverPriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    public int cropFeverTrackerX = 10;
    public int cropFeverTrackerY = 250;
    public float cropFeverTrackerScale = 1.0f;
    public boolean cropMilestoneHudOverflow = false;
    public boolean cropMilestoneAverage = false;
    public boolean gardenUpgradeStackTier = false;
    public boolean hideFarmingRngTitles = false;
    // w13-08-gar
    public boolean atmosphericFilterHud = false;
    public boolean atmosphericFilterOnlyBuff = false;
    public boolean atmosphericFilterShortSeason = false;
    public boolean atmosphericFilterShortPerk = false;
    public boolean atmosphericFilterEverywhere = false;
    public int atmosphericFilterX = 10;
    public int atmosphericFilterY = 230;
    public float atmosphericFilterScale = 1.0f;
    public boolean mhCropDiagnostics = false;
    public boolean eliteLeaderboards = false;
    public boolean eliteLbWeight = true;
    public boolean eliteLbCrop = false;
    public boolean eliteLbPest = false;
    public boolean eliteLbMonthly = false;
    public String eliteLbGamemode = "All";
    public boolean eliteLbOvertake = true;
    public boolean eliteLbEverywhere = false;
    public int eliteLbX = 10;
    public int eliteLbY = 150;
    public float eliteLbScale = 1.0f;
    public boolean cropSpeedMeter = false;
    public int cropSpeedMeterX = 10;
    public int cropSpeedMeterY = 90;
    public float cropSpeedMeterScale = 1.0f;
    public boolean cropCalcCommands = false;
    public boolean fortuneGuide = false;
    public boolean farmToolOverlays = false;
    public boolean farmToolHoe = true;
    public boolean farmToolScythe = true;
    public boolean farmToolDirtWand = true;
    public boolean farmToolPrismapump = true;
    public boolean minionCrystalRadius = true;
    public boolean minionCrystalAlways = false;
    public String farmToolColor = "66B22222";
    public boolean jacobContestFF = false;
    public int jacobContestFFX = 10;
    public int jacobContestFFY = 40;
    public float jacobContestFFScale = 1.0f;
    @Global
    public java.util.Map<String, Double> cropLatestBps = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, Double> cropLatestFortune = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, Double> cropMeasuredPerBlock = new java.util.HashMap<>();
    // w13-09-gar
    public boolean jacobContestTimes = false;
    public String jacobContestBracket = "GOLD";
    public boolean jacobContestCustomBps = false;
    public float jacobContestBps = 19.9f;
    public boolean jacobContestSummary = false;
    public boolean jacobContestMenus = false;
    public boolean jacobMedalIcon = true;
    public boolean jacobMedalBackground = true;
    public boolean jacobRewardStatus = true;
    public boolean jacobRealTime = true;
    public int jacobEliteKey = 341;
    public boolean gardenOptimalSpeed = false;
    public boolean gardenOptimalSpeedHud = true;
    public boolean gardenOptimalSpeedWarn = true;
    public boolean gardenOptimalSpeedRanchersOnly = false;
    public boolean gardenOptimalSpeedSign = true;
    public java.util.Map<String, Integer> gardenOptimalSpeeds = new java.util.HashMap<>();
    public int gardenOptimalSpeedX = 10;
    public int gardenOptimalSpeedY = 150;
    public float gardenOptimalSpeedScale = 1.0f;
    public boolean hoeLevelHud = false;
    public boolean hoeLevelOverflow = true;
    @Global
    public java.util.Map<String, Integer> hoeOverflowLevels = new java.util.HashMap<>();
    public int hoeLevelHudX = 10;
    public int hoeLevelHudY = 170;
    public float hoeLevelHudScale = 1.0f;
    public boolean muteHoeLevelUp = false;
    public boolean greenhouseGrowthTimer = false;
    public boolean greenhouseGrowthOnlyOverdue = false;
    public boolean greenhouseGrowthNotify = true;
    @Global
    public long greenhouseNextStage = 0L;
    @Global
    public boolean greenhouseGrowthNotified = true;
    public int greenhouseGrowthTimerX = 10;
    public int greenhouseGrowthTimerY = 220;
    public float greenhouseGrowthTimerScale = 1.0f;
    public boolean pestGuess = false;
    public boolean pestGuessLine = true;
    public boolean pestGuessPlotMiddle = true;
    public boolean pestGuessHideParticles = false;
    public int pestGuessSeconds = 20;
    public boolean cropMoneyHud = false;
    public int cropMoneyRows = 5;
    public boolean cropMoneyShowCurrent = true;
    public boolean cropMoneyAlwaysOn = false;
    public boolean cropMoneyInstantSell = true;
    public boolean cropMoneySellOffer = false;
    public boolean cropMoneyNpc = false;
    public boolean cropMoneyBountiful = true;
    public boolean cropMoneyMooshroom = true;
    public boolean cropMoneyMergeSeeds = true;
    public int cropMoneyHudX = 10;
    public int cropMoneyHudY = 240;
    public float cropMoneyHudScale = 1.0f;
    // w14-00-gar
    public boolean mantidKillDisplay = false;
    public String mantidShowWhen = "Mantid Armor";
    public int mantidGroupSeconds = 5;
    public int mantidKillDisplayX = 10;
    public int mantidKillDisplayY = 160;
    public float mantidKillDisplayScale = 1.0f;
    public boolean glowingMushrooms = false;
    public com.teslamaps.render.EspStyle glowingMushroomStyle = new com.teslamaps.render.EspStyle("FFFF55FF");
    public boolean mousematAngles = false;
    public boolean mousematCompact = false;
    public java.util.Map<String, Float> mousematAngleValues = new java.util.HashMap<>();
    public boolean noBreakRod = false;
    public boolean noBreakSprayonator = false;
    public String pestSpawnSoundMode = "Default";
    public String pestSpawnSoundName = "NOTE_PLING";
    public float pestSpawnSoundPitch = 1.0f;
    public float pestSpawnSoundVolume = 1.0f;
    public int pestSpawnSoundRepeat = 3;
    public int pestSpawnSoundDelay = 150;
    public boolean pestBaitPanel = false;
    public boolean noCropRotation = false;
    public boolean noCropRotationEverywhere = false;
    public boolean pbFortuneGain = false;
    public boolean pbFortuneOverflow = false;
    public java.util.Map<String, Double> pbItemsPerFortune = new java.util.HashMap<>();
    // w13-03-pv
    public boolean pvSacks = false;
    public boolean pvHunting = false;
    // w13-00-eco
    public boolean fancyAuctionBrowser = false;
    public boolean fancyAuctionView = false;
    public boolean safariShardValue = false;
    public String safariShardPrice = "Instant Sell"; // Instant Sell / Sell Offer / Both
    public boolean safariShardTax = true;
    public boolean npcDayLimit = false;
    public boolean npcDayLimitShort = true;
    public int npcDayLimitX = 10;
    public int npcDayLimitY = 200;
    public float npcDayLimitScale = 1.0f;
    public boolean pricePaid = false;
    public boolean fireSaleWidget = false;
    public int fireSaleWidgetX = 10;
    public int fireSaleWidgetY = 220;
    public float fireSaleWidgetScale = 1.0f;
    public boolean pesthunterProfit = false;
    // w14-06-gar
    public boolean gardenPlotsPestCount = false;
    public boolean visitorHud = false;
    public boolean visitorHudNames = true;
    public int visitorHudX = 10;
    public int visitorHudY = 200;
    public float visitorHudScale = 1.0f;
    public boolean pestOverlaySprayList = false;
    public boolean sprayExpiryChat = false;
    public int pestTpReminderMin = 2;               // 0 = fortune debuff (8)
    public int pestTpReminderInterval = 5;          // seconds
    public boolean farmProfitItemOptions = false;
    public java.util.Map<String, String> farmProfitItemSource = new java.util.HashMap<>();
    public boolean farmProfitResetConfirm = false;
    public boolean farmProfitExtraWindows = false;
    public int mousematReleaseKey = -1;
    public boolean muteVacuum = false;
    // w13-05-pv
    public boolean pvExtras = false;
    public boolean pvPanorama = true;
    public boolean pvErrorCopy = true;
    public boolean pvInfoBoard = true;
    public boolean pvMobsTab = true;
    public boolean pvBingoGoals = true;
    public boolean pvTrophyRank = true;
    public boolean pvPowerStats = true;
    public boolean pvPeek = true;
    // w13-02-eco
    public boolean auctionCopyUnderbid = false;
    public boolean searchOverlayCommands = false;
    public boolean searchOverlayCtrlF = true;
    public int searchOverlayHistorySize = 12;
    public boolean searchOverlayFilters = false;
    public boolean bazaarOrderTooltip = false;
    public boolean petValue = false;
    public boolean essenceShopCosts = false;
    public boolean escrowFix = false;
    // w12-08-eco
    public boolean anitaMedalProfit = false;
    public boolean anitaExtraFortune = false;
    public boolean bazaarHelpers = false;
    public boolean bazaarBestSell = true;
    public boolean bazaarMaxPurse = true;
    public boolean bazaarDailyLimit = true;
    public boolean craftMaterials = false;
    public boolean craftableList = false;
    public boolean craftableListSacks = true;
    public boolean craftableListNoVanilla = true;
    public boolean instaSellHighlight = false;
    public String colorInstaSell = "55FF55";
    public boolean priceHistory = false;
    public int priceHistoryKey = -1;
    public boolean priceHistoryBrowser = false;
    public boolean auctionSortWarning = false;
    public boolean dragonProfitTracker = false;
    public boolean dragonProfitLeeched = false;
    public boolean dragonProfitChat = true;
    public int dragonProfitPriceSource = 1;
    public int dragonProfitTrackerX = 10;
    public int dragonProfitTrackerY = 120;
    public float dragonProfitTrackerScale = 1.0f;
    // w12-01-gui
    public java.util.Map<String, String> hudPanelChroma = new java.util.HashMap<>(); // hud id -> Off / Plain Text / All Text
    public String hudChromaAll = "Off";
    public boolean guiCustomAccent = false;
    public String guiAccentColor = "FF5B8CFF";
    public boolean titleDurationOverride = false;
    public int titleDurationTicks = 40;
    public String chatPrefixStyle = "Long";
    public String chatPrefixCustom = "&b[TM]";
    public boolean outsideSbGate = false;
    public java.util.List<String> outsideSbFeatures = new java.util.ArrayList<>();
    public boolean hideSillyButtons = false;
    public java.util.List<String> hideSillyButtonList = new java.util.ArrayList<>(java.util.List.of(
            "menu.online", "options.accessibility", "options.language", "gui.friends.open", "menu.playerReporting",
            "menu.sendFeedback", "menu.reportBugs", "menu.server_links", "gui.advancements", "gui.stats"));
    public boolean hudEditHelpers = false;
    public int hudEditGrid = 0;
    public boolean hudEditSnapEdges = true;
    public boolean hudEditGuides = true;
    public boolean hudEditMultiDrag = true;
    public boolean hudEditResize = true;
    // w12-09-eco
    public boolean bazaarFillEstimate = false;
    public boolean bazaarFillEstimateAlert = true;
    public boolean bazaarFillEstimateSound = true;
    public int bazaarFillX = 10;
    public int bazaarFillY = 120;
    public float bazaarFillScale = 1.0f;
    public String priceLineAvgBin = "Off";
    public boolean priceLineVolume = false;
    public boolean priceLookup = false;
    public int priceLookupKey = 295;
    public int priceLookupRecipeKey = -1;
    public boolean sackValueHud = false;
    public boolean sackValueHudInSkyblockOnly = true;
    public int sackValueHudX = 10;
    public int sackValueHudY = 160;
    public float sackValueHudScale = 1.0f;
    public boolean wardrobeValue = false;
    // w12-05-perf
    public boolean keepTerrainLoaded = false;
    public boolean outlinedTextCompat = false;
    public boolean catchFeatureErrors = false;
    public boolean featureErrorsInChat = true;
    public boolean muteJerrychine = false;
    public boolean muteFlowerOfTruth = false;
    public boolean devInspector = false;
    public int entityInspectorKey = -1;
    public int itemInspectorKey = -1;
    // w12-00-gui
    public String modifyIndicator = "Off";
    public boolean quickModSwitch = false;
    public boolean quickModSwitchPause = false;
    public int quickModSwitchX = 132, quickModSwitchY = 34; public float quickModSwitchScale = 1.0f;
    public boolean keyConflictWarning = false;
    public boolean noTextShadow = false;
    public int stHealthBarW = -1, stManaBarW = -1;
    public String stHealthBarColor = "E53935", stManaBarColor = "1E88E5";
    public String stHealthBarText = "Off", stManaBarText = "Off";
    public boolean stHealthBarMax = true, stManaBarMax = true;
    public boolean stHealthBarOver = true, stManaBarOver = true;
    public boolean stHealthBarReverse = false, stManaBarReverse = false;
    public String safariShowWhere = "Auto";
    // w12-03-perf
    public boolean suppressLogSpam = false;
    public boolean hypixelModApi = false;
    public boolean hideFarEntities = false;
    public int hideFarEntitiesMinDistance = 10;
    public int hideFarEntitiesMaxAmount = 30;
    public boolean irisCompat = false;
    public boolean espInstancing = false;
    public boolean mapTextureCache = false;
    // w12-06-eco
    public boolean bzTracker = false;
    public int bzTrackerMaxOrders = 10;
    public boolean bzTrackerOnlyMenus = false;
    public boolean bzTrackerHideEmpty = false;
    public boolean bzTrackerSoundFilled = true;
    public boolean bzTrackerSoundPartial = false;
    public boolean bzTrackerSoundOutbid = true;
    public int bzTrackerX = 5;
    public int bzTrackerY = 60;
    public float bzTrackerScale = 1.0f;
    public java.util.Map<String, java.util.List<BzTrackedOrder>> bzTrackerOrders = new java.util.HashMap<>();
    public static class BzTrackedOrder {
        public boolean buy;
        public String item;
        public int amount;
        public int filled;
        public double price;
        public long created;
    }
    public boolean purchaseWarning = false;
    public float purchaseWarnBazaarM = 10f;
    public float purchaseWarnHexM = 10f;
    public boolean purchaseWarnBin = true;
    public float purchaseWarnBinUnder = 30f;
    public float purchaseWarnBinOver = 100f;
    public boolean manageAuctionsHelper = false;
    public boolean manageAuctionsPanel = true;
    public boolean manageAuctionsOverpriced = true;
    public String colorAuctionSold = "#55FF55";
    public String colorAuctionExpired = "#FF5555";
    public String colorAuctionOverpriced = "#FFAA00";
    public boolean auctionOutbidWarning = false;
    public boolean auctionOutbidSound = true;
    public boolean bzQuickQuantities = false;
    public int bzQuickQty1 = 64;
    public int bzQuickQty2 = 160;
    public int bzQuickQty3 = 1024;
    public boolean bzQuickQtyClose = false;
    public boolean bzReorderHelper = false;
    public boolean bzReorderOnCancel = true;
    public boolean bzPurchaseHelper = false;
    // w12-02-gui
    public boolean containerPanelDrag = false;
    public int croesusPanelDX = 0;
    public int croesusPanelDY = 0;
    public int shardPanelDX = 0;
    public int shardPanelDY = 0;
    public String invButtonConfirm = "Off";
    public boolean inventoryScaleOverride = false;
    public int inventoryScale = 2;
    public boolean customClassColors = false;
    public String classColorArcher = "FFAA00";
    public String classColorBerserk = "AA0000";
    public String classColorHealer = "FF55FF";
    public String classColorMage = "55FFFF";
    public String classColorTank = "00AA00";
    public boolean quickJoinButton = false;
    @Global
    public String quickJoinIp = "mc.hypixel.net";
    public boolean searchOverlayAllSigns = false;
    public java.util.List<String> otherSearchHistory = new java.util.ArrayList<>();
    public boolean tabWidgetGuide = false;
    // w11-04-par
    public boolean partyCheckCommands = false;
    public java.util.Set<String> chatCommandsExtraEnabled = new java.util.LinkedHashSet<>();
    public boolean chatCommandsInviteAutoConfirm = false;
    public boolean chatCommandsDtReasons = false;
    public boolean markedPlayers = false;
    public java.util.Set<String> markedPlayerNames = new java.util.LinkedHashSet<>();
    public boolean markedPlayersWorld = true;
    public boolean markedPlayersChat = true;
    public String markedPlayersChatColor = "FFFF55";
    public boolean markedPlayersJoinLeave = false;
    public com.teslamaps.render.EspStyle markedPlayersStyle = new com.teslamaps.render.EspStyle("FFFFFF55");
    public boolean socialHideFakePlayers = false;
    public boolean scamCheck = false;
    public boolean scamCheckClose = true;
    public boolean modChat = false;
    public boolean modChatJoinMessage = false;
    // w11-08-gui
    public boolean configColumnLayout = false;
    public boolean configColumnFling = false;
    public java.util.Map<String, float[]> configColumns = new java.util.LinkedHashMap<>(); // category -> x, y, scale, collapsed
    public boolean hudEditExtras = false;
    public boolean hudEditReset = true;
    public boolean hudEditNudge = true;
    public boolean hudEditShowDisabled = true;
    public boolean hudEditRightClickToggle = true;
    public boolean hudEditJump = true;
    @Global
    public boolean configBackups = false;
    @Global
    public int configBackupsMax = 20;
    public boolean featureToggleKeys = false;
    public boolean featureToggleChat = true;
    public java.util.Map<String, Integer> featureToggleBinds = new java.util.LinkedHashMap<>(); // boolean field -> key
    // w12-07-eco
    public boolean contestShowDarkAuction = false;
    public boolean bitsShopHelper = false;
    public boolean bitsShopBits = true;
    public boolean bitsShopCopper = true;
    public boolean bitsShopHighlight = true;
    public boolean priceLineCraftCheapest = false;
    public boolean bazaarOnlyMyOrders = false;
    public boolean bazaarFlipTracker = false;
    public boolean bazaarFlipShowTotal = false;
    public boolean bazaarFlipInstant = true;
    public int bazaarFlipX = 5;
    public int bazaarFlipY = 180;
    public float bazaarFlipScale = 1.0f;
    public boolean tradeValue = false;
    public boolean tradeValueChanges = true;
    public boolean bazaarChatSummary = false;
    // w11-07-gui
    public boolean configResetOptions = false;
    public boolean pauseMenuConfigButton = false;
    public boolean skyblockMenuConfigButton = false;
    public boolean userSoundFolder = false;
    public boolean featureConditions = false;
    public java.util.Map<String, FeatureRule> featureConditionRules = new java.util.LinkedHashMap<>();
    public boolean customFont = false;
    public String customFontFile = "";
    public float customFontSize = 9f;
    public float customFontOversample = 4f;
    public float customFontShiftY = 0f;
    public boolean widgetsHelper = false;
    public boolean widgetsHelperList = true;

    public static class FeatureRule {
        public boolean invert = false;
        public java.util.List<java.util.List<String>> combos = new java.util.ArrayList<>();
    }
    // w11-09-gui
    public boolean radialMenus = false;
    public java.util.List<String> radialMenusOn = new java.util.ArrayList<>();
    public boolean radialMenuTooltips = false;
    public boolean hudLayoutsPerLocation = false;
    public java.util.Map<String, java.util.Map<String, float[]>> hudLayouts = new java.util.HashMap<>();
    public boolean keybindsInControls = false;
    public String hudTextShadow = "Shadow";
    public java.util.Map<String, String> hudPanelTextShadow = new java.util.HashMap<>();
    public java.util.Map<String, String> hudPanelTextAlign = new java.util.HashMap<>();
    public java.util.Map<String, String> hudPanelLineBackdrop = new java.util.HashMap<>();
    public boolean joinTips = false;
    public boolean clickablePrefix = false;
    // w11-01-par
    public boolean pbOnJoinButtons = false;
    public boolean chatNamesOpenPv = false;
    public boolean pbOnJoinKuudra = false;
    public boolean kuudraPfKickLine = true;
    public boolean kuudraPfSound = false;
    public boolean kuudraPfAutoKick = false;
    public boolean kuudraPfInform = false;
    public boolean kuudraPfApiOffKick = true;
    public int kuudraPfMinMp = 1000;
    public int kuudraPfMinT5 = 50;
    public boolean kuudraPfReqGdrag = false;
    public boolean kuudraPfReqHeart = false;
    public boolean kuudraPfReqTux = false;
    public boolean kuudraPfKickCache = true;
    public boolean kuudraRunsReply = false;
    public boolean acceptLastInvite = false;
    public boolean manaDrainAnnounce = false;
    public boolean spellAnnounce = false;
    public boolean spellAnnounceSpark = true;
    public boolean spellAnnounceRush = true;
    public boolean spellAnnounceWind = true;
    public boolean spellAnnounceIchor = true;
    public boolean mutePartySpam = false;
    public java.util.List<String> mutePartySpamPrefixes = new java.util.ArrayList<>(com.teslamaps.features.MutePartySpam.DEFAULTS);
    public boolean safariHotspotPost = false;
    public String safariHotspotPostChannel = "Party";
    public boolean hunterTrades = false;
    public String hunterTradesChannel = "Party";
    public boolean hunterTradesChat = true;
    public boolean hunterTradesAcceptShared = true;
    public boolean hunterTradesWaypoints = true;
    public int hunterTradesX = 5;
    public int hunterTradesY = 160;
    public float hunterTradesScale = 1.0f;
    // w11-06-gui
    public float hudCustomBlur = 0f;               // HUD Style "Custom": backdrop blur radius, 0 = off
    public String hudCustomBlurKernel = "Gaussian"; // Gaussian / Box
    public float hudGlobalScale = 1.0f;            // multiplies every movable HUD element
    public boolean newOptionsNotify = false;
    @Global
    public java.util.List<String> knownToggles = new java.util.ArrayList<>(); // "Category/Label" seen so far
    @Global
    public int worldOverlayKey = -1;               // hides every world overlay while toggled
    public boolean worldOverlayFade = true;
    // w11-02-par
    public boolean multiPartyInviteFix = false;
    public boolean teslaUserLabels = false;
    public String teslaUserLabelColor = "55FFFF";
    public String pfRightClickMode = "Off";
    public boolean fancyPartyFinder = false;
    public boolean nickHider = false;
    public String nickHiderName = "";
    public boolean partyOnBoop = false;
    public boolean partyOnBoopBingoOnly = false;
    public boolean replyBoop = false;
    // w11-03-par
    public boolean repartyCommand = false;
    public boolean repartyAutoAccept = false;
    public boolean pfLevelReq = false;
    public boolean pfMissingOverlay = false;
    public boolean pfTooltipStats = false;
    public boolean partyListFormat = false;
    public boolean partyHud = false;
    public boolean partyHudOnlyAreas = false;
    public int partyHudMax = 8;
    public int partyHudX = 5;
    public int partyHudY = 120;
    public float partyHudScale = 1.0f;
    public boolean partyJoinSound = false;
    public String partyJoinSoundType = "NOTE_PLING";
    public boolean kickedLimboAlert = false;
    public boolean safariPlayersOnEnd = false;
    public boolean throttleNotifier = false;
    public boolean throttleNotifierCooldown = true;
    public String throttleNotifierText = "Menu throttled, give me a second!";
    // w11-05-par
    public boolean partyShortcuts = false;
    public boolean partyKickReason = false;
    public boolean partyTabComplete = false;
    public boolean reversePtClickable = false;
    @Global
    public String reversePtMessage = "";
    public boolean highlightPartyOutside = false;
    public String highlightPartyColor = "55FFFF";
    public boolean dianaPartyFinder = false;
    public float dianaPartyFinderMinRate = 0f;
    public boolean posMessages = false;
    public boolean posMessagesOnlyDungeons = true;
    public boolean posMessagesOncePerWorld = false;
    public boolean posMessagesShow = true;
    public float posMessagesHeight = 0.2f;
    public boolean posMessagesDepth = true;
    public boolean posMessagesShowText = true;
    public float posMessagesTextSize = 1.0f;
    public static class PosMessage {
        public double x, y, z;
        public Double x2, y2, z2;
        public int delay;
        public Double distance;
        public int color = 0xFFFFFFFF;
        public String message = "";
    }
    @Global
    public List<PosMessage> posMessageList = new ArrayList<>();
    // w11-00-chat
    public boolean fullChatChroma = false;
    public boolean chatStackNormalizeNumbers = false;
    public boolean chatFilterPerkBuffsCompact = false;
    public boolean chatEncrypt = false;
    // w10-00-vis
    public boolean blockEsp = false;
    public java.util.Map<String, String> blockEspBlocks = new java.util.LinkedHashMap<>();
    public com.teslamaps.render.EspStyle blockEspStyle = new com.teslamaps.render.EspStyle("FF55FFFF");
    public int blockEspMaxShown = 2000;
    public boolean chestEsp = false;
    public String chestEspWhere = "Everywhere";
    public boolean chestEspHideClicked = true;
    public com.teslamaps.render.EspStyle chestEspStyle = new com.teslamaps.render.EspStyle("FFFFAA00");
    public boolean customCameraDistance = false;
    public float cameraDistance = 4.0f;
    public boolean cameraClip = false;
    public boolean glowUnbuiltSections = false;
    public int glowOutlineWidth = 1;
    // w10-09-chat
    public boolean dungeonFilterFull = false;
    public boolean dungeonFilterBoss = true;
    public boolean dungeonFilterPrepare = true;
    public boolean dungeonFilterAmbience = true;
    public boolean dungeonFilterReminder = true;
    public boolean dungeonFilterBuff = true;
    public boolean dungeonFilterNotPossible = true;
    public boolean dungeonFilterDamage = true;
    public boolean dungeonFilterAbility = true;
    public boolean dungeonFilterPuzzle = true;
    public boolean dungeonFilterEnd = true;
    public boolean chatCompactDungeon = false;
    public boolean chatCompactLobby = false;
    public boolean chatCompactForaging = false;
    public boolean chatSendQueue = false;
    public int chatSendQueueGap = 200;
    public boolean terminatorCooldownMute = false;
    public boolean terminatorCooldownMuteChat = true;
    public boolean imageLinkPreview = false;
    public int imageLinkPreviewKey = -1;
    // w10-08-chat
    public boolean chatFilterSeparators = false;
    public boolean playerChatFormat = false;
    public boolean playerChatHideRank = true;
    public boolean playerChatKeepYoutube = true;
    public boolean playerChatNoLevelBrackets = false;
    public boolean playerChatLevelColorName = false;
    public boolean playerChatWhiteText = false;
    public boolean smoothChatMessages = false;
    public int smoothChatMessageMs = 150;
    public boolean smoothChatOpen = false;
    public int smoothChatOpenMs = 200;
    public boolean chatHideMessageIndicator = false;
    public boolean reportAutoConfirm = false;
    public boolean chatTranslator = false;
    public String chatTranslatorLanguage = "en";
    public boolean commandSuggestionsExtra = false;
    @Global
    public String ownHypixelRank = "";
    // w10-01-chat
    public String chatAutopetMode = "Show";          // Show / Hide / Action Bar / Toast
    public float chatAutopetToastSeconds = 3f;
    public boolean chatLog = false;                  // every chat line into logs/teslamaps-chat
    public int chatLogDays = 7;
    public boolean chatLogFilterStatus = false;      // status + reason, blocked/modified files, outgoing
    public boolean chatTimestamps = false;
    public String chatTimestampFormat = "24h";
    public String chatTimestampColor = "555555";
    public boolean currentChatDisplay = false;
    public int currentChatDisplayX = 4;
    public int currentChatDisplayY = 150;
    public float currentChatDisplayScale = 1.0f;
    public int chatPeekKey = -1;
    public String chatNotifyColor = "FFFF55";
    public String chatNotifySound = "NOTE_PLING";
    public float chatNotifyVolume = 1.0f;
    public boolean chatNotifyIgnoreOwn = true;
    // w10-03-chat
    public boolean compactJacobClaim = false;
    public boolean compactLevelUps = false;
    public boolean compactLevelUpsHideBorder = false;
    public boolean compactExperimentRewards = false;
    public boolean shortenChatCoins = false;
    public boolean darkAuctionItemName = false;
    public boolean hideArachneMessages = false;
    // w10-05-chat
    public boolean rareDropPetRarity = false;
    public boolean rareDropBookName = false;
    public boolean chatCompactCombat = false;
    public boolean chatCompactDamage = true;
    public boolean chatCompactDeaths = true;
    public boolean chatCompactDrops = false;
    public boolean chatCompactNpcGrey = false;
    public boolean chatCompactMining = false;
    public boolean melodyCompact = false;
    public boolean melodyCompactMute = false;
    public boolean hoppityCompact = false;
    public boolean hoppityCompactRarity = true;
    public boolean hoppityCompactDupeNumber = true;
    public boolean hoppityCompactTimeTower = false;
    public boolean hoppityCompactHitman = true;
    public int hoppityCompactHitmanThreshold = 5;
    public boolean hotkeyPlaceholders = false;
    // w10-04-chat
    public boolean chatKeepScroll = false;
    public boolean chatFilterBlazetekk = false;
    public boolean chatSoundResponses = false;
    public java.util.List<String> chatSoundResponsesOff = new java.util.ArrayList<>();
    public boolean chatUserAlerts = false;
    public boolean chatCompactChannels = false;
    public boolean chatCompactParty = true;
    public boolean chatCompactGuild = true;
    public boolean chatCompactPublic = false;
    public boolean chatCompactHideRank = true;
    public boolean chatCompactHideGuildRank = true;
    public boolean chatCompactHideLevel = false;
    public boolean chatCompactHideEmblem = false;
    public boolean chatStripDiscordWarning = false;
    public String rareDropAnnounce = "Off";
    public float rareDropAnnounceMin = 0f;
    public boolean rareDropCopy = false;
    // w10-07-chat
    public boolean chatMarkdown = false;
    public boolean sackMessagePrices = false;
    public boolean sackNotify = false;
    public java.util.List<String> sackNotifyItems = new java.util.ArrayList<>(java.util.List.of("Glossy Gemstone"));
    public boolean skyblockXpChat = false;
    public boolean chatScamWarning = false;
    public boolean chatMute = false;
    @Global
    public java.util.List<String> chatMutedPlayers = new java.util.ArrayList<>();
    public boolean hideFarDeaths = false;
    // w10-06-chat
    public boolean chatHighlightOwnName = false;
    public String chatOwnNameColor = "FFAA00";
    public boolean chatProfileBadges = false;
    public boolean piggyRepairLink = false;
    public boolean compactJoinLeave = false;
    public boolean explosiveShotPerEnemy = false;
    public boolean logSearch = false;
    public boolean bridgeFormat = false;
    public String bridgeBotName = "";
    public String bridgeNameColor = "55FFFF";
    // w10-02-chat
    public boolean chatFilterAbilityCooldown = false;
    public boolean muteAbilityCooldownSound = false;
    public boolean abiphoneDnd = false;
    public boolean abiphoneDndCallerId = true;
    public boolean confirmPromptHelper = false;
    public boolean chatSearch = false;
    public boolean chatEmotes = false;
    public boolean compactPotionMessages = false;
    public boolean compactPotionClickable = true;
    public boolean compactBestiary = false;
    // w08-00-hud
    public boolean tabStatsHud = false;
    public String tabStatsHudStats = "Ferocity";
    public int tabStatsHudX = 5;
    public int tabStatsHudY = 120;
    public float tabStatsHudScale = 1.0f;
    public boolean trackerInteractive = false;
    @Global
    public java.util.Map<String, java.util.List<String>> trackerHidden = new java.util.HashMap<>();
    public java.util.Map<String, java.util.List<String>> hudLineOrder = new java.util.HashMap<>();
    public boolean expDryStreak = false;
    public boolean expDryStreakAttempts = true;
    public boolean expDryStreakXp = true;
    public int expDryStreakX = 5;
    public int expDryStreakY = 60;
    public float expDryStreakScale = 1.0f;
    public boolean expProfitTracker = false;
    public boolean expProfitTrackBottles = true;
    public int expProfitPriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    public int expProfitX = 5;
    public int expProfitY = 90;
    public float expProfitScale = 1.0f;
    public boolean expGuardianReminder = false;
    @Global
    public java.util.Map<String, long[]> expDryStreakData = new java.util.HashMap<>();
    public boolean perfHudCoords = false;
    public boolean perfHudLookingAt = false;
    public boolean lobbyPlayers = false;
    public boolean lobbyPlayersMax = true;
    public int lobbyPlayersX = 5;
    public int lobbyPlayersY = 150;
    public float lobbyPlayersScale = 1.0f;
    public boolean maxwellHud = false;
    public boolean maxwellHudMp = true;
    public boolean maxwellHudTunings = true;
    public boolean maxwellHudCompact = true;
    public int maxwellHudX = 5;
    public int maxwellHudY = 170;
    public float maxwellHudScale = 1.0f;
    @Global
    public java.util.Map<String, MaxwellData> maxwellData = new java.util.HashMap<>();

    public static class MaxwellData {
        public String power;
        public int magicalPower = -1;
        public java.util.List<String> tunings;
    }
    // w08-01-hud
    public boolean customScoreboardChunked = false;
    public int customScoreboardChunkSize = 3;
    public boolean customScoreboardChunkHideEmpty = true;
    public boolean customScoreboardChunkTab = true;
    public boolean fancyTabHud = false;
    public float fancyTabScale = 1.0f;
    public boolean fancyTabPlayers = true;
    public boolean fancyTabBars = true;
    public int fancyTabVanillaKey = -1;
    public boolean hoppityLiveStats = false;
    public boolean hoppityLiveAlways = false;
    public boolean hoppityEventSummary = false;
    public int hoppityLiveX = 10;
    public int hoppityLiveY = 160;
    public float hoppityLiveScale = 1.0f;
    public boolean limboTracker = false;
    public int limboTrackerX = 10;
    public int limboTrackerY = 60;
    public float limboTrackerScale = 1.0f;
    @Global public long limboPersonalBest = 0;
    @Global public long limboTotalSeconds = 0;
    public boolean profitTracker = false;
    public String profitTrackerPreset = "Auto";
    public boolean profitTrackerPresetOnly = false;
    public boolean profitTrackerSacks = true;
    public boolean profitTrackerCoins = true;
    public boolean profitTrackerSlayerCost = true;
    public int profitTrackerPriceSource = 1;
    public int profitTrackerIdle = 90;
    public int profitTrackerX = 10;
    public int profitTrackerY = 200;
    public float profitTrackerScale = 1.0f;
    public boolean playerStatHuds = false;
    public boolean statHudIcons = true;
    public boolean statHealth = true;
    public boolean statMana = true;
    public boolean statOverflow = true;
    public boolean statDefense = true;
    public boolean statVitality = false;
    public boolean statEhp = false;
    public boolean statSpeed = false;
    public boolean statHealthBar = false;
    public boolean statManaBar = false;
    public boolean statHideHearts = true;
    public int statBarWidth = 80;
    public int stHealthX = 10, stHealthY = 240; public float stHealthScale = 1.0f;
    public int stManaX = 10, stManaY = 250; public float stManaScale = 1.0f;
    public int stOverflowX = 10, stOverflowY = 260; public float stOverflowScale = 1.0f;
    public int stDefenseX = 10, stDefenseY = 270; public float stDefenseScale = 1.0f;
    public int stVitalityX = 10, stVitalityY = 280; public float stVitalityScale = 1.0f;
    public int stEhpX = 10, stEhpY = 290; public float stEhpScale = 1.0f;
    public int stSpeedX = 10, stSpeedY = 300; public float stSpeedScale = 1.0f;
    public int stHealthBarX = 100, stHealthBarY = 240; public float stHealthBarScale = 1.0f;
    public int stManaBarX = 100, stManaBarY = 250; public float stManaBarScale = 1.0f;
    public boolean quiverHud = false;
    public boolean quiverOnlyBow = false;
    public boolean quiverLowWarning = false;
    public int quiverHudX = 10;
    public int quiverHudY = 320;
    public float quiverHudScale = 1.0f;
    // w08-07-hud
    public boolean perfHudColorCoded = false;
    public String perfHudTpsMode = "Average";
    public String perfHudPingMode = "Instant";
    public boolean ghostLootCounter = false;
    public boolean dragonLootCounter = false;
    public int lootCounterPriceSource = com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL;
    @Global
    public int dragonsSinceSuperior = 0;
    public int ghostLootX = 10, ghostLootY = 120;
    public float ghostLootScale = 1.0f;
    public int dragonLootX = 10, dragonLootY = 120;
    public float dragonLootScale = 1.0f;
    public boolean splitsWitherDoors = false;
    public boolean splits300Score = false;
    public boolean skillTrackerPauseCustom = false;
    public java.util.Map<String, Integer> skillTrackerPauseSec = new java.util.HashMap<>();
    public boolean tabWidgetHud = false;
    public java.util.List<String> tabWidgetHudWidgets = new java.util.ArrayList<>();
    public int tabWidgetHudX = 10, tabWidgetHudY = 60;
    public float tabWidgetHudScale = 1.0f;
    public boolean safariSnoozleNone = false;
    public boolean valuableDropWarn = false;
    public float valuableDropChatValue = 1f;
    public float valuableDropTitleValue = 10f;
    // w09-08-vis
    public boolean hideHealerOrbParticles = false;
    public float hurtCamStrength = 0f;
    public boolean moreOverlayHiders = false;
    public boolean hidePortalOverlay = false;
    public boolean hidePumpkinOverlay = false;
    public boolean hideInBlockOverlay = false;
    public boolean noWaterFov = false;
    public boolean hideWitherImpactParticles = false;
    public boolean nightVisionControl = false;
    public float nightVisionStrength = 100f;
    public boolean nightVisionNoBlink = false;
    public boolean particleHider = false;
    public boolean particleHideFar = false;
    public boolean particleHideCloseRedstone = false;
    public boolean particleHideFireball = false;
    public boolean particleHideBlaze = false;
    public boolean particleHideEnderman = false;
    public boolean particleHideFireBlock = false;
    public boolean particleHideSmoke = false;
    public boolean noBlockBreakingGardenOnly = false;
    public boolean noArrowsGroundedOnly = false;
    public boolean noEnchantGlintArmorOnly = false;
    // w09-02-vis
    public boolean itemAnimations = false;
    public float itemAnimX = 0f;
    public float itemAnimY = 0f;
    public float itemAnimZ = 0f;
    public float itemAnimYaw = 0f;
    public float itemAnimPitch = 0f;
    public float itemAnimRoll = 0f;
    public float itemAnimScale = 0f;
    public float itemAnimSwingSpeed = 0f;
    public boolean itemAnimIgnoreEffects = false;
    public boolean itemAnimNoReequip = false;
    public boolean itemAnimInPlaceSwing = false;
    public boolean itemAnimNoSwing = false;
    public boolean itemAnimNoSwingTerm = false;
    public boolean itemAnimNoSway = false;
    public boolean itemAnimEmptyHand = true;
    public boolean gyroWandRange = false;
    public String gyroWandColor = "805500AA";
    public boolean gyroWandCooldown = true;
    public String gyroWandCooldownColor = "80FF5555";
    public int gyroWandCooldownSeconds = 30;
    public boolean gyroWandThroughWalls = false;
    public boolean hideHypeHearts = false;
    public boolean hideEnchantRunes = false;
    public boolean followingLine = false;
    public String followingLineColor = "FFFFFF";
    public float followingLineWidth = 4f;
    public int followingLineSeconds = 15;
    public boolean followingLineThroughWalls = false;
    public boolean flamingFlayHider = false;
    public int flamingFlayHideDistance = 10;
    public boolean removeGlowEffect = false;
    public boolean fixRidingCamera = false;
    // w09-04-vis
    public boolean noReequipAnimation = false;
    public boolean hideItemCooldownOverlay = false;
    public boolean hideHotbarItemName = false;
    public boolean compactTabList = false;
    public boolean compactTabHideAds = true;
    public boolean compactTabHideFireSales = false;
    public boolean compactTabBackground = true;
    public boolean compactTabToggle = false;
    public String compactTabSort = "Rank";
    public boolean compactTabSortReverse = false;
    public boolean compactTabHideHeads = false;
    public boolean compactTabHideLevel = false;
    public boolean compactTabHideEmblems = false;
    public boolean compactTabHideRankColor = false;
    public boolean compactTabPartyIcon = false;
    public boolean lowHealthVignette = false;
    public float lowHealthVignetteThreshold = 0.5f;
    public String lowHealthVignetteColor = "CCFF0000";
    public boolean lowerNametags = false;
    public boolean hidePlayerArmor = false;
    public String hidePlayerArmorMode = "Others";
    public boolean hidePlayerArmorHelmetOnly = false;
    public boolean hideJerryRune = false;
    // w09-01-vis
    public boolean effectiveAreaOverlay = false;
    public float effectiveAreaRadius = 7f;
    public String effectiveAreaColor = "4055FFFF";
    public boolean fireVeilCircle = false;
    public String fireVeilMode = "Circle";
    public String fireVeilColor = "FFFF5555";
    public boolean hideFireworks = false;
    public boolean hideHorses = false;
    public boolean droppedItemScaling = false;
    public float droppedScaleCommon = 100f;
    public float droppedScaleUncommon = 100f;
    public float droppedScaleRare = 100f;
    public float droppedScaleEpic = 100f;
    public float droppedScaleLegendary = 100f;
    public float droppedScaleMythic = 100f;
    public float droppedScaleDivine = 100f;
    public float droppedScaleSpecial = 100f;
    public boolean endChestTint = false;
    public String endChestColor = "FFAA55FF";
    public boolean customHurtTint = false;
    public String customHurtTintColor = "4D5555FF";
    public boolean dvdLogo = false;
    public String dvdText = "TeslaMaps";
    public float dvdSpeed = 1.5f;
    public float dvdScale = 1.5f;
    public boolean dvdCornerAlert = true;
    // w09-00-vis
    public boolean bigItems = false;
    public float bigItemsScale = 1.25f;
    public boolean bigItemsHeadsOnly = false;
    public boolean bigItemsRarity = false;
    public boolean customSkin = false;
    public boolean customSkinSelf = true;
    public boolean customSkinOthers = false;
    public String customSkinFile = "";
    public boolean coneHat = false;
    public String coneHatFile = "";
    public float coneHatHeight = 0.45f;
    public float coneHatRadius = 0.25f;
    public float coneHatYOffset = -0.5f;
    public float coneHatSpin = 0f;
    public boolean customHubMap = false;
    public String customHubMapFile = "";
    public boolean endermanTeleportHider = false;
    public float espFadeDistance = 0f;
    public boolean entityHighlight = false;
    public float entityHighlightWidth = 2f;
    public String entityHighlightPlayer = "55FFFF";
    public String entityHighlightMob = "FF5555";
    public String entityHighlightAnimal = "55FF55";
    public String entityHighlightOther = "FFFFFF";
    // w09-07-vis
    public boolean hideEntityFire = false;
    public boolean fireOverlayAdjust = false;
    public float fireOverlayHeight = 100f;
    public float fireOverlayOpacity = 100f;
    public boolean hideZeroHpNametags = false;
    public boolean hidePlayersTransparent = false;
    public float hidePlayersOpacity = 30f;
    public boolean ghostPlayers = false;
    public float ghostPlayersOpacity = 40f;
    public boolean muteEndermanEnd = false;
    public boolean noExplosionsChickenRace = false;
    public boolean chromaFormatCode = false;
    public boolean chromaEverything = false;
    // w09-06-vis
    public boolean transparentArmor = false;
    public float transparentArmorSelf = 40f;
    public float transparentArmorOthers = 40f;
    public boolean zealotColor = false;
    public String zealotColorValue = "55FF55";
    public boolean zealotColorChroma = false;
    public boolean visualWords = false;
    public boolean visualWordsIgnoreCase = false;
    public java.util.List<com.teslamaps.features.TextVisuals.VisualWord> visualWordList = new java.util.ArrayList<>();
    public boolean fullTextShadow = false;
    public boolean noLightningSkyFlash = false;
    public boolean noBlockHitParticles = false;
    public float blindnessFogOpacity = 0f;
    // w09-03-vis
    public boolean changeCrouch = false;
    public boolean instantCrouch = true;
    public boolean legacyCrouchHeight = true;
    public boolean hideGlitchMobs = false;
    public boolean smoothTooltipLines = false;
    public boolean headDisplaySize = false;
    public float headDisplayPercent = 100f;
    public boolean safariHideonwallPerches = false;
    public String colorHideonwallPerch = "AA55FF";
    public String colorHideonwallFound = "55FF55";
    public boolean biggerBatModels = false;
    public float biggerBatScale = 3f;
    public boolean lowerEndermanNametags = false;
    public boolean cosmetics = false;
    public boolean cosmeticHalo = true;
    public String cosmeticHaloColor = "FFFF55";
    public boolean cosmeticShowOthers = true;
    // w09-09-vis
    public boolean hideCloudsIslands = false;
    public java.util.List<String> cloudHiderIslands = new java.util.ArrayList<>(com.teslamaps.features.IslandClouds.DEFAULTS);
    public boolean etherwarpZeroPing = false;
    public boolean iHateDoors = false;
    public boolean iHateDoorsEntrance = false;
    public String iHateDoorsEntranceColor = "White";
    public boolean iHateDoorsWither = true;
    public String iHateDoorsWitherColor = "Black";
    public boolean iHateDoorsBlood = true;
    public String iHateDoorsBloodColor = "Red";
    public boolean trajectories = false;
    public boolean trajBows = true;
    public boolean trajTerminator = true;
    public boolean trajPearls = true;
    public boolean trajLines = true;
    public boolean trajBoxes = true;
    public boolean trajPlane = false;
    public boolean trajEntities = true;
    public boolean trajThroughWalls = false;
    public int trajRange = 60;
    public float trajLineWidth = 2.0f;
    public String trajColor = "00AAAA";
    public boolean customEspRules = false;
    public java.util.List<com.teslamaps.features.CustomEspRules.EspRule> customEspRuleList = new java.util.ArrayList<>();
    public boolean customEspRuleLabels = true;
    public float customEspRuleLineWidth = 2.0f;
    public String customEspRuleSound = "NOTE_PLING";
    public boolean orderedOnGround = false;
    public boolean parkourGuides = false;
    public int parkourLookAhead = 3;
    public boolean parkourRainbow = true;
    public String parkourColor = "FFFFFF";
    public boolean parkourShortcuts = true;
    public boolean particleCloak = false;
    public String particleCloakType = "Dust";
    public String particleCloakColor = "AA00FF";
    public int particleCloakDensity = 3;
    public float particleCloakMotion = 0.5f;
    public boolean particleCloakFirstPerson = false;
    public boolean coloredNames = false;
    public String coloredNameSelf = "FF55FF";
    public boolean coloredNameUsers = true;
    public String coloredNameUserColor = "55FFFF";
    // w09-05-vis
    public boolean mobIcons = false;
    public String mobIconsStyle = "Short";
    public java.util.Map<String, String> mobIconColors = new java.util.HashMap<>();
    public boolean playerSize = false;
    public String playerSizeTarget = "Self";
    public float playerSizeX = 1f;
    public float playerSizeY = 1f;
    public float playerSizeZ = 1f;
    public boolean playerTrail = false;
    public String playerTrailColor = "FF55FFFF";
    public float playerTrailLength = 40f;
    public float playerTrailWidth = 2f;
    public boolean playerModel = false;
    public String playerModelTarget = "Self";
    public String playerModelType = "Cat";
    public boolean playerModelBaby = false;
    public boolean petDragon = false;
    public float petDragonSize = 0.15f;
    public float petDragonHeight = 2.5f;
    public float petDragonDistance = 2.5f;
    public float petDragonAngle = 0f;
    public float petDragonSpeed = 1f;
    public String petDragonColor = "None";
    public boolean petDragonThirdPersonOnly = false;
    public boolean hideCloakCreepers = false;
    public boolean hideAbsorption = false;
    public boolean hideMountHealth = false;
    public boolean hideDeathParticles = false;
    public boolean noNightVision = false;
    // w07-03-qol
    public boolean updateChangelog = false;
    public boolean itemSearchWarpIsland = false;
    public boolean pageScrollWheel = false;
    public boolean pageScrollInvert = false;
    public boolean hotkeyAdvanced = false;
    public String safariMissingChannel = "Party";
    public boolean oldSkyblockMenu = false;
    // w08-06-hud
    public boolean perfHudDay = false;
    public boolean scoreboardPiggyToPurse = false;
    public boolean scoreboardColorMonths = false;
    public boolean abilityCooldownHud = false;
    public boolean abilityCooldownNotify = false;
    public boolean abilityCooldownShade = false;
    public int abilityCooldownHudX = 10;
    public int abilityCooldownHudY = 120;
    public float abilityCooldownHudScale = 1.0f;
    public boolean safariAnnounceAllButMacaw = false;
    public boolean safariAttemptCounts = false;
    public boolean tabWidgetHint = false;
    public boolean hideHealthBar = false;
    public boolean hideAirBar = false;
    public boolean hideBossBar = false;
    public boolean hideXpBar = false;
    // w08-03-hud
    public boolean perfHudTime = false;
    public String perfHudTimeFormat = "24h";
    public boolean perfHudTimeSeconds = false;
    public String perfHudTimeLabel = "Text";
    public boolean safariMoundCounter = false;
    public boolean playersInRange = false;
    public int playersInRangeX = 10;
    public int playersInRangeY = 120;
    public float playersInRangeScale = 1.0f;
    public boolean reaperTimer = false;
    public boolean reaperTimerBar = false;
    public int reaperTimerX = 10;
    public int reaperTimerY = 140;
    public float reaperTimerScale = 1.0f;
    public boolean skeletonHelmetHideBones = false;
    public boolean skeletonHelmetBar = false;
    public int skeletonBarX = 10;
    public int skeletonBarY = 160;
    public float skeletonBarScale = 1.0f;
    public boolean jyrreTimer = false;
    public boolean jyrreShowInactive = false;
    public long jyrreRemainingMs = 0;
    public int jyrreTimerX = 10;
    public int jyrreTimerY = 180;
    public float jyrreTimerScale = 1.0f;
    // w08-05-hud
    public boolean trackerHistory = false;
    public int trackerHistoryKeep = 30;
    public boolean moveVanillaHud = false;
    public boolean moveVanillaHudEverywhere = false;
    public boolean moveHotbar = false;
    public int vHotbarX = -1;
    public int vHotbarY = -1;
    public float vHotbarScale = 1.0f;
    public boolean moveXpBar = false;
    public int vXpBarX = -1;
    public int vXpBarY = -1;
    public float vXpBarScale = 1.0f;
    public boolean moveActionBar = false;
    public int vActionBarX = -1;
    public int vActionBarY = -1;
    public float vActionBarScale = 1.0f;
    public boolean moveHeldItemName = false;
    public int vHeldItemX = -1;
    public int vHeldItemY = -1;
    public float vHeldItemScale = 1.0f;
    public boolean moveTabList = false;
    public int vTabListX = -1;
    public int vTabListY = -1;
    public float vTabListScale = 1.0f;
    public boolean vanillaManaBar = false;
    public boolean vanillaManaBarBlink = true;
    public boolean vanillaManaBarHungerSprites = false;
    public boolean vanillaHpHud = false;
    public int vanillaHpX = 10;
    public int vanillaHpY = 320;
    public float vanillaHpScale = 1.0f;
    public boolean inWaterDisplay = false;
    public boolean inWaterStrandedOnly = true;
    public int inWaterX = 10;
    public int inWaterY = 340;
    public float inWaterScale = 1.0f;
    public boolean customScoreboardNumberDiff = false;
    // w08-04-hud
    public boolean profileInfoHud = false;
    public boolean profileInfoType = true;
    public boolean profileInfoName = false;
    public boolean profileInfoLevel = true;
    public int profileInfoX = 5;
    public int profileInfoY = 160;
    public float profileInfoScale = 1.0f;
    public boolean sbLevelXpBar = false;
    public boolean titleStack = false;
    public boolean titleStackUp = false;
    public boolean titleStackClearOnWorld = true;
    public int titleStackX = 110;
    public int titleStackY = 60;
    public float titleStackScale = 1.0f;
    public boolean springBootsHeight = false;
    public boolean springBootsBox = true;
    public int springBootsX = 5;
    public int springBootsY = 200;
    public float springBootsScale = 1.0f;
    public boolean safariQuotas = false;
    public boolean dailyTimers = false;
    public String dailyTimersShow = "Always";
    public boolean dailyTimersCookie = true;
    public boolean dailyTimersGodPot = true;
    public boolean dailyTimersCakes = true;
    public boolean dailyTimersPuzzler = true;
    public boolean dailyTimersFetchur = true;
    public boolean dailyTimersPowder = true;
    public boolean dailyTimersRift = true;
    public boolean dailyTimersShopLimit = true;
    public int dailyTimersX = 5;
    public int dailyTimersY = 230;
    public float dailyTimersScale = 1.0f;
    public boolean sbClockYear = false;
    // w08-08-vis
    public boolean hideArcherPassive = false;
    public boolean arrowTrail = false;
    public boolean arrowTrailOnlyOwn = true;
    public boolean arrowTrailOwnColor = false;
    public String arrowTrailColor = "C855FF55";
    public String arrowTrailOwnColorHex = "C855FFFF";
    public float arrowTrailSeconds = 0.5f;
    public float arrowTrailWidth = 4f;
    public boolean arrowHitboxes = false;
    public String arrowHitboxColor = "FFFFFFFF";
    public String arrowHitboxFillColor = "00000000";
    public float arrowHitboxWidth = 1f;
    public boolean customCrosshair = false;
    public String crosshairShape = "Vanilla";
    public String crosshairColor = "FFFFFFFF";
    public boolean crosshairChroma = false;
    public int crosshairSize = 6;
    public int crosshairThickness = 1;
    public int crosshairGap = 0;
    public boolean crosshairThirdPerson = false;
    public boolean crosshairCentered = false;
    public boolean oldItemTextures = false;
    public boolean oldItemTexturesHeads = true;
    public boolean oldItemTexturesKeepPack = true;
    public boolean betterContainers = false;
    public String betterContainersStyle = "Dark";
    public boolean betterContainersToggles = true;
    // w08-02-hud
    public boolean effectTimers = false;
    public boolean effectTimersCookie = true;
    public boolean effectTimersGodPot = true;
    public boolean effectTimersMixins = false;
    public boolean effectTimersWarn = false;
    public boolean effectTimersWarnSound = true;
    public int effectTimersWarnSeconds = 30;
    public int effectTimersX = 10;
    public int effectTimersY = 160;
    public float effectTimersScale = 1.0f;
    public boolean mayorOverlay = false;
    public boolean mayorOverlayPerks = true;
    public boolean mayorOverlayDesc = false;
    public boolean mayorOverlayMinister = true;
    public boolean mayorOverlayCandidates = true;
    public boolean mayorOverlayNewMayor = true;
    public int mayorOverlayX = 10;
    public int mayorOverlayY = 200;
    public float mayorOverlayScale = 1.0f;
    public boolean notificationPopups = false;
    public boolean notificationOverInventory = false;
    public int notificationCloseKey = 88;
    public boolean sackHud = false;
    public java.util.List<String> sackHudItems = new java.util.ArrayList<>();
    public int sackHudMax = 10;
    public boolean sackHudOnlyMenus = false;
    public boolean sackHudHideEmpty = true;
    public boolean sackHudHighlight = true;
    public boolean sackHudIcons = true;
    public int sackHudX = 10;
    public int sackHudY = 240;
    public float sackHudScale = 1.0f;
    public boolean minionWidget = false;
    public boolean minionWidgetIcons = true;
    public boolean minionWidgetOnlyProblems = false;
    public int minionWidgetX = 10;
    public int minionWidgetY = 280;
    public float minionWidgetScale = 1.0f;
    public boolean noBitsWarning = false;
    public boolean noBitsWarningSound = true;
    public boolean bitsGainMessage = false;
    public int bitsGainThreshold = 400;
    public java.util.Map<String, Long> bitsAvailableByProfile = new java.util.HashMap<>();
    public boolean screenFlash = false;
    public String screenFlashColor = "66FF4D4D";
    public int screenFlashMs = 600;
    public boolean screenFlashRareCatch = true;
    public boolean screenFlashPests = true;
    public boolean screenFlashKicked = true;
    public boolean screenFlashEffects = true;
    public boolean screenFlashBits = true;
    // w08-09-vis
    public boolean containerTweaks = false;
    public boolean containerNoDim = false;
    public boolean containerTint = false;
    public String containerTintColor = "FFFFFFFF";
    public boolean hideCraftingText = false;
    public boolean noFillerPaneHighlight = false;
    public boolean hideOffhandSlotIcon = false;
    public boolean commonSeaCreatureHider = false;
    public float commonSeaCreatureAlpha = 30f;
    public boolean commonSeaCreatureRodOnly = false;
    public boolean chumBucketHider = false;
    public boolean chumBucketHideBucket = false;
    public boolean chumBucketHideOwn = false;
    public boolean hideArachneLootTags = false;
    public boolean colorParticleFix = false;
    public boolean customWitherCloak = false;
    public float witherCloakCount = 8f;
    public float witherCloakSpeed = 45f;
    public float witherCloakDistance = 1.2f;
    public String witherCloakColor = "6655FFFF";
    public boolean lookTargeting = false;
    public boolean lookTargetingLine = true;
    public float lookTargetingRange = 32f;
    // w07-04-qol
    public boolean signCalculator = false;
    public boolean signCalculatorEquals = false;
    public boolean calcCommand = false;
    public boolean hotkeyCombos = false;
    public boolean imageShareHistory = false;
    @Global
    public java.util.Map<String, String> imageShareLinks = new java.util.HashMap<>();
    public boolean muteRatPet = false;
    public boolean muteRatPetSqueakOnly = false;
    public boolean muteBackpackOpen = false;
    public boolean signEnterShiftNewline = false;
    // w07-01-qol
    public boolean commandLowercase = false;
    public boolean shortcutTabComplete = false;
    public String sendCoordsChannel = "Party";
    public boolean abiphoneAccept = false;
    public int abiphoneAcceptKey = -1;
    // w07-07-hud
    public boolean customTrackers = false;
    public java.util.List<CustomTrackerDef> customTrackerDefs = new java.util.ArrayList<>();
    public int customTrackerX = 10;
    public int customTrackerY = 120;
    public float customTrackerScale = 1.0f;
    public boolean beaconPower = false;
    public boolean beaconPowerStat = true;
    public boolean beaconPowerCompress = false;
    @Global
    public java.util.Map<String, Long> beaconPowerExpiry = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, String> beaconPowerStats = new java.util.HashMap<>();
    public int beaconPowerX = 10;
    public int beaconPowerY = 200;
    public float beaconPowerScale = 1.0f;
    public boolean arrowPoisonHud = false;
    public int arrowPoisonHudX = 10;
    public int arrowPoisonHudY = 230;
    public float arrowPoisonHudScale = 1.0f;
    public boolean customTextBox = false;
    public String customTextBoxText = "&aHello &fWorld\\n&7second line";
    public boolean customTextBoxOnlyInGui = false;
    public int customTextBoxX = 10;
    public int customTextBoxY = 260;
    public float customTextBoxScale = 1.0f;
    public boolean perfHudCps = false;
    public boolean movementSpeed = false;
    public int movementSpeedX = 10;
    public int movementSpeedY = 290;
    public float movementSpeedScale = 1.0f;
    public boolean crownOfAvarice = false;
    public boolean crownOfAvariceShort = true;
    public boolean crownOfAvariceResetOnWorld = false;
    public int crownOfAvariceAfk = 120;
    public int crownOfAvariceX = 10;
    public int crownOfAvariceY = 310;
    public float crownOfAvariceScale = 1.0f;

    public static class CustomTrackerDef {
        public boolean enabled = true;
        public String name = "Custom Tracker";
        public String areas = "";
        public String items = "";
        public boolean coins = false;
        public int priceSource = 1;
        public int pauseSeconds = 60;
    }

    // w07-08-hud
    public boolean chickenHeadTimer = false;
    public boolean chickenHeadHideChat = false;
    public int chickenHeadX = 10;
    public int chickenHeadY = 140;
    public float chickenHeadScale = 1.0f;
    public boolean cakeTracker = false;
    public String cakeTrackerShow = "Missing";
    public boolean cakeTrackerAhHighlight = true;
    public int cakeTrackerX = 10;
    public int cakeTrackerY = 40;
    public float cakeTrackerScale = 1.0f;
    public boolean draconicTracker = false;
    public boolean draconicOnlyAltar = false;
    public int draconicPriceSource = 1;
    public int draconicTrackerX = 10;
    public int draconicTrackerY = 60;
    public float draconicTrackerScale = 1.0f;
    public boolean executionLog = false;
    public int executionLogX = 4;
    public int executionLogY = 220;
    public float executionLogScale = 1.0f;
    public boolean itemPickupLog = false;
    public boolean pickupLogSacks = true;
    public boolean pickupLogCoins = false;
    public boolean pickupLogCompact = true;
    public boolean pickupLogIcons = true;
    public int pickupLogExpire = 10;
    public boolean pickupLogValue = false;
    public int pickupLogPriceSource = 1;
    public int pickupLogX = 10;
    public int pickupLogY = 100;
    public float pickupLogScale = 1.0f;
    public boolean customTodos = false;
    public boolean customTodoSound = true;
    public boolean customTodoOnlyReady = false;
    public java.util.List<CustomTodo> customTodoList = new java.util.ArrayList<>();
    public int customTodoX = 10;
    public int customTodoY = 180;
    public float customTodoScale = 1.0f;
    public static class CustomTodo {
        public String label = "";
        public String trigger = "";
        public String matcher = "Contains"; // Contains / Starts With / Equals / Regex
        public String time = "1h";
        public boolean enabled = true;
        public java.util.Map<String, Long> readyAt = new java.util.HashMap<>();
        public CustomTodo() {}
    }
    public boolean equipmentHud = false;
    public boolean equipmentHudVertical = false;
    public int equipmentHudX = 10;
    public int equipmentHudY = 200;
    public float equipmentHudScale = 1.0f;
    // w07-06-hud
    public boolean deployableOrb3d = false;
    public boolean customScoreboard = false;
    public boolean customScoreboardTitle = true;
    public boolean customScoreboardHideServer = true;
    public boolean customScoreboardHideFooter = true;
    public boolean customScoreboardHideBlank = false;
    public boolean customScoreboardBackground = true;
    public int customScoreboardX = 500;
    public int customScoreboardY = 60;
    public float customScoreboardScale = 1.0f;
    public boolean armorHud = false;
    public boolean armorHudVertical = false;
    public boolean armorHudBackground = true;
    public boolean armorHudSlotBg = false;
    public boolean armorHudEmpty = false;
    public boolean armorHudHelmet = true;
    public boolean armorHudChest = true;
    public boolean armorHudLegs = true;
    public boolean armorHudBoots = true;
    public int armorHudX = 5;
    public int armorHudY = 120;
    public float armorHudScale = 1.0f;
    public boolean armorStackHud = false;
    public int armorStackHudX = 5;
    public int armorStackHudY = 150;
    public float armorStackHudScale = 1.0f;
    public boolean accurateAbsorption = false;
    public float accurateAbsorptionMax = 40f;
    public boolean allSkillsDisplay = false;
    public boolean allSkillsXp = true;
    public boolean allSkillsOverflow = true;
    public java.util.List<String> allSkillsHidden = new java.util.ArrayList<>();
    public int allSkillsX = 5;
    public int allSkillsY = 170;
    public float allSkillsScale = 1.0f;
    public boolean skillGoals = false;
    public boolean skillGoalAlert = true;
    public java.util.Map<String, Integer> skillGoalLevels = new java.util.HashMap<>();
    // w07-09-hud
    public boolean itemCooldowns = false;
    public boolean itemCooldownsCrosshair = true;
    public boolean inventoryHud = false;
    public boolean inventoryHudArmor = false;
    public int inventoryHudX = 5;
    public int inventoryHudY = 120;
    public float inventoryHudScale = 1.0f;
    public boolean perfHudLag = false;
    public float perfHudLagThreshold = 300f;
    public boolean eyesPlacedHud = false;
    public boolean eyesPlacedAnywhere = false;
    public int eyesPlacedX = 5;
    public int eyesPlacedY = 60;
    public float eyesPlacedScale = 1.0f;
    public boolean endStatsHud = false;
    public boolean endStatsSoulflow = true;
    public boolean endStatsFinalDestination = true;
    public boolean endStatsOverflow = true;
    public boolean endStatsOverflowWithMana = false;
    public boolean endStatsLegion = true;
    public int endStatsX = 5;
    public int endStatsY = 80;
    public float endStatsScale = 1.0f;
    public boolean explosiveShotTimer = false;
    public boolean fatalTempoHud = false;
    public int fatalTempoX = 5;
    public int fatalTempoY = 140;
    public float fatalTempoScale = 1.0f;
    // w07-02-qol
    public boolean muteErrorSound = false;
    public boolean dumpGuiSingleItem = false;
    public boolean shortcutChains = false;
    public int shortcutChainDelay = 500;
    public boolean seamlessServerSwitch = false;
    public boolean abiphoneHelper = false;
    public boolean abiphoneFavorites = true;
    public String abiphoneFavoriteColor = "FFAA00";
    public boolean abiphoneRequirements = true;
    public boolean abiphoneRemoveConfirm = true;
    @Global
    public boolean abiphoneFavOnly = false;
    @Global
    public java.util.List<String> abiphoneFavoriteList = new java.util.ArrayList<>();
    public boolean waypointGroups = false;
    public boolean waypointGroupsBeam = true;
    public boolean waypointGroupsDistance = true;
    // w07-05-qol
    public boolean tabCompleteShowSlots = false;
    public boolean tabCompleteBestFriends = false;
    public boolean tabCompleteTradeIsland = false;
    public List<String> bestFriendNames = new ArrayList<>();
    public boolean scoreboardLogger = false;
    public boolean scoreboardLoggerFull = false;
    public boolean scoreboardLoggerColors = false;
    public boolean scoreboardLoggerDungeonsOnly = false;
    public boolean warpShortcuts = false;
    public boolean wikiLookup = false;
    public int wikiLookupKey = 290;
    public boolean chatWaypointCommands = false;
    public boolean routeEditorExtras = false;
    public int routeEditKey = -1;
    public int routePlaceKey = -1;
    public int routePauseKey = -1;
    public int routeNextKey = -1;
    public int routePrevKey = -1;
    public int routeStopKey = -1;
    public boolean abilityMenuFix = false;
    // w07-00-qol
    public boolean musicOverlay = false;
    public boolean musicOverlayArt = true;
    public boolean musicOverlayLyrics = false;
    public int musicPlayPauseKey = -1;
    public int musicNextKey = -1;
    public int musicPrevKey = -1;
    public int musicOverlayX = 5;
    public int musicOverlayY = 200;
    public float musicOverlayScale = 1.0f;
    public boolean level7Guide = false;
    public int level7GuideStep = 1;
    public int level7GuideX = 5;
    public int level7GuideY = 60;
    public float level7GuideScale = 1.0f;
    public boolean warpTransferWait = false;
    public boolean warpTransferWaitMessage = false;
    public boolean tiaRelayHelper = false;
    public boolean tiaRelayMute = false;
    public boolean tiaRelayNextWaypoint = true;
    public boolean tiaRelayAllWaypoints = false;
    public boolean queueEstimate = false;
    public int queueEstimateX = -1;
    public int queueEstimateY = -1;
    public float queueEstimateScale = 1.0f;
    public boolean calcXpCommand = false;
    public boolean clockOffsetWarning = false;
    // w06-00-qol
    public boolean commandHelpers = false;
    public boolean cmdWarpIs = true;
    public boolean cmdViewRecipe = true;
    public boolean cmdViewRecipeTab = true;
    public boolean coopAddConfirm = false;
    public boolean coopRequestBlock = true;
    public boolean stopMovingOnDeath = false;
    public boolean alwaysShowItemName = false;
    // w06-02-qol
    public boolean copyPlaytime = false;
    public boolean autoJoinSkyblock = false;
    public boolean autoJumpOutsideSkyblock = false;
    @Global
    public Boolean autoJumpOriginal = null;
    public boolean achievements = false;
    public boolean achievementSound = true;
    public boolean crashOnDeath = false;
    public boolean crashOnTttFail = false;
    // w06-09-qol
    public boolean spiderRelics = false;
    public boolean spiderRelicsShowFound = false;
    public boolean spiderRelicsPath = true;
    public boolean ravengardLootBagChecks = false;
    public boolean teleportPadCompactName = false;
    public boolean teleportPadNumbers = false;
    public boolean skyblockTimeReminder = false;
    public boolean skyblockTimeReminderSound = true;
    public boolean seeThroughWindow = false;
    public int seeThroughWindowKey = -1;
    public int seeThroughWindowOpacity = 50;
    public boolean soopyCommands = false;
    public boolean spyMode = false;
    // w06-04-qol
    public boolean reminders = false;
    public boolean remindersTitle = true;
    public boolean remindersAutoDelete = false;
    public int remindersInterval = 5;
    public boolean reminderHud = false;
    public int reminderHudX = 10;
    public int reminderHudY = 120;
    public float reminderHudScale = 1.0f;
    @Global
    public java.util.List<Reminder> reminderList = new java.util.ArrayList<>();
    public static class Reminder {
        public String id = "";
        public String text = "";
        public long at;
        public long last;
        public Reminder() {}
        public Reminder(String id, String text, long at) { this.id = id; this.text = text; this.at = at; }
    }
    public boolean damageAlert = false;
    public int damageAlertMin = 0;
    public boolean damageAlertSound = true;
    public String damageAlertSoundType = "NOTE_PLING";
    public boolean damageAlertFlash = false;
    public boolean damageAlertTitle = false;
    public boolean damageAlertChat = false;
    public boolean containerTriggers = false;
    public java.util.List<ContainerTrigger> containerTriggerList = new java.util.ArrayList<>();
    public static class ContainerTrigger {
        public String action = "Sound"; // Sound / Title / Chat / Close
        public String title = "";
        public ContainerTrigger() {}
        public ContainerTrigger(String action, String title) { this.action = action; this.title = title; }
    }
    public boolean gfsPiggyBank = false;
    public boolean mhDungeonHubSelector = false;
    public boolean harpHelper = false;
    public int[] harpKeys = {49, 50, 51, 52, 53, 54, 55};
    public boolean harpKeyLabels = true;
    public boolean harpGuiScale = false;
    public boolean harpQuickRestart = false;
    public boolean harpHideTooltips = false;
    @Global
    public boolean discordRpc = false;
    @Global
    public boolean discordRpcLocation = true;
    @Global
    public boolean discordRpcDungeon = true;
    @Global
    public String discordRpcAppId = "";
    // w06-03-qol
    public boolean fairySouls = false;
    public boolean fairySoulsShowFound = false;
    public boolean fairySoulsNearby = false;
    public String colorFairySoulMissing = "FF55FF55";
    public String colorFairySoulFound = "FFFF5555";
    public boolean focusLossPrevention = false;
    public boolean confirmDisconnect = false;
    public float confirmDisconnectMs = 150f;
    public boolean enchantedClockReminder = false;
    public java.util.List<String> enchantedClockDisabled = new java.util.ArrayList<>();
    public float enchantedClockRepeatMin = 0f;
    public boolean upgradeReminder = false;
    public boolean cityProjectReminder = false;
    public boolean cityProjectMaterials = false;
    public boolean cityProjectHighlight = false;
    public boolean centuryCakeTimer = false;
    public boolean centuryCakeOnlyExpired = false;
    public int centuryCakeTimerX = 10;
    public int centuryCakeTimerY = 200;
    public float centuryCakeTimerScale = 1.0f;
    public boolean centuryCakeGlow = false;
    public String colorCenturyCakeReady = "55FF55";
    // w06-06-qol
    public boolean minionOverlay = false;
    public boolean minionOverlayName = true;
    public boolean minionOverlayOnlyTier = false;
    public boolean minionOverlayEmptied = true;
    public float minionOverlayDistance = 16f;
    public boolean minionLastOpened = true;
    public String minionLastOpenedColor = "55FFFF";
    public boolean minionCoinsPerDay = true;
    public boolean minionHideMobNametags = false;
    public boolean minionWarnings = false;
    public boolean minionWarnFull = true;
    public boolean minionWarnReach = true;
    public String minionWarnSound = "NOTE_PLING";
    public boolean minionHideLocationWarning = false;
    public boolean minionOnlyEnchanted = false;
    public boolean minionXpTooltip = false;
    public boolean huntrapMisclick = false;
    public boolean snakeGameKeys = false;
    public boolean middleClickFix = false;
    public boolean mobSpawnAlert = false;
    public String mobSpawnAlertMode = "Title + Sound";
    public String mobSpawnAlertSound = "NOTE_PLING";
    public float mobSpawnAlertCooldown = 5f;
    public boolean mobSpawnAlertChat = true;
    public String mobSpawnAlertCoords = "Nobody";
    public boolean islandAlerts = false;
    public String islandAlertMode = "Title + Sound";
    public String islandAlertSound = "NOTE_PLING";
    public List<String> islandAlertRules = new ArrayList<>();
    // w06-08-qol
    public boolean previousLobby = false;
    public int previousLobbyMinutes = 10;
    public boolean quickCraftConfirm = false;
    public boolean autoShiftClick = false;
    public boolean autoShiftNpcSell = true;
    public boolean autoShiftBrewing = true;
    public boolean autoShiftEquipment = true;
    public boolean hideServerId = false;
    public String hideServerIdMode = "Hidden";
    public boolean hideProfileId = false;
    public boolean screenshotPreview = false;
    public boolean screenshotPreviewAnimation = true;
    public boolean screenshotPreviewSound = true;
    public boolean screenshotEditor = false;
    // w06-01-qol
    public boolean buildersWandPreview = false;
    public boolean buildersRulerPreview = true;
    public boolean blockZapperPreview = true;
    public boolean blockZapperUndo = false;
    public String buildersWandColor = "5055FFFF";
    @Global
    public boolean borderlessWindow = false;
    @Global
    public boolean customWindowTitle = false;
    @Global
    public String windowTitle = "";
    @Global
    public boolean customWindowIcon = false;
    public boolean brewingStandColors = false;
    public boolean bingoCardDisplay = false;
    public boolean bingoHideCommunity = false;
    public boolean bingoNextHint = true;
    public boolean bingoCommunityChange = false;
    public boolean bingoStepHelper = false;
    public int bingoCardDisplayX = 5;
    public int bingoCardDisplayY = 120;
    public float bingoCardDisplayScale = 1f;
    @Global
    public boolean hotkeyConditions = false;
    public boolean arrowHitSound = false;
    public String arrowHitSoundType = "NOTE_HARP";
    public float arrowHitSoundVolume = 1f;
    public float arrowHitSoundPitch = 1f;
    // w06-05-qol
    public boolean fancyWarpMenu = false;
    public boolean devWorldTools = false;
    public boolean devRegionSelect = true;
    public boolean devParkourRecorder = true;
    public int devParkourSaveKey = -1;
    public int devParkourDeleteKey = -1;
    public boolean devGraphEditor = false;
    public int devGraphPlaceKey = -1;
    public int devGraphSelectKey = -1;
    public int devGraphDeleteKey = -1;
    public int devGraphUndoKey = -1;
    public boolean deathSound = false;
    public String deathSoundType = "BLAZE_DEATH";
    public String deathSoundSpecial = "WITHER_SPAWN";
    public float deathSoundVolume = 1.0f;
    public boolean noFrontView = false;
    public int noFrontViewKey = -1;
    public boolean infernoFuelBlocker = false;
    public boolean infernoProfitTracker = false;
    public boolean infernoTrackerAfterCollect = true;
    public int infernoTrackerPriceSource = 1;
    public int infernoTrackerX = 10;
    public int infernoTrackerY = 120;
    public float infernoTrackerScale = 1.0f;
    public boolean doubleSneakFix = false;
    public boolean ridingInputFix = false;
    public boolean customFov = false;
    public int customFovValue = 110;
    public boolean oldCrouch = false;
    public boolean noSwimPose = false;
    // w06-07-qol
    public boolean callLinks = false;
    public boolean callLinkMismyla = true;
    public boolean callLinkFred = true;
    public boolean callLinkTrevor = true;
    public boolean sbClock = false;
    public boolean sbClockLobby = true;
    public boolean sbClockLobbyDate = false;
    public boolean sbClockDate = true;
    public boolean sbClockTime = true;
    public boolean sbClock24h = false;
    public boolean sbClockExactMinutes = false;
    public boolean sbClockSymbol = true;
    public int sbClockX = 5;
    public int sbClockY = 120;
    public float sbClockScale = 1.0f;
    public boolean islandNav = false;
    public String islandNavColor = "00FFFF";
    public boolean islandNavAreaHud = false;
    public boolean islandNavAreaTitle = false;
    public boolean islandNavAreaLabels = false;
    public int islandNavHudX = 5;
    public int islandNavHudY = 160;
    public float islandNavHudScale = 1.0f;
    public boolean monoAudio = false;
    public boolean stopHeartsBouncing = false;
    public boolean textFieldUndo = false;
    public boolean chatFilterSbLevel = false;
    public int chatFilterSbLevelMin = 100;
    public boolean hideItemDurability = false;
    public boolean forceEnchantGlint = false;
    public boolean hideArmorStandArmor = false;
    public boolean hideWitherbornWithers = false;
    public boolean hideMageBeams = false;
    public boolean middleClickSbMenu = false;
    public boolean serverRestartWarning = false;
    public boolean serverRestartSound = true;
    public boolean serverRestartTitle = true;
    public boolean serverRestartHud = true;
    public int serverRestartHudX = 200;
    public int serverRestartHudY = 40;
    public float serverRestartHudScale = 1.0f;
    // w05-02-inv
    public boolean menuSlotText = false;
    public boolean mstLevels = true;
    public boolean mstMinion = true;
    public boolean mstPotion = true;
    public boolean mstShopGuide = true;
    public boolean mstEssence = true;
    public boolean mhStatsTuningMore = false;
    public boolean statIcons = false;
    public boolean statIconsReplace = false;
    // w05-04-inv
    public boolean hideVanillaEnchants = false;
    public boolean rarityTooltipBorder = false;
    public float rarityTooltipBorderOpacity = 1.0f;
    public boolean hotxCurrencyPerPerk = false;
    public boolean hotxCurrencySpent = true;
    public boolean hotxCurrencyFor10 = true;
    public boolean hotxCurrencyCurrent = true;
    public boolean toolkitCropIcons = false;
    public boolean mhTabWidgets = false;
    public boolean mhStrandedNpcs = false;
    public boolean expHideTooltips = false;
    // w05-09-inv
    public boolean storageQuickButtons = false;
    public boolean storageVanillaTextures = false;
    public boolean trashHighlighter = false;
    public String colorTrash = "80FF3333";
    public String trashHighlightStyle = "Fill";
    public java.util.List<TrashFilter> trashFilters = com.teslamaps.features.TrashHighlighter.defaults();
    public static class TrashFilter {
        public String target = "ID";      // ID / Name / Lore
        public String mode = "Contains";  // Equals / Contains / Regex
        public String value = "";
        public TrashFilter() {}
        public TrashFilter(String target, String mode, String value) { this.target = target; this.mode = mode; this.value = value; }
    }
    public boolean instaLoadoutSwap = false;
    public int[] instaLoadoutKeys = {-1, -1, -1, -1, -1, -1, -1, -1, -1};
    public float instaLoadoutDelay = 0.2f;
    public int instaLoadoutRandomMs = 150;
    public boolean instaLoadoutAutoClose = true;
    public boolean middleClickGui = false;
    public int middleClickGuiBlacklistKey = -1;
    public java.util.List<String> middleClickGuiBlacklist = new java.util.ArrayList<>();
    // w05-01-inv
    public boolean ravengardCompare = false;
    public boolean ravengardDps = false;
    public boolean wizardPortalLore = false;
    public boolean stackingEnchantHud = false;
    public boolean stackingEnchantHudCompact = true;
    public int stackingEnchantHudX = 10;
    public int stackingEnchantHudY = 200;
    public float stackingEnchantHudScale = 1.0f;
    public boolean protectEvolvingConsumables = false;
    public boolean starsStackSize = false;
    public boolean starsStackSizeColors = false;
    public boolean redMasterStars = false;
    public boolean skyblockIdTooltip = false;
    // w05-08-inv
    public boolean slotTextControl = false;
    public String slotTextMode = "Hold To Hide";
    public int slotTextKey = 342;
    public List<String> slotTextDisabled = new ArrayList<>();
    public boolean enchantAbbrevBazaar = false;
    public boolean storageRememberLast = false;
    public boolean storageSettleSwap = false;
    public boolean storageCachedWhileLoading = false;
    public int storageWidthPercent = 100;
    public boolean storageShowUnopened = false;
    public boolean storageSearchResults = false;
    public boolean searchHighlight = false;
    public int searchHighlightSeconds = 30;
    public String colorSearchHighlight = "9055FF55";
    // w05-07-inv
    public boolean salvageBlockOthers = false;
    public boolean salvageMuseumGuard = false;
    public boolean salvageMuseumBlock = false;
    public boolean compactorStatusIcon = false;
    public String compactorPreviewMode = "Always";
    public int compactorPreviewKey = 340;
    public boolean rarityBgHideHotbar = false;
    public String rarityBgPalette = "Hypixel";
    public boolean sackTable = false;
    public boolean sackTableClick = false;
    public boolean slotLockPerProfile = false;
    public boolean slotLockRiftSet = false;
    public boolean slotLockBinds = false;
    public int slotLockDropDelay = 0;
    public java.util.Map<String, java.util.List<Integer>> slotLockData = new java.util.HashMap<>();
    public boolean romanNumerals = false;
    // w05-00-inv
    public boolean oldMasterStars = false;
    public boolean sackStitchOverlay = false;
    public boolean sackStitchLore = false;
    public boolean midasBidBreakdown = false;
    public boolean petFullXp = false;
    public boolean reforgeStoneStats = false;
    public boolean reforgeStoneHideDefault = false;
    public boolean gemstoneStats = false;
    public boolean prehistoricEggBlocks = false;
    public boolean radioactiveBonus = false;
    public boolean ravengardCrownValues = false;
    // w05-03-inv
    public boolean itemList = false;
    public int itemListKey = -1;
    public String craftHelperShardTree = "";           // SkyShards export, empty = normal target
    public boolean wardrobeAutoClose = false;
    public int wardrobeCloseDelay = 0;                 // ms
    public boolean wardrobeCloseSound = false;
    public boolean wardrobeCloseOnlyEquip = false;
    public boolean storageGuiScaleOverride = false;
    public int storageGuiScale = 2;
    public boolean tooltipAnvilUses = false;
    public boolean tooltipSwordKills = false;
    public boolean tooltipRecomb = false;
    public boolean skyblockIdTooltipAdvanced = false;
    public boolean tooltipItemStars = false;
    public boolean tooltipEvolvingTime = false;
    public boolean tooltipCookieBits = false;
    public boolean tooltipStonksPrice = false;
    public boolean tooltipFannCost = false;
    public boolean supercraftReminder = false;
    public String fameRank = "";
    // w05-05-inv
    public boolean progressHighlightTier = false;
    public boolean progressHighlightRed = false;
    public String colorProgressUnfinished = "FF5555";
    public boolean devItemInfo = false;
    public int devCopyIdKey = -1;
    public int devCopyNbtKey = -1;
    public int devSlotNumbersKey = -1;
    public boolean devMissingRepoItems = false;
    public boolean hideNotClickable = false;
    public boolean hideNotClickableBlock = false;
    public boolean hideNotClickableGreen = false;
    public float hideNotClickableOpacity = 0.8f;
    public boolean itemProtectionRarity = false;
    public String itemProtectionHotbarRarity = "Legendary";
    public String itemProtectionInvRarity = "Epic";
    public boolean itemProtectionConfirm = true;
    public boolean itemProtectionSwitchGuard = true;
    public boolean itemProtectionRaritySell = true;
    public boolean slotLockHotbarIcon = false;
    public int slotLockWorldKey = -1;
    public boolean slotLockItemFrames = false;
    @Global
    public int customizeKey = -1;
    @Global
    public boolean customizeNoUuid = false;
    // w05-06-inv
    public boolean itemValueRequireKey = false;
    public int itemValueKey = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
    public boolean itemValueSkipMenus = false;
    public boolean customGlintColors = false;
    public java.util.Map<String, String> customGlintColor = new java.util.HashMap<>();
    public boolean sackApi = false;
    public boolean itemNumbers = false;
    public java.util.List<String> itemNumberTypes = new java.util.ArrayList<>();
    public boolean hotmHighlight = false;
    public String colorHotmEnabled = "55FF55";
    public String colorHotmDisabled = "FF5555";
    public boolean gemstoneBuffs = false;
    public boolean powerScrollBuff = false;
    public boolean itemCycle = false;
    public java.util.List<ItemCycleRule> itemCycleRules = new java.util.ArrayList<>();
    public boolean itemProtectionStarred = false;

    public static class ItemCycleRule {
        public String target = "";
        public String island = "";
        public int button = -1;
        public String clicked = "";
    }
    // w04-03-inv
    public boolean moveInsertIntoSack = false;
    public boolean arrowPoisonIndicator = false;
    public boolean revertAxes = false;
    public boolean bingoCardTips = false;
    public boolean bingoHideDoneDifficulty = false;
    public boolean highlightDisabledPotions = false;
    public boolean dungeonQualityTooltip = false;
    public String dungeonQualityStyle = "42/50";
    public boolean dungeonTierTooltip = false;
    public boolean armorHexTooltip = false;
    public boolean armorExoticTooltip = false;
    public boolean dyeHexColors = false;
    // w04-00-dun
    public boolean croesusKeyCost = false;             // subtract the Dungeon Chest Key when a chest needs one
    public boolean croesusNoEssence = false;           // leave essence out of the profit
    public boolean croesusAvgPrices = false;           // 7-day average prices, current price as fallback
    public boolean croesusHighlightSecond = false;     // second best chest in yellow
    public boolean croesusDerpyWarn = false;
    public boolean croesusRerollProtect = false;
    public float croesusRerollProtectMin = 4.0f;       // million coins
    public boolean diamanteAlert = false;
    public boolean starMobIdLink = false;              // link ✯ label to mob by entity id
    public boolean starMobTypeColors = false;
    public String colorESPStarredChunky = "FF0080";
    public String colorESPStarredSkeletonMaster = "FF8000";
    public String colorESPStarredMini = "EB01A5";
    // w04-04-inv
    public boolean equipmentDisplay = false;
    public String equipmentDisplayPos = "Left";
    public boolean missingEnchants = false;
    public boolean removeGearScore = false;
    public boolean drillLoreCompact = false;
    public boolean evolvingProgress = false;
    public boolean evolvingSlotText = false;
    public boolean gemstoneList = false;
    public boolean experimentHelper = false;
    public boolean experimentChronomatron = true;
    public boolean experimentUltrasequencer = true;
    public boolean experimentSuperpairs = true;
    // w04-06-inv
    public String opheliaPotLock = "Off";              // Off / I..VII: only that Dungeon Potion is clickable at Ophelia
    public boolean duplicateEnchantFix = false;
    public boolean enchantRename = false;
    public List<String> enchantRenames = new ArrayList<>();   // "Old=New"
    public boolean forgeRecipeViewer = false;
    public boolean enchantTablePanel = false;
    public boolean enchantPanelSortName = false;
    public boolean enchantPanelConflicts = true;
    public boolean fancyCraftingTable = false;
    public boolean hoppityCollectionStats = false;
    public boolean hoppityHighlightRequirements = true;
    @Global
    public java.util.Map<String, Integer> hoppityRabbits = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, String> hoppityLocationRabbits = new java.util.HashMap<>();
    @Global
    public java.util.Map<String, String> hoppityRabbitRarity = new java.util.HashMap<>();
    // w04-05-inv
    public boolean experimentationAlerts = false;
    public boolean expMaxRoundsAlert = true;
    public boolean expUltraRareAlert = true;
    public boolean expSuperpairsXp = true;
    public boolean fullInventoryWarning = false;
    public int fullInventorySlots = 0;
    public boolean fullInventorySound = true;
    public boolean focusMode = false;
    public boolean focusModeAlways = false;
    public int focusModeKey = -1;
    public boolean focusModeSkipMenus = true;
    public boolean xpInTooltips = false;
    public boolean duplicateItems = false;
    public boolean duplicateItemsEverywhere = false;
    public String colorDuplicateItem = "80FF0000";
    public boolean hideExCoopMembers = false;
    public boolean hoppityWarpUniques = false;
    public boolean hoppityWarpHideMaxed = false;
    public java.util.List<String> hiddenCoopMembers = new ArrayList<>();
    public java.util.Map<String, java.util.List<String>> hoppityUniqueEggs = new java.util.HashMap<>();
    // w04-08-inv
    public boolean limitDroppingItems = false;
    public boolean compactLevelBars = false;
    public boolean legacyAttributeBg = false;
    public String colorLegacyAttributeBg = "80FF0000";
    public boolean itemPickupAlert = false;
    public List<String> itemPickupAlertNames = new ArrayList<>();
    public boolean itemPickupAlertTitle = true;
    public boolean itemPickupAlertSound = true;
    public boolean museumTooltip = false;
    public boolean museumSlotMarker = false;
    public String colorMuseumMissing = "60FF55FF";
    public boolean reforgeStop = false;
    public List<String> reforgeStopList = new ArrayList<>();
    public boolean reforgeStopStones = false;
    public boolean preventPlacingWeapons = false;
    public boolean preventPlacingHeads = false;
    public boolean preventPlacingExceptGarden = true;
    public int preventPlacingBlacklistKey = -1;
    public java.util.Set<String> preventPlacingBlacklist = new java.util.HashSet<>();
    // w04-09-inv
    public boolean menuHighlights = false;
    public boolean mhBeacon = true;
    public boolean mhHubSelector = true;
    public boolean mhRngMeter = true;
    public boolean mhMuseum = true;
    public boolean mhLoadout = true;
    public boolean mhPowerStones = true;
    public boolean mhPowerStoneBazaar = false;
    public boolean mhFavoritePowers = true;
    public boolean mhStatsTuning = true;
    public java.util.List<String> favoritePowers = new java.util.ArrayList<>();
    public boolean museumHelper = false;
    public int museumHelperRows = 15;
    public String museumHelperSort = "Lowest Price";
    public String museumHelperCategory = "All";
    public boolean priceLineKeyValue = false;
    public boolean obtainedDate = false;
    public boolean searchOverlayRecipes = false;
    public java.util.List<String> recipeSearchHistory = new java.util.ArrayList<>();
    public boolean minionCraftHelper = false;
    public int minionCraftHelperX = 10;
    public int minionCraftHelperY = 120;
    public float minionCraftHelperScale = 1.0f;
    public boolean minionUpgrades = false;
    public boolean powerStoneStats = false;
    public java.util.Map<String, Integer> magicalPower = new java.util.HashMap<>();
    // w04-01-inv
    public boolean backpackPreviewColors = false;      // tint previews + opened backpacks in their dye colour
    public boolean backpackPreviewCakeBag = false;     // preview the New Year Cake Bag too
    public String backpackPreviewStyle = "Compact";    // Compact / Chest
    public boolean anvilHelper = false;
    public boolean anvilHelperMatches = true;          // green: inventory items that fit the one in the anvil
    public boolean anvilHelperMismatch = true;         // red result slot when two books differ
    public boolean animatedDyeTooltipFix = false;
    public boolean accessoryHelper = false;            // panel next to the Accessory Bag
    public boolean accessoryHelperHighestOnly = false;
    public boolean accessoryTooltip = false;           // "Accessory: ✔ Collected" line
    public boolean accessorySlotSymbol = false;        // status symbol in the slot
    public boolean craftHelper = false;
    public boolean craftHelperSacks = true;
    public boolean craftHelperStorage = true;
    public boolean craftHelperDoneMessage = true;
    public String craftHelperTarget = "";              // item id
    public int craftHelperAmount = 1;
    public int craftHelperX = 5;
    public int craftHelperY = 120;
    public float craftHelperScale = 1.0f;
    // w04-02-inv
    public boolean customWardrobe = false;
    public boolean customWardrobeHideEmpty = false;
    public boolean customWardrobeOnlyFavorites = false;
    public boolean customWardrobeValue = true;
    public boolean customWardrobeFollowMouse = true;
    public int customWardrobeCardWidth = 64;
    public int customWardrobeCardHeight = 110;
    public String colorWardrobeEquipped = "55FF55";
    public String colorWardrobeFavorite = "FFAA00";
    @Global
    public java.util.Set<Integer> wardrobeFavorites = new java.util.HashSet<>();
    public boolean countdownTooltip = false;
    public boolean countdownTooltip24h = true;
    public boolean brokenFragmentTooltip = false;
    public boolean backpackSlot = false;
    public int backpackSlotKey = -1;
    public boolean backpackSlotScroll = true;
    public boolean backpackSlotPreview = true;
    public float backpackSlotOpacity = 0.6f;
    public boolean backpackSlotLeft = false;
    public int backpackSlotIndex = 0;
    public boolean attributeOverlay = false;
    public boolean attributeOverlayGoodRolls = true;
    public boolean attributeOverlayOnlyGood = false;
    public int attributeOverlayMinLevel = 0;
    public boolean migrateItemModels = false;
    public boolean brewingStandOverlay = false;
    // w04-07-inv
    public boolean starUpgradeCost = false;            // next star / dungeonize cost in tooltip
    public boolean starUpgradeItems = false;           // required items without ctrl
    public boolean shardSyphonReady = false;           // hunting box: green = can syphon, yellow = locked
    public boolean itemDataPreview = false;            // cake bag / builder's wand / backpack nbt preview
    public boolean itemDataPreviewCakeBag = true;
    public boolean itemDataPreviewInAh = false;
    public boolean fixTooltipStyle = false;            // drop non-vanilla tooltip_style
    public boolean hideFillerItems = false;            // no tooltip, no clicks on filler panes
    public boolean magicalPowerStack = false;          // MP as stack size on accessories
    public boolean magicalPowerRarityColor = true;
    public int abiphoneContacts = 0;                   // read from the abiphone menu for abicase MP
    public boolean itemSearchFromHover = false;        // search key over an item searches its name
    public int itemHighlightKey = -1;                  // highlight hovered item in menus
    // w03-03-dun
    public boolean dungeonCleanEnd = false;            // hide entities/particles after the boss is done
    public boolean dungeonCleanEndGuardians = false;   // F3/M3: guardians stay visible while sneaking
    public boolean deathTickShowClear = false;
    public boolean breakerCharges = false;
    public int breakerChargesX = 10;
    public int breakerChargesY = 260;
    public float breakerChargesScale = 1.0f;
    public boolean copyFails = false;
    public boolean icedMobs = false;
    public String icedMobsOutline = "FFADD8E6";
    public String icedMobsFill = "50ADBCE6";
    public float icedMobsLineWidth = 3.0f;
    public boolean partyFinderHelper = false;
    public boolean pfFloorStack = true;
    public boolean pfMemberCount = false;
    public boolean pfMarkCarries = true;
    public boolean pfMarkNonPugs = true;
    public boolean pfMarkIneligible = true;
    public int pfBelowClassLevel = 0;
    public boolean pfMarkMissingClass = true;
    public boolean pfColorLevels = true;
    public boolean pfMissingClasses = true;
    public boolean runLogger = false;
    // w03-06-dun
    public boolean score270Title = false;                  // big title at 270 score
    public String score270TitleText = "&e270 Score!";
    public String score270Text = "";                       // own chat/party text, empty = default
    public boolean score300Title = false;
    public String score300TitleText = "&c300 Score!";
    public boolean scoreTitleSound = false;
    public String score300Text = "";
    public boolean chestProfitHud = false;                 // movable chest profit list after the run
    public int chestProfitHudX = 10;
    public int chestProfitHudY = 120;
    public float chestProfitHudScale = 1.0f;
    public boolean chestProfitHudEssence = true;
    public boolean chestProfitHudCompact = true;
    public boolean teammateHealth = false;                 // label + low/critical warning on teammates
    public boolean teammateHealthLabels = true;
    public boolean teammateHealthOutline = true;
    public boolean teammateHealthMap = true;
    public boolean teammateHealthOnlyHealer = false;
    public boolean warpCooldownBlockJoin = false;          // block /joininstance while the cooldown runs
    public boolean warpCooldownEndMessage = false;
    public boolean watcherSpeedAnnounce = false;           // watcher grade to party chat
    public int bloodAssistDecimals = 2;
    public boolean customSplits = false;                   // own split messages per floor
    public java.util.Map<String, java.util.List<CustomSplit>> customSplitDefs = new java.util.HashMap<>();

    public static class CustomSplit {
        public String name = "";
        public String message = "";
    }

    // w03-00-boss
    public boolean bossbarHealthPercent = false;       // " - 25.0%" on boss bars without a known max hp
    public boolean dragonHealthColorName = false;      // colour name on the live dragon label
    public boolean dragonHealthStatue = false;         // "R" while the dragon sits in its statue box
    public float witherDragonBoxWidth = 3f;            // spawn box line width
    public boolean witherDragonLbSpots = false;        // last breath beams per spawning dragon
    public boolean dragonGlow = false;                 // glow live dragons in their colour
    public float witherDragonTracerWidth = 2f;
    public boolean witherDragonCenterTimer = false;    // big spawn countdown mid-screen
    public int dragonBoxMode = 0;                      // 0 full box, 1 part hitboxes
    public float dragonHitboxWidth = 2f;
    public boolean dragonHitboxCustomColor = false;
    public String dragonHitboxColor = "00FFFF";
    public boolean bloodCampSound = false;             // cowbell when a predicted mob lands
    public float bloodCampSoundTicks = 1.3f;
    public boolean lividColorTitle = false;
    public boolean lividAnnounce = false;              // /pc the found colour
    public String lividAnnounceText = "Livid is [color]!";
    public boolean lividHpLabel = false;               // 3D hp text over the real livid
    public boolean lividHpHud = false;
    public int lividHpHudX = 10;
    public int lividHpHudY = 120;
    public float lividHpHudScale = 1.0f;
    public boolean watcherHudAllClasses = false;
    public int watcherTimeFormat = 0;                  // 0 real, 1 server ticks, 2 both
    public boolean watcherChatSplits = false;
    public boolean watcherBossbarKills = false;        // "n/total" blood kills on the watcher bar
    public boolean watcherBossbarHide = false;         // hide the watcher bar outside blood
    // w03-08-dun
    public boolean croesusKismetCount = false;         // kismet feathers owned on the Reroll Chest button
    public String leapBoxStyle = "Default";            // Default / Class Color / Custom
    public String leapBoxColor = "BF3B3B3B";           // Custom box background
    public boolean leapKeyHint = false;                // key badge on each box
    public boolean leapClassLevel = false;             // class level next to the class
    public boolean leapOfflineStatus = false;          // OFFLINE from head lore + chat line on dead/offline click
    public boolean leapDoorOpenerHighlight = false;    // tint the last door opener's box
    public String leapDoorOpenerColor = "80AA5500";
    public boolean leapOnRelease = false;              // leap on mouse/key release
    public boolean leapStaticQuadrants = false;        // missing classes keep their quadrant empty
    public String leapAnnounceText = "Leaped to {name}!";
    public boolean leapMapAfterBlood = false;          // class boxes until blood opens, then map only
    public boolean leapMapCustomScale = false;
    public float leapMapScale = 1.5f;
    public boolean hideNonStarredNametags = false;     // dungeon: hide nametags of mobs without ✯
    public boolean scoreSecretsMaxBonus = false;       // needed secrets assume 5 crypts + mimic + prince
    public boolean scoreSecretsShowNeeded = false;     // middle number = needed total instead of still missing
    public boolean scoreSecretsTabTotal = false;       // total from tab found/% until the scan knows every room
    // w03-09-dun
    public boolean splitsTickTime = false;          // server-tick time behind every split
    public boolean splitsBossEntry = false;         // extra "Boss Entry" row = first 3 segments
    public boolean splitsCurrentHud = false;        // small HUD with only the running split in ticks
    public int currentSplitHudX = 10;
    public int currentSplitHudY = 200;
    public float currentSplitHudScale = 1.0f;
    public boolean keyHud = false;                  // "Key Obtained" while the party holds a wither/blood key
    public int keyHudX = 10;
    public int keyHudY = 215;
    public float keyHudScale = 1.0f;
    public boolean secretPickupHud = false;         // ticks until nearby secret items are pickable
    public boolean secretPickupHudSpawnTick = true; // 20-tick secret spawn cycle above
    public boolean secretPickupBox = false;         // draw a colored box instead of the item
    public float secretPickupBoxSize = 1.0f;
    public int secretPickupHudX = 200;
    public int secretPickupHudY = 130;
    public float secretPickupHudScale = 1.0f;
    public boolean spiritPetPartyCheck = false;     // warn before start when a party member shows a Spirit pet
    public boolean dungeonGambling = false;         // CSGO case animation on obsidian/bedrock chests
    public boolean dungeonGamblingCroesus = false;
    public float dungeonGamblingTime = 5.0f;        // seconds
    public boolean totemAnimation = false;          // totem particles/sound/item pop on mask/phoenix/remnant procs
    public boolean easySuperboom = false;           // pick-block uses superboom tnt from the hotbar
    public int easySuperboomDelay = 100;            // ms fixed
    public int easySuperboomRandom = 80;            // ms random extra
    // w03-07-dun
    public boolean scoreHud = false;                   // own score HUD with rank + breakdown
    public String scoreHudMode = "Full";               // Full / Minimized / Score Only
    public boolean scoreHudHideInBoss = false;
    public int scoreHudX = 10;
    public int scoreHudY = 120;
    public float scoreHudScale = 1.0f;
    public boolean extraStatsCompact = false;          // hide the end-of-run block, print a short summary
    public String extraStatsTeam = "Both";             // Off / Personal / Team / Both
    public boolean extraStatsTeammates = false;
    public boolean dungeonInfoHud = false;             // teammates (ult, revives), blessings, downed
    public boolean dungeonInfoPlayers = true;
    public boolean dungeonInfoBlessings = true;
    public boolean dungeonInfoBlessingDigits = false;
    public boolean dungeonInfoDowned = true;
    public boolean dungeonInfoHideInBoss = false;
    public int dungeonInfoHudX = 10;
    public int dungeonInfoHudY = 200;
    public float dungeonInfoHudScale = 1.0f;
    public boolean keyPickupWho = false;               // key title names who picked it up
    public boolean secretChimeBat = false;             // chime on secret bat death
    public boolean secretChimeItem = false;            // chime on secret item pickup
    public boolean secretMuteChest = false;            // mute Hypixel's chest secret sound
    public boolean secretMuteLever = false;            // mute Hypixel's lever secret sound
    public boolean pbOnJoinExtra = false;              // class level + gear hover in the join line
    public boolean dungeonSession = false;             // session runs / xp per hour HUD
    public boolean dungeonSessionOverview = false;     // chat line after each run
    public boolean dungeonSessionFrags = true;         // fragment counter line
    public int dungeonSessionX = 10;
    public int dungeonSessionY = 260;
    public float dungeonSessionScale = 1.0f;
    // w03-05-dun
    public boolean secretTrackerLocator = false;       // SECRET_TRACKER particles -> SECRET label
    public boolean serverLagTimer = false;             // lag line at run end
    public boolean dungeonLevelSlotText = false;       // cata/class/sb level as slot text
    public boolean swapSound = false;                  // sound on pickaxe swap-stonk click
    public boolean swapSoundOnlyBlock = true;
    public String swapSoundType = "NOTE_PLING";
    public float swapSoundVolume = 1.0f;
    public float swapSoundPitch = 1.0f;
    public boolean treasureTalismanAlert = false;      // title + chat + sound on a dropped treasure talisman
    public boolean scoreAssumeNoSpirit = false;        // first death -2 unless the spirit check says otherwise
    public boolean autoGFSLeaps = false;               // spirit leaps to 16, only when 4+ missing
    public int autoGFSMinMissing = 1;                  // other items: refill only when this many are missing
    public boolean autoGFSSkipDead = false;            // no refill while dead
    public boolean autoGFSKuudra = false;              // Dungeon Only also allows Kuudra
    public boolean autoGFSTwilight = false;            // M7 twilight arrow poison per class
    public int autoGFSTwilightAmount = 8;
    public int autoGFSPearlKey = -1;                   // pearls up to the next multiple of 16
    // w03-01-boss
    public boolean fuckDiorite = false;                // storm pillars as glass in F7 P2
    public int fuckDioriteMode = 0;                    // 0 pillar color, 1 fixed color, 2 random
    public int fuckDioriteColor = 0;                   // 0 clear glass, 1..16 stained colors
    public boolean arrowAlignBlockWrong = false;       // swallow clicks on frames that are already right
    public int arrowAlignOverrideKey = -1;             // extra override key besides sneak
    public boolean arrowAlignInvertOverride = false;   // block only while override is held
    public boolean hidePlayersOnlyDevs = false;        // hide only at SS / 4th device in P3
    public boolean lividGlow = true;
    public boolean lividBox = true;
    public boolean lividUseLividColor = false;
    public String lividColor = "00FF00";
    public float lividLineWidth = 3.0f;
    public float lividTracerWidth = 2.0f;
    public boolean lividLabel = false;
    public boolean dragonStackAimer = false;           // M7 P5 arrow stack aim point
    public boolean dragonStackAimerPing = true;
    public boolean dragonStackAimerHud = true;
    public boolean dragonStackAimerAnyClass = false;
    public String dragonStackAimerColor = "00FFFF";
    public String dragonStackAimerTracerColor = "00FFFF";
    public int dragonStackAimerX = 10;
    public int dragonStackAimerY = 160;
    public float dragonStackAimerScale = 1.5f;
    // w03-02-dun
    public List<String> waypointPacksActive = new ArrayList<>(List.of("Default"));
    public String waypointEditPack = "Default";
    public boolean waypointEditMode = false;           // right-click places/removes dungeon waypoints
    public boolean waypointEditTitles = false;         // sneak + right-click asks for a title
    public int waypointEditModeKey = -1;
    public boolean croesusChestCounter = false;
    public boolean croesusChestWarn = true;
    public int croesusChestWarnAt = 55;
    public boolean croesusChestWarnSound = true;
    @Global
    public int croesusChestCount = -1;               // last seen, -1 = unknown
    public int croesusChestCounterX = 10;
    public int croesusChestCounterY = 280;
    public float croesusChestCounterScale = 1.0f;
    public boolean batBonusScore = false;
    public String batKilledChannel = "Nobody";
    public boolean deathCounter = false;
    public boolean deathCounterChat = true;
    public int deathCounterX = 10;
    public int deathCounterY = 300;
    public float deathCounterScale = 1.0f;
    public boolean visitedTracking = false;
    public boolean rareGroundDrops = false;
    public boolean rareGroundDropsSmAllFloors = false;
    public float rareGroundDropsTime = 3.0f;

    // w03-04-dun
    public boolean lootLogger = false;                 // log bought dungeon chests, /tmap loot
    public boolean bossbarHideUseless = false;         // hide ad/event bossbars, watcher stays in clear
    public boolean dungeonTabClassColor = false;       // tab: colored class level in dungeons
    public boolean mageBeam = false;                   // mage beam as one line
    public String mageBeamColor = "AA0000";
    public int mageBeamDuration = 40;
    public float mageBeamWidth = 8f;
    public boolean mageBeamDepth = true;
    public boolean mageBeamHideParticles = true;
    public int mageBeamMinPoints = 3;
    public boolean teamStats = false;                  // per-player stats at run end
    public boolean teamStatsSecrets = true;
    public boolean teamStatsRooms = true;
    public boolean shadowAssassinAlert = false;        // title on SA teleport
    // w02-06-term
    public boolean terminalMelodyKeys = false;         // keys 1-4 click the melody row buttons
    public int terminalDropKey = -1;                   // own key/mouse button that clicks the hovered terminal slot
    public boolean terminalSplits = false;             // section + P3 time on terminal lines, summary at core
    public boolean terminalSplitsServerTicks = false;  // server ticks instead of real time
    public int melodyProgressFormat = 0;               // 0 = 25%, 1 = 1/4
    public String melodyProgressText = "Melody";       // prefix of the progress message
    public boolean terminalHideClutter = false;        // hide chat, boss bar, labels, carried item while a terminal is open
    public boolean terminalStopPanes = false;
    public boolean terminalStopRubix = false;
    public boolean terminalStopNumbers = false;
    public boolean terminalStopStartsWith = false;
    public boolean terminalStopSelect = false;
    public int terminalSlotStyle = 0;                  // 0 rounded, 1 rect, 2 bordered, 3 button

    // w02-03-puz
    public boolean puzzleHud = false;
    public boolean puzzleHudPlayers = true;
    public boolean puzzleHudPreRunOnly = false;
    public boolean puzzleHudCounts = false;
    public boolean puzzleHudCountsOnly = false;
    public int puzzleHudX = 10;
    public int puzzleHudY = 160;
    public float puzzleHudScale = 1.0f;
    public boolean quizTimer = false;
    public int quizTimerHudX = 10;
    public int quizTimerHudY = 230;
    public float quizTimerHudScale = 1.0f;
    public boolean quizSplits = false;
    public String quizSplitsFormat = "Real Time";
    public int quizSplitsHudX = 10;
    public int quizSplitsHudY = 245;
    public float quizSplitsHudScale = 1.0f;
    public boolean autoGFSDraftAnnounce = false;
    public boolean waterBoardPreview = false;
    public boolean waterBoardPreviewLevers = true;
    public boolean blazeSpots = false;
    public String blazeSpotOutline = "FF00FF00";
    public String blazeSpotFill = "5000FF00";
    public String blazeEtherOutline = "FFFFFF00";
    public String blazeEtherFill = "50FFFF00";

    // w02-05-term
    public boolean terminalTitles = false;
    public int terminalTitleMode = 0;               // 0 replace, 1 hide others, 2 hide all
    public int terminalTitleFormat = 0;             // index into TerminalTitles.FORMATS
    public boolean terminalTitleSubtitle = true;
    public boolean terminalTitleSelfOnly = false;
    public boolean terminalTitlePhaseDone = false;
    public boolean terminalTitleTerminal = true;
    public boolean terminalTitleLever = true;
    public boolean terminalTitleDevice = true;
    public boolean termInfoHud = false;
    public boolean termInfoCompact = false;
    public int termInfoHudX = 10;
    public int termInfoHudY = 120;
    public float termInfoHudScale = 1.0f;
    public boolean terminalBreakdown = false;
    public boolean terminalSounds = false;
    public String terminalClickSound = "TM_CLICK";
    public float terminalClickVolume = 1.0f;
    public float terminalClickPitch = 1.0f;
    public boolean terminalCompleteSound = false;
    public String terminalCompleteSoundId = "EXPERIENCE_ORB";
    public float terminalCompleteVolume = 1.0f;
    public float terminalCompletePitch = 1.0f;
    public boolean terminalCancelLastClick = false;
    public boolean terminalGateSound = false;
    public boolean melodyDisplay = false;
    public float melodyDisplayDuration = 2.5f;
    public boolean melodyDisplaySound = false;
    public int melodyDisplayX = 200;
    public int melodyDisplayY = 90;
    public float melodyDisplayScale = 1.0f;
    // w02-08-boss
    public boolean guardianRespawnTimer = false;    // F3 dead-guardian respawn timers in the timer HUD
    public boolean giantHpDisplay = false;          // F6 giant nametags as HUD list
    public boolean giantHpFeetLabel = false;        // same, as label 10 blocks down at the feet
    public int giantHpX = 10;
    public int giantHpY = 120;
    public float giantHpScale = 1.0f;
    public boolean arrowsDeviceSolver = false;      // F7 P3 sharp shooter: hit/target blocks
    public String arrowsDeviceHitColor = "8055FFFF";
    public String arrowsDeviceTargetColor = "80FF55FF";
    public boolean arrowsDeviceAim = false;         // up to 3 aim points covering open blocks
    public boolean arrowsDeviceAlert = false;       // title + chat on device done
    public boolean lightsDeviceHighlight = false;   // F7 P3 lights device: unpowered levers
    public String lightsDeviceColor = "BFFF5555";
    public boolean p3GateHighlight = false;         // F7 P3 box around the current section's gate
    public com.teslamaps.render.EspStyle p3GateEsp = new com.teslamaps.render.EspStyle("40FFAA00", com.teslamaps.render.EspStyle.BOTH);
    public boolean maxorCrystals = false;           // F7 P1 energy crystal helpers (master)
    public boolean maxorCrystalSpawnTimer = true;   // crystal respawn countdown (34t) in the timer HUD
    public boolean maxorCrystalPlaceTimer = true;   // chat: pickup -> place time with PB
    public boolean maxorCrystalAlert = true;        // "⚠ Crystal ⚠" while holding an unplaced crystal in P1
    public double maxorCrystalPb = 0;
    public boolean dragonHitCounter = false;        // M7 P5 arrow hits on the priority dragon
    public boolean simonSaysTime = false;           // SS completion time in chat
    public double simonSaysPb = 0;
    // w02-09-boss
    public boolean sadanTerracottaHide = false;
    public boolean sadanTerracottaSounds = true;
    public boolean sadanTerracottaSplashes = false;
    public boolean sadanInterest = false;
    public boolean m4HlSpiritBow = false;
    public String m4ColorSpiritBow = "FF00FF";
    public boolean scarfSpawnTimer = false;
    public boolean springBootsReady = false;
    public boolean hideWitherKing = false;
    public boolean kingRelics = false;
    public boolean kingRelicBeacon = true;
    public boolean kingRelicPlaceTime = true;
    public boolean kingRelicSpawnMsg = true;
    public int relicSpawnTicks = 45;
    @Global
    public java.util.Map<String, Float> kingRelicPbs = new java.util.HashMap<>();
    public boolean lastBreathHud = false;
    public int lastBreathHudX = 200;
    public int lastBreathHudY = 150;
    public float lastBreathHudScale = 1.0f;
    public boolean lastBreathPerDragon = false;
    public int lastBreathRed = 10;
    public int lastBreathOrange = 10;
    public int lastBreathGreen = 10;
    public int lastBreathBlue = 10;
    public int lastBreathPurple = 10;

    // w02-00-map
    public boolean mapColorOpenDoors = false;
    public String colorDoorOpenWither = "71421B";
    public boolean mapScanChatInfo = false;
    public boolean mapBlazeDoneFromChat = false;
    public boolean mapInterpolatePlayers = false;
    public boolean mapBgImage = false;
    public String mapBgImageName = "";
    public float mapBgImageAlpha = 1.0f;
    public float mapBgImageInset = 0.0f;
    public boolean roomClearedAlert = false;
    public boolean roomClearedOnlyBloodRush = false;
    public boolean roomClearedNoBlood = false;
    public boolean roomSecretsDoneAlert = false;
    public float roomAlertTime = 1.0f;
    public boolean secretHud = false;
    public String secretHudPrefix = "Secrets:";
    public int secretHudX = 10;
    public int secretHudY = 120;
    public float secretHudScale = 1.0f;
    // w02-04-puz
    public boolean tttShowAllBest = false;
    public boolean tttGamble = false;
    public boolean tttPrediction = false;
    public boolean tttDoneMessage = false;
    public String tttStyle = "Filled + Outline";
    public String colorTttBest = "8000FF00";
    public String colorTttGamble = "80FFAA00";
    public String colorTttPrediction = "50FFA500";
    public String creeperBeamsSound = "NOTE_IRON_XYLO";
    public boolean creeperBeamsMuteHurt = false;
    public boolean creeperBeamsMuteExplosion = false;
    public String creeperBeamsStyle = "ESP Default";
    public float creeperBeamsAlpha = 1.0f;
    public String weirdosStyle = "Filled + Outline";
    public String colorWeirdosCorrect = "8000FF00";
    public String colorWeirdosWrong = "80FF0000";
    public boolean weirdosSolvedSound = false;
    public String weirdosSound = "NOTE_PLING";
    public String quizStyle = "ESP Default";
    public boolean quizDepthCheck = false;
    public String colorQuizChat = "FF5555";
    public String draftMode = "Auto";
    public boolean draftPartyFails = false;
    public boolean quizBlockWrongClicks = false;
    public boolean tttBlockWrongClicks = false;
    public boolean weirdosBlockWrongClicks = false;

    // w02-02-map
    public float mapRoomSize = 24f;                  // px of a 28px cell
    public float mapDoorWidth = 8f;
    public boolean mapDoorsRoomColor = false;
    public String mapTextShadow = "Drop";            // Drop / None / Outline
    public String mapNameAlign = "Center L";
    public String mapCheckmarkAlign = "Default";
    public String mapBorderStyle = "Default";        // Default / Solid / Chroma / None
    public String colorMapBorder = "FFFFFFFF";
    public float mapBorderWidth = 1.0f;
    public float mapBorderChromaSpeed = 1.0f;
    public boolean mapSelfVanillaArrow = false;
    public float mapPlayerNameScale = 1.0f;
    public boolean mapPlayerClassName = false;
    public String mapPlayerNameColor = "Class";      // Class / White
    public boolean mapFallbackTyped = false;
    public boolean starMobRadar = false;
    public float starMobRadarSize = 1.0f;
    public boolean starMobRadarHeads = true;
    public boolean starMobRadarBorder = true;
    public String colorStarMobRadar = "F57738";
    public String colorStarMobRadarBorder = "000000";
    public boolean starMobRadarMinibossColors = true;

    // w02-07-boss
    public boolean lividSkinDetection = false;
    @Global
    public java.util.Map<String, String> lividSkins = new java.util.HashMap<>(); // skin url -> Livid enum name
    public boolean terminalWaypoints = false;
    public boolean terminalWaypointsTerms = true;
    public boolean terminalWaypointsDevices = true;
    public boolean terminalWaypointsLevers = true;
    public boolean terminalWaypointsText = true;
    public boolean terminalWaypointsBeacon = true;
    public boolean terminalWaypointsBox = true;
    public boolean terminalWaypointsHideNames = false;
    public boolean terminalWaypointsThroughWalls = true;
    public String terminalWaypointsColor = "66FFFF55";
    public boolean fireFreezeTimer = false;
    public boolean fireFreezeSound = true;
    public int fireFreezeX = 10;
    public int fireFreezeY = 140;
    public float fireFreezeScale = 1.0f;
    public boolean guardianHealth = false;
    public boolean dimensionalSlashAlert = false;
    public boolean f7Titles = false;
    public boolean f7CrystalTitles = true;
    public boolean f7BossTitles = true;
    public boolean f7BossSounds = false;
    public boolean f7BossSoundsAboveCap = false;
    public boolean f7BossSoundsCap = false;
    public boolean dragonRecolor = false;
    public boolean dragonHideHurt = false;
    public boolean dragonPerchWarning = false;
    public boolean dragonHideBossbars = false;
    // w02-01-map
    public boolean mapWhiteCheckBlood = false;
    public boolean rushSplits = false;
    public ClassFilter rushSplitsClasses = new ClassFilter(true, true, false, true, false);
    public int rushSplitsX = 10, rushSplitsY = 120;
    public float rushSplitsScale = 1.0f;
    public boolean leapHoverLink = false;
    public boolean leapCloseOnDeath = false;
    public boolean mapRareRoomColor = false;
    public String colorRare = "FFCB59";
    public boolean secretWaypointLabels = false;
    public boolean secretWaypointDistance = false;
    public String secretWaypointStyle = "Outline";
    public boolean secretWaypointAdaptiveBox = false;
    public boolean secretWaypointRoutes = false;
    public List<String> secretRouteCategories = new ArrayList<>(List.of("entrance", "superboom", "stonk", "aotv", "pearl", "lever"));
    public java.util.Map<String, String> secretRouteColors = new java.util.HashMap<>();
    public boolean secretSoundBatKill = false;
    public boolean secretSoundRealOnly = false;
    public boolean secretSoundLeverSeparate = false;
    public String secretSoundLeverType = "NOTE_PLING";
    public float secretSoundLeverVolume = 1.0f;
    public float secretSoundLeverPitch = 1.5f;
    public boolean secretSoundLockedCustom = false;
    public String secretSoundLockedType = "ANVIL_LAND";
    public float secretSoundLockedVolume = 1.0f;
    public float secretSoundLockedPitch = 1.0f;
    public boolean legitPeekToggle = false;
    public int mapPeekKey = -1;
    public boolean mapPeekToggle = false;

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
    /**
     * Old pest ESP coloured every invisible hitbox in the garden the same green; Mob Visuals
     * reads the skull and tells the 15 species apart. Anyone who had the old one on gets the
     * new one on the same mobs. Clearing the flags is what makes this run exactly once - a
     * later "all Garden pests off" must not be undone on the next launch.
     */
    private static void migratePestEsp(TeslaMapsConfig c) {
        if (!c.pestESP && !c.pestTracers) return;

        if (c.pestESP) {
            for (com.teslamaps.features.mobvisuals.Mob mob
                    : com.teslamaps.features.mobvisuals.Mob.of(
                            com.teslamaps.features.mobvisuals.MobArea.GARDEN)) {
                c.mobVisualTargets.add(mob.name());
            }
            c.mobVisuals = true;
            c.mobVisualGlow = true;
            c.mobVisualTracer = c.pestTracers;
        }

        c.pestESP = false;
        c.pestTracers = false;
    }

    static void normalize(TeslaMapsConfig c) {
        if (c.lotusPityData == null) c.lotusPityData = new java.util.HashMap<>();
        c.lotusPityData.values().removeIf(v -> v == null || v.length < 4);
        if (c.slayerBossesSinceRng == null) c.slayerBossesSinceRng = new java.util.HashMap<>();
        if (c.slayerLastRngMs == null) c.slayerLastRngMs = new java.util.HashMap<>();
        if (c.slayerNametagMode == null) c.slayerNametagMode = "Own + Carries";
        if (c.chScanStructures == null) c.chScanStructures = new java.util.HashMap<>();
        c.chScanStructures.values().removeIf(java.util.Objects::isNull);
        if (c.hotmSkyMallPerk == null) c.hotmSkyMallPerk = "";
        if (c.slayerCarries == null) c.slayerCarries = new java.util.ArrayList<>();
        if (c.slayerTierLockLast == null) c.slayerTierLockLast = "";
        if (c.voidgloomHideLaserFor == null) c.voidgloomHideLaserFor = "All bosses";
        if (c.zealotSpawnColor == null) c.zealotSpawnColor = "7F00FFFF";
        c.slayerCarries.removeIf(x -> x == null || x.customer == null || x.type == null);
        if (c.slayerCarryPrices == null) c.slayerCarryPrices = new java.util.HashMap<>();
        if (c.forgeSlotData == null) c.forgeSlotData = new java.util.ArrayList<>();
        if (c.mineshaftSinceType == null) c.mineshaftSinceType = new java.util.HashMap<>();
        if (c.mineshaftLastType == null) c.mineshaftLastType = new java.util.HashMap<>();
        if (c.chCrystalStates == null) c.chCrystalStates = new java.util.HashMap<>();
        if (c.chPartsDelivered == null) c.chPartsDelivered = new java.util.ArrayList<>();
        if (c.miningChestSummary == null) c.miningChestSummary = "Off";
        if (c.visitorIgnored == null) c.visitorIgnored = new java.util.ArrayList<>();
        if (c.kingsTalkedTo == null) c.kingsTalkedTo = new java.util.ArrayList<>();
        if (c.hotmPowderGoals == null) c.hotmPowderGoals = new java.util.HashMap<>();
        c.hotmPowderGoals.values().removeIf(java.util.Objects::isNull);
        if (c.speedPresetValues == null) c.speedPresetValues = defaultSpeedPresets();
        c.speedPresetValues.values().removeIf(java.util.Objects::isNull);
        if (c.stereoActiveVinyl == null) c.stereoActiveVinyl = "";
        if (c.visitorStatRarities == null) c.visitorStatRarities = new java.util.HashMap<>();
        if (c.visitorStatRewards == null) c.visitorStatRewards = new java.util.HashMap<>();
        c.visitorStatRarities.values().removeIf(java.util.Objects::isNull);
        c.visitorStatRewards.values().removeIf(java.util.Objects::isNull);
        if (c.cropLatestBps == null) c.cropLatestBps = new java.util.HashMap<>();
        if (c.cropLatestFortune == null) c.cropLatestFortune = new java.util.HashMap<>();
        if (c.cropMeasuredPerBlock == null) c.cropMeasuredPerBlock = new java.util.HashMap<>();
        c.cropLatestBps.values().removeIf(java.util.Objects::isNull);
        c.cropLatestFortune.values().removeIf(java.util.Objects::isNull);
        c.cropMeasuredPerBlock.values().removeIf(java.util.Objects::isNull);
        if (c.eliteLbGamemode == null) c.eliteLbGamemode = "All";
        if (c.farmToolColor == null) c.farmToolColor = "66B22222";
        if (c.contestCropWarnFor == null) c.contestCropWarnFor = new java.util.ArrayList<>();
        if (c.pestGlowVinylId == null) c.pestGlowVinylId = "";
        if (c.gardenOptimalSpeeds == null) c.gardenOptimalSpeeds = new java.util.HashMap<>();
        if (c.hoeOverflowLevels == null) c.hoeOverflowLevels = new java.util.HashMap<>();
        c.gardenOptimalSpeeds.values().removeIf(java.util.Objects::isNull);
        c.hoeOverflowLevels.values().removeIf(java.util.Objects::isNull);
        if (c.jacobContestBracket == null) c.jacobContestBracket = "GOLD";
        if (c.mousematAngleValues == null) c.mousematAngleValues = new java.util.HashMap<>();
        if (c.pbItemsPerFortune == null) c.pbItemsPerFortune = new java.util.HashMap<>();
        if (c.mantidShowWhen == null) c.mantidShowWhen = "Mantid Armor";
        if (c.pestSpawnSoundMode == null) c.pestSpawnSoundMode = "Default";
        if (c.featureConditionRules == null) c.featureConditionRules = new java.util.LinkedHashMap<>();
        c.featureConditionRules.values().removeIf(java.util.Objects::isNull);
        for (FeatureRule r : c.featureConditionRules.values()) {
            if (r.combos == null) r.combos = new java.util.ArrayList<>();
            r.combos.removeIf(java.util.Objects::isNull);
            for (java.util.List<String> l : r.combos) l.removeIf(java.util.Objects::isNull);
        }
        if (c.customFontFile == null) c.customFontFile = "";
        if (c.radialMenusOn == null) c.radialMenusOn = new java.util.ArrayList<>();
        if (c.hudLayouts == null) c.hudLayouts = new java.util.HashMap<>();
        c.hudLayouts.values().removeIf(java.util.Objects::isNull);
        if (c.hudTextShadow == null) c.hudTextShadow = "Shadow";
        if (c.hudPanelTextShadow == null) c.hudPanelTextShadow = new java.util.HashMap<>();
        if (c.hudPanelTextAlign == null) c.hudPanelTextAlign = new java.util.HashMap<>();
        if (c.hudPanelLineBackdrop == null) c.hudPanelLineBackdrop = new java.util.HashMap<>();
        if (c.mutePartySpamPrefixes == null) c.mutePartySpamPrefixes = new java.util.ArrayList<>(com.teslamaps.features.MutePartySpam.DEFAULTS);
        c.mutePartySpamPrefixes.removeIf(s -> s == null || s.isEmpty());
        if (c.safariHotspotPostChannel == null) c.safariHotspotPostChannel = "Party";
        if (c.hunterTradesChannel == null) c.hunterTradesChannel = "Party";
        if (c.knownToggles == null) c.knownToggles = new java.util.ArrayList<>();
        if (c.hudCustomBlurKernel == null) c.hudCustomBlurKernel = "Gaussian";
        if (c.enchantedClockDisabled == null) c.enchantedClockDisabled = new java.util.ArrayList<>();
        if (c.posMessageList == null) c.posMessageList = new java.util.ArrayList<>();
        c.posMessageList.removeIf(java.util.Objects::isNull);
        if (c.reversePtMessage == null) c.reversePtMessage = "";
        if (c.sackNotifyItems == null) c.sackNotifyItems = new java.util.ArrayList<>();
        if (c.chatMutedPlayers == null) c.chatMutedPlayers = new java.util.ArrayList<>();
        if (c.mobIconColors == null) c.mobIconColors = new java.util.HashMap<>();
        if (c.imageShareLinks == null) c.imageShareLinks = new java.util.HashMap<>();
        if (c.tabStatsHudStats == null) c.tabStatsHudStats = "Ferocity";
        if (c.chatSoundResponsesOff == null) c.chatSoundResponsesOff = new java.util.ArrayList<>();
        if (c.rareDropAnnounce == null) c.rareDropAnnounce = "Off";
        if (c.trackerHidden == null) c.trackerHidden = new java.util.HashMap<>();
        if (c.hudLineOrder == null) c.hudLineOrder = new java.util.HashMap<>();
        c.hudLineOrder.values().removeIf(java.util.Objects::isNull);
        c.trackerHidden.values().removeIf(java.util.Objects::isNull);
        if (c.expDryStreakData == null) c.expDryStreakData = new java.util.HashMap<>();
        c.expDryStreakData.values().removeIf(v -> v == null || v.length != 2);
        if (c.maxwellData == null) c.maxwellData = new java.util.HashMap<>();
        c.maxwellData.values().removeIf(java.util.Objects::isNull);
        if (c.bzTrackerOrders == null) c.bzTrackerOrders = new java.util.HashMap<>();
        c.bzTrackerOrders.values().removeIf(java.util.Objects::isNull);
        c.bzTrackerOrders.values().forEach(l -> l.removeIf(o -> o == null || o.item == null));
        if (c.colorAuctionSold == null) c.colorAuctionSold = "#55FF55";
        if (c.colorAuctionExpired == null) c.colorAuctionExpired = "#FF5555";
        if (c.colorAuctionOverpriced == null) c.colorAuctionOverpriced = "#FFAA00";
        if (c.visualWordList == null) c.visualWordList = new java.util.ArrayList<>();
        c.visualWordList.removeIf(w -> w == null || w.from == null || w.from.isEmpty());
        for (var w : c.visualWordList) if (w.to == null) w.to = "";
        if (c.customTrackerDefs == null) c.customTrackerDefs = new java.util.ArrayList<>();
        c.customTrackerDefs.removeIf(java.util.Objects::isNull);
        if (c.beaconPowerExpiry == null) c.beaconPowerExpiry = new java.util.HashMap<>();
        if (c.beaconPowerStats == null) c.beaconPowerStats = new java.util.HashMap<>();
        if (c.sackHudItems == null) c.sackHudItems = new java.util.ArrayList<>();
        c.sackHudItems.removeIf(java.util.Objects::isNull);
        if (c.bitsAvailableByProfile == null) c.bitsAvailableByProfile = new java.util.HashMap<>();
        if (c.screenFlashColor == null) c.screenFlashColor = "66FF4D4D";
        if (c.customTextBoxText == null) c.customTextBoxText = "";
        if (c.invButtons == null || c.invButtons.isEmpty()) {
            c.invButtons = com.teslamaps.features.invbuttons.ButtonPresets.defaults();
        }
        if (c.wardrobeFavorites == null) c.wardrobeFavorites = new java.util.HashSet<>();
        if (c.reminderList == null) c.reminderList = new java.util.ArrayList<>();
        if (c.containerTriggerList == null) c.containerTriggerList = new java.util.ArrayList<>();
        if (c.customTodoList == null) c.customTodoList = new java.util.ArrayList<>();
        c.customTodoList.removeIf(java.util.Objects::isNull);
        for (CustomTodo t : c.customTodoList) {
            if (t.readyAt == null) t.readyAt = new java.util.HashMap<>();
            if (t.label == null) t.label = "";
            if (t.trigger == null) t.trigger = "";
            if (t.matcher == null) t.matcher = "Contains";
            if (t.time == null) t.time = "1h";
        }
        if (c.cakeTrackerShow == null) c.cakeTrackerShow = "Missing";
        if (c.priceLineAvgBin == null) c.priceLineAvgBin = "Off";
        c.containerTriggerList.removeIf(java.util.Objects::isNull);
        if (c.islandAlertMode == null) c.islandAlertMode = "Title + Sound";
        if (c.harpKeys == null || c.harpKeys.length != 7) c.harpKeys = new int[]{49, 50, 51, 52, 53, 54, 55};
        if (c.damageAlertSoundType == null) c.damageAlertSoundType = "NOTE_PLING";
        if (c.discordRpcAppId == null) c.discordRpcAppId = "";
        if (c.colorWardrobeEquipped == null) c.colorWardrobeEquipped = "55FF55";
        if (c.colorWardrobeFavorite == null) c.colorWardrobeFavorite = "FFAA00";
        if (c.waypointPacksActive == null) c.waypointPacksActive = new ArrayList<>(List.of("Default"));
        if (c.waypointEditPack == null) c.waypointEditPack = "Default";
        if (c.batKilledChannel == null) c.batKilledChannel = "Nobody";
        if (c.opheliaPotLock == null) c.opheliaPotLock = "Off";
        if (c.enchantRenames == null) c.enchantRenames = new ArrayList<>();
        if (c.hoppityRabbits == null) c.hoppityRabbits = new java.util.HashMap<>();
        if (c.hoppityLocationRabbits == null) c.hoppityLocationRabbits = new java.util.HashMap<>();
        if (c.hoppityRabbitRarity == null) c.hoppityRabbitRarity = new java.util.HashMap<>();
        if (c.backpackPreviewStyle == null) c.backpackPreviewStyle = "Compact";
        if (c.craftHelperTarget == null) c.craftHelperTarget = "";
        if (c.craftHelperShardTree == null) c.craftHelperShardTree = "";
        if (c.fameRank == null) c.fameRank = "";
        if (c.chatCommandsWhitelist == null) c.chatCommandsWhitelist = new ArrayList<>();
        if (c.dragonHitboxColor == null) c.dragonHitboxColor = "00FFFF";
        if (c.lividAnnounceText == null) c.lividAnnounceText = "Livid is [color]!";
        if (c.chatCommandsDisabled == null) c.chatCommandsDisabled = new ArrayList<>();
        if (c.chatCommandsExtraEnabled == null) c.chatCommandsExtraEnabled = new java.util.LinkedHashSet<>();
        if (c.markedPlayerNames == null) c.markedPlayerNames = new java.util.LinkedHashSet<>();
        if (c.markedPlayersChatColor == null) c.markedPlayersChatColor = "FFFF55";
        if (c.savedButtonPresets == null) c.savedButtonPresets = new java.util.LinkedHashMap<>();
        if (c.mobVisualTargets == null) c.mobVisualTargets = new java.util.HashSet<>();
        if (c.hotmPlanBase == null) c.hotmPlanBase = new java.util.HashMap<>();
        if (c.hotfPlanBase == null) c.hotfPlanBase = new java.util.HashMap<>();
        if (c.hotmPlanTarget == null) c.hotmPlanTarget = new java.util.HashMap<>();
        if (c.hotfPlanTarget == null) c.hotfPlanTarget = new java.util.HashMap<>();
        if (c.superprotectronEsp == null) c.superprotectronEsp = new com.teslamaps.render.EspStyle("FFFF55FF", com.teslamaps.render.EspStyle.GLOW);
        if (c.blockEspBlocks == null) c.blockEspBlocks = new java.util.LinkedHashMap<>();
        if (c.chestEspWhere == null) c.chestEspWhere = "Everywhere";
        if (c.seaCreatureAlert == null) c.seaCreatureAlert = new java.util.HashMap<>();
        if (c.seaCreatureAnnounce == null) c.seaCreatureAnnounce = new java.util.HashMap<>();
        if (c.npcLockedWarps == null) c.npcLockedWarps = new java.util.HashMap<>();
        if (c.kuudraKeyCoins == null) c.kuudraKeyCoins = new java.util.HashMap<>();
        if (c.kuudraFaction == null) c.kuudraFaction = "";
        migratePestEsp(c);
        if (c.customESPMobs == null) c.customESPMobs = new ArrayList<>();
        if (c.islandAlertRules == null) c.islandAlertRules = new ArrayList<>();
        if (c.enchantColorRules == null) c.enchantColorRules = new ArrayList<>();
        if (c.protectedItems == null) c.protectedItems = new java.util.HashSet<>();
        if (c.preventPlacingBlacklist == null) c.preventPlacingBlacklist = new java.util.HashSet<>();
        if (c.itemPickupAlertNames == null) c.itemPickupAlertNames = new ArrayList<>();
        if (c.reforgeStopList == null) c.reforgeStopList = new ArrayList<>();
        if (c.customESPTracers == null) c.customESPTracers = new ArrayList<>();
        if (c.customESPColors == null) c.customESPColors = new java.util.HashMap<>();
        if (c.colorESPCustom == null || c.colorESPCustom.isBlank()) c.colorESPCustom = "00FFFF";
        if (c.recentPlayers == null) c.recentPlayers = new ArrayList<>();
        if (c.hiddenCoopMembers == null) c.hiddenCoopMembers = new ArrayList<>();
        if (c.hoppityUniqueEggs == null) c.hoppityUniqueEggs = new java.util.HashMap<>();
        if (c.guildNames == null) c.guildNames = new ArrayList<>();
        if (c.friendNames == null) c.friendNames = new ArrayList<>();
        if (c.bestFriendNames == null) c.bestFriendNames = new ArrayList<>();
        if (c.splitPbs == null) c.splitPbs = new java.util.HashMap<>();
        if (c.kuudraSplitPbs == null) c.kuudraSplitPbs = new java.util.HashMap<>();
        if (c.kuudraTentacleColor == null) c.kuudraTentacleColor = "FFFFFF";
        if (c.customSplitDefs == null) c.customSplitDefs = new java.util.HashMap<>();
        if (c.lockedSlots == null) c.lockedSlots = new java.util.ArrayList<>();
        if (c.slotLockData == null) c.slotLockData = new java.util.HashMap<>();
        if (c.slotBindsData == null) c.slotBindsData = new java.util.HashMap<>();
        if (c.slotBindsAreaProfiles == null) c.slotBindsAreaProfiles = new java.util.HashMap<>();
        if (c.storagePageNames == null) c.storagePageNames = new java.util.HashMap<>();
        if (c.slotTextDisabled == null) c.slotTextDisabled = new ArrayList<>();
        if (c.slotTextMode == null) c.slotTextMode = "Hold To Hide";
        if (c.colorSearchHighlight == null) c.colorSearchHighlight = "9055FF55";
        if (c.puzzlePbs == null) c.puzzlePbs = new java.util.HashMap<>();
        if (c.termSimPbs == null) c.termSimPbs = new java.util.HashMap<>();
        if (c.terminalPbs == null) c.terminalPbs = new java.util.HashMap<>();
        if (c.kingRelicPbs == null) c.kingRelicPbs = new java.util.HashMap<>();
        if (c.sectionEnabled == null) c.sectionEnabled = new java.util.HashMap<>();
        Boolean oldGeneral = c.sectionEnabled.remove("General"); // category renamed to Skills
        if (oldGeneral != null) c.sectionEnabled.putIfAbsent("Skills", oldGeneral);
        Boolean oldDragons = c.sectionEnabled.remove("Dragons"); // category merged into F7 / M7
        if (Boolean.FALSE.equals(oldDragons)) c.witherDragons = false;
        if (c.farmPriceMode == null || c.farmPriceMode.isBlank()) c.farmPriceMode = "Instant Sell";
        if (c.mutedSounds == null) c.mutedSounds = new java.util.ArrayList<>();
        if (c.lividSkins == null) c.lividSkins = new java.util.HashMap<>();
        if (c.soundVolumes == null) c.soundVolumes = new java.util.HashMap<>();
        if (c.actionBarHidden == null) c.actionBarHidden = new java.util.ArrayList<>();
        if (c.chatFilterGroupActions == null) c.chatFilterGroupActions = new java.util.HashMap<>();
        if (c.chatUserFilters == null) c.chatUserFilters = new java.util.ArrayList<>();
        c.chatUserFilters.removeIf(f -> f == null || f.text == null);
        if (c.chatUserFilterAction == null) c.chatUserFilterAction = "Hide";
        if (c.chatAutopetMode == null) c.chatAutopetMode = "Show";
        if (c.chatTimestampFormat == null) c.chatTimestampFormat = "24h";
        if (c.chatRedirectTarget == null) c.chatRedirectTarget = "Spam HUD";
        if (c.chatFilterAction == null || c.chatFilterAction.isBlank()) c.chatFilterAction = "Hide";
        if (c.skillTotalXp == null) c.skillTotalXp = new java.util.HashMap<>();
        if (c.allSkillsHidden == null) c.allSkillsHidden = new java.util.ArrayList<>();
        if (c.skillGoalLevels == null) c.skillGoalLevels = new java.util.HashMap<>();
        if (c.skillTrackerPin == null) c.skillTrackerPin = "";
        if (c.flareMob == null || c.flareMob.isBlank()) c.flareMob = "Flare";
        if (c.bestiaryTracked == null) c.bestiaryTracked = new java.util.ArrayList<>();
        if (c.bestiaryKillsToMax == null) c.bestiaryKillsToMax = new java.util.HashMap<>();
        if (c.bestiaryIdleMinutes == null) c.bestiaryIdleMinutes = new java.util.HashMap<>();
        if (c.imageShareBlocked == null) c.imageShareBlocked = new java.util.ArrayList<>();
        if (c.dragonPbs == null) c.dragonPbs = new java.util.HashMap<>();
        if (c.keybinds == null) c.keybinds = defaultKeybinds();
        c.keybinds.removeIf(java.util.Objects::isNull);
        for (Keybind kb : c.keybinds) if (kb.actions == null) kb.actions = "";
        if (c.hudAnchorRefs == null) c.hudAnchorRefs = new java.util.HashMap<>();
        if (c.configColumns == null) c.configColumns = new java.util.LinkedHashMap<>();
        c.configColumns.values().removeIf(v -> v == null || v.length < 4);
        if (c.featureToggleBinds == null) c.featureToggleBinds = new java.util.LinkedHashMap<>();
        c.featureToggleBinds.values().removeIf(java.util.Objects::isNull);
        if (c.hudPanelStyles == null) c.hudPanelStyles = new java.util.HashMap<>();
        if (c.hudPanelChroma == null) c.hudPanelChroma = new java.util.HashMap<>();
        if (c.outsideSbFeatures == null) c.outsideSbFeatures = new java.util.ArrayList<>();
        if (c.hideSillyButtonList == null) c.hideSillyButtonList = new java.util.ArrayList<>();
        if (c.hudChromaAll == null) c.hudChromaAll = "Off";
        if (c.chatPrefixStyle == null) c.chatPrefixStyle = "Long";
        if (c.modifyIndicator == null) c.modifyIndicator = "Off";
        if (c.safariShowWhere == null) c.safariShowWhere = "Auto";
        if (c.shortcuts == null) c.shortcuts = new ArrayList<>();
        if (c.abiphoneFavoriteList == null) c.abiphoneFavoriteList = new ArrayList<>();
        if (!c.kuudraShortcutsAdded) {
            c.kuudraShortcutsAdded = true;
            String[][] kuudra = {{"t1", "joinkuudra basic"}, {"t2", "joinkuudra hot"}, {"t3", "joinkuudra burning"},
                    {"t4", "joinkuudra fiery"}, {"t5", "joinkuudra infernal"}};
            for (String[] k : kuudra) {
                boolean exists = c.shortcuts.stream().anyMatch(s -> s.alias != null && s.alias.trim().equalsIgnoreCase(k[0]));
                if (!exists) c.shortcuts.add(new Shortcut(k[0], k[1]));
            }
        }
        if (c.leapCustomOrder == null) c.leapCustomOrder = new ArrayList<>();
        if (c.charmedVisitorNames == null) c.charmedVisitorNames = new ArrayList<>();
        if (c.feastCrops == null) c.feastCrops = new ArrayList<>();
        if (c.farmProfitExcluded == null) c.farmProfitExcluded = new ArrayList<>();
        if (c.farmProfitItemSource == null) c.farmProfitItemSource = new java.util.HashMap<>();
        if (c.farmKernelItem == null) c.farmKernelItem = "Feast I";
        if (c.feastShareChannel == null) c.feastShareChannel = "Nobody";
        if (c.corpseFinderChannel == null) c.corpseFinderChannel = "Nobody";
        if (c.slayerPbChannel == null) c.slayerPbChannel = "Nobody";
        if (c.safariBannerChannel == null) c.safariBannerChannel = "Nobody";
        if (c.hotspotShareChannel == null) c.hotspotShareChannel = "Party";
        if (c.safariSparklingChannel == null) c.safariSparklingChannel = "Nobody";
        if (c.safariShareTimeChannel == null) c.safariShareTimeChannel = "Nobody";
        if (c.safariMissingChannel == null) c.safariMissingChannel = "Party";
        if (c.pickaxeAbilityReadyText == null) c.pickaxeAbilityReadyText = "Right";
        if (c.farmLanes == null) c.farmLanes = new java.util.LinkedHashMap<>();
        if (c.feastNextSeason == null) c.feastNextSeason = new java.util.LinkedHashMap<>();
        if (c.headTargets == null) c.headTargets = new ArrayList<>();
        if (c.headESPColor == null || c.headESPColor.isBlank()) c.headESPColor = "#FFAA00";
        if (c.headGoldenDragonColor == null || c.headGoldenDragonColor.isBlank()) c.headGoldenDragonColor = "#FFD700";
        if (c.shardsSyphoned == null) c.shardsSyphoned = new java.util.HashMap<>();
        if (c.shardsInBox == null) c.shardsInBox = new java.util.HashMap<>();
        if (c.shardOrders == null) c.shardOrders = new java.util.HashMap<>();
        if (c.rushSplitsClasses == null) c.rushSplitsClasses = new ClassFilter(true, true, false, true, false);
        if (c.secretRouteCategories == null) c.secretRouteCategories = new ArrayList<>();
        if (c.secretRouteColors == null) c.secretRouteColors = new java.util.HashMap<>();
        if (c.secretWaypointStyle == null) c.secretWaypointStyle = "Outline";
        if (c.secretSoundLeverType == null) c.secretSoundLeverType = "NOTE_PLING";
        if (c.secretSoundLockedType == null) c.secretSoundLockedType = "ANVIL_LAND";
        if (c.colorRare == null) c.colorRare = "FFCB59";
        if (c.auctionSearchHistory == null) c.auctionSearchHistory = new ArrayList<>();
        if (c.bazaarSearchHistory == null) c.bazaarSearchHistory = new ArrayList<>();
        if (c.recipeSearchHistory == null) c.recipeSearchHistory = new ArrayList<>();
        if (c.otherSearchHistory == null) c.otherSearchHistory = new ArrayList<>();
        if (c.museumSearchHistory == null) c.museumSearchHistory = new ArrayList<>();
        if (c.supercraftPresetAmounts == null) c.supercraftPresetAmounts = new ArrayList<>(java.util.List.of(4, 8, 16, 32, 64, 128, 256, 512));
        c.supercraftPresetAmounts.removeIf(java.util.Objects::isNull);
        if (c.invButtonConfirm == null) c.invButtonConfirm = "Off";
        if (c.favoritePowers == null) c.favoritePowers = new ArrayList<>();
        if (c.magicalPower == null) c.magicalPower = new java.util.HashMap<>();
        if (c.museumHelperSort == null) c.museumHelperSort = "Lowest Price";
        if (c.museumHelperCategory == null) c.museumHelperCategory = "All";
        if (c.dungeonQualityStyle == null) c.dungeonQualityStyle = "42/50";
        if (c.equipmentDisplayPos == null) c.equipmentDisplayPos = "Left";
        if (c.capeSelected == null) c.capeSelected = "";
        if (c.capeServerUrl == null || c.capeServerUrl.isBlank() || c.capeServerUrl.contains("wynnextras.com")) {
            c.capeServerUrl = CAPE_SERVER_URL;
        }
        if (c.capeServerKey == null || c.capeServerKey.isBlank()) c.capeServerKey = CAPE_SERVER_KEY;
        if (c.greenhouseCode == null) c.greenhouseCode = "";
        if (c.greenhousePresets == null) c.greenhousePresets = new java.util.LinkedHashMap<>();
        if (c.composterUpgrades == null) c.composterUpgrades = new java.util.HashMap<>();
        if (c.composterTimeUnit == null) c.composterTimeUnit = "Hour";
        if (c.composterMatterItem == null) c.composterMatterItem = "";
        if (c.composterFuelItem == null) c.composterFuelItem = "";
        if (c.cropFeverSound == null) c.cropFeverSound = "LEVEL_UP";
        if (c.greenhouseSoil == null) c.greenhouseSoil = new java.util.HashMap<>();
        if (c.greenhousePlants == null) c.greenhousePlants = new java.util.HashMap<>();
        if (c.greenhouseRotations == null) c.greenhouseRotations = new java.util.HashMap<>();
        if (c.greenhouseMutations == null) {
            c.greenhouseMutations = new java.util.ArrayList<>(java.util.List.of("TIMESTALK"));
        }
        if (c.greenhouseFocus == null) c.greenhouseFocus = "";
        if (c.shardSort == null || c.shardSort.isBlank()) c.shardSort = "Price to Max";
        if (c.shardPriceSource == null || c.shardPriceSource.isBlank()) c.shardPriceSource = "Instant Buy";
        // indexed straight through, so a short array out of an older config would throw
        c.wardrobeKeys = fitKeys(c.wardrobeKeys, 9);
        c.loadoutKeys = fitKeys(c.loadoutKeys, 12);
        c.instaLoadoutKeys = fitKeys(c.instaLoadoutKeys, 9);
        if (c.trashFilters == null) c.trashFilters = com.teslamaps.features.TrashHighlighter.defaults();
        c.trashFilters.removeIf(f -> f == null || f.value == null);
        for (TrashFilter f : c.trashFilters) {
            if (f.target == null) f.target = "ID";
            if (f.mode == null) f.mode = "Contains";
        }
        if (c.colorTrash == null) c.colorTrash = "80FF3333";
        if (c.trashHighlightStyle == null) c.trashHighlightStyle = "Fill";
        if (c.middleClickGuiBlacklist == null) c.middleClickGuiBlacklist = new java.util.ArrayList<>();
        c.middleClickGuiBlacklist.removeIf(java.util.Objects::isNull);
        if (c.customArmorTrims == null) c.customArmorTrims = new java.util.HashMap<>();
        if (c.customDyeColors == null) c.customDyeColors = new java.util.HashMap<>();
        if (c.customAnimatedDyes == null) c.customAnimatedDyes = new java.util.HashMap<>();
        if (c.customHelmetTextures == null) c.customHelmetTextures = new java.util.HashMap<>();
        if (c.customAnimatedHelmetTextures == null) c.customAnimatedHelmetTextures = new java.util.HashMap<>();
        if (c.customItemNames == null) c.customItemNames = new java.util.HashMap<>();
        if (c.customGlint == null) c.customGlint = new java.util.HashMap<>();
        if (c.customGlintColor == null) c.customGlintColor = new java.util.HashMap<>();
        if (c.itemNumberTypes == null) c.itemNumberTypes = new java.util.ArrayList<>();
        if (c.skillTrackerPauseSec == null) c.skillTrackerPauseSec = new java.util.HashMap<>();
        if (c.tabWidgetHudWidgets == null) c.tabWidgetHudWidgets = new java.util.ArrayList<>();
        if (c.perfHudTpsMode == null) c.perfHudTpsMode = "Average";
        if (c.perfHudPingMode == null) c.perfHudPingMode = "Instant";
        if (c.cloudHiderIslands == null) c.cloudHiderIslands = new java.util.ArrayList<>(com.teslamaps.features.IslandClouds.DEFAULTS);
        if (c.customEspRuleList == null) c.customEspRuleList = new java.util.ArrayList<>();
        if (c.itemCycleRules == null) c.itemCycleRules = new java.util.ArrayList<>();
        c.itemCycleRules.removeIf(java.util.Objects::isNull);
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

    private static int version;

    /** Bumps on every save; together with the instance it tells when cached derived values are stale. */
    public static int version() { return version; }

    static void setInstance(TeslaMapsConfig c) {
        instance = c;
        version++;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(json);
                boolean migrated = false;
                if (tree != null && tree.isJsonObject()) {
                    com.google.gson.JsonObject fixed = ConfigMigrator.migrate(tree.getAsJsonObject(), "config.json", CONFIG_PATH);
                    migrated = fixed != tree;
                    instance = GSON.fromJson(fixed, TeslaMapsConfig.class);
                } else {
                    instance = GSON.fromJson(json, TeslaMapsConfig.class);
                }
                if (instance == null) {
                    instance = new TeslaMapsConfig();
                }
                normalize(instance);
                ConfigBackups.afterLoad(CONFIG_PATH);
                if (migrated) save();
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

    public static Path path() { return CONFIG_PATH; }

    public static void reloadFromDisk() {
        load();
        version++;
    }

    public static void save() {
        version++;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            ConfigBackups.beforeSave(CONFIG_PATH);
            Files.writeString(CONFIG_PATH, GSON.toJson(instance));
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to save config", e);
        }
    }
}
