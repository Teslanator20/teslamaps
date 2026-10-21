package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/P2Storm.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class P2Storm extends Fight.Phase {
    public P2Storm() { super("P2"); }

    @Override
    public Fight.Start restart() { return Fight.Start.P2; }

    private BossWither storm;

    public class Pillar {
        public final String name;
        public final int minX;
        public final int minZ;
        public int bottom;
        public final AABB pad;
        public int steps = 0;
        public int nextStep = -1;
        public int lastStep = -1000;
        public int floorAt = -1;
        public boolean raising = false;
        public int lowerTo = -1;
        public boolean spent = false;
        public int resetAt = -1;

        public Pillar(String name, int minX, int minZ, int bottom, AABB pad) {
            this.name = name; this.minX = minX; this.minZ = minZ; this.bottom = bottom; this.pad = pad;
        }

        public AABB square() { return new AABB((double) minX, 168.5, (double) minZ, minX + 7.0, 170.5, minZ + 7.0); }
        public Vec3 centre() { return new Vec3(minX + 3.5, 169.0, minZ + 3.5); }
        public boolean under(Vec3 p) { return square().contains(p); }
        public boolean resting() { return !spent && steps == 0 && floorAt < 0 && !raising && lowerTo < 0 && bottom > 169; }
    }

    private final List<Pillar> pillars = List.of(
        new Pillar("Purple", 97, 62, 175, new AABB(111.0, 169.5, 91.0, 118.0, 171.5, 98.0)),
        new Pillar("Yellow", 43, 62, 175, new AABB(29.0, 169.5, 91.0, 36.0, 171.5, 98.0)),
        new Pillar("Green", 43, 38, 175, new AABB(29.0, 169.5, 9.0, 36.0, 171.5, 16.0)),
        new Pillar("Red", 97, 38, 181, null)
    );
    private Pillar purple() { return pillars.get(0); }
    private Pillar yellow() { return pillars.get(1); }

    private List<Pair<Integer, Integer>> footprint;

    private List<Pair<Integer, Integer>> footprint() {
        if (footprint == null) {
            List<Pair<Integer, Integer>> out = null;
            JsonObject o = Blocks.extra("anims-p124.json", "pillars");
            JsonArray arr = o != null ? o.getAsJsonArray("footprint") : null;
            if (arr != null) {
                out = new ArrayList<>();
                for (JsonElement it : arr) { JsonArray a = it.getAsJsonArray(); out.add(Pair.of(a.get(0).getAsInt(), a.get(1).getAsInt())); }
            }
            if (out == null) {
                out = new ArrayList<>();
                for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
                    if (!((x == 0 || x == 6) && (z < 2 || z > 4)) && !((x == 1 || x == 5) && (z == 0 || z == 6))) out.add(Pair.of(x, z));
                }
            }
            footprint = out;
        }
        return footprint;
    }

    private final List<Vec3> ROUTE = List.of(new Vec3(73.0, 183.0, 83.0), new Vec3(43.0, 183.0, 53.0), new Vec3(73.0, 183.0, 23.0), new Vec3(103.0, 183.0, 53.0));
    private final Vec3 PARK = new Vec3(102.375, 183.0, 52.375);
    private int leg = 0;
    private int crushes = 0;
    private boolean pinnedUntilBeam = false;
    private int pinnedAt = -1;
    private int enrageAt = -1;
    private int enragedAt = -1;
    private int takeoffAt = -1;
    private int crush2At = -1;
    private Pillar lastCrush = null;
    private boolean flyingToYellow = false;
    private int deadAt = -1;
    private int lightningAt = 548;
    private final int LEAVE = 99;
    private final int giga2 = 19 + KRandom.DEFAULT.nextInt(2);
    private int lightning2At() { return lightningAt + 940; }
    private boolean chasing = false;
    private boolean laterChase = false;
    private int chaseSince = -1;
    private boolean handed = false;
    private double gigaDamage = 9350.0;
    private int nextTaunt = -1;
    private final List<String> TAUNTS = List.of(
        "BEGONE PILLAR!", "No more adventurers, no more heroes, death and thunder!", "FINALLY! This took way too long.",
        "Not just your land, but every kingdom will soon be ruled by our army of undead!",
        "This factory is too small for me!", "Slowing me down will be your greatest accomplishment!",
        "The days are numbered until I am finally unleashed again on the world!", "Now that you're a Ghost, can you help me clean up?",
        "The Age of Men is over, we are creating tens, hundreds of withers!!", "THAT WAS ONLY IN MY WAY!"
    );

    @Override
    public void start() {
        if (!(Fight.previous instanceof P1Maxor)) Sim.forEachPlayer(p -> {
            Spots.Spot s = Spots.PURPLE_PAD;
            Sim.tp(p, s.x, s.y, s.z, s.yaw, s.pitch);
            SimItems.giveHotbar(p, false);
        });
        storm = new BossWither("Storm", new Vec3(103.0, 188.0, 53.0));
        line("Pathetic Maxor, just like expected.");
        startBots();
        double r = KRandom.DEFAULT.nextDouble();
        gigaDamage = (double) Math.round(r < 0.5 ? 1783 + r * 2 * (9350 - 1783) : 9350 + (r - 0.5) * 2 * (18194 - 9350));
        double q = KRandom.DEFAULT.nextDouble();
        nextTaunt = q < 0.5 ? KRandom.DEFAULT.nextInt(844, 848) : q < 0.85 ? KRandom.DEFAULT.nextInt(848, 882) : KRandom.DEFAULT.nextInt(882, 902);
        nextFireball = 75 + (int) (Math.pow(KRandom.DEFAULT.nextDouble(), 2.0) * 150);
    }

    @Override
    public void stop() {
        if (storm != null && !handed) storm.remove();
        if (!handed) StormFx.clearSpeech();
    }

    private void line(String text) { StormFx.line(storm, text); }

    @Override
    public void tick() {
        switch (t) {
            case 62 -> line("Don't boast about beating this simple-minded Wither.");
            case 124 -> line("My abilities are unparalleled, in many ways I am the last bastion.");
            case 186 -> line("The memory of your death will be your fondest, focus up!");
            case 424 -> line("The power of lightning is quite phenomenal. A single strike can vaporize a person whole.");
            case 486 -> line("I'd be happy to show you what that's like!");
            default -> {}
        }
        if (t == lightningAt || (t == lightning2At() && deadAt < 0)) line(KRandom.DEFAULT.nextBoolean() ? "ENERGY HEED MY CALL!" : "THUNDER LET ME BE YOUR CATALYST!");
        if (t == lightningAt + 10 || t == lightningAt + giga2) giga();
        if (deadAt < 0 && (t == lightning2At() + 36 || t == lightning2At() + 46)) giga();
        if (deadAt < 0 && t == nextTaunt) { line(TAUNTS.get(KRandom.DEFAULT.nextInt(TAUNTS.size()))); nextTaunt = t + KRandom.DEFAULT.nextInt(60, 64); }
        for (Pillar pl : pillars) tickPillar(pl);
        fields.removeIf(f -> !f.tick());
        bar();
        tickBots();
        StormFx.follow();
        if (deadAt >= 0 && t >= deadAt) { dead(); return; }
        move();
        if (t % 20 == 19) check();
        if (deadAt == t) { dead(); return; }
        attacks();
        if (pinnedUntilBeam && crushes < 2) {
            if (beamed && t >= pinnedAt && takeoffAt < 0) { int e = t + KRandom.DEFAULT.nextInt(2); if (enrageAt < 0 || e < enrageAt) enrageAt = e; }
            if (enrageAt >= 0 && t >= enrageAt && takeoffAt < 0) release();
            if (takeoffAt >= 0 && t >= takeoffAt) takeoff();
        }
        beamed = false;
    }

    private void dead() {
        int d = t - deadAt;
        if (d == 0) { line("I should have known that I stood no chance."); handed = true; new StormCorpse(storm).start(); }
        else if (d == StormEnd.SON) line("At least my son died by your hands.");
        else if (d == StormEnd.LEAD - GoldorPhase.LEAD_IN) Fight.begin(new GoldorPhase(1, true));
    }

    private boolean beamed = false;

    public void beam() { if (PhasesKt.lookingAt(storm.pos.add(0.0, 2.0, 0.0), 6.0, 45.0)) beamed = true; }

    private int pinLength() {
        double r = KRandom.DEFAULT.nextDouble();
        if (r < 0.40) return KRandom.DEFAULT.nextInt(0, 5);
        if (r < 0.50) return KRandom.DEFAULT.nextInt(5, 10);
        if (r < 0.72) return KRandom.DEFAULT.nextInt(10, 21);
        if (r < 0.80) return 24;
        if (r < 0.90) return KRandom.DEFAULT.nextInt(21, 26);
        return KRandom.DEFAULT.nextInt(26, 61);
    }

    private int deathDelay() {
        double r = KRandom.DEFAULT.nextDouble();
        if (r < 0.10) return KRandom.DEFAULT.nextInt(0, 3);
        if (r < 0.55) return KRandom.DEFAULT.nextInt(3, 8);
        if (r < 0.70) return KRandom.DEFAULT.nextInt(8, 11);
        if (r < 0.85) return KRandom.DEFAULT.nextInt(11, 22);
        if (r < 0.92) return KRandom.DEFAULT.nextInt(22, 25);
        return KRandom.DEFAULT.nextInt(25, 31);
    }

    private void move() {
        if (pinnedUntilBeam) return;
        if (flyingToYellow) {
            Vec3 pt = new Vec3(46.0, 172.8, 65.0);
            double d = Math.hypot(storm.pos.x - pt.x, storm.pos.z - pt.z);
            if (d <= 2.4) { flyingToYellow = false; laterChase = true; chaseSince = t; return; }
            double skip = storm.pos.x > 82 ? 0.02 : storm.pos.x > 70 ? 0.07 : 0.0;
            if (KRandom.DEFAULT.nextDouble() < skip) return;
            storm.step(pt, Math.min(0.7157, 0.36 + 0.0134 * d)); return;
        }
        if (laterChase) {
            Vec3 target = PhasesKt.closest(storm.pos);
            Vec3 aim = target.add(0.0, 3.3, 0.0);
            double d = Math.hypot(aim.x - storm.pos.x, aim.z - storm.pos.z);
            if (d > 3.0) {
                double sp = 0.145 + 0.0205 * d;
                double dy = Math.clamp(aim.y - storm.pos.y, -0.3, 0.3);
                storm.moveTo(new Vec3(storm.pos.x + (aim.x - storm.pos.x) / d * sp, storm.pos.y + dy, storm.pos.z + (aim.z - storm.pos.z) / d * sp), target);
            } else storm.moveTo(storm.pos, target);
            return;
        }
        if (chasing) {
            Vec3 target = PhasesKt.closest(storm.pos);
            Vec3 aim = target.add(0.0, 3.0, 0.0);
            if (Math.hypot(storm.pos.x - aim.x, storm.pos.z - aim.z) > 3.0) storm.step(aim, Math.min(0.9, 0.2 + 0.023 * storm.pos.distanceTo(aim)), target);
            return;
        }
        if (leg < ROUTE.size()) {
            if (storm.pos.distanceTo(ROUTE.get(leg)) < 1.0) { leg++; if (leg == ROUTE.size()) storm.moveTo(PARK); }
            if (leg < ROUTE.size()) storm.step(ROUTE.get(leg), 0.40);
        }
        if (t >= lightningAt + LEAVE) { chasing = true; chaseSince = t; }
    }

    private void check() {
        List<Vec3> on = everyone();
        for (Pillar pl : pillars) {
            if (pl.pad != null && pl.resting()) {
                boolean any = false;
                for (Vec3 it : on) if (pl.pad.contains(it)) { any = true; break; }
                if (any) pl.steps = 5;
            }
            if (pl.pad != null && pl.steps > 0 && pl.nextStep < 0) pl.nextStep = t;
        }
        if (deadAt >= 0 || pinnedUntilBeam) return;
        for (Pillar pl : pillars) {
            if (pl.spent) continue;
            Vec3 s = storm.pos;
            boolean inZone = s.x >= pl.minX && s.x <= pl.minX + 6 && s.z >= pl.minZ && s.z <= pl.minZ + 6;
            if (inZone && s.y + 2.975 >= pl.bottom && t - pl.lastStep <= 60) { crush(pl); return; }
        }
    }

    private void crush(Pillar pl) {
        crushes++;
        line(KRandom.DEFAULT.nextBoolean() ? "Ouch, that hurt!" : "Oof");
        storm.armour(false);
        pl.resetAt = t + 20;
        pl.spent = true;
        pl.steps = 0; pl.nextStep = -1;
        storm.moveTo(storm.pos);
        flyingToYellow = false; chasing = false; laterChase = false;
        stuck = null;
        if (crushes >= 2) { deadAt = t + deathDelay(); crush2At = t; pinnedUntilBeam = true; return; }
        pinnedUntilBeam = true;
        pinnedAt = t;
        lastCrush = pl;
        takeoffAt = -1;
        enrageAt = !Players.isPlayerClass(DungeonClass.MAGE) && (P3Sim.bots() || !P3Sim.mageAll()) ? t + pinLength() : -1;
    }

    private void release() {
        if (crushes >= 2) return;
        Sim.chat("§c⚠ Storm is enraged! ⚠");
        enragedAt = t;
        takeoffAt = t + (KRandom.DEFAULT.nextDouble() < 0.6 ? 2 : 3);
        nextSkull = t + 6 + (int) (Math.pow(KRandom.DEFAULT.nextDouble(), 1.6) * 41);
        yellowPlan();
    }

    private void takeoff() {
        pinnedUntilBeam = false;
        storm.armour(true);
        takeoffAt = -1; enrageAt = -1;
        chasing = false;
        if (lastCrush != null && "Purple".equals(lastCrush.name)) { flyingToYellow = true; laterChase = false; } else { laterChase = true; chaseSince = t; }
    }

    private void pushDown(Pillar pl) {
        if (storm == null || handed) return;
        Vec3 s = storm.pos;
        if (s.x + 0.45 <= pl.minX || s.x - 0.45 >= pl.minX + 7 || s.z + 0.45 <= pl.minZ || s.z - 0.45 >= pl.minZ + 7) return;
        if (s.y >= pl.bottom + 1 || s.y + 3.5 <= pl.bottom) return;
        double y = Math.max(169.0, pl.bottom - 3.5);
        if (y < s.y) storm.moveTo(new Vec3(s.x, y, s.z));
    }

    private void tickPillar(Pillar pl) {
        if (pl.resetAt >= 0 && t >= pl.resetAt) {
            pl.resetAt = -1;
            for (int y = 169; y < RESET_BOTTOM; y++) layer(pl, y, false);
            for (int y = RESET_BOTTOM; y <= 189; y++) layer(pl, y, true);
            pl.bottom = RESET_BOTTOM;
            return;
        }
        if (pl.spent) return;
        if (pl.nextStep >= 0 && t >= pl.nextStep && pl.steps > 0) {
            if (pl.bottom > 169) { pl.bottom--; layer(pl, pl.bottom, true); pl.lastStep = t; pushDown(pl); piston(pl, SoundEvents.PISTON_EXTEND, pl.bottom); }
            pl.steps--;
            pl.nextStep = pl.steps > 0 ? t + 4 : -1;
            if (pl.bottom <= 169) { pl.steps = 0; pl.nextStep = -1; pl.floorAt = t; }
        }
        if (pl.floorAt >= 0 && t >= pl.floorAt + 28) { pl.floorAt = -1; pl.raising = true; pl.nextStep = t; }
        if (pl.raising && t >= pl.nextStep) {
            if (pl.bottom < 189) { layer(pl, pl.bottom, false); piston(pl, SoundEvents.PISTON_CONTRACT, pl.bottom); pl.bottom++; pl.nextStep = t + 4; }
            else { pl.raising = false; pl.lowerTo = 186; pl.nextStep = t + 24; }
        }
        if (pl.lowerTo >= 0 && t >= pl.nextStep) {
            if (pl.bottom > pl.lowerTo) { pl.bottom--; layer(pl, pl.bottom, true); pushDown(pl); piston(pl, SoundEvents.PISTON_EXTEND, pl.bottom); pl.nextStep = t + 4; } else pl.lowerTo = -1;
        }
    }

    private void piston(Pillar pl, SoundEvent s, int y) {
        Sim.sound(s, 3f, 1f, new Vec3(pl.minX + 0.5, y + 0.5, pl.minZ + 2.5), net.minecraft.sounds.SoundSource.BLOCKS);
    }

    private void layer(Pillar pl, int y, boolean solid) {
        BlockState st = solid ? net.minecraft.world.level.block.Blocks.POLISHED_DIORITE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (Pair<Integer, Integer> f : footprint()) Blocks.set(new BlockPos(pl.minX + f.first, y, pl.minZ + f.second), st);
    }

    private List<Vec3> everyone() {
        List<Vec3> out = new ArrayList<>();
        for (ServerPlayer p : Sim.players()) out.add(p.position());
        for (Party.Bot b : Party.bots()) if (b.entity != null) out.add(b.pos);
        return out;
    }

    private void giga() {
        HashSet<Pillar> rings = new HashSet<>();
        for (Vec3 p : everyone()) {
            Pillar under = null;
            for (Pillar pl : pillars) if (pl.under(p)) { under = pl; break; }
            if (under != null) { rings.add(under); continue; }
            StormFx.bolt(p.x + KRandom.DEFAULT.nextDouble(-0.5, 0.5), p.z + KRandom.DEFAULT.nextDouble(-1.0, 1.0), p.y);
            for (int k = 0; k < 30; k++) StormFx.bolt(p.x + KRandom.DEFAULT.nextDouble(-8.0, 8.0), p.z + KRandom.DEFAULT.nextDouble(-8.0, 8.0), p.y);
        }
        for (Pillar pl : rings) for (int i = 0; i < 40; i++) {
            double a = 2 * Math.PI * i / 40;
            StormFx.bolt(pl.minX + 4 + 7 * Math.cos(a), pl.minZ + 4 + 7 * Math.sin(a), 169.0);
        }
        Sim.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 1.4f);
        Sim.forEachPlayer(p -> {
            boolean underAny = false;
            for (Pillar pl : pillars) if (pl.under(p.position())) { underAny = true; break; }
            if (!underAny && !SimItems.cloaked()) StormFx.hit("Giga Lightning", gigaDamage, true);
        });
    }

    private int nextFireball = -1;
    private int nextSkull = -1;
    private final int frenzyPhase = KRandom.DEFAULT.nextInt(10);

    private void attacks() {
        if (leg < ROUTE.size() && t >= nextFireball && nextFireball >= 0) {
            fireball();
            double r = KRandom.DEFAULT.nextDouble();
            nextFireball = t + (r < 0.8 ? KRandom.DEFAULT.nextInt(37, 52) : KRandom.DEFAULT.nextInt(52, 71));
        }
        boolean free = crushes == 1 && !pinnedUntilBeam;
        if (free && nextSkull >= 0 && t >= nextSkull) { skulls(); nextSkull = t + 5; }
        if (free && t % 10 == frenzyPhase) Sim.forEachPlayer(p -> {
            if (p.position().distanceTo(storm.pos.add(0.0, 1.5, 0.0)) <= 6.3 && !SimItems.cloaked()) {
                double v = KRandom.DEFAULT.nextDouble() < 0.7 ? KRandom.DEFAULT.nextDouble(1990.0, 2170.0) : KRandom.DEFAULT.nextDouble(2170.0, 14000.0);
                StormFx.hit("Frenzy", Math.round(v * 10) / 10.0);
            }
        });
    }

    private void fireball() {
        List<Vec3> targets = everyone();
        if (targets.isEmpty()) return;
        Vec3 to = targets.get(KRandom.DEFAULT.nextInt(targets.size())).add(0.0, 1.0, 0.0);
        Vec3 from = storm.pos.add(0.0, 0.8, 0.0);
        Vec3 dir = to.subtract(from).normalize();
        StormFireball f = new StormFireball(Sim.level(), this::impact);
        f.setOwner(storm.e);
        f.snapTo(from.x, from.y, from.z, 0f, 0f);
        f.accelerationPower = 0.15;
        f.setDeltaMovement(dir.scale(0.001));
        Sim.spawn(f);
    }

    private void impact(Vec3 at) {
        if (Fight.phase != this) return;
        StormFx.burst(at);
        Sim.forEachPlayer(p -> {
            if (p.position().add(0.0, 0.9, 0.0).distanceTo(at) <= 3.5 && !SimItems.cloaked()) StormFx.hit("Lightning Fireball", 75000.0);
        });
        if (KRandom.DEFAULT.nextDouble() < 0.55) fields.add(new Field(at, KRandom.DEFAULT.nextDouble() < 0.55, t));
    }

    private void skulls() {
        Vec3 target = PhasesKt.closest(storm.pos).add(0.0, 1.0, 0.0);
        double r = Math.toRadians((double) storm.e.getYRot());
        Vec3 side = new Vec3(Math.cos(r), 0.0, Math.sin(r));
        for (double s : new double[] {-1.3, 1.3}) {
            Vec3 from = storm.pos.add(0.0, 2.2, 0.0).add(side.scale(s));
            StormSkull k = new StormSkull(Sim.level());
            k.setOwner(storm.e);
            k.snapTo(from.x, from.y, from.z, 0f, 0f);
            k.accelerationPower = 0.1;
            k.setDeltaMovement(target.subtract(from).normalize().scale(0.001));
            Sim.spawn(k);
        }
    }

    private class Field {
        final Vec3 c;
        final boolean plus;
        final int start;
        final HashSet<String> hit = new HashSet<>();

        Field(Vec3 c, boolean plus, int start) { this.c = c; this.plus = plus; this.start = start; }

        boolean tick() {
            int k = RING_T.indexOf(t - start);
            if (k < 0) return t - start < RING_T.get(RING_T.size() - 1);
            double a = 1.8 * k;
            List<Pair<Double, Double>> pts;
            if (k == 0) { pts = new ArrayList<>(); for (int i = 0; i < 4; i++) pts.add(Pair.of(0.0, 0.0)); }
            else if (plus) pts = List.of(Pair.of(a, 0.0), Pair.of(-a, 0.0), Pair.of(0.0, a), Pair.of(0.0, -a));
            else pts = List.of(Pair.of(a, a), Pair.of(a, -a), Pair.of(-a, a), Pair.of(-a, -a));
            for (Pair<Double, Double> pt : pts) {
                Vec3 b = StormFx.bolt(c.x + pt.first, c.z + pt.second, c.y + 0.5);
                Sim.forEachPlayer(pl -> {
                    Vec3 p = pl.position();
                    String name = pl.getGameProfile().name();
                    if (!hit.contains(name) && Math.hypot(b.x - p.x, b.z - p.z) <= 3.0 && Math.abs(b.y - p.y) < 3 && !SimItems.cloaked()) {
                        hit.add(name);
                        StormFx.hit("Static Field", 10800.0);
                    }
                });
            }
            return k < RING_T.size() - 1;
        }
    }

    private final ArrayList<Field> fields = new ArrayList<>();

    private final int barPhase = KRandom.DEFAULT.nextInt(20);

    private void bar() {
        if (t % 20 != barPhase && !(t == 0 && !(Fight.previous instanceof P1Maxor))) return;
        float v;
        if (deadAt >= 0 && t >= deadAt) v = 0f;
        else if (crush2At >= 0) v = (float) (0.40 * (1 - (double) (t - crush2At) / Math.max(1, deadAt - crush2At)));
        else if (enragedAt >= 0) v = 0.45f;
        else if (crushes == 1) {
            if (enrageAt > pinnedAt) v = (float) (0.45 + 0.50 * (1 - (double) (t - pinnedAt) / (enrageAt - pinnedAt)));
            else v = (float) Math.max(0.47, 0.95 - 0.005 * (t - pinnedAt));
        }
        else v = 1f;
        BossBar.show("§c§lStorm", v);
    }

    private final HashMap<Party.Bot, Vec3> botTo = new HashMap<>();
    private final HashMap<String, Party.Bot> role = new HashMap<>();
    private Pillar stuck = null;
    private int yellowPress = -1;

    private List<Party.Bot> live() {
        List<Party.Bot> out = new ArrayList<>();
        for (Party.Bot b : Party.bots()) if (b.entity != null) out.add(b);
        return out;
    }

    private void startBots() {
        if (!P3Sim.bots()) return;
        List<Party.Bot> bots = Party.bots();
        HashSet<Party.Bot> taken = new HashSet<>();
        pick(bots, taken, "yellow", DungeonClass.ARCHER, DungeonClass.BERSERK, DungeonClass.MAGE, DungeonClass.TANK, DungeonClass.HEALER);
        pick(bots, taken, "purple", DungeonClass.TANK, DungeonClass.MAGE, DungeonClass.BERSERK, DungeonClass.HEALER, DungeonClass.ARCHER);
        pick(bots, taken, "lure", DungeonClass.BERSERK, DungeonClass.MAGE, DungeonClass.HEALER, DungeonClass.ARCHER, DungeonClass.TANK);
        pick(bots, taken, "other", DungeonClass.MAGE, DungeonClass.HEALER, DungeonClass.BERSERK, DungeonClass.ARCHER, DungeonClass.TANK);
        Party.Bot other = role.get("other");
        boolean p3 = other != null && other.clazz == DungeonClass.HEALER;
        Map<String, Vec3> at = Map.of("yellow", new Vec3(32.5, 170.0, 94.5), "purple", new Vec3(114.5, 170.0, 94.5), "lure", new Vec3(73.5, 169.0, 60.5),
            "other", p3 ? PREDEV : new Vec3(73.5, 169.0, 45.5));
        List<Vec3> spots = new ArrayList<>();
        for (Party.Bot b : bots) {
            String key = null;
            for (Map.Entry<String, Party.Bot> en : role.entrySet()) if (en.getValue() == b) { key = en.getKey(); break; }
            Vec3 v = key != null ? at.get(key) : null;
            spots.add(v != null ? v : new Vec3(73.5, 169.0, 50.5));
        }
        Party.standAt(spots);
        if (p3) role.remove("other");
    }

    private void pick(List<Party.Bot> bots, HashSet<Party.Bot> taken, String key, DungeonClass... order) {
        Party.Bot b = null;
        for (DungeonClass c : order) {
            for (Party.Bot it : bots) if (it.clazz == c && !taken.contains(it)) { b = it; break; }
            if (b != null) break;
        }
        if (b == null) for (Party.Bot it : bots) if (!taken.contains(it)) { b = it; break; }
        if (b == null) return;
        taken.add(b); role.put(key, b);
    }

    private void send(String key, Vec3 to) {
        Party.Bot b = role.get(key);
        if (b != null) botTo.put(b, to);
    }

    public void botsChanged() {
        if (!P3Sim.bots()) return;
        for (String key : new ArrayList<>(role.keySet())) {
            Party.Bot old = role.get(key);
            if (old == null) continue;
            if (old.entity != null) continue;
            Party.Bot free = null;
            for (Party.Bot b : Party.bots()) {
                if (b.entity == null) continue;
                boolean used = false;
                for (Party.Bot r : role.values()) if (r == b) { used = true; break; }
                if (!used) { free = b; break; }
            }
            if (free == null) { role.remove(key); continue; }
            role.put(key, free);
            Vec3 was = botTo.remove(old);
            if (was != null) botTo.put(free, was);
        }
    }

    private void tickBots() {
        if (!P3Sim.bots() || role.isEmpty()) return;
        switch (t) {
            case 40 -> { send("yellow", new Vec3(32.5, 169.0, 86.5)); send("purple", new Vec3(114.5, 169.0, 86.5)); }
            case 520 -> { send("yellow", new Vec3(46.5, 169.0, 65.5)); send("lure", new Vec3(45.0, 169.0, 67.0)); send("purple", new Vec3(100.5, 169.0, 65.5)); send("other", new Vec3(99.0, 169.0, 67.0)); }
            case 575 -> { send("yellow", YELLOW_WAIT); send("lure", LURE); send("purple", PURPLE_WAIT); send("other", new Vec3(58.0, 169.0, 68.0)); }
            case 595 -> send("purple", new Vec3(114.5, 170.0, 94.5));
            case 601 -> send("purple", PURPLE_WAIT);
            default -> {}
        }
        if (yellowPress >= 0 && crushes == 1) {
            if (t == yellowPress - 12) send("yellow", new Vec3(32.5, 170.0, 94.5));
            if (t == yellowPress + 22) send("yellow", YELLOW_WAIT);
        }
        if (crushes == 1 && t == takeoffAt) { send("lure", new Vec3(60.0, 169.0, 80.0)); send(baitKey(), BAIT_ON); }
        fallback();
        if (deadAt >= 0 && t == deadAt + 8) { botTo.clear(); role.clear(); Party.standAt(StormEnd.SS_LANDING); return; }
        for (Map.Entry<Party.Bot, Vec3> en : botTo.entrySet()) {
            Party.Bot b = en.getKey();
            Vec3 to = en.getValue();
            var e = b.entity;
            if (e == null) continue;
            Vec3 d = to.subtract(b.pos);
            double len = d.length();
            if (len < 1e-3) continue;
            b.pos = len <= 0.6 || len > 30 ? to : b.pos.add(d.scale(0.6 / len));
            if (len > 0.6) b.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            e.snapTo(b.pos.x, b.pos.y, b.pos.z, b.yaw, 0f);
            e.yHeadRot = b.yaw; e.yBodyRot = b.yaw;
        }
    }

    private void yellowPlan() {
        if (!P3Sim.bots() || lastCrush != purple() || yellow().spent) return;
        int c = t + 35;
        while (c % 20 != 19) c++;
        yellowPress = c;
    }

    private String baitKey() { return role.containsKey("other") ? "other" : "lure"; }

    private void fallback() {
        if (deadAt >= 0 || pinnedUntilBeam || flyingToYellow || !(chasing || laterChase) || t - chaseSince < 60) return;
        if (stuck == null) {
            Pillar pl = null;
            for (Pillar it : List.of(purple(), yellow(), pillars.get(2))) if (!it.spent) { pl = it; break; }
            if (pl == null) return;
            stuck = pl;
            Vec3 c = pl.centre();
            Vec3 raw = new Vec3(c.x - storm.pos.x, 0.0, c.z - storm.pos.z);
            Vec3 dir = raw.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : raw.normalize();
            send(baitKey(), new Vec3(c.x + dir.x * 1.5, 170.0, c.z + dir.z * 1.5));
            send("purple", new Vec3(pl.pad.minX + 3.5, 170.0, pl.pad.minZ + 3.5));
        }
    }

    public String status() {
        if (deadAt >= 0) return "Storm dead";
        if (pinnedUntilBeam) return "Pinned (crush " + crushes + ")";
        StringBuilder sb = new StringBuilder("Crushes " + crushes + "/2");
        for (Pillar it : pillars) if (it.pad != null) sb.append(" ").append(it.name.charAt(0)).append(it.spent ? "x" : String.valueOf(it.bottom));
        return sb.toString();
    }

    public static final int RESET_BOTTOM = 183;
    public static final Vec3 PREDEV = new Vec3(56.5, 114.0, 95.5);
    public static final Vec3 LURE = new Vec3(94.5, 165.0, 92.0);
    public static final Vec3 PURPLE_WAIT = new Vec3(116.0, 170.0, 103.0);
    public static final Vec3 YELLOW_WAIT = new Vec3(32.5, 170.0, 88.5);
    public static final Vec3 BAIT_ON = new Vec3(46.0, 170.0, 66.0);
    public static final List<Integer> RING_T = List.of(0, 3, 7, 12, 16, 21, 25);
}
