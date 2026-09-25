package com.teslamaps.features.mining;

// After SkyHanni's GoblinRaidFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The Superprotectron during a Goblin Raid in the Dwarven Mines. */
public final class SuperprotectronHighlight {

    private static List<LivingEntity> found = List.of();
    private static int ticks;

    private SuperprotectronHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SuperprotectronHighlight").section("Mining").when(() -> cfg().superprotectronHighlight)
                .tick(SuperprotectronHighlight::tick)
                .world(SuperprotectronHighlight::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Superprotectron Highlight", () -> c.superprotectronHighlight, v -> c.superprotectronHighlight = v)
                            .desc("Goblin Raid: highlights the Superprotectron in the Dwarven Mines.")
                            .tags("goblin raid", "superprotectron", "dwarven", "event", "esp");
                    b.espStyle("Superprotectron", () -> c.superprotectronEsp);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInArea("Dwarven Mines")) {
            found = List.of();
            return;
        }
        if (++ticks % 10 == 0) {
            List<LivingEntity> out = new ArrayList<>(1);
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) continue;
                if (!e.getName().getString().contains("Superprotectron")) continue;
                if (e instanceof ArmorStand) {
                    LivingEntity owner = below(mc, e);
                    if (owner != null && !out.contains(owner)) out.add(owner);
                } else if (!out.contains(le)) {
                    out.add(le);
                }
            }
            found = out;
        }
        EspStyle style = EspStyle.of(cfg().superprotectronEsp);
        for (LivingEntity e : found) if (e.isAlive()) style.tickEntity(e);
    }

    private static LivingEntity below(Minecraft mc, Entity tag) {
        LivingEntity best = null;
        double bestD = 9;
        for (Entity e : mc.level.getEntities(tag, tag.getBoundingBox().inflate(1.5, 4, 1.5))) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || e == mc.player || !le.isAlive()) continue;
            if (e.getY() > tag.getY() + 0.5) continue;
            double d = e.distanceToSqr(tag.getX(), e.getY(), tag.getZ()) + Math.abs(tag.getY() - e.getY());
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (found.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().superprotectronEsp);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (int i = 0, n = found.size(); i < n; i++) {
            LivingEntity e = found.get(i);
            if (e.isAlive() && !e.isRemoved()) style.drawEntity(pose, e, cam, pt);
        }
    }
}
