package com.teslamaps.features.foraging;

// Ported from Skysoft's ThrowingAxeParticleHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Drops the wax trail a flying Throwing Axe leaves. */
public final class ThrowingAxeParticles {

    private static final Set<String> AXES = Set.of("JUNGLE_AXE", "TREECAPITATOR_AXE", "FIG_AXE", "FIGSTONE_AXE",
            "DECENT_AXE", "SERIOUSLY_DAMAGED_AXE", "HELIX_CHOPPER");

    private ThrowingAxeParticles() {}

    /** From ClientPlayNetworkHandlerMixin, main thread. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hideThrowingAxeParticles || p.particle().getType() != ParticleTypes.WAX_ON) return false;
        if (p.count() != 2 || p.xMaxSpeed() != 1f || p.overrideLimiter()) return false;
        if (p.xDist() != 0f || p.yDist() != 0f || p.zDist() != 0f) return false;
        if (!c.section("Foraging")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (isAxe(mc.player.getMainHandItem())) return true;
        long time = mc.level.getGameTime();
        if (time != axesAt) {
            axesAt = time;
            List<ArmorStand> found = new ArrayList<>();
            for (ArmorStand as : EntityCache.armorStands()) {
                if (as.isInvisible() && isAxe(as.getMainHandItem())) found.add(as);
            }
            axes = found;
        }
        for (ArmorStand as : axes) {
            if (as.position().distanceToSqr(p.x(), p.y(), p.z()) <= 16) return true;
        }
        return false;
    }

    private static List<ArmorStand> axes = List.of();
    private static long axesAt = -1;

    private static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (AXES.contains(ItemUtil.skyblockId(stack))) return true;
        return ItemUtil.loreContains(stack, "Ability: Throwing Axe");
    }
}
