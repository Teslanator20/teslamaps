// Ported from ADM's p3sim/Mage.kt
package com.teslamaps.adm.sim;

import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Mage {
    private Mage() {}

    private static final Set<String> BEAM_ITEMS = Set.of("HYPERION", "WITHER_CLOAK", "STARRED_BONZO_STAFF", "BONZO_STAFF");

    public static final double BEAM_RANGE = 40.0;
    private static final int BEAM_GAP = 4;
    private static final int SHEEP_COOLDOWN = 139;
    private static final double SHEEP_SPEED = 1.0;
    private static final int SHEEP_SURGE_FROM = 4;
    private static final int SHEEP_SURGE_TO = 6;
    private static final int SHEEP_LIFE = 60;

    private static final PerPlayer<Integer> beamReady = new PerPlayer<>(() -> 0);
    private static final PerPlayer<ArrayList<Integer>> clicks = new PerPlayer<>(ArrayList::new);
    private static final PerPlayer<Integer> sheepReady = new PerPlayer<>(() -> 0);
    private static final PerPlayer<Boolean> readyNoted = new PerPlayer<>(() -> true);

    public static class GhostSheep extends Sheep {
        public GhostSheep(Level level) { super(EntityTypes.SHEEP, level); }

        @Override public boolean isPickable() { return false; }
        @Override public boolean isPushable() { return false; }
        @Override public boolean canBeCollidedWith(Entity other) { return false; }
        @Override protected void pushEntities() {}
    }

    private static class Flying {
        final Sheep e;
        final int at;
        int boomAt = -1;

        Flying(Sheep e, int at) {
            this.e = e;
            this.at = at;
        }
    }

    private static final PerPlayer<ArrayList<Flying>> sheep = new PerPlayer<>(ArrayList::new);

    public static boolean isMage() { return P3Sim.myClass() == DungeonClass.MAGE || P3Sim.mageAll(); }

    public static boolean beamItem(String id) { return id != null && BEAM_ITEMS.contains(id); }

    public static void reset() {
        beamReady.set(0);
        clicks.get().clear();
        sheepReady.set(0);
        readyNoted.set(true);
        for (Flying it : sheep.get()) it.e.discard();
        sheep.get().clear();
    }

    public static void click(ServerPlayer p) {
        if (!isMage()) return;
        int now = Fight.serverTick;
        if (now >= beamReady.get()) {
            beamReady.set(now + BEAM_GAP);
            beam(p);
        }
        ArrayList<Integer> c = clicks.get();
        c.add(now);
        while (c.size() > 3) c.remove(0);
        if (c.size() == 3 && gapOk(c.get(1) - c.get(0)) && gapOk(c.get(2) - c.get(1)) && now >= sheepReady.get()) {
            c.clear();
            Fight.later(1, "guided sheep", () -> {
                if (!p.isRemoved()) launchSheep(p);
            });
        }
    }

    private static boolean gapOk(int d) { return d >= 2 && d <= 5; }

    public static void ability(ServerPlayer p) {
        if (!isMage()) return;
        int now = Fight.serverTick;
        if (now < sheepReady.get()) {
            Sim.chat("§cYour Regular Ability is currently on cooldown for " + (sheepReady.get() - now + 19) / 20 + " more seconds.");
            return;
        }
        Sim.chat("§aUsed §6Guided Sheep§a!");
        launchSheep(p);
    }

    private static Vec3 look(ServerPlayer p) { return look(p, 0.0); }

    private static Vec3 look(ServerPlayer p, double down) {
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot() + down);
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static Pair<LivingEntity, Vec3> firstMob(Vec3 from, Vec3 to, ServerPlayer p) {
        AABB box = new AABB(from, to).inflate(1.0);
        Pair<LivingEntity, Vec3> best = null;
        for (LivingEntity e : Sim.level().getEntitiesOfClass(LivingEntity.class, box, it ->
                it != p && it.isAlive() && (DamageModule.mobFor(it) != null
                        || !(it instanceof net.minecraft.world.entity.player.Player) && !(it instanceof Mannequin) && !(it instanceof ArmorStand) && !(it instanceof Sheep) && !it.isInvisible()))) {
            Vec3 hit = e.getBoundingBox().inflate(0.3).clip(from, to).orElse(null);
            if (hit == null) continue;
            if (best == null || hit.distanceToSqr(from) < best.second.distanceToSqr(from)) best = Pair.of(e, hit);
        }
        return best;
    }

    private static void beam(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = look(p);
        Vec3 far = eye.add(dir.scale(BEAM_RANGE));
        BlockHitResult block = Sim.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : far;
        Pair<LivingEntity, Vec3> mob = firstMob(eye, end, p);
        if (mob != null) end = mob.second;
        Vec3 start = eye.add(0.0, -0.2, 0.0).add(dir.scale(0.6));
        double len = end.distanceTo(start);
        double d = 0.0;
        while (d <= len) {
            Vec3 at = start.add(dir.scale(d));
            Sim.level().sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            d += 0.5;
        }
        if (Fight.phase instanceof P2Storm storm) storm.beam();
        if (mob != null) {
            LivingEntity e = mob.first;
            DamageModule.beamHit(e, p);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(e));
            Fight.later(1, "beam hit", () -> {
                SoundEvent s = e instanceof net.minecraft.world.entity.boss.wither.WitherBoss ? SoundEvents.WITHER_HURT : SoundEvents.GENERIC_HURT;
                Sim.sound(s, 1f, 1f, p.position(), SoundSource.HOSTILE);
            });
        }
    }

    private static void launchSheep(ServerPlayer p) {
        int now = Fight.serverTick;
        if (now < sheepReady.get()) return;
        sheepReady.set(now + SHEEP_COOLDOWN);
        readyNoted.set(false);
        spawnSheep(p);
    }

    public static void spawnSheep(ServerPlayer p) {
        GhostSheep e = new GhostSheep(Sim.level());
        e.setNoAi(true);
        e.setNoGravity(true);
        e.setPermanentlyInvulnerable(true);
        e.setSilent(true);
        e.snapTo(p.getX(), p.getY() + 0.781, p.getZ(), p.getYRot(), 0f);
        e.setYHeadRot(p.getYRot() - 10f);
        sheep.get().add(new Flying(Sim.spawn(e), Fight.serverTick));
        Sim.sound(SoundEvents.GHAST_SHOOT, 0.3f, 1f, p.position(), SoundSource.HOSTILE);
    }

    public static void tick() {
        int now = Fight.serverTick;
        if (!readyNoted.get() && now >= sheepReady.get()) {
            readyNoted.set(true);
            if (isMage()) Sim.chat("§6Guided Sheep §r§ais now available!");
        }
        ArrayList<Flying> list = sheep.get();
        if (list.isEmpty()) return;
        ServerPlayer p = Sim.player();
        Iterator<Flying> it = list.iterator();
        while (it.hasNext()) {
            Flying f = it.next();
            Sheep e = f.e;
            if (e.isRemoved()) {
                it.remove();
                continue;
            }
            if (f.boomAt >= 0) {
                if (now > f.boomAt) {
                    e.discard();
                    it.remove();
                }
                continue;
            }
            int age = now - f.at;
            Vec3 v = (p != null ? look(p) : e.getDeltaMovement().normalize())
                    .scale(SHEEP_SPEED * (age >= SHEEP_SURGE_FROM && age <= SHEEP_SURGE_TO ? 2 : 1));
            Vec3 from = e.position();
            Vec3 to = from.add(v);
            BlockHitResult block = Sim.level().clip(new ClipContext(from.add(0.0, 0.4, 0.0), to.add(0.0, 0.4, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            Pair<LivingEntity, Vec3> mob = p != null ? firstMob(from.add(0.0, 0.4, 0.0), to.add(0.0, 0.4, 0.0), p) : null;
            Vec3 stop;
            if (mob != null) stop = mob.second.subtract(0.0, 0.4, 0.0);
            else if (block.getType() == HitResult.Type.BLOCK) stop = block.getLocation().subtract(0.0, 0.4, 0.0);
            else stop = null;
            Vec3 at = stop != null ? stop : to;
            e.setDeltaMovement(v);
            e.snapTo(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-v.x, v.z)), 0f);
            if (stop != null || now - f.at >= SHEEP_LIFE) {
                f.boomAt = now;
                Sim.sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.8f + KRandom.DEFAULT.nextFloat() * 0.18f, at, SoundSource.MASTER);
                Sim.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                int hit = DamageModule.blast(at.x, at.y, at.z, com.teslamaps.adm.sim.run.DamageSource.MAGE_BEAM, p != null ? p.getUUID() : null);
                if (hit > 0) {
                    String total = String.format(Locale.ROOT, "%,.1f", hit * DamageModule.blastDamage());
                    if (total.endsWith(".0")) total = total.substring(0, total.length() - 2);
                    Sim.chat("§7Your Guided Sheep hit §c" + hit + " §7" + (hit == 1 ? "enemy" : "enemies") + " for §c" + total + " §7damage.");
                } else if (mob != null && DamageModule.activeRun() == null) Sim.chat("§7Your Guided Sheep hit §c1 §7enemy for §c0 §7damage.");
                if (Fight.phase instanceof GoldorPhase g) {
                    int gate = g.gateNear(at, 2.0);
                    if (gate > 0) g.blowGate(gate, Sim.me());
                }
            }
        }
    }
}
