package com.teslamaps.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.TeslaMaps;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

// config.json stays the live config; profiles/<name>.json are the saved copies you switch between.
// @Global fields (PBs, keybinds, api key, ...) are carried across a switch instead of loaded.
public class ConfigProfiles {

    public static final String DEFAULT = "Recommended";
    public static final String MINIMAL = "Minimal";
    public static final String ALL_OFF = "All off";

    private static final String[] PRESETS = {DEFAULT, MINIMAL, ALL_OFF};

    // dropped presets: "Full Legit" (Legit Mode is a toggle) and "Teslanator" (same as the default)
    private static final String[] RETIRED = {"Full Legit", "Teslanator"};
    private static final String OLD_DEFAULT = "Default";
    private static boolean migrated = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("profiles");

    public static String active() {
        migrate();
        String p = TeslaMapsConfig.get().activeProfile;
        return p == null || p.isBlank() ? DEFAULT : p;
    }

    /** Renames the old "Default" profile and drops the retired presets, once per launch. */
    private static void migrate() {
        if (migrated) return;
        migrated = true;
        checkPresetFields();
        try {
            Path old = file(OLD_DEFAULT);
            if (Files.exists(old) && !Files.exists(file(DEFAULT))) Files.move(old, file(DEFAULT));
            for (String name : RETIRED) Files.deleteIfExists(file(name));
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to migrate config profiles", e);
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (OLD_DEFAULT.equals(c.activeProfile) || isRetired(c.activeProfile)) {
            c.activeProfile = DEFAULT;
            TeslaMapsConfig.save();
        }
    }

    private static boolean isRetired(String name) {
        for (String r : RETIRED) if (r.equals(name)) return true;
        return false;
    }

    public static List<String> list() {
        migrate();
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> files = Files.list(DIR)) {
                files.filter(f -> f.getFileName().toString().endsWith(".json"))
                        .forEach(f -> {
                            String n = f.getFileName().toString();
                            out.add(n.substring(0, n.length() - 5));
                        });
            }
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to list config profiles", e);
        }
        for (String preset : PRESETS) {
            if (!out.contains(preset)) out.add(preset);
        }
        if (!out.contains(active())) out.add(active());
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    /** Writes the live config to the active profile's file. */
    public static void saveActive() {
        write(active(), TeslaMapsConfig.get());
        TeslaMapsConfig.save();
    }

    /**
     * Saves the current profile, then makes {@code name} live. A profile file that does not exist yet
     * is created from the matching preset, so the presets work on a fresh install.
     */
    public static void switchTo(String name) {
        if (name == null || name.isBlank()) return;
        saveActive();

        TeslaMapsConfig loaded = read(name);
        if (loaded == null) {
            loaded = preset(name);
            write(name, loaded);
        }

        TeslaMapsConfig.normalize(loaded);
        carryGlobals(TeslaMapsConfig.get(), loaded);
        loaded.activeProfile = name;

        TeslaMapsConfig.setInstance(loaded);
        TeslaMapsConfig.save();
        TeslaMaps.LOGGER.info("Switched config profile to {}", name);
    }

    /** Copies the current profile's settings into a new profile and switches to it. */
    public static void copyToNew(String name) {
        if (name == null || name.isBlank() || exists(name)) return;
        write(name, TeslaMapsConfig.get());
        switchTo(name);
    }

    /** Writes a shared config as profile {@code name} and switches to it; own @Global fields are kept. */
    public static boolean importShared(String name, com.google.gson.JsonObject tree) {
        if (name == null || name.isBlank() || isPreset(name)) return false;
        try {
            TeslaMapsConfig c = GSON.fromJson(ConfigMigrator.migrate(tree, "shared " + name, null), TeslaMapsConfig.class);
            if (c == null) return false;
            TeslaMapsConfig.normalize(c);
            if (name.equals(active())) {
                carryGlobals(TeslaMapsConfig.get(), c);
                c.activeProfile = name;
                write(name, c);
                TeslaMapsConfig.setInstance(c);
                TeslaMapsConfig.save();
                return true;
            }
            carryGlobals(TeslaMapsConfig.get(), c);
            write(name, c);
            switchTo(name);
            return true;
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to import shared config {}", name, e);
            return false;
        }
    }

    /** Resets a profile back to its preset values. */
    public static void reset(String name) {
        write(name, preset(name));
        if (name.equals(active())) switchTo(name);
    }

    /** Presets are always listed and can only be reset, never removed. */
    public static boolean isPreset(String name) {
        for (String preset : PRESETS) if (preset.equals(name)) return true;
        return false;
    }

    public static boolean delete(String name) {
        if (name == null || name.equals(active()) || isPreset(name)) return false;
        try {
            return Files.deleteIfExists(file(name));
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to delete config profile {}", name, e);
            return false;
        }
    }

    public static boolean exists(String name) {
        return Files.exists(file(name));
    }

    private static Path file(String name) {
        return DIR.resolve(name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".json");
    }

    private static TeslaMapsConfig read(String name) {
        Path f = file(name);
        if (!Files.exists(f)) return null;
        try {
            com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(Files.readString(f));
            if (tree == null || !tree.isJsonObject()) return null;
            return GSON.fromJson(ConfigMigrator.migrate(tree.getAsJsonObject(), "profile " + name, f), TeslaMapsConfig.class);
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to read config profile {}", name, e);
            return null;
        }
    }

    private static void write(String name, TeslaMapsConfig c) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(file(name), GSON.toJson(c));
        } catch (IOException e) {
            TeslaMaps.LOGGER.error("Failed to write config profile {}", name, e);
        }
    }

    private static void carryGlobals(TeslaMapsConfig from, TeslaMapsConfig to) {
        for (Field f : TeslaMapsConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (!f.isAnnotationPresent(Global.class)) continue;
            try {
                f.set(to, f.get(from));
            } catch (ReflectiveOperationException e) {
                TeslaMaps.LOGGER.error("Failed to carry over field {}", f.getName(), e);
            }
        }
    }

    private static TeslaMapsConfig preset(String name) {
        TeslaMapsConfig c = new TeslaMapsConfig();
        switch (name) {
            case MINIMAL -> { allOff(c); turnOn(c, MINIMAL_ON); }
            case ALL_OFF -> allOff(c);
            default -> {
            }
        }
        return c;
    }

    /** Minimal: the dungeon essentials. No terminals, no automation, no cosmetics, no non-dungeon features. */
    private static final String[] MINIMAL_ON = {
            // map
            "mapEnabled", "onlyShowInDungeon", "mapHideInBoss", "showMapBackground",
            "showRoomNames", "hideEntranceBloodFairyNames",
            "showSecretCount", "hideOneSecretCount", "hideSecretsWhenDone", "showCrypts",
            "showMimicStatus", "showPrinceIcon", "showPrinceStatus", "showDungeonScore", "announce300",
            "showCheckmarks", "showWhiteCheckmarks", "showGreenCheckmarks", "showFailedCheckmarks",
            "showPlayerMarker", "showSelfMarker", "showOtherPlayers", "showPlayerNames",
            "useHeadsInsteadOfMarkers", "rotatePlayerHeads",
            // dungeon esp
            "starredMobESP", "lividGlow", "witherKeyESP", "mimicChestESP", "mimicDeadMessage", "princeDeadMessage",
            // hiders (no player hiding)
            "hideDeadMobs", "hideSoulweaverSkull", "hideSkeletonSkull", "hideThrownBones",
            "hideSuperboomTnt", "hideBlessing", "hideReviveStone", "hidePremiumFlesh",
            "hideJournalEntry", "hideHealerOrbs", "hideHealerFairy", "hideCheapCoins",
            "hideClouds", "noFog", "noNetherFog", "noLightning", "noFallingBlocks", "noBlockBreaking", "noXpOrbs",
            "packetHiders", "noExplosions", "noArrows", "noStuckArrows", "noDeathAnimation",
            "noFire", "noWaterOverlay", "noVignette", "noBlind", "noNausea", "noEnchantGlint",
            "hideInventoryEffects", "noEffects", "noHurtCam", "noViewBobbing",
            // puzzles (terminals stay off)
            "solveBlaze", "blazeDoneMessage", "modernBlazeSolver", "solveThreeWeirdos", "solveTicTacToe",
            "solveCreeperBeams", "creeperBeamsTracers", "creeperBeamsDing",
            "solveBoulder", "showAllBoulderClicks", "solveQuiz", "quizBeacon", "quizChatHighlight",
            "solveTPMaze", "tpMazeTracer", "tpMazePrioritizeDiagonal",
            "solveWaterBoard", "waterBoardTracers", "solveIceFill", "icePathSolver",
            "puzzleTimers", "simonSaysProgress", "simonSaysAlerts", "simonSaysAlertChat",
            "spiritBearTimer", "terracottaTimer",
            // runs
            "splitsEnabled", "splitsShowPb", "splitsSendAllOnEnd",
            "etherwarp", "etherwarpShowFail", "etherwarpFilled", "etherwarpSkullFail", "etherwarpCustomSound",
            "croesusProfitOverlay", "croesusHighlightBest", "croesusDimOthers",
            "croesusHideOpened", "croesusHighlightUnopened",
            // chat filter (spam only; watcher/thorn/stash/revive/oruo stay visible)
            "chatFilterEnabled", "chatFilterEmpty", "chatFilterBlessings", "chatFilterEssence",
            "chatFilterKeys", "chatFilterDoors", "chatFilterWish", "chatFilterUltReady",
            "chatFilterAoeDamage", "chatFilterKillCombo", "chatFilterBlocksInWay", "chatFilterBonePlating",
            "chatFilterPickups", "chatFilterGuildXp", "chatFilterSacks", "chatFilterGfs",
            "chatFilterMilestone", "chatFilterPerkBuffs", "chatFilterWatchdog", "chatFilterMort",
            "chatFilterProfileInfo", "chatFilterServerMsgs",
            // qol
            "infiniteChat", "chatStacking", "commandSuggestions", "commandSuggestionsPlayers",
            "gfsSuggestions", "gfsQueued", "gfsSuperCraft",
            "searchbar", "scrollableTooltip", "tooltipFitScreen", "hudDragInInventory", "backpackPreview", "signEnterKey",
            "enchantAbbrev", "rescueMission",
    };

    /** Dev only: a preset naming a field that no longer exists is logged at launch, not when picked. */
    public static void checkPresetFields() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        for (String name : MINIMAL_ON) {
            try {
                if (TeslaMapsConfig.class.getDeclaredField(name).getType() != boolean.class)
                    TeslaMaps.LOGGER.error("Preset field {} is not a boolean", name);
            } catch (NoSuchFieldException e) {
                TeslaMaps.LOGGER.error("Preset names unknown config field {} - renamed? add a ConfigMigrator.move", name);
            }
        }
    }

    private static void turnOn(TeslaMapsConfig c, String[] fields) {
        for (String name : fields) {
            try {
                TeslaMapsConfig.class.getDeclaredField(name).setBoolean(c, true);
            } catch (ReflectiveOperationException e) {
                TeslaMaps.LOGGER.error("Unknown preset field {}", name, e);
            }
        }
    }

    /** Every non-global boolean off. Minimal builds on this. */
    private static void allOff(TeslaMapsConfig c) {
        for (Field f : TeslaMapsConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != boolean.class) continue;
            if (f.isAnnotationPresent(Global.class)) continue;
            try {
                f.setBoolean(c, false);
            } catch (ReflectiveOperationException e) {
                TeslaMaps.LOGGER.error("Failed to clear field {}", f.getName(), e);
            }
        }
    }
}
