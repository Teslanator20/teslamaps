package com.teslamaps.features;

import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks broken per second, averaged over a rolling window.
 * <p>
 * Counted from block updates near the player rather than from swings, so it
 * measures crops actually breaking instead of how fast the key is held.
 */
public class FarmSpeed {

    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final int MAX_EVENTS = 20000;
    private static final double RANGE_SQUARED = 8 * 8;

    // Ring buffer of break timestamps; a deque would allocate per break.
    private static final long[] times = new long[MAX_EVENTS];
    private static int head = 0;
    private static int count = 0;

    private static long lastBreak = 0L;

    /** When the player last broke a crop, for telling farming from standing around. */
    public static long lastBreakMillis() { return lastBreak; }

    /** One block actually broken by the player. */
    public static void onBreakAttempt(BlockPos pos) {
        if (!SkyblockUtils.isInGarden()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (!isCrop(state)) return;
        FarmProgress.onCropBreak(state);
        MousematAngles.onCrop(state);
        FarmProfit.onCropBreak(state, mc.level.getOverworldClockTime());

        lastBreak = System.currentTimeMillis();
        times[head] = lastBreak;
        head = (head + 1) % MAX_EVENTS;
        if (count < MAX_EVENTS) count++;

        // Farming with nothing dropping is still farming, as far as the session
        // clock is concerned.
        FarmProfit.markActivity();
        SixthVisitor.onCropBreak();
        GardenUptime.onCropBreak();
    }

    /**
     * Anything that grows. VegetationBlock covers the crops, nether wart and the
     * double plants — Sunflower and Wild Rose are the latter, not CropBlock, and
     * checking for crops alone missed those two entirely.
     */
    static boolean isCrop(BlockState state) {
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.VegetationBlock
                || block instanceof net.minecraft.world.level.block.CocoaBlock
                || block instanceof net.minecraft.world.level.block.SugarCaneBlock
                || block instanceof net.minecraft.world.level.block.CactusBlock
                || block instanceof net.minecraft.world.level.block.MushroomBlock
                || block instanceof net.minecraft.world.level.block.HugeMushroomBlock
                || block == net.minecraft.world.level.block.Blocks.MELON
                || block == net.minecraft.world.level.block.Blocks.PUMPKIN
                || block == net.minecraft.world.level.block.Blocks.HAY_BLOCK;
    }

    /** Breaks per second over the window, or 0 when nothing was broken. */
    public static double blocksPerSecond() {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        int recent = 0;
        long oldest = now;
        for (int i = 0; i < count; i++) {
            long time = times[(head - 1 - i + MAX_EVENTS) % MAX_EVENTS];
            if (time < cutoff) break;
            recent++;
            oldest = time;
        }
        if (recent < 2) return 0;

        // Divide by the span actually covered, so the number is meaningful before
        // the full window has filled up.
        double seconds = Math.max((now - oldest) / 1000.0, 1.0);
        return recent / seconds;
    }

    public static void reset() {
        head = 0;
        count = 0;
    }
}
