package com.teslamaps.features.mining;

// Ported from SkyOcean's RedCarpets.kt (dwarven_mines_carpets tag)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Dwarven Mines: grey/blue carpets drawn red so Titanium stands out. */
public final class MinesRecolor {

    private static volatile boolean carpets;
    private static int ticks;

    private MinesRecolor() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MinesRecolor").section("Mining")
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Red Carpets (Dwarven Mines)", () -> c.miningRedCarpets, v -> c.miningRedCarpets = v)
                            .desc("Draws the grey, light grey and light blue carpets of the Dwarven Mines red, so Titanium is easier to spot.")
                            .tags("carpet", "titanium", "dwarven", "recolor");
                });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ticks++ % 10 != 0) return;
            TeslaMapsConfig c = cfg();
            boolean want = mc.level != null && c.section("Mining") && c.miningRedCarpets
                    && SkyblockUtils.isInArea("Dwarven Mines");
            if (want == carpets) return;
            carpets = want;
            if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
        });
    }

    /** Section-compile worker threads, from RenderSectionRegionMixin. */
    public static BlockState swap(BlockPos pos, BlockState state) {
        if (!carpets) return state;
        Block b = state.getBlock();
        if (b == Blocks.CARPET.gray() || b == Blocks.CARPET.lightGray() || b == Blocks.CARPET.lightBlue()) {
            return Blocks.CARPET.red().defaultBlockState();
        }
        return state;
    }
}
