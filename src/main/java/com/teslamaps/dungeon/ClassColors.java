package com.teslamaps.dungeon;

// Ported from NoammAddons' ClassColors.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

/** One class palette for map, leap menu and teammate highlight, only while the toggle is on. */
public final class ClassColors {

    public static final String ARCHER = "FFAA00", BERSERK = "AA0000", HEALER = "FF55FF", MAGE = "55FFFF", TANK = "00AA00";

    private ClassColors() {}

    public static void register() {
        Feature.of("ClassColors")
                .config("Map", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Class Colors");
                    b.toggle("Custom Class Colors", () -> c.customClassColors, v -> c.customClassColors = v)
                            .desc("One colour per dungeon class, used by the map heads, the leap menu and Highlight Teammates.")
                            .tags("class", "colour", "archer", "berserk", "healer", "mage", "tank");
                    b.color("  Archer", () -> c.classColorArcher, v -> c.classColorArcher = v);
                    b.color("  Berserk", () -> c.classColorBerserk, v -> c.classColorBerserk = v);
                    b.color("  Healer", () -> c.classColorHealer, v -> c.classColorHealer = v);
                    b.color("  Mage", () -> c.classColorMage, v -> c.classColorMage = v);
                    b.color("  Tank", () -> c.classColorTank, v -> c.classColorTank = v);
                    b.button("  Reset Class Colors", () -> {
                        c.classColorArcher = ARCHER;
                        c.classColorBerserk = BERSERK;
                        c.classColorHealer = HEALER;
                        c.classColorMage = MAGE;
                        c.classColorTank = TANK;
                        TeslaMapsConfig.save();
                    });
                });
    }

    /** The configured colour, or {@code fallback} while custom colours are off or the class is unknown. */
    public static int or(String dungeonClass, int fallback) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.customClassColors || dungeonClass == null) return fallback;
        String hex;
        if (dungeonClass.equalsIgnoreCase("Archer")) hex = c.classColorArcher;
        else if (dungeonClass.equalsIgnoreCase("Berserk")) hex = c.classColorBerserk;
        else if (dungeonClass.equalsIgnoreCase("Healer")) hex = c.classColorHealer;
        else if (dungeonClass.equalsIgnoreCase("Mage")) hex = c.classColorMage;
        else if (dungeonClass.equalsIgnoreCase("Tank")) hex = c.classColorTank;
        else hex = null;
        return hex == null ? fallback : TeslaMapsConfig.parseColor(hex);
    }
}
