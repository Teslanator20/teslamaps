package com.teslamaps.features;

// Ported from OdinClient's FuckDiorite.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.F7Devices;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ThreadLocalRandom;

public final class FuckDiorite {

    public static final String[] MODES = {"Pillar Color", "Fixed Color", "Random"};
    public static final String[] COLORS = {"Clear", "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink",
            "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};

    private static final int[][] PILLARS = {{46, 169, 41}, {46, 169, 65}, {100, 169, 65}, {100, 169, 41}};
    private static final int[] PILLAR_COLORS = {5, 4, 10, 14};

    private static Block[] stained;
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private FuckDiorite() {}

    public static void register() {
        Feature.of("FuckDiorite").when(() -> TeslaMapsConfig.get().fuckDiorite).tick(FuckDiorite::tick);
    }

    private static Block[] stained() {
        if (stained == null) stained = new Block[]{
                Blocks.STAINED_GLASS.white(), Blocks.STAINED_GLASS.orange(), Blocks.STAINED_GLASS.magenta(),
                Blocks.STAINED_GLASS.lightBlue(), Blocks.STAINED_GLASS.yellow(), Blocks.STAINED_GLASS.lime(),
                Blocks.STAINED_GLASS.pink(), Blocks.STAINED_GLASS.gray(), Blocks.STAINED_GLASS.lightGray(),
                Blocks.STAINED_GLASS.cyan(), Blocks.STAINED_GLASS.purple(), Blocks.STAINED_GLASS.blue(),
                Blocks.STAINED_GLASS.brown(), Blocks.STAINED_GLASS.green(), Blocks.STAINED_GLASS.red(),
                Blocks.STAINED_GLASS.black()};
        return stained;
    }

    private static void tick() {
        if (F7Devices.phase() != 2) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        for (int p = 0; p < PILLARS.length; p++) {
            int[] base = PILLARS[p];
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    for (int dy = 0; dy <= 37; dy++) {
                        cursor.set(base[0] + dx, base[1] + dy, base[2] + dz);
                        BlockState s = level.getBlockState(cursor);
                        if (s.is(Blocks.DIORITE) || s.is(Blocks.POLISHED_DIORITE)) {
                            level.setBlock(cursor.immutable(), glass(c, p), 3);
                        }
                    }
        }
    }

    private static BlockState glass(TeslaMapsConfig c, int pillar) {
        Block[] st = stained();
        return switch (c.fuckDioriteMode) {
            case 0 -> st[PILLAR_COLORS[pillar]].defaultBlockState();
            case 2 -> st[ThreadLocalRandom.current().nextInt(st.length)].defaultBlockState();
            default -> c.fuckDioriteColor <= 0 || c.fuckDioriteColor > 16
                    ? Blocks.GLASS.defaultBlockState() : st[c.fuckDioriteColor - 1].defaultBlockState();
        };
    }
}
