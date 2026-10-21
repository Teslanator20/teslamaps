package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapArrow.kt

import com.teslamaps.adm.sim.util.KRandom;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An arrow from a trap dispenser: vanilla flight, hits only a human player (1.8's 0.3 margin) and blocks, gone at the first hit or after
 * maxTicks. A player it hits goes to onHitPlayer. Never saved, never picked up.
 */
public class TrapArrow extends Arrow {
    private final int maxTicks;
    private final Predicate<Entity> isHuman;
    private final Consumer<ServerPlayer> onHitPlayer;
    private int removeAt = -1;

    public TrapArrow(Level level, double x, double y, double z, int maxTicks, Predicate<Entity> isHuman, Consumer<ServerPlayer> onHitPlayer) {
        super(level, x, y, z, new ItemStack(Items.ARROW), null);
        this.maxTicks = maxTicks;
        this.isHuman = isHuman;
        this.onHitPlayer = onHitPlayer;
        pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    @Override public boolean shouldBeSaved() { return false; }

    @Override
    public void tick() {
        if (removeAt >= 0 && tickCount >= removeAt) { discard(); return; }
        if (tickCount > maxTicks) { discard(); return; }
        super.tick();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return removeAt < 0 && entity instanceof ServerPlayer && entity.isAlive() && isHuman.test(entity);
    }

    @Override
    protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
        // 26.1.2's overload has no trailing boolean (26.3 added one)
        return ProjectileUtil.getManyEntityHitResult(level(), this, from, to, getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0),
                this::canHitEntity, 0.3f, ClipContext.Block.COLLIDER, false);
    }

    @Override
    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hitResult) {
        if (removeAt >= 0) return ProjectileDeflection.NONE;
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            if (!(((EntityHitResult) hitResult).getEntity() instanceof ServerPlayer p)) return ProjectileDeflection.NONE;
            float pitch = Mth.floor(1.2f / (KRandom.DEFAULT.nextFloat() * 0.2f + 0.9f) * 63f) / 63f;
            level().playSound((Entity) null, getX(), getY(), getZ(), SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1f, pitch);
            onHitPlayer.accept(p);
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            removeAt = tickCount + 1;
            return ProjectileDeflection.NONE;
        }
        ProjectileDeflection d = super.hitTargetOrDeflectSelf(hitResult);
        if (hitResult.getType() == HitResult.Type.BLOCK) discard();
        return d;
    }
}
