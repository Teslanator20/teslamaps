package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;

import java.util.regex.Pattern;

/** Drops tab-widget fakes and NPCs from the vanilla Social Interactions screen on SkyBlock. */
public final class SocialScreenFilter {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");

    private SocialScreenFilter() {}

    public static void register() {
        Feature.of("SocialScreenFilter")
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Social Interactions");
                    b.toggle("Hide Fake Players in Social Screen", () -> c.socialHideFakePlayers, v -> c.socialHideFakePlayers = v)
                            .desc("The vanilla Social Interactions screen (P) only lists real player names on SkyBlock, no tab-list fakes or NPCs");
                });
    }

    /** Called once per entry when the screen fills, never per frame. */
    public static boolean hide(String name) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.socialHideFakePlayers || !c.section("Chat")) return false;
        return SkyblockUtils.isInSkyblock() && (name == null || !NAME.matcher(name).matches());
    }
}
