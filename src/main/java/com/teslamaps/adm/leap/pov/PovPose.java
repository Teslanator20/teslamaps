package com.teslamaps.adm.leap.pov;

import com.teslamaps.adm.leap.mixin.AdmLivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

// Ported from ADM's pov/PovPose.kt
/** Where a teammate's eyes are for a POV pass; rewrites the entity's lerp fields and hands back the undo. */
public final class PovPose {

    private PovPose() {}

    public enum Mode { VANILLA, RAW, CUSTOM }

    public interface Restore { void restore(); }

    private static final Restore NONE = () -> {};
    private static final PovInterpolator interpolator = new PovInterpolator();
    private static final long TICK_NANOS = 50_000_000L;

    public static Restore begin(LivingEntity entity, Mode mode, int ticks) {
        return switch (mode) {
            case VANILLA -> NONE;
            case RAW -> apply(entity, packetTarget(entity));
            case CUSTOM -> {
                if (ticks <= 0) yield apply(entity, packetTarget(entity));
                long at = System.nanoTime() - ticks * TICK_NANOS;
                PovInterpolator.Pose p = interpolator.poseAt(entity.getUUID(), at);
                yield apply(entity, p != null ? p : packetTarget(entity));
            }
        };
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { interpolator.clear(); return; }
        long now = System.nanoTime();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            interpolator.sample(player.getUUID(), now, packetTarget(player));
        }
        interpolator.evict(now);
    }

    public static PovInterpolator.Sample lastSeen(UUID id) { return interpolator.newest(id); }

    public static void reset() { interpolator.clear(); }

    private static PovInterpolator.Pose packetTarget(LivingEntity entity) {
        InterpolationHandler h = entity.getInterpolation();
        boolean active = h != null && h.hasActiveInterpolation();
        Vec3 pos = active ? h.position() : entity.position();
        float pitch = active ? h.xRot() : entity.getXRot();
        AdmLivingEntityAccessor acc = (AdmLivingEntityAccessor) entity;
        float headYaw = acc.adm$getLerpHeadSteps() > 0 ? (float) acc.adm$getLerpYHeadRot() : entity.yHeadRot;
        return new PovInterpolator.Pose(pos.x, pos.y, pos.z, headYaw, pitch);
    }

    private static Restore apply(LivingEntity e, PovInterpolator.Pose p) {
        Vec3 oldPos = e.position();
        double oldXo = e.xo, oldYo = e.yo, oldZo = e.zo;
        float oldXRot = e.getXRot(), oldXRotO = e.xRotO, oldHead = e.yHeadRot, oldHeadO = e.yHeadRotO;
        e.setPos(p.x(), p.y(), p.z());
        e.xo = p.x(); e.yo = p.y(); e.zo = p.z();
        e.setXRot(p.pitch());
        e.xRotO = p.pitch();
        e.yHeadRot = p.headYaw();
        e.yHeadRotO = p.headYaw();
        return () -> {
            e.setPos(oldPos.x, oldPos.y, oldPos.z);
            e.xo = oldXo; e.yo = oldYo; e.zo = oldZo;
            e.setXRot(oldXRot);
            e.xRotO = oldXRotO;
            e.yHeadRot = oldHead;
            e.yHeadRotO = oldHeadO;
        };
    }
}
