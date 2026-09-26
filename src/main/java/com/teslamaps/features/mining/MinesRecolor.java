package com.teslamaps.features.mining;

// Ported from SkyOcean's RedCarpets.kt (dwarven_mines_carpets tag) and Skytils' darkModeMist
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Dwarven Mines: grey/blue carpets drawn red so Titanium stands out, The Mist drawn dark. */
public final class MinesRecolor {

    private static volatile boolean any;
    private static boolean carpets, mist;
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
                    b.toggle("Dark Mist (Dwarven Mines)", () -> c.miningDarkMist, v -> c.miningDarkMist = v)
                            .desc("White glass and white carpet in The Mist (below y 77) turn grey.")
                            .tags("mist", "dark", "dwarven", "recolor");
                });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ticks++ % 10 != 0) return;
            TeslaMapsConfig c = cfg();
            boolean on = mc.level != null && c.section("Mining") && (c.miningRedCarpets || c.miningDarkMist)
                    && SkyblockUtils.isInArea("Dwarven Mines");
            boolean wantCarpets = on && c.miningRedCarpets, wantMist = on && c.miningDarkMist;
            if (wantCarpets == carpets && wantMist == mist) return;
            carpets = wantCarpets;
            mist = wantMist;
            any = wantCarpets || wantMist;
            if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
        });
    }

    /** Section-compile worker threads, from RenderSectionRegionMixin. */
    public static BlockState swap(BlockPos pos, BlockState state) {
        if (!any) return state;
        Block b = state.getBlock();
        if (carpets && (b == Blocks.GRAY_CARPET || b == Blocks.LIGHT_GRAY_CARPET || b == Blocks.LIGHT_BLUE_CARPET)) {
            return Blocks.RED_CARPET.defaultBlockState();
        }
        if (mist && pos.getY() <= 76) {
            if (b == Blocks.WHITE_STAINED_GLASS) return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            if (b == Blocks.WHITE_CARPET) return Blocks.GRAY_CARPET.defaultBlockState();
        }
        return state;
    }
}
