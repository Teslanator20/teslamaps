package com.teslamaps.features.mining;

// After Skyblocker's BlockBreakPrediction
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Break animation driven by the real SkyBlock break time (mining speed + block strength). */
public final class BlockBreakPrediction {

    private static final Pattern BREAKING_POWER = Pattern.compile("Breaking Power (\\d+)");

    private static BlockPos target;
    private static long start, breakMs = -1;
    private static boolean sounded;
    private static ItemStack lastTool;
    private static int toolPower = -1;

    private BlockBreakPrediction() {}

    public static void register() {
        Feature.of("BlockBreakPrediction").section("Mining").when(() -> TeslaMapsConfig.get().blockBreakPrediction)
                .config("Mining", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.toggle("Block Break Prediction", () -> c.blockBreakPrediction, v -> c.blockBreakPrediction = v)
                            .desc("Mining islands: the crack animation follows the real break time from your Mining Speed and the block's strength")
                            .tags("break", "animation", "mining speed", "crack");
                    b.toggle("  Sound When Broken", () -> c.blockBreakPredictionSound, v -> c.blockBreakPredictionSound = v);
                    b.info("Needs Mining Speed in the Stats tab widget (/tab)");
                });
    }

    /** From LevelRenderer, render thread. Returns the stage to draw for this block. */
    public static int stage(BlockPos pos, BlockState state, int progress) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.blockBreakPrediction || !c.section("Mining")) return progress;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !Mining.onMiningIsland()) return progress;
        if (!(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) return progress;
        if (!mc.gameMode.isDestroying()) {
            target = null;
            return progress;
        }
        if (!pos.equals(target)) {
            target = pos.immutable();
            start = System.currentTimeMillis();
            sounded = false;
            breakMs = breakTime(mc, state);
        }
        if (breakMs <= 0) return progress;
        long elapsed = System.currentTimeMillis() - start;
        if (c.blockBreakPredictionSound && !sounded && elapsed >= breakMs) {
            sounded = true;
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        return (int) Math.min(9, elapsed * 10 / breakMs);
    }

    private static long breakTime(Minecraft mc, BlockState state) {
        boolean hollows = com.teslamaps.utils.SkyblockUtils.getArea().equalsIgnoreCase("Crystal Hollows");
        OreStrength o = OreStrength.of(state, hollows);
        if (o == null) return -1;
        if (power(mc.player.getMainHandItem()) < o.breakingPower) return -1;
        int ticks = o.ticks(OreStrength.miningSpeed());
        return ticks <= 0 ? -1 : ticks * 50L;
    }

    private static int power(ItemStack tool) {
        if (tool == lastTool && tool != null) return toolPower;
        lastTool = tool;
        toolPower = -1;
        for (String line : ItemUtil.loreLines(tool)) {
            Matcher m = BREAKING_POWER.matcher(StringUtil.strip(line));
            if (m.find()) {
                toolPower = Integer.parseInt(m.group(1));
                break;
            }
        }
        return toolPower;
    }
}
