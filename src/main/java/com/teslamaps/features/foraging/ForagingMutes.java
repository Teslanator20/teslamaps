// Ported from SkyHanni's Mute*.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;

/** Phantom / Fusion Machine / tree-break sound mutes, driven from SoundPlayMixin. */
public class ForagingMutes {

    private static final String CREAKING_DEATH = "minecraft:entity.creaking.death";

    // the machine plays these at volume 20, far louder than any normal firework
    private static final java.util.Set<String> FUSION = java.util.Set.of(
            "minecraft:entity.firework_rocket.blast",
            "minecraft:entity.firework_rocket.blast_far");

    public static boolean enabled(TeslaMapsConfig c) {
        return c.foragingMutePhantoms || c.foragingMuteFusion || c.foragingMuteTreeBreak;
    }

    public static boolean shouldMute(TeslaMapsConfig c, String id, float volume) {
        if (c.foragingMuteTreeBreak && id.equals(CREAKING_DEATH)) {
            // trees only break on the foraging islands, elsewhere this is a real creaking mob
            if (!Foraging.onForagingIsland() || c.foragingMuteTreeBreakGalatea) return true;
        }
        if (c.foragingMutePhantoms && id.startsWith("minecraft:entity.phantom.") && Foraging.inMoonglade()) return true;
        if (c.foragingMuteFusion && FUSION.contains(id) && volume >= 19.9f && Foraging.inMoonglade()) return true;
        return false;
    }
}
