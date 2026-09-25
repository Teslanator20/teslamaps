package com.teslamaps.features.garden;

// Crop in hand, latest fortune/BPS per crop and the running contest, after SkyHanni's GardenApi,
// FarmingFortuneDisplay, GardenCropSpeed and FarmingContestApi
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GardenCropData {

    private static final Pattern FORTUNE = Pattern.compile("^(.+?) Fortune: \\D*([\\d,.]+)");
    private static final Pattern CONTEST_TIME = Pattern.compile("(?:\\d+m ?)?\\d+s|\\d+m");
    private static final int RUN = 200;
    private static final long RUN_GAP_MS = 5000;

    private static GardenCrop inHand;
    private static long toolSwitchAt;
    private static GardenCrop lastBroken;
    private static long lastBreakAt;

    private static final long[] run = new long[RUN];
    private static int runHead, runCount;
    private static GardenCrop runCrop;
    private static long lastSave;

    private static GardenCrop contestCrop;
    private static long contestStart;
    private static int contestBlocks;
    private static int ticks;

    private GardenCropData() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static boolean needed() {
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

    public static Double latestFortune(GardenCrop crop) {
        return crop == null ? null : cfg().gardenLatestFortune.get(crop.key);
    }

    public static Double latestBps(GardenCrop crop) {
        return crop == null ? null : cfg().gardenLatestBps.get(crop.key);
    }

    /** Crops per second from the latest fortune and BPS, null without data. */
    public static Double cropsPerSecond(GardenCrop crop) {
        Double ff = latestFortune(crop), bps = latestBps(crop);
        if (ff == null || bps == null || bps <= 0) return null;
        return ff * crop.baseDrops * bps / 100.0;
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
        readFortune();
        readContest();
    }

    private static void readFortune() {
        if (inHand == null) return;
        double universal = -1, crop = -1;
        GardenCrop cropOf = null;
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            if (!line.contains(" Fortune: ")) continue;
            Matcher m = FORTUNE.matcher(line);
            if (!m.find()) continue;
            double v;
            try { v = Double.parseDouble(m.group(2).replace(",", "")); } catch (NumberFormatException e) { continue; }
            String what = m.group(1).trim();
            if (what.equals("Farming")) universal = v;
            else {
                GardenCrop c = GardenCrop.byName(what);
                if (c != null) { crop = v; cropOf = c; }
            }
        }
        if (universal < 0 || cropOf != inHand) return;
        Double old = cfg().gardenLatestFortune.put(inHand.key, universal + crop);
        if (old == null || Math.abs(old - (universal + crop)) > 0.5) saveSoon();
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

        if (crop != runCrop || runCount > 0 && now - run[(runHead - 1 + RUN) % RUN] > RUN_GAP_MS) {
            runCrop = crop;
            runHead = 0;
            runCount = 0;
        }
        run[runHead] = now;
        runHead = (runHead + 1) % RUN;
        if (runCount < RUN) runCount++;
        if (runCount < 30) return;
        long oldest = run[(runHead - runCount + RUN) % RUN];
        double span = (now - oldest) / 1000.0;
        if (span < 10) return;
        cfg().gardenLatestBps.put(crop.key, (runCount - 1) / span);
        saveSoon();
    }

    private static void saveSoon() {
        long now = System.currentTimeMillis();
        if (now - lastSave < 60_000L) return;
        lastSave = now;
        TeslaMapsConfig.save();
    }
}
