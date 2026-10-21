// Ported from ADM's p3sim/run/plugin/AdsPlugin.kt
package com.teslamaps.adm.sim.run.plugin;

import java.util.List;

/**
 * The plug-in seam: an optional mod implements this and declares the Fabric entrypoint "teslamaps:ads". Interfaces a plug-in
 * implements only gain members with defaults; breaking that bumps AdsPlugins.API_VERSION.
 */
public interface AdsPlugin {
    /** A short stable id for logs and settings. */
    String id();

    /** The AdsPlugins.API_VERSION this plug-in was compiled against; larger than the game's = ignored. */
    default int apiVersion() { return 1; }

    /** Once after loading, before any run. */
    default void onLoad(PluginContext ctx) {}

    /** The blood camp; null = none (the Watcher's own camp runs). */
    default BloodCampFactory bloodCamp() { return null; }

    /** Party strategy presets this plug-in offers. */
    default List<PartyStrategy> partyStrategies() { return List.of(); }
}
