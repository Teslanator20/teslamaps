package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

// Drops the spawn packet for purely decorative entities instead of skipping them in
// EntityRenderDispatcher.shouldRender, so they are never created or ticked at all.
// Concept from Odin's RenderOptimizer.
//
// Only entity types that carry no gameplay meaning in Skyblock are listed, and nothing
// in this mod reads them (verified: the only references are the render hider itself).
// Anything you can pick up or has to be hit must NOT go here - SoulweaverHider
// deliberately stays on the render path because it also hides Revive Stone / Premium
// Flesh item drops, which are collectable secrets.
public class PacketHiders {

    private static int cancelled = 0;

    public static int cancelledCount() { return cancelled; }

    /** Runs on the netty thread - config field reads only, no world or sound access. */
    public static boolean shouldCancelSpawn(EntityType<?> type) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.packetHiders) return false;

        boolean cancel = (type == EntityTypes.LIGHTNING_BOLT && cfg.noLightning)
                || (type == EntityTypes.FALLING_BLOCK && cfg.noFallingBlocks)
                || (type == EntityTypes.EXPERIENCE_ORB && cfg.noXpOrbs)
                || (type == EntityTypes.FIREWORK_ROCKET && cfg.hideFireworks);
        if (cancel) cancelled++;
        return cancel;
    }
}
