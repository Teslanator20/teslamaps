package com.teslamaps.adm.adr;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Set by the sim port (com.teslamaps.adm.sim); ADR only reads them. */
public final class AdrSimHooks {
    private AdrSimHooks() {}

    /** The ADS world is loaded (sim server running in singleplayer): record into simDir, never as a Hypixel run. */
    public static volatile BooleanSupplier adsWorld = () -> false;
    /** An ADS run has told the client its stage: keep the ADS recording from here. */
    public static volatile BooleanSupplier adsInRun = () -> false;
    /** The P3 Sim world: never recorded as a Hypixel run. */
    public static volatile BooleanSupplier p3SimWorld = () -> false;
    /** Last ADS launch as {layout (String), seed (Long)}, or null. */
    public static volatile Supplier<Object[]> lastLaunch = () -> null;

    static boolean get(BooleanSupplier s) {
        try {
            return s != null && s.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }
}
