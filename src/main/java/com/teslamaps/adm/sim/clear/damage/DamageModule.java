package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/DamageModule.kt

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Mage;
import com.teslamaps.adm.sim.Masks;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.clear.secrets.Blessings;
import com.teslamaps.adm.sim.clear.secrets.LootItem;
import com.teslamaps.adm.sim.clear.secrets.SecretWorld;
import com.teslamaps.adm.sim.clear.secrets.SecretsModule;
import com.teslamaps.adm.sim.pack.mobs.HitQuantiles;
import com.teslamaps.adm.sim.pack.mobs.MeleeTable;
import com.teslamaps.adm.sim.pack.mobs.PlayerDamageTable;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.MobKilled;
import com.teslamaps.adm.sim.run.PlayerDamaged;
import com.teslamaps.adm.sim.run.PlayerDied;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PlayerRevived;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.Runs;
import com.teslamaps.adm.sim.run.SecretFound;
import com.teslamaps.adm.sim.run.api.DamageService;
import com.teslamaps.adm.sim.run.api.HurtResult;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public final class DamageModule implements AdsModule, DamageService {
    public static final DamageModule INSTANCE = new DamageModule();

    private DamageModule() {}

    @Override public String id() { return "damage"; }

    @Override public String owner() { return "damage"; }

    // ---- settings

    private static NumSetting arrowS;
    private static NumSetting beamS;
    private static NumSetting meleeS;
    private static NumSetting blastS;
    private static NumSetting implosionS;
    private static NumSetting regenS;
    private static NumSetting shieldS;

    public static final double DEFAULT_ARROW = 1_200_000.0;
    public static final double DEFAULT_BEAM = 1_000_000.0;
    public static final double DEFAULT_MELEE = 500_000.0;
    public static final double DEFAULT_BLAST = 3_000_000.0;
    public static final double BLAST_RADIUS = 3.0;
    public static final double DEFAULT_REGEN = 2.3;
    public static final int REGEN_STEP_TICKS = 20;
    public static final int REGEN_AFTER_HIT_TICKS = 5;

    public static double arrowDamage() { return arrowS != null ? arrowS.getInt() * 1000.0 : DEFAULT_ARROW; }

    public static double beamDamage() { return beamS != null ? beamS.getInt() * 1000.0 : DEFAULT_BEAM; }

    public static double meleeDamage() { return meleeS != null ? meleeS.getInt() * 1000.0 : DEFAULT_MELEE; }

    public static double blastDamage() { return blastS != null ? blastS.getInt() * 1000.0 : DEFAULT_BLAST; }

    private static double implosionMultiplier() { return implosionS != null ? implosionS.getInt() / 100.0 : 1.0; }

    public static double regenPercentPerSecond() { return regenS != null ? regenS.getInt() / 10.0 : DEFAULT_REGEN; }

    public static double shieldPoolPercent() { return shieldS != null ? (double) shieldS.getInt() : WitherShield.DEFAULT_POOL_PERCENT; }

    @Override
    public void registerSettings() {
        arrowS = SimSettings.num("Clear Arrow Damage", 1200, 50, 20000, 50, "k", "In a full run: the HP one arrow takes off a mob (thousands). Not measured on Hypixel; F7 mobs have 420k to 8.8M.");
        beamS = SimSettings.num("Clear Beam Damage", 1000, 50, 20000, 50, "k", "In a full run: the HP one Mage beam takes off a mob (thousands). Not measured on Hypixel.");
        meleeS = SimSettings.num("Clear Sword Damage", 500, 50, 20000, 50, "k", "In a full run: the HP a sword hit takes off a mob (thousands). Not measured on Hypixel.");
        blastS = SimSettings.num("Clear Blast Damage", 3000, 50, 20000, 50, "k", "In a full run: the HP an Explosive Shot blast or a Guided Sheep takes off each mob within 3 blocks (thousands). Not measured on Hypixel.");
        implosionS = SimSettings.num("Implosion Damage", 100, 10, 400, 5, "%", "In a full run: scales the floor's measured Implosion damage per mob (F7 25.4M, M7 56.6M). 100 is your recorded gear; on M7 lower values need more casts per mob.");
        regenS = SimSettings.num("Clear Health Regen", 23, 0, 100, 1, " per 10 s", "In a full run: percent of max health that comes back every 10 seconds, in a step every second (23 means 2.3 percent a second, the middle of what Hypixel shows: 1.3 to 3.5).");
        numbersS = SimSettings.num("Damage Numbers", DamageNumbers.DEFAULT_CHANCE, 0, 100, 1, "%", "In a full run: the share of hits on a mob that float a damage number over it for a second (grey, or a coloured ✧crit✧). On Hypixel every hit by every player gets one.");
        shieldS = SimSettings.num("Wither Shield Size", 93, 0, 200, 1, "%", "In a full run: how much damage a Hyperion cast's Wither Shield soaks, in percent of your max health. Not measured on Hypixel.");
    }

    // ---- state

    private static NumSetting numbersS;
    private static KRandom numberRolls = new KRandom(0L);

    private static final MobDamageLedger mobLedger = new MobDamageLedger();
    private static KRandom rolls = new KRandom(0L);

    private static KRandom meleeRandom() { return rolls; }

    private static final HashMap<UUID, Integer> regenCarry = new HashMap<>();
    private static final HashMap<UUID, WitherShield> shields = new HashMap<>();
    private static final HashMap<UUID, Double> dealt = new HashMap<>();
    private static final HashMap<UUID, Integer> milestonesShown = new HashMap<>();

    private static class Reviving {
        final UUID by;
        final int dueT;

        Reviving(UUID by, int dueT) {
            this.by = by;
            this.dueT = dueT;
        }
    }

    private static final HashMap<UUID, Reviving> reviving = new HashMap<>();
    private static final HashMap<UUID, Float> shown = new HashMap<>();

    public static Predicate<MobRecord> sleepingCheck = it -> Services.mobs().isAsleep(it.id);

    public static final String ASLEEP_TAG = "ads_asleep";

    public static class Cast {
        public final int count;
        public final double total;
        public final int killed;

        public Cast(int count, double total, int killed) {
            this.count = count;
            this.total = total;
            this.killed = killed;
        }
    }

    public static Run activeRun() {
        Run r = Runs.current;
        return r != null && !r.isOver() && r.stage.inDungeon() ? r : null;
    }

    private static final KillCombo combo = new KillCombo();
    public static volatile boolean killCombo = true;

    @Override
    public void onRunStart(Run run) {
        combo.clear();
        mobLedger.clear(); regenCarry.clear(); shown.clear(); shields.clear(); dealt.clear(); milestonesShown.clear(); reviving.clear();
        rolls = run.subRandom("damage");
        numberRolls = run.subRandom("damage.numbers");
        ClassAbilities.reset();
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        ClassAbilities.reset();
        mobLedger.clear(); regenCarry.clear(); shields.clear(); dealt.clear(); milestonesShown.clear(); reviving.clear();
        Ec.safely("ads damage end", () -> {
            Masks.stopped();
            // the boss sim expects its permanent 16 absorption back
            for (ServerPlayer p : Sim.players()) {
                p.setHealth((float) PlayerLedger.FULL_BAR);
                p.setAbsorptionAmount((float) WitherShield.FULL_ABSORPTION);
            }
        });
    }

    @Override
    public void restore(Run run, JsonObject data) {
        ClassAbilities.reset();
        mobLedger.clear(); regenCarry.clear(); shown.clear(); shields.clear(); dealt.clear(); milestonesShown.clear(); reviving.clear();
        for (PlayerRecord rec : run.state.players.values()) if (!rec.ghost) {
            ServerPlayer p = human(rec.uuid);
            if (p != null) Sim.asPlayer(p, () -> { if (Masks.ghost.get()) Masks.reviveGhost(p, null); });
        }
    }

    @Override
    public void onEvent(Run run, RunEvent event) {
        if (event instanceof MobKilled mk && killCombo && mk.by != null) {
            UUID id = mk.by;
            ServerPlayer p = human(id);
            if (p != null) {
                String line = combo.kill(id, run.t());
                if (line != null) Ec.safely("ads kill combo", () -> Sim.asPlayer(p, () -> Sim.chat(line)));
            }
        }
        // the Dungeonbreaker gains 5 charges for each secret its finder collects
        if (event instanceof SecretFound sf && sf.player != null) {
            ServerPlayer p = human(sf.player);
            if (p != null) Ec.safely("ads damage breaker charges", () -> Sim.asPlayer(p, () -> SimItems.addBreakerCharges(BREAKER_CHARGES_PER_SECRET)));
        }
    }

    public static final int BREAKER_CHARGES_PER_SECRET = 5;

    // ---- tick

    @Override
    public void onTick(Run run) {
        if (!run.stage.inDungeon()) return;
        int t = run.t();
        Ec.safely("ads class abilities", () -> ClassAbilities.tick(run));
        for (Pair<UUID, String> ex : combo.expire(t)) {
            String line = ex.second;
            if (line != null) {
                ServerPlayer p = human(ex.first);
                if (p != null) Ec.safely("ads kill combo", () -> Sim.asPlayer(p, () -> Sim.chat(line)));
            }
        }
        for (PlayerRecord rec : new ArrayList<>(run.state.players.values())) {
            if (PlayerLedger.reviveDue(rec, t)) {
                reviving.remove(rec.uuid);
                INSTANCE.revive(run, rec.uuid, null);
                continue;
            }
            Reviving r = reviving.get(rec.uuid);
            if (r != null) {
                // the reviver dying or leaving cancels it
                PlayerRecord reviver = run.state.players.get(r.by);
                if (!rec.ghost || reviver == null || reviver.ghost) reviving.remove(rec.uuid);
                else if (t >= r.dueT) {
                    reviving.remove(rec.uuid);
                    INSTANCE.revive(run, rec.uuid, r.by);
                    continue;
                }
            }
            if (!rec.ghost && rec.hpPercent < 100.0 && regenPercentPerSecond() > 0.0) {
                Integer next = regenCarry.get(rec.uuid);
                if (next == null) {
                    next = rec.lastHitT != Integer.MIN_VALUE ? rec.lastHitT + REGEN_AFTER_HIT_TICKS : t + REGEN_STEP_TICKS;
                    regenCarry.put(rec.uuid, next);
                }
                if (t >= next) {
                    rec.hpPercent = Math.min(100.0, rec.hpPercent + regenPercentPerSecond() * REGEN_STEP_TICKS / 20.0);
                    regenCarry.put(rec.uuid, t + REGEN_STEP_TICKS);
                }
            } else regenCarry.remove(rec.uuid);
            ServerPlayer p = human(rec.uuid);
            if (p != null) {
                showBar(p, rec.ghost ? 100.0 : rec.hpPercent);
                WitherShield sh = shields.get(rec.uuid);
                showShield(p, rec.ghost ? 0.0 : (sh != null ? sh.absorption(t) : 0.0));
            }
        }
    }

    private static void showBar(ServerPlayer p, double percent) {
        float v = (float) PlayerLedger.vanilla(percent);
        Float was = shown.get(p.getUUID());
        if (was != null && was == v && p.getHealth() == v) return;
        shown.put(p.getUUID(), v);
        p.setHealth(v);
    }

    private static void showShield(ServerPlayer p, double amount) {
        float v = (float) amount;
        if (p.getAbsorptionAmount() == v) return;
        // 26.1.2 and later clamp absorption to the attribute
        AttributeInstance a = p.getAttribute(Attributes.MAX_ABSORPTION);
        if (a != null && a.getBaseValue() < WitherShield.FULL_ABSORPTION) a.setBaseValue(WitherShield.FULL_ABSORPTION);
        p.setAbsorptionAmount(v);
    }

    public static void startShield(ServerPlayer p) {
        Run run = activeRun();
        if (run == null) return;
        PlayerRecord rec = run.state.players.get(p.getUUID());
        if (rec == null) return;
        if (rec.ghost) return;
        WitherShield cur = shields.get(p.getUUID());
        if (cur == null || !cur.active(run.t())) {
            WitherShield s = new WitherShield(shieldPoolPercent());
            s.start(run.t());
            shields.put(p.getUUID(), s);
        }
    }

    private static ServerPlayer human(UUID id) {
        for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(id)) return p;
        return null;
    }

    // ---- mob side

    public static MobRecord mobFor(Entity e) {
        if (activeRun() == null) return null;
        int id = e.getId();
        MobRecord rec = Services.mobs().recordOfEntity(id);
        return rec != null && rec.alive ? rec : null;
    }

    private static Entity entityOf(MobRecord rec) {
        Integer eid = rec.entityId;
        return eid == null ? null : Sim.level().getEntity(eid);
    }

    private static boolean asleep(MobRecord rec) {
        if (sleepingCheck.test(rec)) return true;
        Entity e = entityOf(rec);
        if (e == null) return false;
        return e.entityTags().contains(ASLEEP_TAG);
    }

    @Override
    public boolean hurtMob(Run run, UUID mob, double asked, DamageSource source, UUID by) {
        if (!run.stage.inDungeon()) return false;
        MobRecord rec = Services.mobs().mob(mob);
        if (rec == null) return false;
        BiFunction<UUID, UUID, Double> rh = replayHit;
        Double replayed = rh != null ? rh.apply(mob, by) : null;
        double amount = replayed != null ? replayed : asked;
        if (!rec.alive || amount <= 0.0 || Double.isNaN(amount)) return false;
        // a sleeping Fels cannot be hurt, except by a teammate bot
        if (asleep(rec)) {
            PlayerRecord byRec = by != null ? run.state.players.get(by) : null;
            if (!(byRec != null && byRec.isBot)) return false;
        }
        // for now every hit kills: the mob takes all it has left
        double left = oneShot && replayed == null ? 0.0 : Math.max(0.0, rec.hp - amount);
        mobLedger.record(mob, amount, source, by);
        // milestones count the health a hit took, not the hit
        if (by != null) addDealt(run, by, Math.min(amount, rec.hp), source);
        int numbers = numbersS != null ? numbersS.getInt() : DamageNumbers.DEFAULT_CHANCE;
        if (numbers > 0 && numberRolls.nextInt(100) < numbers) Ec.safely("ads damage number", () -> {
            Entity e = entityOf(rec);
            if (e != null) DamageNumbers.show(e.getX(), e.getY(), e.getZ(), amount, numberRolls);
        });
        MobRecord after = Services.mobs().setHp(run, mob, left, by);
        // the kill counts only when the mob really died
        if (left <= 0.0 && after != null && !after.alive) {
            mobLedger.forget(mob);
            if (by != null) {
                PlayerRecord pr = run.state.players.get(by);
                if (pr != null) {
                    Object k = pr.extras.get(KILLS);
                    pr.extras.put(KILLS, (k instanceof Integer i ? i : 0) + 1);
                }
            }
        } else if (left > 0.0) Ec.safely("ads damage hurt animation", () -> {
            Entity e = entityOf(rec);
            if (e instanceof LivingEntity le) for (ServerPlayer p : Sim.players()) p.connection.send(new ClientboundHurtAnimationPacket(le));
        });
        return true;
    }

    private static void addDealt(Run run, UUID by, double amount, DamageSource source) {
        PlayerRecord rec = run.state.players.get(by);
        if (rec == null) return;
        // an Archer's milestones count ranged damage only
        if ("archer".equalsIgnoreCase(rec.dungeonClass) && source != DamageSource.BOW && source != DamageSource.PROJECTILE) return;
        Double prev = dealt.get(by);
        double total = (prev != null ? prev : 0.0) + amount;
        dealt.put(by, total);
        String cls = rec.dungeonClass;
        if (cls == null) return;
        long[] table = ClassMilestones.thresholds(cls);
        if (table == null) return;
        Integer sh = milestonesShown.get(by);
        int shownSoFar = sh != null ? sh : 0;
        int now = ClassMilestones.reached(table, total);
        if (now <= shownSoFar) return;
        milestonesShown.put(by, now);
        ServerPlayer sp = human(by);
        if (sp == null) return;
        int seconds = Blessings.seconds(run.t());
        for (int i = shownSoFar; i < now; i++) {
            String line = ClassMilestones.line(cls, i, table, seconds);
            Sim.asPlayer(sp, () -> Sim.chat(line));
        }
    }

    public static volatile BiFunction<UUID, UUID, Double> replayHit = null;

    public static volatile boolean oneShot = true;

    public static final String KEY_KILLS = "damage.kills";
    private static final String KILLS = KEY_KILLS;

    public static MobDamageLedger.Entry taken(UUID mob) { return mobLedger.of(mob); }

    @Override
    public int implode(Run run, UUID caster, double[] box, Double damage) { return cast(run, caster, box, damage).count; }

    public static Cast castImplosion(Run run, ServerPlayer p) {
        return cast(run, p.getUUID(), ImplosionRules.castBox(p.getX(), p.getEyeY(), p.getZ()), null);
    }

    private static ImplosionRules.Hitbox hitboxOf(MobRecord rec) {
        Entity e = entityOf(rec);
        if (e != null) {
            AABB b = e.getBoundingBox();
            return new ImplosionRules.Hitbox((b.minX + b.maxX) / 2, b.minY, (b.minZ + b.maxZ) / 2, b.maxX - b.minX, b.maxY - b.minY);
        }
        Pair<Double, Double> wh = ImplosionRules.size(rec.kind);
        return new ImplosionRules.Hitbox(rec.x, rec.y, rec.z, wh.first, wh.second);
    }

    private static List<ImplosionRules.Candidate> candidates(Iterable<MobRecord> mobs) {
        List<ImplosionRules.Candidate> out = new ArrayList<>();
        for (MobRecord m : mobs) out.add(new ImplosionRules.Candidate(m.id, hitboxOf(m), m.alive, asleep(m)));
        return out;
    }

    public static Cast cast(Run run, UUID caster, double[] box, Double damage) {
        if (!run.stage.inDungeon()) return new Cast(0, 0.0, 0);
        List<MobRecord> all = new ArrayList<>(Services.mobs().all());
        // a secret bat is a plain bat: the secrets module kills the ones in the box
        int bats = box.length >= 6 ? killBats(run, new AABB(box[0], box[1], box[2], box[3], box[4], box[5]), caster) : 0;
        List<UUID> ids = ImplosionRules.select(box, candidates(all));
        if (ids.isEmpty() && bats == 0) return new Cast(0, 0.0, 0);
        ImplosionRules.Profile profile = ImplosionRules.profile(run.config.floor);
        double base = damage != null ? damage : profile.castBase(rolls, implosionMultiplier());
        double scale = run.config.mobHpScale != null ? run.config.mobHpScale : 1.0;
        // a bat takes the cast's base roll
        int hit = bats;
        int killed = bats;
        double total = bats * base;
        for (UUID id : ids) {
            MobRecord before = Services.mobs().mob(id);
            double dmg = damage != null || before == null ? base : profile.perMob(before.kind, before.miniboss, before.maxHp, scale, base, rolls);
            if (INSTANCE.hurtMob(run, id, dmg, DamageSource.IMPLOSION, caster)) {
                hit++;
                total += dmg;
                if (before != null && !before.alive) killed++;
            }
        }
        return new Cast(hit, total, killed);
    }

    // ---- secret bats

    public static boolean isSecretBat(Entity e) {
        return e instanceof Bat b && b.isAlive() && b.isSilent() && b.isPersistenceRequired() && activeRun() != null;
    }

    @FunctionalInterface
    public interface BatKiller {
        int kill(Run run, AABB box, UUID by);
    }

    public static BatKiller batKiller = SecretsModule::killBats;

    private static int killBats(Run run, AABB box, UUID by) {
        int[] n = {0};
        Ec.safely("ads damage bats", () -> n[0] = batKiller.kill(run, box, by));
        return n[0];
    }

    public static boolean arrowHit(Entity e, ServerPlayer shooter) {
        Run run = activeRun();
        if (run == null) return false;
        MobRecord rec = mobFor(e);
        // a secret bat dies to the arrow like to any other damage
        if (rec == null) return isSecretBat(e) && killBats(run, e.getBoundingBox().inflate(BAT_HIT_MARGIN), shooter != null ? shooter.getUUID() : null) > 0;
        INSTANCE.hurtMob(run, rec.id, arrowDamage(), DamageSource.BOW, shooter != null ? shooter.getUUID() : null);
        return true;
    }

    private static final double BAT_HIT_MARGIN = 0.05;

    public static boolean beamHit(Entity e, ServerPlayer p) {
        Run run = activeRun();
        if (run == null) return false;
        MobRecord rec = mobFor(e);
        if (rec == null) {
            // a secret bat dies to the beam
            if (e instanceof Bat) killBats(run, e.getBoundingBox().inflate(0.3), p.getUUID());
            return false;
        }
        INSTANCE.hurtMob(run, rec.id, beamDamage(), DamageSource.MAGE_BEAM, p.getUUID());
        return true;
    }

    public static int blast(double x, double y, double z, DamageSource source, UUID by) {
        Run run = activeRun();
        if (run == null) return 0;
        double[] box = {x - BLAST_RADIUS, y - BLAST_RADIUS, z - BLAST_RADIUS, x + BLAST_RADIUS, y + BLAST_RADIUS, z + BLAST_RADIUS};
        List<UUID> ids = ImplosionRules.select(box, candidates(Services.mobs().all()));
        int bats = killBats(run, new AABB(box[0], box[1], box[2], box[3], box[4], box[5]), by);
        int n = 0;
        for (UUID id : ids) if (INSTANCE.hurtMob(run, id, blastDamage(), source, by)) n++;
        return n + bats;
    }

    @Override
    public InteractionResult onAttackEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        MobRecord rec = mobFor(entity);
        if (rec == null) return null;
        // a Mage's beam item fires from the click itself
        String held = SimItems.idOf(player.getMainHandItem());
        if (!(Mage.isMage() && Mage.beamItem(held))) hurtMob(run, rec.id, meleeDamage(), DamageSource.MELEE, player.getUUID());
        return InteractionResult.SUCCESS;
    }

    // ---- the Revive Stone

    public static final String REVIVE_STONE = "REVIVE_STONE";

    @Override
    public InteractionResult onUseItem(Run run, ServerPlayer player, InteractionHand hand) { return useReviveStone(run, player, hand); }

    @Override
    public InteractionResult onUseBlock(Run run, ServerPlayer player, InteractionHand hand, BlockHitResult hit) { return useReviveStone(run, player, hand); }

    private static InteractionResult useReviveStone(Run run, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !REVIVE_STONE.equals(SimItems.idOf(held))) return null;
        PlayerRecord me = run.state.players.get(player.getUUID());
        if (me == null) return null;
        if (me.ghost) return null;
        List<PlayerRecord> ghosts = new ArrayList<>();
        for (PlayerRecord r : run.state.players.values()) if (r.ghost && !r.uuid.equals(player.getUUID())) ghosts.add(r);
        if (ghosts.isEmpty()) {
            Sim.chat("§cThere are no players available to revive right now!");
            return InteractionResult.FAIL;
        }
        PlayerRecord dead = null;
        for (PlayerRecord g : ghosts) if (reviving.get(g.uuid) == null) { dead = g; break; }
        if (dead == null) {
            Sim.chat("§cSomeone else is currently reviving that player!");
            return InteractionResult.FAIL;
        }
        held.shrink(1);
        // the revive takes about 6 seconds and is announced when it starts
        reviving.put(dead.uuid, new Reviving(player.getUUID(), run.t() + PlayerLedger.REVIVE_TICKS));
        String line = "§a ❣ §r" + colourOf(me) + me.name + " §r§7is reviving §r" + colourOf(dead) + dead.name + "§r§7!";
        Sim.everyone(() -> Sim.chat(line));
        return InteractionResult.SUCCESS;
    }

    // ---- player side

    @Override
    public double hpPercent(Run run, UUID player) {
        PlayerRecord r = run.state.players.get(player);
        return r != null ? r.hpPercent : 100.0;
    }

    @Override
    public HurtResult meleeHit(Run run, UUID mob, UUID player) {
        MobRecord rec = Services.mobs().mob(mob);
        if (rec == null) return new HurtResult(0.0, false, true);
        PlayerDamageTable table = run.packs.mobs != null ? run.packs.mobs.playerDamage : null;
        String floor = run.config.floor;
        Double pct = null;
        if (table != null) {
            Map<String, HitQuantiles> byArch = table.meleeByArchetype != null ? table.meleeByArchetype.get(floor) : null;
            HitQuantiles hq = byArch != null ? byArch.get(rec.kind) : null;
            if (hq != null) pct = MeleeSampler.fromArchetype(hq, meleeRandom());
        }
        if (pct == null && table != null) {
            MeleeTable mt = table.melee != null ? table.melee.get(floor) : null;
            if (mt != null) pct = MeleeSampler.sample(mt, meleeRandom());
        }
        if (pct == null) pct = MeleeSampler.fromArchetype(MeleeSampler.FALLBACK, meleeRandom());
        return hit(run, player, pct, DamageSource.MELEE, mob, false, table != null ? table.immunityTicks : PlayerLedger.IMMUNITY_TICKS, rec.name);
    }

    @Override
    public HurtResult hurtPlayer(Run run, UUID player, double percent, DamageSource source, UUID attacker, boolean trueDamage) {
        String cause = null;
        if (attacker != null) {
            MobRecord m = Services.mobs().mob(attacker);
            cause = m != null ? m.name : null;
        }
        return hit(run, player, percent, source, attacker, trueDamage, PlayerLedger.IMMUNITY_TICKS, cause);
    }

    private static HurtResult hit(Run run, UUID player, double percent, DamageSource source, UUID attacker, boolean trueDamage, int immunity, String cause) {
        HurtResult ignored = new HurtResult(0.0, false, true);
        if (!run.stage.inDungeon()) return ignored;
        PlayerRecord rec = run.state.players.get(player);
        if (rec == null) return ignored;
        ServerPlayer sp = human(player);
        // a ghost, a proc's safe window and the Creeper Veil take the hit before the ledger sees it
        if (sp != null && !rec.ghost && Sim.asPlayer(sp, () -> Masks.covered())) return ignored;
        // the veil takes a hit outside the immunity window for free, never a true-damage hit
        boolean windowOpen = rec.lastHitT != Integer.MIN_VALUE && ((long) run.t() - rec.lastHitT) < immunity;
        if (sp != null && !rec.ghost && !windowOpen && !trueDamage && source != DamageSource.FALL && source != DamageSource.OTHER && source != DamageSource.TRAP && source != DamageSource.CRUSH
                && Sim.asPlayer(sp, () -> SimItems.veilAbsorbs())) return ignored;
        // a Wither Shield soaks the hit first
        double left = percent;
        WitherShield shield = shields.get(player);
        if (shield != null && shield.active(run.t()) && !windowOpen && !rec.ghost && percent > 0.0) {
            left = shield.absorb(run.t(), percent);
            if (left <= 0.0) {
                rec.lastHitT = run.t();
                felt(sp);
                run.bus.post(new PlayerDamaged(run.t(), player, 0.0, rec.hpPercent, source, attacker, trueDamage));
                return new HurtResult(0.0, false, false);
            }
        }
        regenCarry.remove(player);
        PlayerLedger.Hit h = PlayerLedger.hit(rec, left, run.t(), run.config.noDeaths && !rec.isBot, immunity);
        return switch (h.outcome) {
            case IGNORED -> ignored;
            case HURT, HELD -> {
                felt(sp);
                run.bus.post(new PlayerDamaged(run.t(), player, h.applied, rec.hpPercent, source, attacker, trueDamage));
                yield new HurtResult(h.applied, false, false);
            }
            case LETHAL -> lethal(run, rec, sp, source, attacker, trueDamage, cause);
        };
    }

    private static HurtResult lethal(Run run, PlayerRecord rec, ServerPlayer sp, DamageSource source, UUID attacker, boolean trueDamage, String cause) {
        if (sp != null && Sim.asPlayer(sp, () -> Masks.tryProc(sp))) {
            PlayerLedger.saved(rec);
            run.bus.post(new PlayerDamaged(run.t(), rec.uuid, 0.0, rec.hpPercent, source, attacker, trueDamage));
            felt(sp);
            return new HurtResult(0.0, false, true);
        }
        double taken = rec.hpPercent;
        felt(sp);
        die(run, rec, sp, source, attacker, cause);
        run.bus.post(new PlayerDamaged(run.t(), rec.uuid, taken, 0.0, source, attacker, trueDamage));
        return new HurtResult(taken, true, false);
    }

    private static void die(Run run, PlayerRecord rec, ServerPlayer sp, DamageSource source, UUID attacker, String cause) {
        PlayerLedger.die(rec, run.t());
        run.state.deaths.teamDeaths++;
        run.state.deaths.log.add(Pair.of(run.t(), rec.uuid));
        if (sp != null) Sim.asPlayer(sp, () -> Masks.die(sp, cause, PlayerLedger.REVIVE_TICKS, source == DamageSource.CRUSH));
        else {
            String line = deathLine(rec.name, source, cause);
            Sim.everyone(() -> Sim.chat(line));
        }
        regenCarry.remove(rec.uuid);
        run.bus.post(new PlayerDied(run.t(), rec.uuid, source, attacker));
    }

    static String deathLine(String name, DamageSource source, String cause) {
        if (source == DamageSource.DISCONNECT) return "§c ☠ §r§7" + name + " disconnected and became a ghost.";
        if (source == DamageSource.CRUSH) return "§c ☠ §r§7" + name + " was crushed and became a ghost.";
        if (cause == null) return "§c ☠ §r§7" + name + " died and became a ghost.";
        return "§c ☠ §r§7" + name + " was killed by " + cause + " and became a ghost.";
    }

    private static void felt(ServerPlayer sp) {
        if (sp == null) return;
        Ec.safely("ads damage felt", () -> {
            sp.connection.send(new ClientboundHurtAnimationPacket(sp.getId(), 0f));
            Sim.asPlayer(sp, () -> Sim.sound(SoundEvents.PLAYER_HURT, 1f, 1f, null, SoundSource.PLAYERS));
        });
    }

    @Override
    public void kill(Run run, UUID player, DamageSource source, UUID attacker) {
        if (!run.stage.inDungeon()) return;
        PlayerRecord rec = run.state.players.get(player);
        if (rec == null) return;
        if (rec.ghost) return;
        ServerPlayer sp = human(player);
        if (sp != null && Sim.asPlayer(sp, () -> Masks.covered())) return;
        // "No deaths" holds the ledger at the floor; the kill still counts as a hit for the window
        if (run.config.noDeaths && !rec.isBot) {
            rec.hpPercent = Math.min(rec.hpPercent, PlayerLedger.NO_DEATH_FLOOR);
            rec.lastHitT = run.t();
            return;
        }
        String cause = null;
        if (attacker != null) {
            MobRecord m = Services.mobs().mob(attacker);
            cause = m != null ? m.name : null;
        }
        lethal(run, rec, sp, source, attacker, false, cause);
    }

    @Override
    public void revive(Run run, UUID player, UUID by) {
        PlayerRecord rec = run.state.players.get(player);
        if (rec == null) return;
        if (!rec.ghost) return;
        PlayerLedger.revive(rec);
        PlayerRecord byRec = by != null ? run.state.players.get(by) : null;
        PlayerRecord reviver = byRec != null ? byRec : nearestMate(run, rec);
        ServerPlayer sp = human(player);
        if (sp != null) {
            String name = reviver != null ? reviver.name : null;
            String colour = reviver != null ? colourOf(reviver) : "§b";
            Sim.asPlayer(sp, () -> Masks.reviveGhost(sp, name, colour));
        } else {
            PlayerRecord who = reviver != null ? reviver : rec;
            String line = "§a ❣ §r" + colourOf(rec) + rec.name + "§r§a was revived by §r" + colourOf(who) + (reviver != null ? reviver.name : rec.name) + "§r§a!";
            Sim.everyone(() -> Sim.chat(line));
        }
        run.bus.post(new PlayerRevived(run.t(), player, by));
    }

    private static String colourOf(PlayerRecord rec) {
        return rec.extras.get("nameColour") instanceof String s ? s : "§b";
    }

    private static PlayerRecord nearestMate(Run run, PlayerRecord of) {
        PlayerRecord best = null;
        double bestD = 0.0;
        for (PlayerRecord it : run.state.players.values()) {
            if (it == of || it.ghost) continue;
            double d = (it.x - of.x) * (it.x - of.x) + (it.y - of.y) * (it.y - of.y) + (it.z - of.z) * (it.z - of.z);
            if (best == null || d < bestD) {
                best = it;
                bestD = d;
            }
        }
        return best;
    }

    // ---- dev commands

    private static final Map<String, LootItem> ITEM_NAMES;

    static {
        LinkedHashMap<String, LootItem> m = new LinkedHashMap<>();
        m.put("leap", LootItem.SPIRIT_LEAP);
        m.put("decoy", LootItem.DECOY);
        m.put("jerry", LootItem.INFLATABLE_JERRY);
        m.put("trap", LootItem.TRAP);
        m.put("kit", LootItem.DEFUSE_KIT);
        m.put("weights", LootItem.TRAINING_WEIGHTS);
        m.put("potion", LootItem.HEALING_POTION);
        ITEM_NAMES = Collections.unmodifiableMap(m);
    }

    private static Double toDoubleOrNull(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toIntOrNull(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static String f0(double v) { return String.format(Locale.ROOT, "%,.0f", v); }

    @Override
    public List<AdsCommand> devCommands() {
        return List.of(
                new AdsCommand("hurt", "/ads dev hurt <percent>", "Hurts you by that percent of max health, as a trap would (15 is a trap).", true, null, c -> {
                    Run run = c.run();
                    if (run == null) {
                        c.reply("No run is on.");
                    } else {
                        Double a = toDoubleOrNull(c.arg(0));
                        double pct = a != null ? a : 15.0;
                        HurtResult r = hurtPlayer(run, c.player.getUUID(), pct, DamageSource.TRAP, null, true);
                        c.reply("hurt " + f1(pct) + "%: took " + f1(r.applied) + ", ignored=" + r.ignored + ", died=" + r.died + "; health " + f1(hpPercent(run, c.player.getUUID())) + "%");
                    }
                }),
                new AdsCommand("heal", "/ads dev heal", "Sets your health to full and revives you if you are a ghost.", true, null, c -> {
                    Run run = c.run();
                    if (run == null) {
                        c.reply("No run is on.");
                    } else {
                        revive(run, c.player.getUUID(), null);
                        PlayerRecord r = run.state.players.get(c.player.getUUID());
                        if (r != null) r.hpPercent = 100.0;
                        c.reply("health 100%");
                    }
                }),
                new AdsCommand("cast", "/ads dev cast", "Casts an Implosion from where you stand and says what it hit.", true, null, c -> {
                    Run run = c.run();
                    if (run == null) {
                        c.reply("No run is on.");
                    } else {
                        Cast r = castImplosion(run, c.player);
                        c.reply("Implosion hit " + r.count + " mobs for " + f0(r.total) + " in all, " + r.killed + " died (floor " + run.config.floor + ").");
                    }
                }),
                new AdsCommand("mobhit", "/ads dev mobhit <hp>", "Takes that many HP off the nearest live mob within 30 blocks.", true, null, c -> {
                    Run run = c.run();
                    if (run == null) {
                        c.reply("No run is on.");
                    } else {
                        Double a = toDoubleOrNull(c.arg(0));
                        double amount = a != null ? a : 1_000_000.0;
                        ServerPlayer p = c.player;
                        MobRecord m = null;
                        double bestD = 0.0;
                        for (MobRecord it : Services.mobs().all()) {
                            if (!it.alive) continue;
                            double d = (it.x - p.getX()) * (it.x - p.getX()) + (it.y - p.getY()) * (it.y - p.getY()) + (it.z - p.getZ()) * (it.z - p.getZ());
                            if (m == null || d < bestD) {
                                m = it;
                                bestD = d;
                            }
                        }
                        if (m == null) c.reply("No live mob.");
                        else {
                            boolean ok = hurtMob(run, m.id, amount, DamageSource.OTHER, p.getUUID());
                            c.reply(m.name + " (" + m.kind + "): ok=" + ok + ", hp now " + f0(m.hp) + " of " + f0(m.maxHp));
                        }
                    }
                }),
                new AdsCommand("item", "/ads dev item <leap|decoy|jerry|trap|kit|weights|potion> [count]", "Puts a secret item in your inventory: the Spirit Leap stack (5 by default), or one of the items whose ability is not in ADS yet.", true, null, c -> {
                    String a0 = c.arg(0);
                    String name = a0 != null ? a0.toLowerCase(Locale.ROOT) : null;
                    LootItem item = name != null ? ITEM_NAMES.get(name) : null;
                    if (item == null) {
                        c.reply("Say which item: " + String.join(", ", ITEM_NAMES.keySet()) + ".");
                    } else {
                        Integer n = toIntOrNull(c.arg(1));
                        int count = n != null ? Math.clamp(n, 1, 64) : (item == LootItem.SPIRIT_LEAP ? 5 : 1);
                        Run run = c.run();
                        List<SkullTexture> textures = run != null && run.packs != null && run.packs.rooms != null && run.packs.rooms.textures != null ? run.packs.rooms.textures : List.of();
                        SecretWorld.give(c.player, SecretWorld.stack(item, count, textures));
                        c.reply("Gave " + count + " x " + item.displayName + ".");
                    }
                }),
                new AdsCommand("ledger", "/ads dev ledger", "Prints every party member's health percent, ghost state and deaths.", true, null, c -> {
                    Run run = c.run();
                    if (run == null) {
                        c.reply("No run is on.");
                    } else {
                        for (PlayerRecord r : run.state.players.values()) {
                            Object kills = r.extras.get(KILLS);
                            c.reply(r.name + ": " + f1(r.hpPercent) + "%" + (r.ghost ? " ghost, back at t=" + r.reviveAtT : "") + ", deaths " + r.deaths + ", kills " + (kills != null ? kills : 0));
                        }
                        c.reply("Team deaths " + run.state.deaths.teamDeaths + "; No deaths " + (run.config.noDeaths ? "on" : "off") + "; mobs with damage on the book " + mobLedger.size() + ".");
                    }
                })
        );
    }
}
