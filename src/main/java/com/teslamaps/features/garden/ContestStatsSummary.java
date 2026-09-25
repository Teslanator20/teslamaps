package com.teslamaps.features.garden;

// Ported from SkyHanni's JacobContestStatsSummary.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ContestStatsSummary {

    private static Feature feature;

    private ContestStatsSummary() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("JacobContestSummary").section("Garden").when(() -> cfg().jacobContestSummary)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Jacob's Contest");
                    b.toggle("Contest Stats Summary", () -> c.jacobContestSummary, v -> c.jacobContestSummary = v)
                            .desc("Counts the contest crop you break during a Jacob's Contest and prints blocks, average BPS (red above 19) and time at the end. Changing the crop resets it.")
                            .tags("jacob", "contest", "bps", "blocks");
                });
    }

    private static boolean on() {
        return feature != null && feature.active();
    }

    static void onStart(GardenCrop crop) {
        if (on()) say("§eTracking your §a" + crop.cropName + " §eContest blocks per second");
    }

    static void onChange(GardenCrop crop) {
        if (on()) say("§eContest crop changed, resetting the blocks per second count");
    }

    static void onStop(GardenCrop crop, int blocks, long millis) {
        if (!on() || blocks == 0) return;
        long secs = Math.max(1, millis / 1000);
        double bps = Math.round(blocks * 100.0 / secs) / 100.0;
        say("§eStats for §a" + crop.cropName + " §eContest:");
        say("§7Blocks Broken in total: §e" + String.format(Locale.US, "%,d", blocks));
        say("§7Average Blocks Per Second: " + (bps > 19 ? "§c" : "§a") + bps);
        say("§7Participated for §b" + (secs / 60) + "m " + (secs % 60) + "s");
    }

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
