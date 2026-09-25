package com.teslamaps.slayer;

// Demon attunement from Skyblocker's AttunementColors, boss line from SkyHanni's LineToVoidgloomSeraph,
// cocoons + tracer filter from SkyHanni's SlayerMiniBossFeatures
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SlayerMarks {

    private static final String LARVA = "4ceb0ed8fc2272b3d3d820676d52a38e7b2e8da8c687a233e0dabaa16c0e96df";
    private static final int IMMUNE = 0xFF5555;

    private static final Map<Entity, Integer> demons = new IdentityHashMap<>();
    private static boolean bossImmune;
    private static final Set<Entity> tracerOk = new HashSet<>();
    private static final List<ArmorStand> cocoons = new ArrayList<>();
    private static final List<Vec3> miniPos = new ArrayList<>();
    private static final List<Long> miniSeen = new ArrayList<>();
    private static int ticks;

    private SlayerMarks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerMarks").section("Combat").legit()
                .when(() -> cfg().slayerAttunementDemons || cfg().slayerBossTracer || cfg().slayerMinibossCocoon
                        || (cfg().slayerMinibossTracer && (cfg().slayerMinibossTracerRange > 0 || cfg().slayerMinibossTracerSight)))
                .tick(SlayerMarks::tick)
                .world(SlayerMarks::render);
    }

    private static boolean demonlord() {
        String b = SlayerQuest.boss();
        return b != null && b.contains("Demonlord");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        tickTracerFilter(mc, c);
        tickCocoons(mc, c);
        if (++ticks % 5 != 0) return;
        demons.clear();
        bossImmune = false;
        if (!c.slayerAttunementDemons || !demonlord()) return;
        Entity boss = SlayerHUD.getBossEntity();
        if (boss != null) bossImmune = attunement(boss) == IMMUNE;
        AABB box = mc.player.getBoundingBox().inflate(30, 10, 30);
        for (Entity e : mc.level.getEntities((Entity) null, box,
                x -> x instanceof Blaze || x instanceof WitherSkeleton || x instanceof ZombifiedPiglin)) {
            if (e == boss || !e.isAlive()) continue;
            int col = attunement(e);
            if (col != -1) demons.put(e, col);
        }
    }

    private static int attunement(Entity e) {
        for (ArmorStand s : NametagMobs.namedStands(e, 2.5)) {
            String n = s.getName().getString();
            if (n.contains("IMMUNE")) return IMMUNE;
            if (n.contains("ASHEN")) return 0x555555;
            if (n.contains("SPIRIT")) return 0xFFFFFF;
            if (n.contains("CRYSTAL")) return 0x55FFFF;
            if (n.contains("AURIC")) return 0xFFFF55;
        }
        return -1;
    }

    private static void tickTracerFilter(Minecraft mc, TeslaMapsConfig c) {
        tracerOk.clear();
        if (!c.slayerMinibossTracer) return;
        double r = c.slayerMinibossTracerRange;
        for (Entity e : SlayerHUD.getMinibossEntities()) {
            if (visible(mc, e, r, c.slayerMinibossTracerSight)) tracerOk.add(e);
        }
        for (ArmorStand s : cocoons) {
            if (visible(mc, s, r, c.slayerMinibossTracerSight)) tracerOk.add(s);
        }
    }

    private static boolean visible(Minecraft mc, Entity e, double range, boolean sight) {
        if (e == null || !e.isAlive()) return false;
        if (range > 0 && e.distanceToSqr(mc.player) > range * range) return false;
        return !sight || mc.player.hasLineOfSight(e);
    }

    private static void tickCocoons(Minecraft mc, TeslaMapsConfig c) {
        if (!c.slayerMinibossCocoon) {
            cocoons.clear();
            miniPos.clear();
            miniSeen.clear();
            return;
        }
        long now = System.currentTimeMillis();
        for (Entity e : SlayerHUD.getMinibossEntities()) {
            miniPos.add(e.position());
            miniSeen.add(now);
        }
        for (int i = miniSeen.size() - 1; i >= 0; i--) {
            if (now - miniSeen.get(i) > 3000) {
                miniSeen.remove(i);
                miniPos.remove(i);
            }
        }
        cocoons.removeIf(s -> !s.isAlive());
        if (ticks % 5 != 0 || miniPos.isEmpty()) return;
        for (ArmorStand s : EntityCache.armorStands()) {
            if (cocoons.contains(s) || s.distanceToSqr(mc.player) > 40 * 40) continue;
            String url = SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD));
            if (!LARVA.equals(url)) continue;
            Vec3 p = s.position();
            for (Vec3 m : miniPos) {
                if (m.distanceToSqr(p) < 4.0) {
                    cocoons.add(s);
                    break;
                }
            }
        }
    }

    /** Glow/box colour override for the boss and demons, -1 without one. */
    public static int colorOf(Entity e) {
        if (!cfg().slayerAttunementDemons) return -1;
        if (e == SlayerHUD.getBossEntity()) return bossImmune ? IMMUNE : -1;
        Integer c = demons.get(e);
        return c == null ? -1 : c;
    }

    public static boolean isDemon(Entity e) {
        return !demons.isEmpty() && cfg().slayerAttunementDemons && cfg().slayerBossESP && demons.containsKey(e);
    }

    public static boolean hasDemons() {
        return !demons.isEmpty();
    }

    /** Miniboss tracer filter (range / line of sight), true when unfiltered. */
    public static boolean tracerAllowed(Entity e) {
        TeslaMapsConfig c = cfg();
        if (c.slayerMinibossTracerRange <= 0 && !c.slayerMinibossTracerSight) return true;
        return tracerOk.contains(e);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (LegitMode.blocksCheats()) return;
        TeslaMapsConfig c = cfg();
        if (c.slayerBossESP && c.slayerAttunementDemons) {
            for (Map.Entry<Entity, Integer> d : demons.entrySet()) {
                Entity e = d.getKey();
                if (!e.isAlive()) continue;
                ESPRenderer.drawBoxOutline(pose, ESPRenderer.lerpBox(e), d.getValue() | 0xFF000000, 2.0f, cam, true);
            }
        }
        Entity boss = SlayerHUD.getBossEntity();
        if (c.slayerBossESP && c.slayerBossTracer && boss != null && boss.isAlive()) {
            ESPRenderer.drawTracerFromCamera(pose, ESPRenderer.lerpBox(boss).getCenter(),
                    SlayerHUD.getGlowColor(boss) | 0xFF000000, cam, true, 2.0f);
        }
        if (c.slayerMinibossCocoon && !cocoons.isEmpty()) {
            for (ArmorStand s : cocoons) {
                if (!s.isAlive()) continue;
                Vec3 p = s.getPosition(1f);
                AABB head = new AABB(p.x - 0.4, p.y + 1.3, p.z - 0.4, p.x + 0.4, p.y + 2.1, p.z + 0.4);
                ESPRenderer.drawBoxOutline(pose, head, 0xFF55FFFF, 2.0f, cam, true);
                if (c.slayerMinibossTracer && tracerAllowed(s)) {
                    ESPRenderer.drawTracerFromCamera(pose, head.getCenter(), 0xFF55FFFF, cam, true,
                            c.slayerMinibossTracerWidth);
                }
            }
        }
    }
}
