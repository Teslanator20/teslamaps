package com.teslamaps.features.mobvisuals;

// Ported from Skysoft's LotumHelper.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/** Lotum confirmed by its nametag, and a line back to the Lotum you clicked. */
public final class LotumHelper {

    private static final double NAMETAG_RANGE = 6.0;
    private static final double HORIZONTAL_RANGE = 1.5;
    private static final int COLOR = 0xFF55FF55;

    private static final List<ArmorStand> clicked = new ArrayList<>();

    private LotumHelper() {}

    public static void register() {
        Feature.of("LotumLine").section("Mob Visuals")
                .when(() -> TeslaMapsConfig.get().mobVisualLotumLine && MobVisuals.active())
                .tickAlways(LotumHelper::tick)
                .world(LotumHelper::render);
    }

    private static boolean lineOn() {
        return TeslaMapsConfig.get().mobVisualLotumLine && MobVisuals.active() && MobArea.LOTUS_ATOLL.isHere();
    }

    /** Frog with a Lotum nametag above it (within 6 blocks, 1.5 sideways) that points back at it. */
    static boolean confirmed(Frog frog) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        ArmorStand tag = nearestTag(mc, frog);
        return tag != null && nearestFrog(mc, tag) == frog;
    }

    private static boolean isLotumTag(Entity e) {
        return e instanceof ArmorStand s && e.isAlive() && s.getCustomName() != null
                && StringUtil.strip(s.getCustomName().getString()).contains("Lotum");
    }

    private static ArmorStand nearestTag(Minecraft mc, Entity near) {
        ArmorStand best = null;
        double bestD = NAMETAG_RANGE * NAMETAG_RANGE;
        for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, near.getBoundingBox().inflate(NAMETAG_RANGE))) {
            if (!isLotumTag(s)) continue;
            double d = s.distanceToSqr(near);
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private static Frog nearestFrog(Minecraft mc, ArmorStand tag) {
        Frog best = null;
        double bestD = Double.MAX_VALUE;
        for (Frog f : mc.level.getEntitiesOfClass(Frog.class, tag.getBoundingBox().inflate(NAMETAG_RANGE))) {
            if (!f.isAlive() || f.getY() >= tag.getY()) continue;
            double d = f.distanceToSqr(tag);
            if (d > NAMETAG_RANGE * NAMETAG_RANGE) continue;
            double dx = f.getX() - tag.getX(), dz = f.getZ() - tag.getZ();
            if (Math.sqrt(dx * dx + dz * dz) >= HORIZONTAL_RANGE) continue;
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }

    /** Called on every left or right click on an entity. */
    public static void onClick(Entity entity) {
        if (!lineOn() || entity == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ArmorStand tag = isLotumTag(entity) ? (ArmorStand) entity : nearestTag(mc, entity);
        if (tag != null && !clicked.contains(tag)) clicked.add(tag);
    }

    private static ArmorStand target;

    private static void tick() {
        target = null;
        if (!lineOn()) {
            clicked.clear();
            return;
        }
        clicked.removeIf(s -> s.isRemoved() || !isLotumTag(s));
        Minecraft mc = Minecraft.getInstance();
        if (clicked.isEmpty() || mc.player == null) return;
        double bestD = Double.MAX_VALUE;
        for (ArmorStand s : clicked) {
            if (!mc.player.hasLineOfSight(s)) continue;
            double d = s.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                target = s;
            }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        ArmorStand t = target;
        if (t == null || t.isRemoved()) return;
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        ESPRenderer.drawTracerFromCamera(pose, t.getPosition(partial), COLOR, cam);
    }
}
