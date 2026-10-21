package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/BlockEntityMaker.kt

import com.teslamaps.adm.sim.Arena;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockStates {
    private BlockStates() {}

    private static final ConcurrentHashMap<String, BlockState> parsed = new ConcurrentHashMap<>();

    public static BlockState parse(String state) { return parsed.computeIfAbsent(state, Arena::parse); }

    public static BlockState rotated(String state, int quarterTurns) {
        BlockState s = parse(state);
        return Math.floorMod(quarterTurns, 4) == 0 ? s : s.rotate(rotation(quarterTurns));
    }

    public static Rotation rotation(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            default -> Rotation.COUNTERCLOCKWISE_90;
        };
    }
}
