package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/ClassAbilities.kt

import com.teslamaps.adm.sim.Bows;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Mage;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

public final class ClassAbilities {
    private ClassAbilities() {}

    private static class Timers {
        int abilityAt;
        int ultAt;
        boolean abilityNoted;
        boolean ultNoted = false;
        int reminderAt = Integer.MAX_VALUE;
        boolean ultUsed = false;

        Timers(int abilityAt, int ultAt, boolean abilityNoted) {
            this.abilityAt = abilityAt;
            this.ultAt = ultAt;
            this.abilityNoted = abilityNoted;
        }
    }

    private static final HashMap<UUID, Timers> timers = new HashMap<>();
    private static Integer t0 = null;

    private static class Effect {
        final UUID player;
        final int until;
        final int every;
        final BiConsumer<ServerPlayer, Integer> step;
        final Consumer<ServerPlayer> end;

        Effect(UUID player, int until, int every, BiConsumer<ServerPlayer, Integer> step, Consumer<ServerPlayer> end) {
            this.player = player;
            this.until = until;
            this.every = every;
            this.step = step;
            this.end = end;
        }

        Effect(UUID player, int until, int every, BiConsumer<ServerPlayer, Integer> step) {
            this(player, until, every, step, p -> {});
        }
    }

    private static final ArrayList<Effect> effects = new ArrayList<>();

    public static void reset() {
        timers.clear();
        effects.clear();
        t0 = null;
    }

    private static Timers timersOf(Run run, ServerPlayer p) {
        Integer start = run.t0Tick;
        if (start == null) return null;
        if (!Objects.equals(t0, start)) {
            timers.clear();
            t0 = start;
        }
        Timers tm = timers.get(p.getUUID());
        if (tm == null) {
            ClassKit.Kit k = kitOf(run, p);
            tm = new Timers(ClassKit.ABILITY_FIRST_READY, ClassKit.ULT_FIRST_READY, !(k != null && "Mage".equals(k.cls)));
            timers.put(p.getUUID(), tm);
        }
        return tm;
    }

    private static ClassKit.Kit kitOf(Run run, ServerPlayer p) {
        PlayerRecord rec = run.state.players.get(p.getUUID());
        return ClassKit.of(rec == null ? null : rec.dungeonClass);
    }

    private static boolean doubled(Run run, String cls) {
        int n = 0;
        for (PlayerRecord r : run.state.players.values()) if (cls.equalsIgnoreCase(r.dungeonClass)) n++;
        return n <= 1;
    }

    private static void say(ServerPlayer p, String line) { Sim.asPlayer(p, () -> Sim.chat(line)); }

    public static boolean press(ServerPlayer p, boolean ultimate) {
        Run run = DamageModule.activeRun();
        if (run == null) return false;
        if (run.stage == RunStage.COUNTDOWN) return true;
        ClassKit.Kit kit = kitOf(run, p);
        if (kit == null) return true;
        Timers tm = timersOf(run, p);
        if (tm == null) return true;
        PlayerRecord rec = run.state.players.get(p.getUUID());
        if (rec != null && rec.ghost) return true;
        int t = run.t();
        if (ultimate) {
            if (t < tm.ultAt) {
                say(p, ClassKit.ultimateCooling(tm.ultAt - t));
                return true;
            }
            say(p, ClassKit.used(kit.ultimate));
            tm.ultAt = t + ClassKit.ultimateCd(kit, doubled(run, kit.cls));
            tm.ultNoted = false;
            tm.ultUsed = true;
            tm.reminderAt = Integer.MAX_VALUE;
            Ec.safely("ads ultimate", () -> ultimateOf(run, p, kit));
        } else {
            if (t < tm.abilityAt) {
                say(p, ClassKit.abilityCooling(tm.abilityAt - t));
                return true;
            }
            say(p, ClassKit.used(kit.ability));
            tm.abilityAt = t + kit.abilityCd;
            tm.abilityNoted = false;
            Ec.safely("ads ability", () -> abilityOf(run, p, kit));
        }
        return true;
    }

    public static void tick(Run run) {
        if (run.t0Tick == null || run.stage == RunStage.COUNTDOWN) return;
        int t = run.t();
        for (ServerPlayer p : Sim.players()) {
            ClassKit.Kit kit = kitOf(run, p);
            if (kit == null) continue;
            Timers tm = timersOf(run, p);
            if (tm == null) continue;
            if (!tm.abilityNoted && t >= tm.abilityAt) {
                tm.abilityNoted = true;
                say(p, ClassKit.abilityAvailable(kit.ability));
            }
            if (!tm.ultNoted && t >= tm.ultAt) {
                tm.ultNoted = true;
                say(p, tm.ultUsed ? ClassKit.ultimateRecharged(kit.cls, kit.ultimate) : ClassKit.ultimateReady(kit.ultimate));
                tm.reminderAt = t + ClassKit.ULT_REMINDER;
            } else if (t >= tm.reminderAt) {
                tm.reminderAt = t + ClassKit.ULT_REMINDER;
                say(p, ClassKit.ultimateReady(kit.ultimate));
            }
        }
        Iterator<Effect> it = effects.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            ServerPlayer p = null;
            for (ServerPlayer pl : Sim.players()) if (pl.getUUID().equals(e.player)) { p = pl; break; }
            if (p == null || t > e.until) {
                if (p != null) {
                    ServerPlayer fp = p;
                    Ec.safely("ads ability end", () -> Sim.asPlayer(fp, () -> e.end.accept(fp)));
                }
                it.remove();
                continue;
            }
            if ((e.until - t) % e.every == 0) {
                ServerPlayer fp = p;
                Ec.safely("ads ability step", () -> Sim.asPlayer(fp, () -> e.step.accept(fp, t)));
            }
        }
    }

    private static Vec3 look(ServerPlayer p) {
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static List<MobRecord> aliveMobsNear(Vec3 at, double r) {
        List<MobRecord> out = new ArrayList<>();
        for (MobRecord m : Services.mobs().all()) if (m.alive && new Vec3(m.x, m.y, m.z).distanceTo(at) <= r) out.add(m);
        return out;
    }

    private static double hurt(Run run, UUID id, UUID by) {
        double dmg = DamageModule.blastDamage();
        return DamageModule.INSTANCE.hurtMob(run, id, dmg, DamageSource.OTHER, by) ? dmg : 0.0;
    }

    private static void abilityOf(Run run, ServerPlayer p, ClassKit.Kit kit) {
        ServerLevel level = Sim.level();
        switch (kit.ability) {
            case "Healing Circle" -> {
                for (PlayerRecord r : run.state.players.values()) {
                    ServerPlayer sp = null;
                    for (ServerPlayer pl : Sim.players()) if (pl.getUUID().equals(r.uuid)) { sp = pl; break; }
                    boolean near = sp != null ? sp.distanceTo(p) <= 8.0 : true;
                    if (!r.ghost && near) r.hpPercent = Math.min(100.0, r.hpPercent + 10.0);
                }
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    level.sendParticles(ParticleTypes.HEART, p.getX() + 3 * Math.cos(a), p.getY() + 0.5, p.getZ() + 3 * Math.sin(a), 1, 0.0, 0.0, 0.0, 0.0);
                }
                Sim.sound(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f, p.position());
            }
            case "Guided Sheep" -> Mage.spawnSheep(p);
            case "Explosive Shot" -> SimItems.volleyArrows(p);
            case "Throwing Axe" -> {
                Vec3 eye = p.getEyePosition();
                Vec3 dir = look(p);
                HashSet<UUID> hit = new HashSet<>();
                for (int s = 1; s <= 24; s++) {
                    Vec3 at = eye.add(dir.scale(s * 0.5));
                    if (s % 2 == 0) level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
                    for (MobRecord m : aliveMobsNear(at, 1.5)) if (hit.add(m.id)) hurt(run, m.id, p.getUUID());
                }
                Sim.sound(SoundEvents.TRIDENT_THROW, 1f, 0.8f, p.position());
            }
            case "Seismic Wave" -> {
                Vec3 l = look(p);
                Vec3 dir = new Vec3(l.x, 0.0, l.z).normalize();
                int n = 0;
                double total = 0.0;
                double[] ds = {3.0, 6.0, 9.0};
                for (int i = 0; i < ds.length; i++) {
                    Vec3 at = p.position().add(dir.scale(ds[i]));
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0.0);
                    for (MobRecord m : aliveMobsNear(at, 2.5)) {
                        double dmg = hurt(run, m.id, p.getUUID());
                        if (dmg > 0) {
                            n++;
                            total += dmg;
                        }
                    }
                    if (i == 0) Sim.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.7f, at);
                }
                if (n > 0) say(p, ClassKit.hitLine("Seismic Wave", n, total));
            }
            default -> {}
        }
    }

    private static void ultimateOf(Run run, ServerPlayer p, ClassKit.Kit kit) {
        ServerLevel level = Sim.level();
        switch (kit.ultimate) {
            case "Wish" -> {
                double healed = 0.0;
                for (PlayerRecord r : run.state.players.values()) if (!r.ghost) {
                    healed += 100.0 - r.hpPercent;
                    r.hpPercent = 100.0;
                }
                level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.0, p.getZ(), 30, 1.5, 1.0, 1.5, 0.0);
                Sim.sound(SoundEvents.BEACON_ACTIVATE, 1f, 1.4f, p.position());
                say(p, ClassKit.wishSelf(0, 0));
            }
            case "Thunderstorm" -> {
                int[] n = {0};
                double[] total = {0.0};
                HashSet<UUID> struck = new HashSet<>();
                effects.add(new Effect(p.getUUID(), run.t() + 100, 10, (sp, ignored) -> {
                    Run r = DamageModule.activeRun();
                    if (r == null) return;
                    List<MobRecord> near = new ArrayList<>(aliveMobsNear(sp.position(), 12.0));
                    KRandom.shuffle(near, KRandom.DEFAULT);
                    for (MobRecord m : near.subList(0, Math.min(3, near.size()))) {
                        LightningBolt b = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
                        b.setVisualOnly(true);
                        b.snapTo(m.x, m.y, m.z, 0f, 0f);
                        level.addFreshEntity(b);
                        double dmg = hurt(r, m.id, sp.getUUID());
                        if (dmg > 0) {
                            total[0] += dmg;
                            if (struck.add(m.id)) n[0]++;
                        }
                    }
                }, sp -> {
                    if (n[0] > 0) say(sp, ClassKit.hitLine("Thunderstorm", n[0], total[0]));
                }));
            }
            case "Rapid Fire" -> effects.add(new Effect(p.getUUID(), run.t() + 80, 2, (sp, ignored) -> {
                Vec3 from = sp.getEyePosition().subtract(0.0, 0.1, 0.0);
                Bows.launch(from, from, look(sp).scale(3.0), sp);
                Sim.sound(SoundEvents.ARROW_SHOOT, 0.5f, 1.2f, sp.position());
            }));
            case "Ragnarok" -> {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 12, 1.0, 0.8, 1.0, 0.0);
                Sim.sound(SoundEvents.RAVAGER_ROAR, 1f, 1f, p.position());
            }
            case "Castle of Stone" -> {
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI / 16;
                    level.sendParticles(ParticleTypes.CLOUD, p.getX() + 2 * Math.cos(a), p.getY() + 0.2, p.getZ() + 2 * Math.sin(a), 1, 0.0, 0.4, 0.0, 0.0);
                }
                Sim.sound(SoundEvents.ANVIL_LAND, 0.8f, 0.6f, p.position());
            }
            default -> {}
        }
    }
}
