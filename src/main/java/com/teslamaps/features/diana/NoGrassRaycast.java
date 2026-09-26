package com.teslamaps.features.diana;

// After FrankyAddons' Hitboxes.noGrassActive
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class NoGrassRaycast {

    /** Read by NoGrassRaycastMixin on every raycast step, so only a cached flag. */
    public static volatile boolean active;
    private static int timer;

    private NoGrassRaycast() {}

    public static void register() {
        // no section gate here: the tick must also run to switch the flag off
        Feature.of("NoGrassRaycast")
                .tickAlways(NoGrassRaycast::tick)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Click Through Grass (Hub)", () -> c.noGrassRaycast, v -> c.noGrassRaycast = v).cheat()
                            .desc("In the Hub your crosshair ignores grass, flowers and other plants, so clicks land on the block behind them (Diana burrows in tall grass). Crops stay clickable")
                            .tags("diana", "grass", "raycast", "burrow", "flowers");
                });
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        active = c.noGrassRaycast && c.section("Events") && !LegitMode.blocksCheats()
                && Minecraft.getInstance().player != null && SkyblockUtils.isInArea("Hub");
    }

    public static boolean passThrough(BlockState state) {
        Block b = state.getBlock();
        return b instanceof VegetationBlock && !(b instanceof CropBlock) && !(b instanceof StemBlock)
                && Minecraft.getInstance().isSameThread();
    }
}
