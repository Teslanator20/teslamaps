package com.teslamaps.features;

// Concept from Skytils' MasterMode7Features.kt (witherKingDragonSlashAlert)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class DimensionalSlash {

    private static final List<AABB> zones = new ArrayList<>();
    private static Object lastLevel;
    private static int tick;

    private DimensionalSlash() {}

    public static void register() {
        Feature.of("DimensionalSlash").when(() -> TeslaMapsConfig.get().dimensionalSlashAlert)
                .tick(DimensionalSlash::tick);
    }

    private static boolean p5() {
        Minecraft mc = Minecraft.getInstance();
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return mc.player != null && DungeonManager.isInDungeon() && f != null && f.getLevel() == 7 && mc.player.getY() <= 45;
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().dimensionalSlashAlert || !Minecraft.getInstance().isSameThread() || !p5()) return;
        if (oldState.is(Blocks.GLOWSTONE)) {
            zones.clear();
            return;
        }
        if (newState.is(Blocks.GLOWSTONE) && !oldState.is(Blocks.PACKED_ICE)) {
            zones.add(new AABB(pos.getX() - 5, pos.getY() - 5, pos.getZ() - 5, pos.getX() + 6, pos.getY() + 6, pos.getZ() + 6));
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            zones.clear();
        }
        if (zones.isEmpty() || ++tick < 15) return;
        tick = 0;
        if (!p5()) {
            zones.clear();
            return;
        }
        for (AABB z : zones) {
            if (z.contains(mc.player.position())) {
                mc.gui.setTimes(0, 10, 5);
                mc.gui.setTitle(Component.literal("§c§lDimensional Slash!"));
                return;
            }
        }
    }
}
