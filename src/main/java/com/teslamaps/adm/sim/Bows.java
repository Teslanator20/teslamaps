// Ported from ADM's p3sim/Bows.kt
package com.teslamaps.adm.sim;

import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.ModuleEvent;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Bows {
    private Bows() {}

    public static final String TERMINATOR = "TERMINATOR";
    public static final String SPIRIT = "ITEM_SPIRIT_BOW";
    public static final String MOSQUITO = "MOSQUITO_BOW";
    public static final String LAST_BREATH = "LAST_BREATH";
    public static final String EXPLOSIVE = "EXPLOSIVE_BOW";
    public static final Set<String> DRAWN = Set.of(LAST_BREATH, EXPLOSIVE);
    public static final Set<String> SHORTBOWS = Set.of(TERMINATOR, SPIRIT, MOSQUITO);

    private static final int NASTY_BITE_COOLDOWN = 10;

    private static class Aim {
        final Vec3 pos;
        final float yaw;
        final float pitch;
        final boolean crouch;

        Aim(Vec3 pos, float yaw, float pitch, boolean crouch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.crouch = crouch;
        }
    }

    private static Aim aim(ServerPlayer p) { return new Aim(p.position(), p.getYRot(), p.getXRot(), p.isShiftKeyDown()); }

    private static class Click {
        final String bow;
        final boolean left;
        final Aim aim;
        final float power;

        Click(String bow, boolean left, Aim aim) { this(bow, left, aim, 1f); }

        Click(String bow, boolean left, Aim aim, float power) {
            this.bow = bow;
            this.left = left;
            this.aim = aim;
            this.power = power;
        }
    }

    private static final PerPlayer<ArrayList<Click>> clicks = new PerPlayer<>(ArrayList::new);

    private static class Later {
        final int at;
        final Runnable run;

        Later(int at, Runnable run) {
            this.at = at;
            this.run = run;
        }
    }

    private static final PerPlayer<ArrayList<Later>> later = new PerPlayer<>(ArrayList::new);
    private static final PerPlayer<ArrayList<Aim>> history = new PerPlayer<>(ArrayList::new);

    private static final PerPlayer<Integer> shotReady = new PerPlayer<>(() -> 0);
    private static final PerPlayer<Integer> biteReady = new PerPlayer<>(() -> 0);
    private static final PerPlayer<String> pendingShot = new PerPlayer<>(() -> null);
    private static final PerPlayer<Boolean> pendingBite = new PerPlayer<>(() -> false);

    private static final PerPlayer<ArrayList<SimArrow>> live = new PerPlayer<>(ArrayList::new);

    public static void reset() {
        clicks.get().clear();
        later.get().clear();
        history.get().clear();
        shotReady.set(0);
        biteReady.set(0);
        pendingShot.set(null);
        pendingBite.set(false);
        for (SimArrow it : live.get()) if (!it.isRemoved()) it.discard();
        live.get().clear();
        Hydra.reset();
    }

    public static void start() {
        clicks.get().clear();
        later.get().clear();
        pendingShot.set(null);
        pendingBite.set(false);
        Hydra.start();
    }

    public static void click(ServerPlayer p, String bow, boolean left) {
        Aim a = aim(p);
        Fight.afterPing("shortbow", () -> {
            if (!p.isRemoved()) clicks.get().add(new Click(bow, left, a));
        });
    }

    public static void release(ServerPlayer p, String bow, float power) {
        Aim a = aim(p);
        Fight.afterPing("bow", () -> {
            if (!p.isRemoved()) clicks.get().add(new Click(bow, false, a, power));
        });
    }

    public static void tick() {
        ServerPlayer p = Sim.player();
        if (p == null) return;
        int now = Fight.serverTick;
        Hydra.tick(p, now);
        String held = SimItems.idOf(p.getMainHandItem());
        String bow0 = pendingShot.get();
        if (bow0 != null && now >= shotReady.get()) {
            pendingShot.set(null);
            if (bow0.equals(held)) fire(p, bow0, false, past(p));
        }
        if (pendingBite.get() && now >= biteReady.get()) {
            pendingBite.set(false);
            if (MOSQUITO.equals(held)) fire(p, MOSQUITO, true, past(p));
        }
        for (Click c : clicks.get()) {
            if (!SHORTBOWS.contains(c.bow)) {
                fire(p, c.bow, false, c.aim, c.power);
                continue;
            }
            if (c.bow.equals(MOSQUITO) && c.left) {
                if (now >= biteReady.get()) fire(p, MOSQUITO, true, past(p));
                else pendingBite.set(true);
            } else if (now >= shotReady.get()) fire(p, c.bow, false, past(p));
            else pendingShot.set(c.bow);
        }
        clicks.get().clear();
        ArrayList<Later> l = later.get();
        if (!l.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Later it : l) if (it.at <= now) due.add(it);
            l.removeAll(due);
            for (Later it : due) Ec.safely("p3sim bow", it.run);
        }
        ArrayList<SimArrow> lv = live.get();
        lv.removeIf(Entity::isRemoved);
        List<SimArrow> old = new ArrayList<>();
        for (SimArrow it : lv) if (it.tickCount > 100) old.add(it);
        for (SimArrow it : old) it.discard();
        ArrayList<Aim> h = history.get();
        h.add(aim(p));
        while (h.size() > 40) h.remove(0);
    }

    private static Aim past(ServerPlayer p) {
        int d = Fight.pingTicks();
        ArrayList<Aim> h = history.get();
        return d <= 0 || h.isEmpty() ? aim(p) : h.get(Math.max(h.size() - d, 0));
    }

    private static void fire(ServerPlayer p, String bow, boolean nasty, Aim a) { fire(p, bow, nasty, a, 1f); }

    private static void fire(ServerPlayer p, String bow, boolean nasty, Aim a, float power) {
        int now = Fight.serverTick;
        java.util.Random r = new java.util.Random();
        Vec3 noise = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.0075);
        double duplexRoll = KRandom.DEFAULT.nextDouble();
        int duplex = duplexRoll < 0.39 ? 3 : duplexRoll < 0.91 ? 4 : 5;
        int dupIdx = 0;
        int dupShift = KRandom.DEFAULT.nextDouble() < 42.0 / 64.0 ? 0 : KRandom.DEFAULT.nextBoolean() ? 1 : -1;
        List<ShotPlan.Planned> plan = ShotPlan.plan(bow, a.pos, a.yaw, a.pitch, a.crouch, Hydra.stacks.get(), noise, duplex, 3.0 * power);
        for (ShotPlan.Planned arrow : plan) {
            if (arrow.delay == 0) {
                Consumer<HitResult> onHit = bow.equals(EXPLOSIVE) && arrow.owned ? h -> explode(h.getLocation()) : null;
                SimArrow s = launch(arrow.from, arrow.at, arrow.v, arrow.owned ? p : null, onHit);
                if (s != null && arrow.owned && DRAWN.contains(bow) && power >= 1f) s.setCritArrow(true);
            } else {
                int delay = arrow.delay + (ShotPlan.DUPLEX.contains(bow) && dupIdx++ == 1 ? dupShift : 0);
                later.get().add(new Later(now + delay, () -> {
                    launch(arrow.from, arrow.at, arrow.v);
                    if (ShotPlan.DUPLEX.contains(bow)) sound(SoundEvents.ARROW_SHOOT, SoundSource.MASTER, 0.5f, 0.7f,
                            new Vec3(Mth.floor(arrow.at.x) + 0.5, Mth.floor(arrow.at.y) + 0.5, Mth.floor(arrow.at.z) + 0.5));
                }));
            }
        }
        sound(SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1f, 1f / (KRandom.DEFAULT.nextFloat() * 0.4f + 1.2f) + 0.5f * power);
        SimItems.quiverShot();
        if (!SHORTBOWS.contains(bow)) return;
        ItemStack stack = p.getMainHandItem();
        if (nasty) {
            biteReady.set(now + NASTY_BITE_COOLDOWN);
            p.getCooldowns().addCooldown(stack, NASTY_BITE_COOLDOWN);
        } else {
            shotReady.set(now + P3Sim.shortbowCooldown());
            p.getCooldowns().addCooldown(stack, P3Sim.shortbowCooldown());
        }
    }

    public static SimArrow launch(Vec3 from, Vec3 at, Vec3 v) { return launch(from, at, v, null, null); }

    public static SimArrow launch(Vec3 from, Vec3 at, Vec3 v, ServerPlayer owner) { return launch(from, at, v, owner, null); }

    public static SimArrow launch(Vec3 from, Vec3 at, Vec3 v, ServerPlayer owner, Consumer<HitResult> onHit) {
        ServerLevel level = Sim.level();
        if (!from.equals(at)) {
            BlockHitResult block = level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner != null ? owner : Sim.player()));
            Vec3 end = block.getType() == HitResult.Type.MISS ? at : block.getLocation();
            Pair<LivingEntity, Vec3> boss = null;
            for (LivingEntity w : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.0),
                    it -> isBoss(it) || (it.isAlive() && (DamageModule.mobFor(it) != null || DamageModule.isSecretBat(it))))) {
                if (immune(w)) continue;
                Vec3 hitAt = w.getBoundingBox().inflate(0.3).clip(from, end).orElse(null);
                if (hitAt == null) continue;
                if (boss == null || hitAt.distanceToSqr(from) < boss.second.distanceToSqr(from)) boss = Pair.of(w, hitAt);
            }
            if (boss != null) {
                hit(new EntityHitResult(boss.first, boss.second), onHit);
                return null;
            }
            if (block.getType() != HitResult.Type.MISS) {
                float pitch = Mth.floor(1.2f / (KRandom.DEFAULT.nextFloat() * 0.2f + 0.9f) * 63f) / 63f;
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1f, pitch);
                hit(block, onHit);
                return null;
            }
        }
        ServerPlayer acting = Sim.acting();
        ServerPlayer shooter = acting != null ? acting : owner;
        boolean all = Sim.toAll();
        SimArrow a = new SimArrow(level, at.x, at.y, at.z, (arrow, h) -> Sim.asPlayer(shooter, all, () -> {
            hit(h, onHit);
        }));
        a.immune = Bows::immune;
        if (owner != null) a.setOwner(owner);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.setDeltaMovement(v);
        float yRot = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        float xRot = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
        a.setYRot(yRot);
        a.setXRot(xRot);
        a.yRotO = yRot;
        a.xRotO = xRot;
        Sim.spawn(a);
        live.get().add(a);
        return a;
    }

    public static boolean immune(Entity e) { return e instanceof LivingEntity && e.getInvulnerableTime() > 10; }

    private static boolean isBoss(Entity e) { return e instanceof WitherBoss && e.isAlive() && e.entityTags().contains(Sim.TAG); }

    private static void hit(HitResult h, Consumer<HitResult> onHit) {
        if (h instanceof BlockHitResult bh) {
            if (Fight.phase instanceof GoldorPhase g) g.devices.target.hit(bh.getBlockPos());
            Run r = DamageModule.activeRun();
            if (r != null) {
                BlockPos b = bh.getBlockPos();
                ServerPlayer by = Sim.acting();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", b.getX());
                m.put("y", b.getY());
                m.put("z", b.getZ());
                m.put("by", by == null ? null : by.getUUID());
                r.bus.post(new ModuleEvent(r.t(), "damage", "arrowHitBlock", m));
            }
        } else if (h instanceof EntityHitResult eh) {
            Hydra.hit(Fight.serverTick);
            DamageModule.arrowHit(eh.getEntity(), Sim.acting());
            Run r = DamageModule.activeRun();
            if (r != null) {
                ServerPlayer s = Sim.acting();
                if (s != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", eh.getEntity().getId());
                    m.put("by", s.getUUID());
                    r.bus.post(new ModuleEvent(r.t(), "damage", "arrowHitEntity", m));
                }
            }
            sound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.NEUTRAL, 1f, 0.8f);
        }
        if (onHit != null) onHit.accept(h);
    }

    public static void explode(Vec3 at) {
        Sim.sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.8f + KRandom.DEFAULT.nextFloat() * 0.18f, at, SoundSource.MASTER);
        Sim.level().sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (Fight.phase instanceof GoldorPhase g) {
            int gate = g.gateNear(at, 2.0);
            if (gate > 0) g.blowGate(gate, Sim.me());
        }
        ServerPlayer by = Sim.acting();
        DamageModule.blast(at.x, at.y, at.z, DamageSource.BOW, by == null ? null : by.getUUID());
    }

    private static void sound(SoundEvent s, SoundSource source, float volume, float pitch) { sound(s, source, volume, pitch, null); }

    private static void sound(SoundEvent s, SoundSource source, float volume, float pitch, Vec3 at) {
        ServerPlayer p = Sim.player();
        if (p == null) return;
        Sim.level().playSound(null, at != null ? at.x : p.getX(), at != null ? at.y : p.getY(), at != null ? at.z : p.getZ(),
                s, source, volume, Mth.floor(pitch * 63f) / 63f);
    }

    public static final class Hydra {
        private Hydra() {}

        public static final PerPlayer<Integer> stacks = new PerPlayer<>(() -> 0);
        private static final PerPlayer<Integer> lastGain = new PerPlayer<>(() -> -100);
        private static final PerPlayer<Integer> nextLoss = new PerPlayer<>(() -> Integer.MAX_VALUE);
        private static final PerPlayer<Integer> period = new PerPlayer<>(() -> 0);
        private static final PerPlayer<Integer> grid = new PerPlayer<>(() -> KRandom.DEFAULT.nextInt(20));
        private static final PerPlayer<Integer> shown = new PerPlayer<>(() -> 0);

        private static int pieces() { return P3Sim.terrorPieces(); }

        private static int periodNow() {
            int pieces = pieces();
            return ((pieces >= 4 ? 10 : pieces == 3 ? 7 : 4) + 1) * 20;
        }

        public static void reset() {
            stacks.set(0);
            lastGain.set(-100);
            nextLoss.set(Integer.MAX_VALUE);
            shown.set(0);
            grid.set(KRandom.DEFAULT.nextInt(20));
            start();
        }

        public static void start() {
            stacks.set(pieces() > 0 ? P3Sim.hydraStart() : 0);
            period.set(periodNow());
            nextLoss.set(Fight.serverTick + period.get());
        }

        public static void hit(int now) {
            if (pieces() == 0 || now - lastGain.get() < 4) return;
            lastGain.set(now);
            period.set(periodNow());
            nextLoss.set(now + period.get());
            if (stacks.get() < 10) stacks.set(stacks.get() + 1);
        }

        public static void tick(ServerPlayer p, int now) {
            if (pieces() == 0) stacks.set(0);
            int phase = Math.floorMod(now - grid.get(), 20);
            if (pieces() > 0 && stacks.get() > 0 && now >= nextLoss.get()) {
                stacks.set(stacks.get() - 1);
                nextLoss.set(nextLoss.get() + period.get());
            }
            if (phase % 10 != 0) return;
            int st = stacks.get();
            if (st > 0) p.sendSystemMessage(Component.literal(st >= 10 ? "§6§l10⁑§r" : "§6" + st + "⁑"), true);
            else if (shown.get() > 0) p.sendSystemMessage(Component.empty(), true);
            shown.set(st);
        }
    }
}
