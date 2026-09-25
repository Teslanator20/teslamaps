package com.teslamaps.features;

// Ported from Devonian's CancelF7BossSounds.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class F7BossSounds {

    private static volatile boolean inBoss;

    private F7BossSounds() {}

    public static void register() {
        Feature.of("F7BossSounds").section("Sounds").when(() -> TeslaMapsConfig.get().f7BossSounds)
                .tick(F7BossSounds::tick);
    }

    private static void tick() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        inBoss = f != null && f.getLevel() == 7 && DungeonManager.isInBossArea();
    }

    /** Sound packet hook, any thread (config + a volatile only). True = drop the packet. */
    public static boolean onSound(Holder<SoundEvent> holder, SoundSource source, float vol, float pitch,
                                  double x, double y, double z, int entityId) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.f7BossSounds || !inBoss || !c.section("Sounds")) return false;
        if (c.f7BossSoundsCap && vol > 1f) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level == null) return;
                if (entityId < 0) {
                    mc.level.playLocalSound(x, y, z, holder.value(), source, 1f, pitch, false);
                    return;
                }
                net.minecraft.world.entity.Entity e = mc.level.getEntity(entityId);
                if (e != null) mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), holder.value(), source, 1f, pitch, false);
            });
            return true;
        }
        if (vol >= 2f || (c.f7BossSoundsAboveCap && vol > 1f)) return true;
        SoundEvent s = holder.value();
        if (s == SoundEvents.WITHER_HURT && vol == 0.5f
                && (pitch == 1.0f || pitch == 0.93650794f || pitch == 1.0158731f || pitch == 1.1111112f)) return true;
        return s == SoundEvents.WITHER_DEATH && vol == 0.5f && pitch >= 0.8730159f && pitch <= 1.1587301f;
    }
}
