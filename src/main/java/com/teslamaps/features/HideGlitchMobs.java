package com.teslamaps.features;

// Ported from Skysoft's features/misc/StaleSkyBlockMobPlayerModels.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Hides player-model mobs Hypixel left behind: fake uuid, mob health, no health nametag. */
public final class HideGlitchMobs {

    private static IntOpenHashSet ids = new IntOpenHashSet();
    private static int timer;

    private HideGlitchMobs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HideGlitchMobs").when(() -> cfg().hideGlitchMobs)
                .tick(HideGlitchMobs::tick)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Glitched Mobs");
                    b.toggle("Hide Glitch Mobs", () -> c.hideGlitchMobs, v -> c.hideGlitchMobs = v)
                            .desc("Hides stuck mob player models that never despawn (no health tag, not a real player).");
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof Player && !ids.isEmpty() && cfg().hideGlitchMobs && ids.contains(e.getId());
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        IntOpenHashSet next = new IntOpenHashSet();
        if (SkyblockUtils.isInSkyblock()) {
            Minecraft mc = Minecraft.getInstance();
            List<ArmorStand> tags = null;
            for (Player p : mc.level.players()) {
                if (p == mc.player || p.getUUID().version() == 4 || p.getVehicle() != null) continue;
                if (p.tickCount < 20) continue;
                float max = p.getMaxHealth(), hp = p.getHealth();
                if (max <= 0f || max > 40f || hp < 1000f || hp <= max * 10f) continue;
                if (tags == null) tags = EntityCache.namedArmorStands();
                if (hasHealthTag(p, tags)) continue;
                next.add(p.getId());
            }
        }
        ids = next;
    }

    private static boolean hasHealthTag(Player p, List<ArmorStand> tags) {
        for (ArmorStand as : tags) {
            double dx = as.getX() - p.getX(), dz = as.getZ() - p.getZ(), dy = as.getY() - p.getY();
            if (dx * dx + dz * dz > 2.25 || dy < -0.5 || dy > 4.0) continue;
            Component n = as.getCustomName();
            if (n != null && n.getString().contains("❤")) return true;
        }
        return false;
    }
}
