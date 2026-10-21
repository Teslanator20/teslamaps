// Ported from ADM's p3sim/run/plugin/AdsPlugin.kt
package com.teslamaps.adm.sim.run.plugin;

/** What a plug-in may learn about the game when it loads. May gain members. */
public interface PluginContext {
    /** The ADS API version of this game. */
    int apiVersion();

    /** A line in the game log with the plug-in's id as prefix. */
    void log(String message);
}
