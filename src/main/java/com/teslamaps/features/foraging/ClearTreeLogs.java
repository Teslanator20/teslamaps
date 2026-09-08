package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/** Hides the block displays a chopped tree throws around, and the ones rune effects spawn. */
public class ClearTreeLogs {

    private static final double EPSILON = 1e-6;
    private static final float SCALE_MIN = 0.4f;
    private static final float SCALE_MAX = 1.0f;

    private static final java.util.Set<net.minecraft.world.level.block.Block> TREE_BLOCKS = java.util.Set.of(
            Blocks.STRIPPED_SPRUCE_WOOD,
            Blocks.MANGROVE_WOOD,
            Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.STRIPPED_BIRCH_WOOD,
            Blocks.STRIPPED_MANGROVE_WOOD,
            Blocks.OAK_LEAVES);

    // shouldRender asks per entity per frame, but the display transform only moves
    // once per tick and reading it interpolates a whole Transformation
    private static final java.util.Map<Entity, Boolean> cache = new java.util.IdentityHashMap<>();
    private static long cacheTick = Long.MIN_VALUE;

    public static boolean shouldHide(Entity entity) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.foragingCleanTreeView && !c.foragingHideRuneEffects) return false;
        if (!(entity instanceof Display.BlockDisplay display)) return false;
        if (!c.section("Foraging")) return false;
        if (!Foraging.onForagingIsland()) return false;

        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return false;
        long tick = level.getGameTime();
        if (tick != cacheTick) {
            cache.clear();
            cacheTick = tick;
        }
        Boolean hit = cache.get(entity);
        if (hit != null) return hit;
        boolean result = compute(c, display);
        cache.put(entity, result);
        return result;
    }

    private static boolean compute(TeslaMapsConfig c, Display.BlockDisplay display) {
        // null for one frame between spawn and the first synched-data update
        Display.BlockDisplay.BlockRenderState render = display.blockRenderState();
        if (render == null) return false;
        BlockState state = render.blockState();
        if (state == null || !TREE_BLOCKS.contains(state.getBlock())) return false;

        if (c.foragingCleanTreeView && c.foragingHideRuneEffects) return true;

        Boolean floating = isFloatingTreeBlock(display);
        if (floating == null) return false;
        return floating ? c.foragingCleanTreeView : c.foragingHideRuneEffects;
    }

    // chopped logs fly out level and shrunk, rune blocks are tilted or full size
    private static Boolean isFloatingTreeBlock(Display display) {
        if (Math.abs(display.getLookAngle().y()) > EPSILON) return false;
        Vector3fc scale;
        try {
            Display.RenderState state = display.renderState();
            if (state == null || state.transformation() == null) return null;
            scale = state.transformation().get(0f).scale();
        } catch (Throwable t) {
            return null;
        }
        if (scale == null) return null;
        float s = scale.x();
        if (Math.abs(scale.y() - s) > EPSILON || Math.abs(scale.z() - s) > EPSILON) return false;
        return s >= SCALE_MIN && s <= SCALE_MAX;
    }
}
