// Ported from ADM's p3sim/Bows.kt
package com.teslamaps.adm.sim;

import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SimArrow extends Arrow {
    private final BiConsumer<SimArrow, HitResult> whenHit;

    public Predicate<Entity> immune = e -> false;
    private int removeAt = -1;

    public SimArrow(Level level, double x, double y, double z, BiConsumer<SimArrow, HitResult> whenHit) {
        super(level, x, y, z, new ItemStack(Items.ARROW), null);
        this.whenHit = whenHit;
    }

    @Override
    public void tick() {
        if (removeAt >= 0 && tickCount >= removeAt) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return removeAt < 0 && entity.isAlive()
                && ((entity instanceof WitherBoss && entity.entityTags().contains(Sim.TAG))
                || (entity instanceof LivingEntity && (DamageModule.mobFor(entity) != null || DamageModule.isSecretBat(entity))));
    }

    @Override
    protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
        return ProjectileUtil.getManyEntityHitResult(level(), this, from, to, getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0),
                this::canHitEntity, 0.3f, ClipContext.Block.COLLIDER, false);
    }

    @Override
    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hitResult) {
        if (removeAt >= 0) return ProjectileDeflection.NONE;
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            setNoGravity(true);
            if (immune.test(((EntityHitResult) hitResult).getEntity())) {
                double speed = 0.29 + KRandom.DEFAULT.nextDouble() * 0.03;
                double yaw = Math.toRadians(getYRot() + (KRandom.DEFAULT.nextBoolean() ? 172.0 : -172.0));
                setDeltaMovement(new Vec3(Math.sin(yaw) * speed, 0.0, Math.cos(yaw) * speed));
                setYRot((float) Math.toDegrees(yaw));
                yRotO = getYRot();
                removeAt = tickCount + 9 + KRandom.DEFAULT.nextInt(2);
                return ProjectileDeflection.NONE;
            }
            Ec.safely("p3sim arrow hit", () -> whenHit.accept(this, hitResult));
            setDeltaMovement(Vec3.ZERO);
            removeAt = tickCount + 1 + KRandom.DEFAULT.nextInt(2);
            return ProjectileDeflection.NONE;
        }
        ProjectileDeflection d = super.hitTargetOrDeflectSelf(hitResult);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            Ec.safely("p3sim arrow hit", () -> whenHit.accept(this, hitResult));
            discard();
        }
        return d;
    }
}
