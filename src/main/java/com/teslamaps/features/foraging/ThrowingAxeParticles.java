package com.teslamaps.features.foraging;

// Ported from Skysoft's ThrowingAxeParticleHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/** Drops the wax trail a flying Throwing Axe leaves. */
public final class ThrowingAxeParticles {

    private static final Set<String> AXES = Set.of("JUNGLE_AXE", "TREECAPITATOR_AXE", "FIG_AXE", "FIGSTONE_AXE",
            "DECENT_AXE", "SERIOUSLY_DAMAGED_AXE", "HELIX_CHOPPER");

    private ThrowingAxeParticles() {}

    /** From ClientPlayNetworkHandlerMixin, main thread. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hideThrowingAxeParticles || p.getParticle().getType() != ParticleTypes.WAX_ON) return false;
        if (p.getCount() != 2 || p.getMaxSpeed() != 1f || p.isOverrideLimiter()) return false;
        if (p.getXDist() != 0f || p.getYDist() != 0f || p.getZDist() != 0f) return false;
        if (!c.section("Foraging")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (isAxe(mc.player.getMainHandItem())) return true;
        AABB box = new AABB(p.getX() - 4, p.getY() - 4, p.getZ() - 4, p.getX() + 4, p.getY() + 4, p.getZ() + 4);
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (as.isInvisible() && isAxe(as.getMainHandItem())
                    && as.position().distanceToSqr(p.getX(), p.getY(), p.getZ()) <= 16) return true;
        }
        return false;
    }

    private static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (AXES.contains(ItemUtil.skyblockId(stack))) return true;
        return ItemUtil.loreContains(stack, "Ability: Throwing Axe");
    }
}
