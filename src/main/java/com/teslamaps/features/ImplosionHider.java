package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Ported from SkyOcean's ImplosionHider.kt
public class ImplosionHider {
    private static final Set<String> WITHER_BLADES = Set.of("HYPERION", "ASTRAEA", "VALKYRIE", "SCYLLA");
    private static final double RANGE_SQ = 12.0;

    private static volatile List<Vec3> players = List.of();
    private static volatile boolean inSkyblock = false;
    private static int ticks = 0;

    public static void register() {
        com.teslamaps.registry.Feature.of("ImplosionHider")
                .when(() -> TeslaMapsConfig.get().hideImplosions)
                .tick(ImplosionHider::tick);
    }

    private static void tick() {
        if (++ticks % 2 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        inSkyblock = mc.level != null && mc.player != null && SkyblockUtils.isInSkyblock();
        if (!inSkyblock) { players = List.of(); return; }
        List<Vec3> list = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || WITHER_BLADES.contains(ItemUtil.skyblockId(p.getMainHandItem()))) list.add(p.position());
        }
        players = list;
    }

    public static boolean shouldHide(double x, double y, double z, double xd) {
        if (!TeslaMapsConfig.get().hideImplosions) return false;
        if (Math.abs(2.0 * (1.0 - xd * 0.5)) > 25) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inSkyblock) return false;
        if (mc.player.position().distanceToSqr(x, y, z) <= RANGE_SQ) return true;
        for (Vec3 p : players) if (p.distanceToSqr(x, y, z) <= RANGE_SQ) return true;
        return false;
    }
}
