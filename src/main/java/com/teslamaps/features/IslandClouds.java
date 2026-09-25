package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;

import java.util.List;
import java.util.Locale;

public final class IslandClouds {

    public static final List<String> ISLANDS = List.of("Hub", "Private Island", "Garden", "The Park",
            "Gold Mine", "Deep Caverns", "Dwarven Mines", "Crystal Hollows", "Mineshaft", "Spider's Den",
            "The End", "Crimson Isle", "Kuudra", "Dungeon Hub", "Catacombs", "Jerry's Workshop", "The Rift",
            "Backwater Bayou", "Galatea", "Dark Auction");
    public static final List<String> DEFAULTS = List.of("Dwarven Mines", "Crystal Hollows", "Mineshaft",
            "Catacombs", "Dungeon Hub", "Kuudra");

    private static boolean onIsland;

    private IslandClouds() {}

    public static void register() {
        Feature.of("IslandClouds").when(() -> TeslaMapsConfig.get().hideCloudsIslands).tick(IslandClouds::tick);
    }

    private static void tick() {
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        boolean hit = false;
        if (!area.isEmpty()) {
            for (String s : TeslaMapsConfig.get().cloudHiderIslands) {
                if (area.contains(s.toLowerCase(Locale.ROOT))) { hit = true; break; }
            }
        }
        onIsland = hit;
    }

    /** From NoCloudsMixin. */
    public static boolean hide() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.hideClouds && (!c.hideCloudsIslands || onIsland);
    }
}
