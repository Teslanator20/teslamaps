// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

/**
 * Chat, titles, the action bar. Implemented by HudModule. text is Hypixel's legacy §-coded form. Server thread only.
 */
public interface ChannelService {
    /** Plain system chat; the default goes straight through Sim.chat. */
    default void chat(String text, Audience to) { ChannelDefaults.chat(text, to); }

    default void chat(String text) { chat(text, Audience.Everyone.INSTANCE); }

    /** Title and subtitle (Hypixel's order). The default goes through Sim.title. */
    default void title(String title, String subtitle, int fadeIn, int stay, int fadeOut, Audience to) {
        ChannelDefaults.title(title, subtitle, fadeIn, stay, fadeOut, to);
    }

    default void title(String title, String subtitle, int fadeIn, int stay, int fadeOut) { title(title, subtitle, fadeIn, stay, fadeOut, Audience.Everyone.INSTANCE); }

    default void title(String title, String subtitle, int fadeIn, int stay) { title(title, subtitle, fadeIn, stay, 5, Audience.Everyone.INSTANCE); }

    default void title(String title, String subtitle, int fadeIn) { title(title, subtitle, fadeIn, 30, 5, Audience.Everyone.INSTANCE); }

    default void title(String title, String subtitle) { title(title, subtitle, 0, 30, 5, Audience.Everyone.INSTANCE); }

    default void title(String title) { title(title, "", 0, 30, 5, Audience.Everyone.INSTANCE); }

    /** One action bar segment; lives ttlTicks ticks or until replaced. */
    default void actionBarSegment(String key, String text, int priority, int ttlTicks, Audience to) {}

    default void actionBarSegment(String key, String text, int priority, int ttlTicks) { actionBarSegment(key, text, priority, ttlTicks, Audience.Everyone.INSTANCE); }

    default void actionBarSegment(String key, String text, int priority) { actionBarSegment(key, text, priority, 20, Audience.Everyone.INSTANCE); }

    default void actionBarSegment(String key, String text) { actionBarSegment(key, text, 0, 20, Audience.Everyone.INSTANCE); }

    default void clearActionBarSegment(String key) {}
}
