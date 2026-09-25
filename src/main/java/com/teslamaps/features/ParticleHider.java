package com.teslamaps.features;

// Ported from SkyHanni's ParticleHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;

import java.util.List;

public final class ParticleHider {

    private static boolean m7Boss;
    private static double[] fireballs = new double[0];
    private static int fireballCount;

    private ParticleHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ParticleHider").when(() -> cfg().particleHider).tick(ParticleHider::tick);
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        m7Boss = c.particleHideFar && DungeonManager.getCurrentFloor() == DungeonFloor.M7 && DungeonManager.isInBossArea();
        if (!c.particleHideFireball) { fireballCount = 0; return; }
        List<SmallFireball> list = EntityCache.byType(SmallFireball.class);
        if (fireballs.length < list.size() * 3) fireballs = new double[list.size() * 3];
        int n = 0;
        for (SmallFireball f : list) {
            fireballs[n * 3] = f.getX();
            fireballs[n * 3 + 1] = f.getY();
            fireballs[n * 3 + 2] = f.getZ();
            n++;
        }
        fireballCount = n;
    }

    public static boolean hide(ParticleOptions options, double x, double y, double z) {
        TeslaMapsConfig c = cfg();
        ParticleType<?> type = options.getType();
        if (c.moreOverlayHiders && c.hideWitherImpactParticles
                && (type == ParticleTypes.WITCH || type == ParticleTypes.HEART)) return true;
        if (!c.particleHider) return false;
        boolean smoke = type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE;
        if (smoke && c.particleHideSmoke) return true;
        if (smoke && fireballCount > 0) {
            for (int i = 0; i < fireballCount; i++) {
                double dx = fireballs[i * 3] - x, dy = fireballs[i * 3 + 1] - y, dz = fireballs[i * 3 + 2] - z;
                if (dx * dx + dy * dy + dz * dz < 25) return true;
            }
        }
        if (c.particleHideFar || c.particleHideCloseRedstone) {
            var p = Minecraft.getInstance().player;
            if (p == null) return false;
            double d = p.distanceToSqr(x, y, z);
            if (c.particleHideFar && d > 1600 && !m7Boss) return true;
            if (c.particleHideCloseRedstone && type == ParticleTypes.DUST && d < 4) return true;
        }
        return false;
    }

    public static boolean hideBlaze() { return cfg().particleHider && cfg().particleHideBlaze; }

    public static boolean hideEnderman() { return cfg().particleHider && cfg().particleHideEnderman; }

    public static boolean hideFireBlock() { return cfg().particleHider && cfg().particleHideFireBlock; }
}
