package com.teslamaps.features;

// Ported from Devonian's HideWitherKing.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.EntityType;

public class HideWitherKing {

    private static volatile boolean active = false;

    public static void register() {
        Feature.of("HideWitherKing").tickAlways(HideWitherKing::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        active = TeslaMapsConfig.get().hideWitherKing && mc.player != null
                && DungeonManager.getCurrentFloor() == DungeonFloor.M7 && DungeonManager.isInDungeon()
                && mc.player.getY() <= 45;
    }

    private static boolean inArea(double y, double z) {
        return y >= 9.0 && y <= 25.0 && z <= 45.0;
    }

    /** Netty thread: reads the tick-computed flag only. */
    public static boolean shouldCancelSpawn(ClientboundAddEntityPacket p) {
        return active && p.getType() == EntityType.ARMOR_STAND && inArea(p.getY(), p.getZ());
    }

    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        return active && p.getParticle().getType() == ParticleTypes.WITCH && inArea(p.getY(), p.getZ());
    }
}
