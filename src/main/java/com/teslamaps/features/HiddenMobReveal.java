package com.teslamaps.features;

// After NoammAddons' HiddenMobs.kt, render-side so the entity itself stays invisible for the ESP checks
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Giant;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Fels, Shadow Assassins and stealthy blood mobs render with their real model instead of staying invisible. */
public final class HiddenMobReveal {

    private static final Set<String> BLOOD_MOBS = Set.of(
            "Putrid", "Reaper", "Vader", "Frost", "Cannibal", "Revoker", "Tear", "Mr. Dead", "Skull",
            "Walker", "Psycho", "Ooze", "Freak", "Flamer", "Mute", "Leech", "Parasite", "Bonzo", "Scarf");

    private static Feature feature;
    private static Set<Entity> revealed = Set.of();
    private static boolean any;
    private static int ticks;

    private HiddenMobReveal() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HiddenMobReveal").section("ESP").legit().when(() -> cfg().revealHiddenMobs)
                .tick(HiddenMobReveal::tick)
                .config("ESP", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Hidden Mobs");
                    b.toggle("Reveal Hidden Mobs", () -> c.revealHiddenMobs, v -> c.revealHiddenMobs = v).cheat()
                            .desc("Dungeons: invisible mobs render with their real model, so you see pose and facing")
                            .tags("invisible", "fel", "dinnerbone", "shadow assassin", "stealthy", "watcher");
                    b.toggle("  Fels", () -> c.revealHiddenFels, v -> c.revealHiddenFels = v);
                    b.toggle("  Shadow Assassins", () -> c.revealHiddenShadowAssassins, v -> c.revealHiddenShadowAssassins = v);
                    b.toggle("  Stealthy Blood Mobs & Giants", () -> c.revealHiddenStealthy, v -> c.revealHiddenStealthy = v);
                });
    }

    private static void tick() {
        if (ticks++ % 5 != 0) return;
        if (LegitMode.blocksCheats() || !DungeonManager.isInDungeon()) {
            revealed = Set.of();
            any = false;
            return;
        }
        TeslaMapsConfig c = cfg();
        Set<Entity> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity e : EntityCache.all()) {
            if (!(e instanceof LivingEntity) || !e.isInvisible() || e.isRemoved()) continue;
            if (matches(c, e)) out.add(e);
        }
        revealed = out;
        any = !out.isEmpty();
    }

    private static boolean matches(TeslaMapsConfig c, Entity e) {
        if (e instanceof Enderman) return c.revealHiddenFels && "Dinnerbone".equals(e.getName().getString().trim());
        if (e instanceof Giant g) return c.revealHiddenStealthy && !g.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        if (!(e instanceof AbstractClientPlayer)) return false;
        String name = e.getName().getString().trim();
        if (c.revealHiddenShadowAssassins && name.contains("Shadow Assassin")) return true;
        return c.revealHiddenStealthy && BLOOD_MOBS.contains(name);
    }

    public static void onExtract(LivingEntity entity, LivingEntityRenderState state) {
        if (!any || !revealed.contains(entity)) return;
        if (feature == null || !feature.active()) {
            any = false;
            revealed = Set.of();
            return;
        }
        state.isInvisible = false;
        state.isInvisibleToPlayer = false;
    }
}
