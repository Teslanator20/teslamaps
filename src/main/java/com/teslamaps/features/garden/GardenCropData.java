package com.teslamaps.features.garden;

// Crop in hand, latest fortune/BPS per crop and the running contest, after SkyHanni's GardenApi,
// FarmingFortuneDisplay, GardenCropSpeed and FarmingContestApi
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.CropStats;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GardenCropData {

    private static final Pattern CONTEST_TIME = Pattern.compile("(?:\\d+m ?)?\\d+s|\\d+m");

    private static GardenCrop inHand;
    private static long toolSwitchAt;
    private static GardenCrop lastBroken;
    private static long lastBreakAt;

    private static GardenCrop contestCrop;
    private static long contestStart;
    private static int contestBlocks;
    private static int ticks;

    private GardenCropData() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean needed() {
        TeslaMapsConfig c = cfg();
        return c.jacobContestTimes || c.gardenOptimalSpeed || c.hoeLevelHud || c.jacobContestSummary || c.cropMoneyHud;
    }

    public static void register() {
        Feature.of("GardenCropData").section("Garden").when(GardenCropData::needed).tick(GardenCropData::tick);
    }

    public static GardenCrop inHand() { return inHand; }
    public static long toolSwitchAt() { return toolSwitchAt; }
    public static GardenCrop lastBroken() { return lastBroken; }

    /** True while crops were broken in the last few seconds. */
    public static boolean farming() {
        return System.currentTimeMillis() - lastBreakAt < 3000;
    }

    private static CropStats.Crop stats(GardenCrop crop) {
        return crop == null ? null : CropStats.byName(crop.cropName);
    }

    public static Double latestFortune(GardenCrop crop) {
        CropStats.Crop c = stats(crop);
        return c == null ? null : CropStats.latestFortune(c);
    }

    public static Double latestBps(GardenCrop crop) {
        CropStats.Crop c = stats(crop);
        return c == null ? null : CropStats.latestBps(c);
    }

    /** Crops per second from CropStats' latest fortune and BPS, null without data. */
    public static Double cropsPerSecond(GardenCrop crop) {
        CropStats.Crop c = stats(crop);
        return c == null ? null : CropStats.cropsPerSecond(c);
    }

    public static GardenCrop contestCrop() { return contestCrop; }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInGarden()) {
            inHand = null;
            return;
        }
        GardenCrop now = GardenCrop.byTool(ItemUtil.skyblockId(mc.player.getMainHandItem()));
        if (now != inHand) {
            inHand = now;
            toolSwitchAt = System.currentTimeMillis();
        }
        if (++ticks % 20 != 0) return;
        readContest();
    }

    private static void readContest() {
        GardenCrop found = null;
        boolean header = false;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = StringUtil.stripTrim(raw);
            if (line.startsWith("Jacob's Contest")) { header = true; continue; }
            int i = 0;
            while (i < line.length() && !Character.isLetter(line.charAt(i))) i++;
            if (i == 0 || i >= line.length()) continue;
            String rest = line.substring(i);
            for (GardenCrop c : GardenCrop.ALL) {
                String name = c == GardenCrop.MELON && rest.startsWith("Melon ") && !rest.startsWith("Melon Slice") ? "Melon" : c.cropName;
                if (!rest.startsWith(name + " ")) continue;
                if (CONTEST_TIME.matcher(rest.substring(name.length() + 1)).lookingAt()) found = c;
            }
        }
        if (!header) found = null;
        long now = System.currentTimeMillis();
        if (found == contestCrop) {
            if (contestCrop != null && now - contestStart > 20 * 60_000L) stop(now);
            return;
        }
        if (found == null) {
            stop(now);
        } else if (contestCrop == null) {
            contestCrop = found;
            contestStart = now;
            contestBlocks = 0;
            ContestStatsSummary.onStart(found);
        } else {
            contestCrop = found;
            contestStart = now;
            contestBlocks = 0;
            ContestStatsSummary.onChange(found);
        }
    }

    private static void stop(long now) {
        GardenCrop crop = contestCrop;
        contestCrop = null;
        if (crop != null && now - contestStart > 2 * 60_000L) {
            ContestStatsSummary.onStop(crop, contestBlocks, Math.min(now - contestStart, 20 * 60_000L));
        }
    }

    /** From FarmSpeed, a crop broken in the garden. */
    public static void onBreak(BlockState state) {
        if (!needed() || !cfg().section("Garden")) return;
        GardenCrop crop = GardenCrop.byBlock(state);
        if (crop == null) return;
        long now = System.currentTimeMillis();
        lastBroken = crop;
        lastBreakAt = now;
        if (crop == contestCrop) contestBlocks++;
    }
}
