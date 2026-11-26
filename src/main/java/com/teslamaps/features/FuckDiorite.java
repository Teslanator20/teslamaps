package com.teslamaps.features;

// Ported from OdinClient's FuckDiorite.kt; render-only here, the world keeps the diorite
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.F7Devices;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** F7 P2: the diorite of the 4 Storm pillars is drawn as glass (DoorGlassModel). The blocks stay diorite. */
public final class FuckDiorite {

    public static final String[] MODES = {"Pillar Color", "Fixed Color", "Random"};
    public static final String[] COLORS = {"Clear", "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink",
            "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};

    private static final int[][] PILLARS = {{46, 169, 41}, {46, 169, 65}, {100, 169, 65}, {100, 169, 41}};
    private static final int[] PILLAR_COLORS = {5, 4, 10, 14};
    private static final int HEIGHT = 37;

    private static Block[] stained;
    /** Storm phase and feature on: diorite gets the wrapper model. */
    public static volatile boolean active;
    private static String lastLook = "";

    private FuckDiorite() {}

    public static void register() {
        Feature.of("FuckDiorite").tickAlways(FuckDiorite::tick);
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

    public static boolean pillarBlock(Block b) {
        return b == Blocks.DIORITE || b == Blocks.POLISHED_DIORITE;
    }

    /** Glass to draw at this block, null = the real block. Called from chunk-build threads. */
    public static BlockState glassAt(BlockPos pos) {
        if (!active) return null;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (int p = 0; p < PILLARS.length; p++) {
            int[] b = PILLARS[p];
            if (Math.abs(x - b[0]) <= 3 && Math.abs(z - b[2]) <= 3 && y >= b[1] && y <= b[1] + HEIGHT) return glass(p, pos);
        }
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean want = c.fuckDiorite && mc.level != null && F7Devices.phase() == 2;
        String look = c.fuckDioriteMode + "|" + c.fuckDioriteColor;
        boolean lookChanged = !look.equals(lastLook);
        lastLook = look;
        if (want == active && !(active && lookChanged)) return;
        active = want;
        if (mc.levelExtractor == null || mc.level == null) return;
        for (int[] b : PILLARS) {
            mc.levelExtractor.setBlocksDirty(b[0] - 3, b[1], b[2] - 3, b[0] + 3, b[1] + HEIGHT, b[2] + 3);
        }
    }

    private static BlockState glass(int pillar, BlockPos pos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Block[] st = stained();
        return switch (c.fuckDioriteMode) {
            case 0 -> st[PILLAR_COLORS[pillar]].defaultBlockState();
            // stable per block, so a rebuild does not reshuffle the colors
            case 2 -> st[Math.floorMod((int) (pos.asLong() * 0x9E3779B97F4A7C15L >>> 40), st.length)].defaultBlockState();
            default -> c.fuckDioriteColor <= 0 || c.fuckDioriteColor > 16
                    ? Blocks.GLASS.defaultBlockState() : st[c.fuckDioriteColor - 1].defaultBlockState();
        };
    }
}
