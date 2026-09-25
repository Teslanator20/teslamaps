package com.teslamaps.slayer;

// Spawn particles: SkyHanni's HideSlayerSpawnParticles.kt, Sven Pups: SkyblockAddons' HIDE_SVEN_PUP_NAMETAGS
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class SlayerSpawnHiders {

    private record Death(int id, double x, double y, double z, long at) {}

    private static final List<Death> deaths = new ArrayList<>();
    private static IntOpenHashSet pups = new IntOpenHashSet();
    private static boolean particleArea;
    private static int ticks;

    private SlayerSpawnHiders() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerSpawnHiders").section("Combat").when(() -> cfg().hideSlayerSpawnParticles || cfg().hideSvenPupNametags)
                .tick(SlayerSpawnHiders::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Hide Slayer Spawn Particles", () -> c.hideSlayerSpawnParticles, v -> c.hideSlayerSpawnParticles = v)
                            .desc("With a slayer quest in its area: drops spawn particles within 5 blocks of mobs that just died")
                            .tags("slayer", "particles", "spawn", "lag");
                    b.toggle("  White (Enchant)", () -> c.hideSlayerParticlesEnchant, v -> c.hideSlayerParticlesEnchant = v);
                    b.toggle("  Purple (Witch)", () -> c.hideSlayerParticlesWitch, v -> c.hideSlayerParticlesWitch = v);
                    b.toggle("  Slayer Specific (Effect)", () -> c.hideSlayerParticlesEffect, v -> c.hideSlayerParticlesEffect = v);
                    b.toggle("Hide Sven Pup Nametags", () -> c.hideSvenPupNametags, v -> c.hideSvenPupNametags = v)
                            .tags("sven", "pup", "nametag", "wolf", "slayer");
                });
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        deaths.removeIf(d -> now - d.at > 3000);
        particleArea = c.hideSlayerSpawnParticles && SlayerItemsOnGround.inSlayerArea();
        if (particleArea) {
            for (LivingEntity e : EntityCache.byType(LivingEntity.class)) {
                if (e instanceof ArmorStand || e instanceof Player || e.getHealth() > 0) continue;
                if (known(e.getId())) continue;
                deaths.add(new Death(e.getId(), e.getX(), e.getY(), e.getZ(), now));
            }
        } else {
            deaths.clear();
        }

        if (++ticks % 10 != 0) return;
        IntOpenHashSet next = new IntOpenHashSet();
        if (c.hideSvenPupNametags && SkyblockUtils.isInSkyblock()) {
            for (ArmorStand s : EntityCache.namedArmorStands()) {
                if (s.getName().getString().contains("Sven Pup")) next.add(s.getId());
            }
        }
        pups = next;
    }

    private static boolean known(int id) {
        for (int i = 0, n = deaths.size(); i < n; i++) if (deaths.get(i).id == id) return true;
        return false;
    }

    /** From EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !pups.isEmpty() && pups.contains(e.getId())
                && cfg().hideSvenPupNametags && cfg().section("Combat");
    }

    /** Main thread, from ClientPlayNetworkHandlerMixin. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        if (!particleArea || deaths.isEmpty()) return false;
        TeslaMapsConfig c = cfg();
        if (!c.hideSlayerSpawnParticles || !c.section("Combat")) return false;
        ParticleType<?> type = p.getParticle().getType();
        boolean match = type == ParticleTypes.ENCHANT && c.hideSlayerParticlesEnchant
                || type == ParticleTypes.WITCH && c.hideSlayerParticlesWitch
                || type == ParticleTypes.ENTITY_EFFECT && c.hideSlayerParticlesEffect;
        if (!match) return false;
        for (int i = 0, n = deaths.size(); i < n; i++) {
            Death d = deaths.get(i);
            double dx = d.x - p.getX(), dy = d.y - p.getY(), dz = d.z - p.getZ();
            if (dx * dx + dy * dy + dz * dz < 25) return true;
        }
        return false;
    }
}
