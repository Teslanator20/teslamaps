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

    public static final String DEFAULT = "Default";
    public static final String MINIMAL = "Minimal";
    public static final String LEGIT = "Full Legit";
    public static final String ALL_OFF = "All off";
    public static final String TESLANATOR = "Teslanator";

    private static final String[] PRESETS = {DEFAULT, MINIMAL, LEGIT, ALL_OFF, TESLANATOR};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("profiles");

    public static String active() {
        String p = TeslaMapsConfig.get().activeProfile;
        return p == null || p.isBlank() ? DEFAULT : p;
    }

    public static List<String> list() {
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
     * is created from the matching preset, so the three presets work on a fresh install.
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
            return GSON.fromJson(Files.readString(f), TeslaMapsConfig.class);
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
        // Teslanator is "whatever I currently have on", so it starts as a copy of the live config
        if (TESLANATOR.equals(name)) return copyOfLive();

        TeslaMapsConfig c = new TeslaMapsConfig();
        switch (name) {
            case MINIMAL -> { allOff(c); c.mapEnabled = true; c.onlyShowInDungeon = true; c.showMapBackground = true; }
            case ALL_OFF -> allOff(c);
            case LEGIT -> c.legitMode = true;
            default -> {
            }
        }
        return c;
    }

    private static TeslaMapsConfig copyOfLive() {
        TeslaMapsConfig copy = GSON.fromJson(GSON.toJson(TeslaMapsConfig.get()), TeslaMapsConfig.class);
        return copy == null ? new TeslaMapsConfig() : copy;
    }

    /** Every non-global boolean off. Minimal builds on this and turns the map back on. */
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
