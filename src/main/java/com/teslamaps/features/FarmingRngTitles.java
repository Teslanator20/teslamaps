package com.teslamaps.features;

// After Skytils' hideFarmingRNGTitles
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;

/** Drops the "RARE DROP!" titles (Melon/Pumpkin Dicer) that cover the screen while farming. */
public final class FarmingRngTitles {

    private static Feature feature;
    private static long hiddenAt;

    private FarmingRngTitles() {}

    public static void register() {
        feature = Feature.of("FarmingRngTitles").section("Garden").when(() -> TeslaMapsConfig.get().hideFarmingRngTitles)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Farming Titles");
                    b.toggle("Hide Farming RNG Titles", () -> c.hideFarmingRngTitles, v -> c.hideFarmingRngTitles = v)
                            .desc("Hides the DROP! titles from the Melon and Pumpkin Dicer in the Garden. The chat line stays.")
                            .tags("dicer", "title", "rare drop");
                });
    }

    /** From ClientPlayNetworkHandlerMixin, main thread only. */
    public static boolean hideTitle(String text) {
        if (feature == null || !text.contains("DROP!") || !feature.active() || !SkyblockUtils.isInGarden()) return false;
        hiddenAt = System.currentTimeMillis();
        return true;
    }

    public static boolean hideSubtitle(String text) {
        if (feature == null || !feature.active() || !SkyblockUtils.isInGarden()) return false;
        return text.contains("Dicer") || System.currentTimeMillis() - hiddenAt < 500;
    }
}
