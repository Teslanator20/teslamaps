package com.teslamaps.slayer;

// Ported from Skytils' RevenantSlayer.kt (rev5TNTPing)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Rev5TntPing {

    private static int ticks;

    private Rev5TntPing() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("Rev5TntPing").section("Combat").when(() -> cfg().rev5TntPing)
                .tick(Rev5TntPing::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Atoned Horror TNT Ping", () -> c.rev5TntPing, v -> c.rev5TntPing = v)
                            .desc("Revenant T5: pings while you stand on a block the Atoned Horror's TNT targets.")
                            .tags("revenant", "atoned", "tnt", "t5");
                });
    }

    private static void tick() {
        if (++ticks % 4 != 0) return;
        if (!SlayerQuest.bossActive() || !"Atoned Horror".equals(SlayerQuest.boss())) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos under = null;
        if (mc.player.onGround()) {
            under = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        } else {
            int minY = mc.level.getMinY();
            for (int y = (int) Math.floor(mc.player.getY() - 0.5); y >= minY; y--) {
                BlockPos p = BlockPos.containing(mc.player.getX(), y, mc.player.getZ());
                if (!mc.level.getBlockState(p).isAir()) {
                    under = p;
                    break;
                }
            }
        }
        if (under != null && danger(mc.level.getBlockState(under))) {
            LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private static boolean danger(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.QUARTZ_SLAB || b == Blocks.QUARTZ_STAIRS || b == Blocks.ACACIA_STAIRS || b == Blocks.ACACIA_SLAB
                || b == Blocks.WHITE_TERRACOTTA || b == Blocks.LIGHT_GRAY_TERRACOTTA || b == Blocks.RED_TERRACOTTA
                || b == Blocks.BEDROCK;
    }
}
