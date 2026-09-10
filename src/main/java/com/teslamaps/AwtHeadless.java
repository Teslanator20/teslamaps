package com.teslamaps;

import java.awt.GraphicsEnvironment;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Minecraft's Main sets java.awt.headless=true, and GraphicsEnvironment caches that value on its
 * first query - after which the clipboard is gone for good (HeadlessException). Running before
 * Main and locking the cached value to false is the only point where that can still be prevented.
 */
public class AwtHeadless implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        try {
            System.setProperty("java.awt.headless", "false");
            GraphicsEnvironment.isHeadless();
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("TeslaMaps").warn(
                    "Could not disable AWT headless mode: {}", t.toString());
        }
    }
}
