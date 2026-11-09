// Ported from ADM's p3sim/clear/mobs/MobWorld.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.mojang.authlib.GameProfile;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.run.RunBlocks;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Collection;
import java.util.UUID;

public final class RealStage implements MobStage {
    public static final RealStage INSTANCE = new RealStage();

    private RealStage() {}

    private static ServerLevel level() { return RunBlocks.level(); }

    @Override public boolean available() { return level() != null; }

    @Override public boolean isLoaded(int x, int z) { return RunBlocks.isLoaded(x, z); }

    @Override
    public boolean materialise(MobEntry e) {
        ServerLevel lv = level();
        if (lv == null) return false;
        return MobWorld.materialise(e, lv, MobsModule.fakePlayersOn(), MobsModule.healthDataOn());
    }

    @Override
    public boolean isGone(MobEntry e) {
        Entity b = e.body;
        if (b == null) return true;
        if (b.isRemoved() || (e.stand != null && e.stand.isRemoved())) return true;
        ServerLevel lv = level();
        if (lv == null) return false;
        if (lv.getGameTime() - e.builtGameTime < MobWorld.LOOKUP_GRACE) return false;
        return lv.getEntity(b.getId()) != b && RunBlocks.isLoaded((int) Math.floor(b.getX()), (int) Math.floor(b.getZ()));
    }

    @Override public void release(MobEntry e) { MobWorld.dematerialise(e); }

    @Override
    public void die(MobEntry e) {
        ServerLevel lv = level();
        if (lv != null) MobWorld.die(e, lv); else MobWorld.dematerialise(e);
    }

    @Override public void retract(MobEntry e) { MobWorld.retract(e); }

    @Override
    public void announceTo(UUID playerId, Collection<MobEntry> entries) {
        ServerPlayer p = null;
        for (ServerPlayer it : Sim.players()) if (it.getUUID().equals(playerId)) { p = it; break; }
        if (p == null) return;
        for (MobEntry e : entries) {
            GameProfile prof = e.profile;
            if (e.announced && prof != null) Disguise.announceTo(p, prof);
        }
    }

    @Override public void wake(MobEntry e) { MobWorld.wake(e); }

    @Override public void reveal(MobEntry e) { MobWorld.reveal(e); }

    @Override
    public void refresh(MobEntry e) {
        MobWorld.updateTag(e);
        if (e.body instanceof LivingEntity le) { if (MobsModule.healthDataOn()) MobBodies.setHealthData(le, e.record.hp); }
    }

    @Override
    public void hurtFlash(MobEntry e) {
        Entity b = e.body;
        if (b == null) return;
        ServerLevel lv = level();
        if (lv != null) lv.getChunkSource().sendToTrackingPlayers(b, new ClientboundHurtAnimationPacket(b.getId(), 0f));
    }

    @Override
    public void tick() {
        ServerLevel lv = level();
        if (lv != null) MobWorld.tick(lv);
    }

    @Override public void flush() { MobWorld.flushDying(); }

    @Override
    public double[] position(MobEntry e) {
        Entity b = e.body;
        if (b == null) return null;
        MobWorld.follow(e);
        return new double[]{b.getX(), b.getY(), b.getZ()};
    }

    @Override
    public boolean canSee(MobEntry e, double x, double y, double z) {
        Entity b = e.body;
        if (b == null) return false;
        ServerLevel lv = level();
        if (lv == null) return false;
        Vec3 from = new Vec3(b.getX(), b.getY() + b.getEyeHeight(), b.getZ());
        BlockHitResult hit = lv.clip(new ClipContext(from, new Vec3(x, y, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    @Override
    public void place(MobEntry e, double x, double y, double z) {
        Entity body = e.body;
        if (body == null) return;
        double dx = x - body.getX(), dz = z - body.getZ();
        if (dx * dx + (y - body.getY()) * (y - body.getY()) + dz * dz < 1e-6) return;
        stop(body);
        float yaw = dx * dx + dz * dz > 1e-4 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : body.getYRot();
        body.snapTo(x, y, z, yaw, body.getXRot());
        if (body instanceof LivingEntity le) { le.yHeadRot = yaw; le.yBodyRot = yaw; }
    }

    @Override
    public void rescue(MobEntry e) {
        Entity it = e.body;
        if (it != null && it.getY() < -8.0) it.snapTo(e.plan.x, e.plan.y, e.plan.z, e.plan.yaw, 0f);
    }

    private static final double BPT_PER_ATTRIBUTE = 2.159;

    static double mobSpeedFor(double bpt) { return Math.sqrt(Math.max(bpt, 0.0) / BPT_PER_ATTRIBUTE); }

    @Override
    public void act(MobEntry e, AiAction action, double targetY, double targetX, double targetZ) {
        Entity body = e.body;
        if (body == null) return;
        if (action == AiAction.Idle.INSTANCE) stop(body);
        else if (action instanceof AiAction.MoveTo mt) move(e, body, mt.x, e.ai.chasing && !Double.isNaN(targetY) ? targetY : body.getY(), mt.z, mt.speed);
        else if (action == AiAction.Melee.INSTANCE || action == AiAction.Shoot.INSTANCE) { stop(body); faceAndSwing(body, targetX, targetZ); }
        else if (action instanceof AiAction.Teleport tp) body.snapTo(tp.x, Double.isNaN(targetY) ? body.getY() : targetY, tp.z, body.getYRot(), 0f);
    }

    private static void faceAndSwing(Entity body, double tx, double tz) {
        if (!Double.isNaN(tx)) {
            float yaw = (float) Math.toDegrees(Math.atan2(-(tx - body.getX()), tz - body.getZ()));
            body.setYRot(yaw);
            if (body instanceof LivingEntity le) { le.yHeadRot = yaw; le.yBodyRot = yaw; }
        }
        if (body instanceof Mob m) m.swing(InteractionHand.MAIN_HAND, m.getMainHandItem().getAttackAnimation(), false);
        else if (body instanceof LivingEntity le) le.swing(InteractionHand.MAIN_HAND, le.getMainHandItem().getAttackAnimation(), true);
    }

    private static void stop(Entity body) {
        if (body instanceof Mob m) {
            if (!m.getNavigation().isDone()) m.getNavigation().stop();
        } else if (body instanceof AdsDisguise d) {
            d.zza = 0f;
            d.setSpeed(0f);
        }
    }

    private static void move(MobEntry e, Entity body, double x, double y, double z, double speed) {
        if (body instanceof Mob m) {
            if (e.navCooldown > 0) e.navCooldown--;
            boolean moved = Double.isNaN(e.lastNavX) || Math.abs(e.lastNavX - x) + Math.abs(e.lastNavZ - z) > 1.0;
            if (e.navCooldown == 0 && (moved || m.getNavigation().isDone())) {
                double attr = Math.max(m.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.01);
                double mod = Math.clamp(mobSpeedFor(speed) / attr, 0.1, 6.0);
                m.getNavigation().moveTo(x, y, z, mod);
                e.navCooldown = 8; e.lastNavX = x; e.lastNavZ = z;
            }
        } else if (body instanceof AdsDisguise d) {
            float yaw = (float) Math.toDegrees(Math.atan2(-(x - d.getX()), z - d.getZ()));
            d.setYRot(yaw); d.yHeadRot = yaw; d.yBodyRot = yaw;
            d.setSpeed((float) (speed / BPT_PER_ATTRIBUTE));
            d.zza = 1f;
            d.setJumping(d.horizontalCollision);
        }
    }
}
