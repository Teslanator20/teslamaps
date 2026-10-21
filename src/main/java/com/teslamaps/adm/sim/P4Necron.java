package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/P4Necron.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class P4Necron extends Fight.Phase {
    public final boolean fromP3;

    public P4Necron() { this(false); }

    public P4Necron(boolean fromP3) {
        super("P4");
        this.fromP3 = fromP3;
    }

    @Override
    public Fight.Start restart() { return Fight.Start.P4; }

    private BossWither necron;

    private static final Vec3 MID = new Vec3(54.0, 66.0, 76.0);
    private static final int INTRO = 42;
    private static final int V1 = 20;
    private static final int L1 = 80; private static final int B1 = L1 + 18;
    private static final int FRENZY = 8;
    private static final int TAUNT1 = Math.max(3 * INTRO + 62, B1);
    private static final int ARGH1 = TAUNT1 + 82;
    private static final int SPACE = ARGH1 + 62;
    private static final int V2 = V1 + 155;
    private static final int L2 = V2 + 60; private static final int B2 = L2 + 5;
    private static final int BREAK = V2 + 100;
    private static final int THROES = ARGH1 + 40;
    private static final int TNT = ARGH1 + 100;
    public static final int GONE = ARGH1 + 119;
    private static final int STATS = ARGH1 + 146;
    public static final double HEAD_Y = 2.97;
    public static final double PITCH = Math.toRadians(11.8);
    public static final double FRENZY_RANGE = 12.0;
    public static final List<String> TAUNTS = List.of("Sometimes when you have a problem, you just need to destroy it all and start again.", "WITNESS MY RAW NUCLEAR POWER!");
    public static final Map<String, Vec3> PLATFORMS = platformMap();
    public static final List<Triple<Integer, Integer, Double>> PILLARS = List.of(Triple.of(92, 76, 0.75), Triple.of(82, 48, 0.73), Triple.of(26, 48, 0.72), Triple.of(54, 38, 0.71), Triple.of(26, 104, 0.70), Triple.of(82, 104, 0.69), Triple.of(16, 76, 0.52));
    public static final String CLASSES = "Healer,Berserk,Archer,Tank,Mage";

    private static Map<String, Vec3> platformMap() {
        Map<String, Vec3> m = new LinkedHashMap<>();
        m.put("N", new Vec3(54.0, 63.0, 42.0));
        m.put("NW", new Vec3(28.0, 63.0, 50.0));
        m.put("NE", new Vec3(80.0, 63.0, 50.0));
        m.put("SW", new Vec3(28.0, 63.0, 102.0));
        m.put("SE", new Vec3(80.0, 63.0, 102.0));
        return Collections.unmodifiableMap(m);
    }

    private final Vec3 side = sideDir();
    private final String taunt1 = TAUNTS.get(KRandom.DEFAULT.nextInt(TAUNTS.size()));
    private final String platform = randomKey();
    private final int barPhase = KRandom.DEFAULT.nextInt(20);
    private final ArrayList<Entity> spawned = new ArrayList<>();
    private List<Entity> tnts = List.of();

    private static Vec3 sideDir() {
        double deg = KRandom.DEFAULT.nextDouble(45.0, 75.0);
        if (!KRandom.DEFAULT.nextBoolean()) deg = -deg;
        double it = Math.toRadians(deg);
        return new Vec3(-Math.sin(it), KRandom.DEFAULT.nextDouble(-0.9, 0.9), Math.cos(it));
    }

    private static String randomKey() {
        List<String> keys = new ArrayList<>(PLATFORMS.keySet());
        return keys.get(KRandom.DEFAULT.nextInt(keys.size()));
    }

    @Override
    public void start() {
        if (!fromP3 && !Sim.players().isEmpty()) {
            Blocks.finish("p3end");
            Spots.Spot s = Spots.P4;
            Sim.forEachPlayer(p -> Sim.tp(p, s.x, s.y, s.z, s.yaw, s.pitch));
        }
        Party.startP4(fromP3);
        necron = new BossWither("Necron", MID);
        Sim.boss("Necron", "You went further than any human before, congratulations.");
        BossBar.show("§c§lNecron", 0f);
        pillars = Pillars.plan();
        storm = Storm.plan();
    }

    @Override
    public void stop() {
        for (Entity e : spawned) e.discard();
        spawned.clear();
        if (necron != null) necron.remove();
    }

    @Override
    public void tick() {
        if (t == INTRO) Sim.boss("Necron", "I'm afraid, your journey ends now.");
        else if (t == 2 * INTRO) Sim.boss("Necron", "Goodbye.");
        else if (t == 3 * INTRO) Sim.boss("Necron", "That's a very impressive trick. I guess I'll have to handle this myself.");
        else if (t == TAUNT1) Sim.boss("Necron", taunt1);
        else if (t == ARGH1) Sim.boss("Necron", "ARGH!");
        else if (t == SPACE) Sim.boss("Necron", "Let's make some space!");
        else if (t == BREAK) breakPlatform();
        else if (t == L2) skulls();
        else if (t == TNT) {
            List<Entity> list = new ArrayList<>();
            for (int i = 0; i < 10; i++) list.add(spawn(new PrimedTnt(Sim.level(), necron.pos.x, necron.pos.y, necron.pos.z, (LivingEntity) null)));
            tnts = list;
        }
        else if (t == TNT + 62) { for (Entity e : tnts) e.discard(); }
        else if (t == GONE - 20) necron.dieAnim();
        else if (t == GONE) necron.remove();
        else if (t == STATS) stats(true);
        else if (t == STATS + 3) { stats(false); if (com.teslamaps.adm.sim.run.Runs.current == null) Sim.note("Done. §fMenu§7 to go again."); }
        move();
        if (t % 20 == barPhase) BossBar.progress((float) Math.rint(health() * 100) / 100f);
        if (t >= V1 && t < V1 + 80 && (t - V1) % 10 == 0) fire(0.0);
        if (t >= V2 && t < V2 + 80 && (t - V2) % 10 == 0) fire(yawTo(PLATFORMS.get(platform)));
        if (t % 20 == FRENZY && ((t >= B1 + 1 && t < V2) || (t >= B2 + 1 && t < THROES))) frenzy();
        if (t >= THROES && (t - THROES) % 5 == 0 && (t - THROES) / 5 < 24) throe((t - THROES) / 5);
        pillars.tick(t);
        storm.tick(t);
    }

    private float health() {
        if (t < L1) return 0f;
        if (t < B1) return 1f - 0.2f * (t - L1) / (B1 - L1);
        if (t < V2 - 5) return 0.8f;
        if (t < V2 + 10) return 0.8f - 0.55f * (t - V2 + 5) / 15;
        if (t < L2) return 0.25f;
        if (t < B2) return 0.25f - 0.2f * (t - L2) / (B2 - L2);
        if (t < THROES) return 0.05f;
        return 0f;
    }

    private void move() {
        Vec3 target = PLATFORMS.get(platform);
        if (t >= L1 && t < L1 + 7) necron.moveTo(necron.pos.add(side.scale(0.25)), necron.pos.add(side.x, 0.0, side.z));
        else if (t >= L1 + 7 && t < L1 + 10) {}
        else if (t >= L1 + 10 && t < B1) necron.step(chase(), 0.49);
        else if (t >= L2 && t < B2) necron.step(chase(), 0.63);
        else if (t == B1 || t == B2) {
            necron.moveTo(MID, PhasesKt.closest(MID));
            Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 10f, 31 / 63f, MID);
        }
        else if (t >= V1 && t < V1 + 80) necron.moveTo(MID, MID.add(0.0, 0.0, 10.0));
        else if (t >= V2 && t < L2) necron.moveTo(MID, target);
        else if (t < GONE && t % 5 == 0) necron.moveTo(necron.pos, PhasesKt.closest(necron.pos));
    }

    private Vec3 chase() { return PhasesKt.closest(necron.pos).add(0.0, 2.0, 0.0); }

    private double yawTo(Vec3 p) { return Math.atan2(-(p.x - MID.x), p.z - MID.z); }

    private <T extends Entity> T spawn(T e) { Sim.spawn(e); spawned.add(e); return e; }

    private void fire(double yaw) {
        Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(PITCH), -Math.sin(PITCH), Math.cos(yaw) * Math.cos(PITCH));
        Vec3 from = necron.pos.add(0.0, HEAD_Y, 0.0);
        Fireball f = new Fireball(Sim.level());
        f.snapTo(from.x, from.y, from.z, 0f, 0f);
        f.setDeltaMovement(dir.scale(0.5));
        spawn(f);
    }

    private void skulls() {
        Vec3 at = PhasesKt.closest(necron.pos).add(0.0, 1.0, 0.0);
        Vec3 d = at.subtract(necron.pos);
        Vec3 rawRight = new Vec3(-d.z, 0.0, d.x);
        Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : rawRight.normalize();
        for (double s : new double[] {-1.3, 1.3}) {
            Vec3 from = necron.pos.add(right.scale(s)).add(0.0, 2.2, 0.0);
            Vec3 dir = at.subtract(from).normalize();
            Skull k = new Skull(Sim.level());
            k.snapTo(from.x, from.y, from.z, 0f, 0f);
            k.setDeltaMovement(dir.scale(0.1));
            spawn(k);
        }
    }

    private void breakPlatform() {
        List<Pair<BlockPos, BlockState>> blocks = Data.platforms().get(platform);
        if (blocks != null) for (Pair<BlockPos, BlockState> b : blocks) Blocks.set(b.first, b.second);
    }

    private void frenzy() {
        Sim.sound(SoundEvents.GENERIC_EXPLODE, 30f, 31 / 63f, necron.pos);
        Sim.sound(SoundEvents.WITHER_AMBIENT, 30f, 44 / 63f, necron.pos);
        Sim.forEachPlayer(p -> {
            if (p.position().distanceTo(MID) <= FRENZY_RANGE && !SimItems.cloaked()) Sim.chat("§cNecron's§r§7 Nuclear Frenzy hit you for §r§c57,600§r§7 damage.");
        });
    }

    private void throe(int i) {
        Sim.sound(SoundEvents.WITHER_HURT, 15f, 1f, necron.pos);
        if (i % 2 == 0) {
            Sim.sound(SoundEvents.GENERIC_EXPLODE, 15f, 31 / 63f, necron.pos);
            Vec3 at = necron.pos.add(0.0, 1.5, 0.0);
            Sim.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void stats(boolean first) { Sim.forEachPlayer(p -> stats(first, p)); }

    private void stats(boolean first, ServerPlayer p) {
        if (com.teslamaps.adm.sim.run.Runs.current != null) return;
        String bar = "§a§l" + "▬".repeat(64);
        List<String> classes = Arrays.asList(CLASSES.split(","));
        String mine = classes.get(Math.clamp(Party.CLASSES().indexOf(Players.classOf(p)), 0, 4));
        int total = Stats.runTicks();
        String scoreLine = "Team Score: §r§a" + score + " §r§f(§r§b§lS+§r§f)";
        String defeated = total > 0 ? String.format(Locale.ROOT, "§r§c☠ §r§eDefeated §r§cMaxor, Storm, Goldor, and Necron §r§ein §r§a%02dm %02ds", total / 1200, total / 20 % 60) : null;
        ArrayList<String> out = new ArrayList<>();
        out.add(bar);
        if (first) {
            out.add(centre("§r§cThe Catacombs §r§8- §r§eFloor VII")); out.add(""); out.add(centre(scoreLine));
            if (defeated != null) out.add(centre(defeated));
            out.add(centre("§6> §e§lEXTRA STATS §6<"));
            if (bits > 0) out.add(centre("§r§8+§r§b" + bits + " Bits"));
            out.add(centre("§r§8+§r§3" + xp(cataXp) + " Catacombs Experience"));
            out.add(centre("§r§8+§r§3" + xp(cataXp * 0.76) + " " + mine + " Experience"));
            List<String> others = new ArrayList<>();
            for (String c : classes) if (!c.equals(mine)) others.add(c);
            KRandom.shuffle(others, KRandom.DEFAULT);
            for (String c : others) out.add(centre("§r§8+§r§3" + xp(cataXp * 0.19) + " " + c + " Experience §r§b(Team Bonus)"));
        } else {
            out.add(centre("§r§cThe Catacombs §r§8- §r§eFloor VII Stats")); out.add(""); out.add(centre(scoreLine));
            if (defeated != null) out.add(centre(defeated));
            out.add("");
            out.add(centre("Total Damage as " + mine + ": §r§a" + String.format(Locale.US, "%,d", KRandom.DEFAULT.nextLong(600_000_000L, 900_000_000L))));
            out.add(centre("Ally Healing: §r§a" + String.format(Locale.US, "%,d", mine.equals("Healer") ? KRandom.DEFAULT.nextInt(50_000, 150_000) : KRandom.DEFAULT.nextInt(800, 4000))));
            out.add(centre("Enemies Killed: §r§a" + KRandom.DEFAULT.nextInt(120, 180)));
            out.add(centre("Deaths: §r§c0"));
            out.add(centre("Secrets Found: §r§b" + KRandom.DEFAULT.nextInt(3, 13)));
            out.add("");
        }
        out.add(bar);
        for (String line : out) Sim.chat(line);
    }

    private final int score = KRandom.DEFAULT.nextInt(300, 312);
    private final double cataXp = KRandom.DEFAULT.nextDouble(46_500.0, 50_000.0);
    private final int bits = KRandom.DEFAULT.nextDouble() < 0.72 ? KRandom.DEFAULT.nextInt(45, 80) : 0;

    private String xp(double v) {
        String s = String.format(Locale.US, "%,.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private String centre(String text) {
        int w = 0; boolean bold = false; int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char f = text.charAt(i + 1);
                if (f == 'l') bold = true; else if ("0123456789abcdefr".indexOf(f) >= 0) bold = false;
                i += 2; continue;
            }
            int cw = switch (c) {
                case '!', ',', '.', ':', ';', '\'', '|' -> 2;
                case 'i' -> 2;
                case 'l', '`' -> 3;
                case ' ', 'I', '[', ']', '"' -> 4;
                case 't', 'f', 'k', '(', ')', '<', '>', '*', '{', '}', '☠' -> 5;
                case '@' -> 7;
                default -> 6;
            };
            w += cw + (bold ? 1 : 0);
            i++;
        }
        return "§f" + " ".repeat(Math.max((int) Math.ceil((160 - w / 2.0) / 4), 0)) + text;
    }

    public String status() { return t < GONE ? "Necron " + (t / 20) + "s" : "Necron dead"; }

    private static class Fireball extends LargeFireball {
        private int life = 100;

        Fireball(ServerLevel level) { super(EntityType.FIREBALL, level); }

        @Override
        public void tick() { super.tick(); if (--life <= 0) discard(); }

        @Override
        protected void onHit(HitResult result) {
            if (result.getType() == HitResult.Type.BLOCK) {
                ServerLevel l = (ServerLevel) level();
                l.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                l.playSound((Entity) null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 4f, KRandom.DEFAULT.nextInt(38, 52) / 63f);
            }
            discard();
        }

        @Override
        protected boolean canHitEntity(Entity entity) { return false; }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    }

    private static class Skull extends WitherSkull {
        private int life = 100;

        Skull(ServerLevel level) { super(EntityType.WITHER_SKULL, level); }

        @Override
        public void tick() { super.tick(); if (--life <= 0) discard(); }

        @Override
        protected void onHit(HitResult result) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            discard();
        }

        @Override
        protected boolean canHitEntity(Entity entity) { return false; }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    }

    private Pillars pillars;
    private Storm storm;

    private static class Pillars {
        final List<Triple<Integer, BlockPos, BlockState>> frames;
        private int next = 0;

        Pillars(List<Triple<Integer, BlockPos, BlockState>> frames) { this.frames = frames; }

        void tick(int t) {
            while (next < frames.size() && frames.get(next).first <= t) { Blocks.set(frames.get(next).second, frames.get(next).third); next++; }
        }

        static Pillars plan() {
            Triple<Integer, Integer, Double> first = PILLARS.get(KRandom.DEFAULT.nextInt(PILLARS.size()));
            ArrayList<Triple<Integer, BlockPos, BlockState>> out = new ArrayList<>();
            out.addAll(pillar(first.first, first.second, L1 + 15 + KRandom.DEFAULT.nextInt(-3, 5)));
            int at = L2 + KRandom.DEFAULT.nextInt(-2, 5);
            List<Triple<Integer, Integer, Double>> shuffled = new ArrayList<>(PILLARS);
            KRandom.shuffle(shuffled, KRandom.DEFAULT);
            for (Triple<Integer, Integer, Double> p : shuffled) if (p != first && KRandom.DEFAULT.nextDouble() < p.third) out.addAll(pillar(p.first, p.second, at++));
            out.sort((a, b) -> Integer.compare(a.first, b.first));
            return new Pillars(out);
        }

        private static List<Triple<Integer, BlockPos, BlockState>> pillar(int x, int z, int start) {
            boolean side = z == 76;
            List<Triple<Integer, BlockPos, BlockState>> t = side ? Data.sideFall() : Data.cornerFall();
            int sx = side ? 16 : 82;
            int sz = side ? 76 : 48;
            boolean flipX = (x - 54) * (sx - 54) < 0; boolean flipZ = (z - 76) * (sz - 76) < 0;
            List<Triple<Integer, BlockPos, BlockState>> out = new ArrayList<>();
            for (Triple<Integer, BlockPos, BlockState> fr : t) {
                int dt = fr.first; BlockPos p = fr.second; BlockState s = fr.third;
                if (x == 54 && p.getY() < 64) continue;
                int dx = p.getX() - sx; int dz = p.getZ() - sz;
                int nx = x == 54 ? x + dx : x + (flipX ? -dx : dx);
                int nz = x == 54 ? z + dz : z + (flipZ ? -dz : dz);
                out.add(Triple.of(start + dt, new BlockPos(nx, p.getY(), nz), s));
            }
            return out;
        }
    }

    private static class Storm {
        final List<Integer> bolts;
        final List<Triple<Integer, BlockPos, BlockState>> frames;
        final Vec3 centre;
        private int nextBolt = 0; private int next = 0;

        Storm(List<Integer> bolts, List<Triple<Integer, BlockPos, BlockState>> frames, Vec3 centre) {
            this.bolts = bolts; this.frames = frames; this.centre = centre;
        }

        void tick(int t) {
            while (next < frames.size() && frames.get(next).first <= t) { Blocks.set(frames.get(next).second, frames.get(next).third); next++; }
            while (nextBolt < bolts.size() && bolts.get(nextBolt) <= t) {
                nextBolt++;
                double a = KRandom.DEFAULT.nextDouble(Math.PI * 2); double r = 12 * Math.sqrt(KRandom.DEFAULT.nextDouble());
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, Sim.level());
                bolt.setVisualOnly(true);
                bolt.snapTo(centre.x + r * Math.cos(a), 63.0, centre.z + r * Math.sin(a), 0f, 0f);
                Sim.spawn(bolt);
            }
        }

        static Storm plan() {
            List<Triple<Integer, Integer, String>> cols = Data.columns();
            if (cols.isEmpty()) return new Storm(List.of(), List.of(), Vec3.ZERO);
            Triple<Integer, Integer, String> col = cols.get(KRandom.DEFAULT.nextInt(cols.size()));
            int x0 = col.first; int z0 = col.second; String kind = col.third;
            Data.StormData s = Data.storms().get(kind);
            if (s == null) return new Storm(List.of(), List.of(), Vec3.ZERO);
            int start = 22;
            ArrayList<Triple<Integer, BlockPos, BlockState>> frames = new ArrayList<>();
            for (Triple<Integer, Integer, BlockState> f : s.frames) {
                for (int dx = 0; dx <= 2; dx++) for (int dz = 0; dz <= 2; dz++) frames.add(Triple.of(start + f.first, new BlockPos(x0 + dx, f.second, z0 + dz), f.third));
            }
            List<Integer> bolts = new ArrayList<>();
            for (int b : s.bolts) bolts.add(start + b);
            return new Storm(bolts, frames, new Vec3(x0 + 1.5, 63.0, z0 + 1.5));
        }
    }

    private static final class Data {
        private Data() {}

        static final class StormData {
            final List<Integer> bolts;
            final List<Triple<Integer, Integer, BlockState>> frames;

            StormData(List<Integer> bolts, List<Triple<Integer, Integer, BlockState>> frames) { this.bolts = bolts; this.frames = frames; }
        }

        private static JsonObject root;
        private static boolean rootDone = false;

        private static synchronized JsonObject root() {
            if (!rootDone) {
                rootDone = true;
                try (InputStream in = P4Necron.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/p4-necron.json")) {
                    if (in == null) throw new NullPointerException("/assets/teslamaps/adm/p3sim/p4-necron.json");
                    root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (Throwable t) {
                    Ec.LOG.warn("[p3sim] p4-necron.json", t);
                    root = null;
                }
            }
            return root;
        }

        private static final HashMap<String, BlockState> states = new HashMap<>();

        private static BlockState state(String s) { return states.computeIfAbsent(s, k -> Arena.parse(k.indexOf(':') >= 0 ? k : "minecraft:" + k)); }

        private static Map<String, List<Pair<BlockPos, BlockState>>> platforms;

        static synchronized Map<String, List<Pair<BlockPos, BlockState>>> platforms() {
            if (platforms == null) {
                Map<String, List<Pair<BlockPos, BlockState>>> m = new LinkedHashMap<>();
                JsonObject r = root();
                JsonObject o = r != null ? r.getAsJsonObject("platforms") : null;
                if (o != null) {
                    for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                        List<Pair<BlockPos, BlockState>> l = new ArrayList<>();
                        for (JsonElement e : en.getValue().getAsJsonArray()) {
                            JsonArray a = e.getAsJsonArray();
                            l.add(Pair.of(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()), state(a.get(3).getAsString())));
                        }
                        m.put(en.getKey(), l);
                    }
                }
                platforms = m;
            }
            return platforms;
        }

        private static List<Triple<Integer, Integer, String>> columns;

        static synchronized List<Triple<Integer, Integer, String>> columns() {
            if (columns == null) {
                List<Triple<Integer, Integer, String>> l = new ArrayList<>();
                JsonObject r = root();
                JsonObject o = r != null ? r.getAsJsonObject("columns") : null;
                if (o != null) {
                    for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                        JsonArray a = en.getValue().getAsJsonArray();
                        l.add(Triple.of(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsString()));
                    }
                }
                columns = l;
            }
            return columns;
        }

        private static Map<String, StormData> storms;

        static synchronized Map<String, StormData> storms() {
            if (storms == null) {
                BlockState lantern = state("sea_lantern"); BlockState iron = state("iron_block");
                Map<String, StormData> m = new LinkedHashMap<>();
                JsonObject r = root();
                JsonObject o = r != null ? r.getAsJsonObject("storms") : null;
                if (o != null) {
                    for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                        JsonObject so = en.getValue().getAsJsonObject();
                        List<Integer> bolts = new ArrayList<>();
                        for (JsonElement it : so.getAsJsonArray("bolts")) bolts.add(it.getAsInt());
                        List<Triple<Integer, Integer, BlockState>> frames = new ArrayList<>();
                        for (JsonElement e : so.getAsJsonArray("frames")) {
                            JsonArray a = e.getAsJsonArray();
                            frames.add(Triple.of(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsString().equals("s") ? lantern : iron));
                        }
                        m.put(en.getKey(), new StormData(bolts, frames));
                    }
                }
                storms = m;
            }
            return storms;
        }

        private static List<Triple<Integer, BlockPos, BlockState>> fall(int x, int z) {
            Blocks.Anim anim = Blocks.anim("p4");
            if (anim == null) return List.of();
            List<Blocks.Frame> f = anim.frames;
            List<Blocks.Frame> mine = new ArrayList<>();
            for (Blocks.Frame fr : f) {
                if (fr.state.isAir()) continue;
                Triple<Integer, Integer, Double> best = null;
                int bestD = 0;
                for (Triple<Integer, Integer, Double> pp : PILLARS) {
                    int px = pp.first; int pz = pp.second;
                    int d = (fr.pos.getX() - px) * (fr.pos.getX() - px) + (fr.pos.getZ() - pz) * (fr.pos.getZ() - pz);
                    if (best == null || d < bestD) { best = pp; bestD = d; }
                }
                if (best.first == x && best.second == z) mine.add(fr);
            }
            if (mine.isEmpty()) return List.of();
            int t0 = Integer.MAX_VALUE;
            for (Blocks.Frame fr : mine) t0 = Math.min(t0, fr.dt);
            List<Triple<Integer, BlockPos, BlockState>> out = new ArrayList<>();
            for (Blocks.Frame fr : mine) out.add(Triple.of(fr.dt - t0, fr.pos, fr.state));
            return out;
        }

        private static List<Triple<Integer, BlockPos, BlockState>> cornerFall;
        private static List<Triple<Integer, BlockPos, BlockState>> sideFall;

        static synchronized List<Triple<Integer, BlockPos, BlockState>> cornerFall() {
            if (cornerFall == null) cornerFall = fall(82, 48);
            return cornerFall;
        }

        static synchronized List<Triple<Integer, BlockPos, BlockState>> sideFall() {
            if (sideFall == null) sideFall = fall(16, 76);
            return sideFall;
        }
    }
}
