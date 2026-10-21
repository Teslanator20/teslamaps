package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/ClearItems.kt

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClearItems {
    private ClearItems() {}

    public static final float PEARL_VOLUME = 0.5f;
    public static final float PEARL_SPEED = 1.5f;
    public static final float PEARL_SPREAD = 1.0f;

    public static float pearlPitch(float r) { return 0.4f / (r * 0.4f + 0.8f); }

    public static final int PEARL_PUFFS = 32;

    public static final String SPIRIT_LEAP = "SPIRIT_LEAP";
    public static final String INFINILEAP = "INFINITE_SPIRIT_LEAP";

    public static int leapCooldownTicks(String id) { return id.equals(SPIRIT_LEAP) ? 100 : 40; }

    public static boolean leapConsumes(String id) { return id.equals(SPIRIT_LEAP); }

    public static final Map<String, String> INERT;

    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("DUNGEON_DECOY", "Decoy");
        m.put("INFLATABLE_JERRY", "Inflatable Jerry");
        m.put("DUNGEON_TRAP", "Trap");
        m.put("DEFUSE_KIT", "Defuse Kit");
        m.put("TRAINING_WEIGHTS", "Training Weights");
        m.put("HEALING_VIII_SPLASH_POTION", "Healing VIII Splash Potion");
        m.put("POTION", "Healing VIII Splash Potion");
        INERT = Collections.unmodifiableMap(m);
    }

    public static String inertNote(String name) { return "The " + name + " ability is not in ADS yet."; }

    public static final int INERT_NOTE_GAP = 40;
}
