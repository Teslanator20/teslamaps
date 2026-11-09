package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/GoldorPhase.kt

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GoldorPhase extends Fight.Phase {
    private static final KRandom R = KRandom.DEFAULT;

    public final int from;
    public final boolean arrived;

    @Override
    public Fight.Start restart() {
        return switch (from) {
            case 2 -> Fight.Start.S2;
            case 3 -> Fight.Start.S3;
            case 4 -> Fight.Start.S4;
            case 5 -> Fight.Start.CORE;
            default -> Fight.Start.P3;
        };
    }

    public final List<Station> stations;
    public final boolean practice;
    public int section = 1;

    public int n() { return t + nOffset; }

    private int nOffset = 0;
    private final int[] sectionStart = new int[6];
    private final int[] sectionEnd = new int[6];
    private final boolean[] gateDown = new boolean[5];
    private final int[] gateAt = new int[5];
    private final boolean[] doorOpen = new boolean[5];
    private int[] autoGateAt = new int[5];
    private int coreAt = -1;
    private int everyoneInAt = -1;
    private int deadAt = -1;
    private final PerPlayer<Integer> frenzyHits = new PerPlayer<>(() -> 0);
    private int arrivedAt = -1;
    private int necronAt = -1;
    private int p3endAt = -1;
    private boolean handOff = false;
    private int deaths = 0;
    private boolean dtEarly = false;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private int lastLine = -1000;
    private int forgiveAt = -1;
    private final HashMap<Integer, String> tauntAt = new HashMap<>();
    private String lastTaunt = null;

    private final boolean[] goldorReached = new boolean[6];

    private int goldorSeg() { return GoldorPhase.Goldor.segment(goldor.s) + 1; }

    public final Devices devices;
    private final ArenaFixes.Replay arenaReplay;
    public final Goldor goldor;

    public GoldorPhase(int from) { this(from, false); }

    public GoldorPhase(int from, boolean arrived) {
        super("P3");
        this.from = from;
        this.arrived = arrived;
        this.stations = Station.all();
        this.practice = Practice.section == from && from >= 1 && from <= 4;
        java.util.Arrays.fill(sectionEnd, -1);
        java.util.Arrays.fill(gateAt, -1);
        java.util.Arrays.fill(autoGateAt, -1);
        this.devices = new Devices(this);
        this.arenaReplay = ArenaFixes.replay();
        this.goldor = new Goldor();
    }

    public Station station(int section, String label) {
        for (Station st : stations) if (st.section == section && st.label.equals(label)) return st;
        throw new java.util.NoSuchElementException("Collection contains no element matching the predicate.");
    }

    public int count(int section) {
        int k = 0;
        for (Station st : stations) if (st.section == section && st.done) k++;
        return k;
    }

    private boolean holding = false;
    private int holdFor = 0;
    private boolean held = false;

    @Override
    public void start() {
        holdFor = practice ? Practice.holdTicks() : 0;
        if (holdFor > 0) {
            holding = true; held = true;
            ServerPlayer player = Sim.player();
            if (player != null) {
                Spots.Spot spot = Practice.startSpot();
                Sim.tp(player, spot.x, spot.y, spot.z, spot.yaw, spot.pitch);
                SimItems.giveHotbar(player, true);
                Practice.holdStartItem(player);
            }
            Practice.hold(holdFor);
            return;
        }
        begin();
    }

    private void begin() {
        stations.forEach(Station::spawnStands);
        devices.start();
        Stats.reset(from, practice);
        int startN = switch (from) { case 2 -> 252; case 3 -> 433; case 4 -> 629; case 5 -> 797; default -> 0; };
        nOffset = startN - t;
        if (arenaReplay != null) arenaReplay.begin(startN);
        for (int s = 1; s < Math.min(from, 5); s++) {
            for (Station st : stations) if (st.section == s) doneAlready(st);
            gateDown[s] = true; doorOpen[s] = true;
            if (s <= 3) {
                if (!practice) Blocks.finish("gate" + s + (s + 1));
                Blocks.finish("door" + s, practice ? gateBlocks(s) : Collections.emptySet());
            }
            sectionEnd[s] = startN;
        }
        if (from >= 2) Blocks.finish("p3start");
        if (practice) {
            List<String> jobs = Practice.practiceJobs();
            for (int s = from; s <= Math.clamp(Practice.lastSection(), from, 4); s++) {
                for (Station st : stations) if (st.section == s && !jobs.contains(st.id())) doneAlready(st);
                if (s <= 3 && !jobs.contains("gate " + s)) gateDown[s] = true;
            }
        }
        section = Math.min(from, 5);
        sectionStart[section] = startN;
        goldor.spawn(startN, !(from == 1 && arrived));
        Party.startP3(this);
        GhostCapture.start(this);
        Sim.forEachPlayer(player -> {
            if (!arrived) {
                if (!held || !Sim.isHost(player)) {
                    Spots.Spot spot = practice && Sim.isHost(player) ? Practice.startSpot() : Spots.p3Start(from);
                    Sim.tp(player, spot.x, spot.y, spot.z, spot.yaw, spot.pitch);
                    SimItems.giveHotbar(player, true);
                    if (practice && Sim.isHost(player)) Practice.holdStartItem(player);
                }
            } else {
                Inventory inv = player.getInventory();
                Integer hype = null;
                for (int i = 0; i <= 8; i++) if ("HYPERION".equals(SimItems.idOf(inv.getItem(i)))) { hype = i; break; }
                Integer boom = null;
                for (int i = 9; i < inv.getContainerSize(); i++) if ("SUPERBOOM_TNT".equals(SimItems.idOf(inv.getItem(i)))) { boom = i; break; }
                if (hype != null && boom != null) {
                    ItemStack h = inv.getItem(hype); inv.setItem(hype, inv.getItem(boom)); inv.setItem(boom, h);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        });
        if (from == 1 && arrived) nOffset = -(LEAD_IN - 1);
        if (from == 1) {
            Runnable opening = () -> {
                if (arrived) BossBar.show("§c§lGoldor", 1f);
                say("Who dares trespass into my domain?");
                lines.addAll(List.of("Little ants, plotting and scheming, thinking they are invincible...",
                    "I won't let you break the factory core, I gave my life to my Master.", "No one matches me in close quarters."));
                int r = R.nextInt(100);
                int k = r < 2 ? 0 : r < 43 ? 1 : r < 93 ? 2 : 3;
                for (int i = 0; i < k; i++) tauntAt.put(20 + R.nextInt(220), taunt());
                Blocks.play("p3start");
            };
            if (arrived) Fight.later(LEAD_IN, "who dares", () -> { if (Fight.phase == this) opening.run(); }); else opening.run();
        } else if (from == 5) {
            PartyClient.send("termStart", 5);
            openCore();
        } else {
            PartyClient.send("termStart", from);
            maybeTaunt();
            if (!practice) Sim.note("Starting at §fS" + from + "§7 (n = " + startN + ", the median fast run's).");
        }
        if (practice) {
            Practice.begin(from, t);
            if (count(from) >= Station.total(from)) sectionDone(from);
        }
    }

    private void doneAlready(Station st) {
        st.done = true; st.doneBy = "-"; st.refreshStands();
        if (st.kind == Station.Kind.DEVICE) devices.shownDone(st.label);
        BlockPos l = st.lever;
        if (l != null) {
            BlockState b = Blocks.get(l);
            if (b != null && b.hasProperty(LeverBlock.POWERED)) Blocks.set(l, b.setValue(LeverBlock.POWERED, true));
        }
    }

    @Override
    public void stop() {
        GhostCapture.stopped(this);
        Terminals.closeAll();
        devices.stop();
        Goldor g = goldor;
        if (necronAt >= 0) Fight.later(P4Necron.GONE + 1, "goldor body", g::remove); else g.remove();
    }

    @Override
    public void tick() {
        if (holding) {
            if (t < holdFor) { Practice.holdTick(t, holdFor); return; }
            holding = false;
            begin();
        }
        int n = n();
        if (n == -1) goldor.tick(this);
        if (n < 0) return;
        devices.tick();
        Sim.forEachPlayer(this::innerChamber);
        String taunt = tauntAt.remove(n);
        if (taunt != null) { if (section <= 4) say(taunt); }
        dialogue();
        if (n % 20 == Fight.refreshPhase) for (Station st : stations) if (st.kind != Station.Kind.LEVER) st.refreshStands();
        for (int s = 1; s <= 3; s++) if (autoGateAt[s] >= 0 && n >= autoGateAt[s] && !gateDown[s]) blowGate(s, null);
        if (section <= 4) {
            if (n % 60 == 58) dtEarly = R.nextInt(98) < 26;
            if ((n % 60 == 58 && dtEarly) || (n % 60 == 59 && !dtEarly)) deathTick();
        }
        goldor.tick(this);
        if (n > 0 && n % 20 == 0) BossBar.resend();
        if (section >= 1 && section <= 4 && goldorSeg() == section && !(section == 4 && goldor.firstLap)) goldorReached[section] = true;
        if (arenaReplay != null) arenaReplay.tick(n);
        if (section == 5) coreTick();
        Party.tickP3(this);
        if (practice) Practice.tick(this);
        GhostCapture.tick(this);
        if (handOff) { handOff = false; handDialogueOver(); Fight.begin(new P4Necron(true)); }
    }

    private final PerPlayer<Vec3> lastIn = new PerPlayer<>(() -> null);
    private final PerPlayer<Integer> innerAt = new PerPlayer<>(() -> -100);

    private void innerChamber(ServerPlayer p) {
        Vec3 was = lastIn.get(); Vec3 now = p.position(); lastIn.set(now);
        if (section < 4 || everyoneInAt >= 0 || goldor.flying || was == null || !CORE_BOX.contains(was) || CORE_BOX.contains(now)) return;
        Sim.tp(p, 54.5, 115.0, 58.3);
        lastIn.set(new Vec3(54.5, 115.0, 58.3));
        if (n() - innerAt.get() < 20) return;
        innerAt.set(n());
        Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 8f, 0f, new Vec3(54.5, 115.0, 58.3), SoundSource.HOSTILE);
        Sim.chat("§cA mystical force prevents you from leaving the inner chamber!");
    }

    // ------------------------------------------------------------------ Goldor's lines

    private void say(String line) {
        if (lines.isEmpty() && n() >= lastLine + 62) speak(line); else lines.add(line);
    }

    private void gsay(String line) { gsay(line, true); }

    private void gsay(String line, boolean stand) { Sim.boss("Goldor", line, goldor.position(), stand); }

    private void speak(String line) {
        gsay(line);
        lastLine = n();
        if (line.equals("....")) forgiveAt = n() + FORGIVE_AFTER;
    }

    private void dialogue() {
        if (!lines.isEmpty() && n() >= lastLine + 62) speak(lines.removeFirst());
        if (forgiveAt >= 0 && forgiveAt <= n()) { forgiveAt = -1; gsay("Necron, forgive me."); Fight.later(12, "goldor rearm", goldor::reArmour); }
    }

    private void handDialogueOver() {
        int at = lastLine;
        int n = n();
        for (String line : lines) {
            at = Math.max(at + 62, n);
            int dt = at - n;
            Fight.later(dt, "goldor line", () -> gsay(line));
            if (line.equals("....")) forgiveAt = at + FORGIVE_AFTER;
        }
        lines.clear();
        if (forgiveAt >= 0) {
            int dt = forgiveAt - n;
            forgiveAt = -1;
            Fight.later(dt, "goldor forgive", () -> { gsay("Necron, forgive me."); Fight.later(12, "goldor rearm", goldor::reArmour); });
        }
    }

    private String taunt() {
        List<String> pool = new ArrayList<>();
        for (String s : TAUNT_POOL) if (!s.equals(lastTaunt)) pool.add(s);
        String t = pool.get(R.nextInt(pool.size()));
        lastTaunt = t;
        return t;
    }

    private void maybeTaunt() {
        if (section >= 2 && section <= 4 && R.nextDouble() < 0.13) tauntAt.put(n() + 20 + R.nextInt(180), taunt());
    }

    // ------------------------------------------------------------------ completions

    public void complete(Station st, String by) { complete(st, by, false); }

    public void complete(Station st, String by, boolean twice) {
        if (st.done || holding) return;
        boolean inProgress = st.section == section;
        boolean early = st.kind == Station.Kind.DEVICE && st.section > section;
        if (!inProgress && !early) return;
        st.done = true;
        st.doneBy = by;
        st.doneAt = n();
        String what = switch (st.kind) {
            case TERMINAL -> "activated a terminal!";
            case LEVER -> "activated a lever!";
            case DEVICE -> "completed a device!";
        };
        int shown = Math.min(section, 4);
        int k = count(shown);
        String line = progressLine(by, what, k, Station.total(shown));
        if (st.kind == Station.Kind.TERMINAL) {
            ServerPlayer except = null;
            for (ServerPlayer p : Sim.players()) if (p.getGameProfile().name().equals(by)) { except = p; break; }
            Terminals.closeFor(st, except);
        }
        if (Sim.isPlayerName(by)) Stats.done(Objects.requireNonNull(by));
        if (Objects.equals(by, Sim.hostName())) GhostCapture.event("done", st.id());
        Sim.everyone(() -> {
            for (int i = 0; i < (twice ? 2 : 1); i++) announce(line);
            if (inProgress && count(section) >= Station.total(section)) sectionDone(section);
            return null;
        });
    }

    private void announce(String line) {
        Sim.chatStyled(line);
        Sim.title("", line.replace("§r", ""), 0, 40, 0);
        Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8f, 4.047619f, null, SoundSource.BLOCKS);
    }

    private String progressLine(String by, String what, int k, int total) {
        return "§b" + by + "§r§a " + what + " (§r§c" + k + "§r§a/" + total + ")";
    }

    private final List<String> TAUNT_POOL = List.of(
        "Do you really think we won't repair everything? Your impact will be minuscule!", "Come closer!",
        "You are breaking precious materials, unforgivable.", "CLOSER!", "There is no stopping me down there!",
        "I am the death zone, you are smart to flee.", "You can't damage me, you can barely slow me down!",
        "Slowing me down only prolongs your pain!", "Closer to me!", "Stop touching those terminals!"
    );

    private final List<String> SECTION_LINES = List.of("The little ants have a brain it seems.", "I will replace that gate with a stronger one!", "YOUR END IS NEAR!!");

    private void sectionDone(int s) {
        Sim.everyone(() -> {
            sectionEnd[s] = n();
            if (s == 4) { openCore(); return null; }
            if (gateDown[s]) openDoor(s);
            else {
                if (!practice) {
                    Sim.chat("§aThe gate will open in 5 seconds!");
                    Sim.title("", "§aThe gate will open in 5 seconds!", 0, 40, 0);
                }
                Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8f, 4.047619f, null, SoundSource.BLOCKS);
                if (!practice) autoGateAt[s] = n() + 100;
                if (!practice) Fight.later(1, "door top", () -> {
                    if (Fight.phase != this || doorOpen[s]) return;
                    Blocks.Anim anim = Blocks.anim("door" + s);
                    if (anim == null) return;
                    for (Blocks.Frame f : anim.frames) {
                        if (f.dt != 0 || f.pos.getY() < 118) continue;
                        BlockState cur = Blocks.get(f.pos);
                        Block b = cur != null ? cur.getBlock() : null;
                        if (b == net.minecraft.world.level.block.Blocks.IRON_BLOCK || b instanceof StairBlock) Blocks.set(f.pos, f.state);
                    }
                });
            }
            return null;
        });
    }

    private void openDoor(int s) {
        Sim.everyone(() -> {
            if (doorOpen[s]) return null;
            doorOpen[s] = true;
            if (!practice || s < Practice.lastSection()) {
                Blocks.play("door" + s, 0, !practice || s > 3 ? Collections.emptySet() : gateBlocks(s), 1);
                if (s == 1) Blocks.play("ss_s1done", 0, Collections.emptySet(), 1);
            }
            say(SECTION_LINES.get(R.nextInt(SECTION_LINES.size())));
            section = s + 1;
            sectionStart[section] = n();
            if (section >= 2 && section <= 4 && !goldor.firstLap && goldorSeg() > section) goldorReached[section] = true;
            maybeTaunt();
            Stats.section(s, Math.max(sectionEnd[s], gateAt[s]) - sectionStart[s]);
            goldor.sectionEnded(s, n());
            if (count(section) >= Station.total(section)) sectionDone(section);
            return null;
        });
    }

    private Set<BlockPos> gateBlocks(int s) {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
        Blocks.Anim anim = Blocks.anim("gate" + s + (s + 1));
        if (anim != null) for (Blocks.Frame f : anim.frames) out.add(f.pos);
        BlockPos.betweenClosedStream(GATE_BOXES[s].inflate(0.5)).map(BlockPos::immutable).forEach(out::add);
        return out;
    }

    public boolean blowGate(int s, String by) {
        return Sim.everyone(() -> {
            if (s < 1 || s > 3 || gateDown[s] || holding) return false;
            if (by != null && section < s) return false;
            gateDown[s] = true;
            gateAt[s] = n();
            if (Objects.equals(by, Sim.hostName())) GhostCapture.event("gate", "", s);
            Sim.chat("§aThe gate has been destroyed!");
            Sim.title("", "§aThe gate has been destroyed!", 0, 40, 0);
            Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8f, 4.047619f, null, SoundSource.MASTER);
            SimItems.gatePuffs(GATE_CENTRES[s], GATE_BOXES[s]);
            if (!practice) Blocks.play("gate" + s + (s + 1), 0, Collections.emptySet(), 1);
            if (sectionEnd[s] >= 0) openDoor(s);
            return true;
        });
    }

    public int gateNear(Vec3 p, double r) {
        for (int s = 1; s <= 3; s++) if (GATE_BOXES[s].inflate(r).contains(p)) return s;
        return 0;
    }

    // ------------------------------------------------------------------ death ticks

    private void deathTick() { Sim.forEachPlayer(p -> deathTick(p)); }

    private void deathTick(ServerPlayer p) {
        if (p.isSpectator() || p.isCreative() || Masks.ghost.get()) return;
        Vec3 seen = Fight.seenPos(p);
        if (inSafeSpot(seen)) return;
        int at = dtZone(seen);
        if (at < 0) return;
        if (at < section) return;
        if (at == section && !(goldorReached[section] && goldorSeg() != section)) return;
        if (P3Sim.deathTicks() == 0) return;
        deaths++;
        Stats.deathTick(p.getGameProfile().name());
        Runnable line = () -> {
            Sim.chat("§4[BOSS] Goldor§r§c: What do you think you are doing there!");
            Sim.sound(SoundEvents.WITHER_AMBIENT, 1f, 1f, null, SoundSource.HOSTILE);
        };
        if (P3Sim.deathTicks() == 1) { line.run(); Sim.title("", "§cDeath tick §7(S" + at + " during S" + section + ")", 0, 25, 5); }
        else Masks.hit(p, null, line);
    }

    private boolean inSafeSpot(Vec3 v) { return CORE_BOX.contains(v) || STRIP.contains(v); }

    // ------------------------------------------------------------------ the core

    private void openCore() {
        Sim.everyone(() -> {
            section = 5;
            coreAt = n();
            Sim.chat("§aThe Core entrance is opening!");
            Sim.title("", "§aThe Core entrance is opening!", 0, 40, 0);
            Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8f, 4.047619f, null, SoundSource.BLOCKS);
            if (from != 5) Stats.section(4, n() - sectionStart[4]);
            if (P3Sim.termsOnly() && !practice && from != 5) Fight.later(1, "stop after terms", () -> { Sim.note("Terms done: stopped §7(Stop After Terms)."); Fight.end(); });
            if (!practice) Blocks.play("core", 0, Collections.emptySet(), 1);
            Stats.p3(n());
            return null;
        });
    }

    private boolean everyoneIn() {
        List<ServerPlayer> ps = Sim.players();
        if (ps.isEmpty()) return false;
        for (ServerPlayer it : ps) {
            if (!Sim.asPlayer(it, false, () -> Masks.ghost.get()) && !CORE_BOX.contains(Fight.seenPos(it))) return false;
        }
        return Party.allIn(CORE_BOX);
    }

    private void coreTick() {
        int n = n();
        if (everyoneInAt < 0 && n >= coreAt + 4 && everyoneIn()) {
            everyoneInAt = n;
            goldor.fly(n);
        }
        if (deadAt < 0 && goldor.flying) {
            if (goldor.arrived && arrivedAt < 0) {
                arrivedAt = n;
                say("You have done it, you destroyed the factory...");
                say("But you have nowhere to hide anymore!");
                say("YOU ARE FACE TO FACE WITH GOLDOR!");
            }
            if (n >= goldor.killAt) die();
        }
        goldor.barTick(n);
        if (deadAt < 0 && n % 10 == 7) Sim.forEachPlayer(p -> {
            double d = p.position().distanceTo(goldor.position());
            if (Masks.ghost.get() || p.isSpectator() || p.isCreative() || !(d >= 2.0 && d <= 14.0)) frenzyHits.set(0);
            else {
                int hit = frenzyHits.get() + 1;
                frenzyHits.set(hit);
                if (hit >= 3) { Masks.hit(p, "Goldor"); frenzyHits.set(0); }
                KRandom r = R;
                double dmg = switch (hit) { case 1 -> 2_000.0 + r.nextDouble(11_000.0); case 2 -> 44_000.0 + r.nextDouble(4_000.0); default -> 52_000.0 + r.nextDouble(8_000.0); };
                dmg = r.nextInt(100) < 40 ? Math.floor(dmg) : Math.round(dmg * 10) / 10.0;
                Sim.chat("§cGoldor's§r§7 Frenzy hit you for §r§c" + StormFx.dmg(dmg) + "§r§7 damage.");
                Sim.sound(SoundEvents.GENERIC_EXPLODE, 0.5f, 0.49f, null, SoundSource.MASTER);
                Sim.sound(SoundEvents.PLAYER_HURT, 1f, 1f, null, SoundSource.MASTER);
            }
        });
        if (deadAt >= 0 && p3endAt < 0 && n >= deadAt + NECRON_AFTER - 2 && !P3Sim.p3Only()) { p3endAt = n; Blocks.play("p3end"); }
        if (deadAt >= 0 && necronAt < 0 && n >= deadAt + NECRON_AFTER) {
            necronAt = n;
            if (P3Sim.p3Only()) { Fight.later(1, "stop after goldor", () -> { Sim.note("Goldor done: stopped §7(Stop After Goldor)."); Fight.end(); }); return; }
            handOff = true;
        }
    }

    private void die() {
        deadAt = n();
        say("....");
        goldor.die();
    }

    public static final int LEAD_IN = 3;
    public static final int NECRON_AFTER = 62;
    public static final int FORGIVE_AFTER = 52;

    public static final List<Pair<Integer, AABB>> DT_ZONES = List.of(
        Pair.of(1, new AABB(90.0, 106.0, 26.0, 114.0, 146.0, 121.0)),
        Pair.of(2, new AABB(20.0, 106.0, 122.0, 114.0, 146.0, 146.0)),
        Pair.of(3, new AABB(-6.0, 106.0, 51.0, 18.0, 146.0, 146.0)),
        Pair.of(4, new AABB(-6.0, 106.0, 26.0, 90.0, 146.0, 50.0))
    );

    public static int dtZone(Vec3 v) {
        for (Pair<Integer, AABB> z : DT_ZONES) if (z.second.contains(v)) return z.first;
        return -1;
    }

    public static final AABB CORE_BOX = new AABB(39.0, 0.0, 54.0, 71.0, 155.5, 118.0);
    public static final AABB STRIP = new AABB(45.0, 100.0, 50.0, 65.0, 160.0, 54.5);
    public static final AABB[] GATE_BOXES = {AABB.ofSize(Vec3.ZERO, 0.0, 0.0, 0.0), new AABB(93.0, 113.0, 121.0, 108.0, 138.0, 125.0), new AABB(16.0, 113.0, 125.0, 20.0, 138.0, 140.0), new AABB(1.0, 113.0, 48.0, 16.0, 138.0, 52.0)};
    public static final Vec3[] GATE_CENTRES = {Vec3.ZERO, new Vec3(100.0, 118.0, 122.5), new Vec3(17.5, 118.0, 132.0), new Vec3(8.0, 118.0, 49.5)};

    // ------------------------------------------------------------------ Goldor

    public static class Goldor {
        private BossWither boss = null;
        private final ArrayList<Giant> giants = new ArrayList<>();
        public double s = START_S;
        public boolean firstLap = true;
        private double speed = WALK;
        private double sprintTo = -1.0;
        private int sprintFrom = -1;
        private int sprintSec = 0;
        public boolean flying = false;
        public boolean arrived = false;
        private boolean dead = false;
        private int flyAt = 0;
        private int nextHurt = 0;
        private Vec3 pos = Vec3.ZERO;
        private final boolean armouredRun = R.nextInt(36) < 23;
        private final int armourAt = 1 + R.nextInt(7);
        private int armourOffAt = Integer.MAX_VALUE;
        private int spawnedN = 0;
        private final int giantsAt = 11 + R.nextInt(9);
        private double giantAngle = 0.0;
        public int killAt = Integer.MAX_VALUE;

        public Vec3 position() { return pos; }

        public void spawn(int n) { spawn(n, true); }

        public void spawn(int n, boolean bar) {
            firstLap = START_S + walkDist(n) < LOOP;
            s = (START_S + walkDist(n)) % LOOP;
            pos = trackPos(s);
            spawnedN = n;
            boss = new BossWither("Goldor", pos, 0, false);
            if (bar) BossBar.show("§c§lGoldor", 1f);
        }

        private void spawnGiants() {
            for (int i = 0; i < 4; i++) {
                SimGiant e = new SimGiant(Sim.level());
                e.setNoAi(true); e.setSilent(true); e.setInvulnerable(true); e.setNoGravity(true); e.setInvisible(true);
                e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
                e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                Vec3 g = giantPos(i);
                e.snapTo(g.x, g.y, g.z, 0f, 0f);
                giants.add(Sim.spawn(e));
            }
        }

        private Vec3 giantPos(int i) {
            double a = Math.toRadians(giantAngle + 90.0 * i);
            return new Vec3(pos.x + 2.4 + 3.0 * Math.cos(a), pos.y - 8.0, pos.z - 3.5 + 3.0 * Math.sin(a));
        }

        private void orbitGiants() {
            giantAngle += 6.0;
            for (int i = 0; i < giants.size(); i++) {
                Giant e = giants.get(i);
                Vec3 g = giantPos(i);
                e.snapTo(g.x, g.y, g.z, (float) (giantAngle + 90.0 * i), 0f);
            }
        }

        public void reArmour() { if (armouredRun && boss != null) boss.armour(true); }

        public void remove() {
            if (boss != null) boss.remove();
            boss = null;
            giants.forEach(Giant::discard);
            giants.clear();
        }

        public void sectionEnded(int sec, int doorN) {
            if (flying || sec < 1 || sec > 3) return;
            boolean inIt = sec == 1 ? s >= S1_ENTRY || s < BOUNDS[1] : segment(s) == sec - 1;
            if (inIt && sprintFrom < 0) { sprintFrom = doorN + SPRINT_LAG_MIN + R.nextInt(SPRINT_LAG_SPREAD); sprintSec = sec; }
        }

        public void fly(int n) {
            if (flying) return;
            flying = true;
            flyAt = n;
            killAt = n + P3Sim.goldorKill();
            armourOffAt = killAt - 4 - R.nextInt(11);
        }

        private void aura() {
            int k = R.nextDouble() < 0.34 ? 2 : 1;
            for (int i = 0; i < k; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                double r = 3.0 + R.nextDouble() * 3.0;
                Sim.level().sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x + Math.cos(a) * r, pos.y + 0.5, pos.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 1.0);
            }
        }

        public void die() { dead = true; }

        public void barTick(int n) {
            if (!flying || killAt == Integer.MAX_VALUE) return;
            int dropAt = killAt - 3;
            int zeroAt = killAt + 9;
            if (n < dropAt) return;
            if (n >= zeroAt) { if (!barZeroed) { barZeroed = true; BossBar.progress(0f); } return; }
            if ((n - dropAt) % 4 == 0) BossBar.progress(0.35f * (zeroAt - n) / (zeroAt - dropAt));
        }

        private boolean barZeroed = false;

        public void tick(GoldorPhase phase) {
            BossWither boss = this.boss;
            if (boss == null) return;
            int n = phase.n();
            if (dead) return;
            aura();
            if (armouredRun && n == spawnedN + armourAt) boss.armour(true);
            if (n >= armourOffAt) { armourOffAt = Integer.MAX_VALUE; boss.armour(false); }
            if (n >= giantsAt && giants.isEmpty()) spawnGiants();
            if (flying) {
                if (n >= nextHurt) {
                    nextHurt = n + 2 + R.nextInt(11);
                    for (ServerPlayer p : Sim.players()) p.connection.send(new ClientboundHurtAnimationPacket(boss.e));
                }
                if (!arrived) {
                    Vec3 to = CORE_POINT.subtract(pos);
                    double d = to.horizontalDistance();
                    double step = Math.min(FLY, d);
                    pos = new Vec3(pos.x + to.x / Math.max(d, 1e-6) * step, Math.max(CORE_POINT.y, pos.y - 0.03), pos.z + to.z / Math.max(d, 1e-6) * step);
                    if (d <= FLY) arrived = true;
                } else {
                    double v = pos.z < CORE_IN_Z ? 0.4 : 0.07;
                    pos = new Vec3(pos.x + Math.clamp(CORE_IN_X - pos.x, -0.1, 0.1), Math.max(116.75, pos.y - 0.006), pos.z + v);
                }
            } else {
                if (sprintFrom >= 0 && sprintFrom <= n && sprintTo < 0) {
                    int sec = sprintSec;
                    boolean inIt = sec == 1 ? s >= S1_ENTRY || s < BOUNDS[1] : segment(s) == sec - 1;
                    if (inIt) { sprintTo = SPRINT_TO[sec - 1]; speed = SPRINT; }
                    sprintFrom = -1;
                }
                if (sprintTo >= 0) {
                    double left = ((sprintTo - s) % LOOP + LOOP) % LOOP;
                    if (left <= SPRINT) { s = sprintTo; sprintTo = -1.0; speed = WALK; } else s += SPRINT;
                } else s += walkStep(n);
                if (s >= LOOP) firstLap = false;
                s %= LOOP;
                pos = trackPos(s);
            }
            float yaw;
            if (arrived) yaw = 0f;
            else if (flying) yaw = (float) Math.toDegrees(Math.atan2(-(CORE_POINT.x - pos.x), CORE_POINT.z - pos.z));
            else yaw = yaw();
            boss.moveTo(pos, pos.add(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw))));
            if (!giants.isEmpty()) orbitGiants();
        }

        private float yaw() {
            return switch (segment(s)) { case 0 -> 0f; case 1 -> 90f; case 2 -> 180f; default -> -90f; };
        }

        public static final double WALK = 0.06;
        public static final double SLOW_WALK = 0.048;
        public static final int SLOW_TICKS = 194;

        public static double walkStep(int age) { return age < SLOW_TICKS ? SLOW_WALK : WALK; }

        public static double walkDist(int age) { return age <= SLOW_TICKS ? SLOW_WALK * age : SLOW_WALK * SLOW_TICKS + WALK * (age - SLOW_TICKS); }

        public static final int SPRINT_LAG_MIN = 48;
        public static final int SPRINT_LAG_SPREAD = 12;
        public static final double SPRINT = 0.60;
        public static final double FLY = 0.80;
        private static final List<Vec3> CORNERS = List.of(new Vec3(99.55, 119.0, 40.0), new Vec3(99.55, 119.0, 131.7), new Vec3(8.4, 118.5, 131.7), new Vec3(8.4, 118.0, 40.0));
        private static final double[] Y_FROM = {119.0, 118.5, 118.0, 118.1};
        private static final double[] Y_TO = {119.0, 118.5, 118.0, 119.0};
        public static final double[] BOUNDS = {0.0, 90.7, 182.1, 272.8, 364.2};
        public static final double LOOP = 364.2;
        public static final double START_S = 344.7;
        public static final double S1_ENTRY = 361.5;
        public static final double[] SPRINT_TO = {91.2, 182.3, 272.8};
        public static final Vec3 CORE_POINT = new Vec3(53.6, 117.0, 40.0);
        public static final double CORE_IN_X = 53.6;
        public static final double CORE_IN_Z = 56.0;

        public static int segment(double s) {
            int i = 0;
            while (i < 3 && s >= BOUNDS[i + 1]) i++;
            return i;
        }

        public static Vec3 trackPos(double s) {
            int i = segment(s);
            double f = Math.clamp((s - BOUNDS[i]) / (BOUNDS[i + 1] - BOUNDS[i]), 0.0, 1.0);
            Vec3 a = CORNERS.get(i); Vec3 b = CORNERS.get((i + 1) % 4);
            return new Vec3(a.x + (b.x - a.x) * f, Y_FROM[i] + (Y_TO[i] - Y_FROM[i]) * f, a.z + (b.z - a.z) * f);
        }
    }

    public static class SimGiant extends Giant {
        public SimGiant(Level level) { super(EntityTypes.GIANT, level); }

        @Override
        public void checkDespawn() {}
    }

    public Station leverAt(BlockPos pos) {
        for (Station st : stations) if (Objects.equals(st.lever, pos)) return st;
        return null;
    }

    public void pullLever(Station st, String by) { pullLever(st, by, false); }

    public void pullLever(Station st, String by, boolean left) {
        BlockPos lever = st.lever;
        if (lever == null) return;
        if (holding) return;
        if (st.done) { if (by.equals(Sim.me())) refuseLever(lever, left); return; }
        if (st.section != section) { if (by.equals(Sim.me()) && !left) SimItems.vanillaLeverToggle(lever); return; }
        BlockState cur = Blocks.get(lever);
        boolean powered = cur != null && cur.hasProperty(LeverBlock.POWERED) ? cur.getValue(LeverBlock.POWERED) : false;
        boolean nowOn = !powered;
        if (!left) {
            BlockState b = Blocks.get(lever);
            if (b != null && b.hasProperty(LeverBlock.POWERED)) Blocks.set(lever, b.setValue(LeverBlock.POWERED, nowOn));
        }
        complete(st, by);
        if (!left) Sim.sound(SoundEvents.LEVER_CLICK, 0.3f, nowOn ? 0.5873016f : 0.4920635f, Vec3.atCenterOf(lever), SoundSource.BLOCKS);
        if (left && st.done && by.equals(Sim.me())) refuseLever(lever, true);
        if (st.done) Fight.later(1, "lever stand", () -> { if (Fight.phase == this) st.refreshStands(); });
    }

    private void refuseLever(BlockPos lever, boolean left) {
        Sim.chat("§cSomeone has already activated this lever!");
        Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 8f, 0f, null, SoundSource.HOSTILE);
        if (left) sendBlock(lever);
    }

    private void sendBlock(BlockPos pos) {
        ServerPlayer p = Sim.player();
        if (p != null && p.connection != null) p.connection.send(new ClientboundBlockUpdatePacket(Sim.level(), pos));
    }

    public void useTerminal(Station st) {
        ServerPlayer p = Sim.player();
        if (p == null) return;
        if (holding) { Fight.later(1, "term refusal", () -> Sim.chatStyled("§cThis Terminal doesn't seem to be responsive at the moment.")); return; }
        if (st.done) { Fight.later(1, "term refusal", () -> Sim.chatStyled("§cThis Terminal has already been completed!")); return; }
        if (st.section != section) { Fight.later(1, "term refusal", () -> Sim.chatStyled("§cThis Terminal doesn't seem to be responsive at the moment.")); return; }
        Fight.later(1, "term open", () -> { if (!st.done && st.section == section && !holding) Terminals.open(p, st); });
    }

    public String status() {
        if (necronAt >= 0) return "P3 done";
        if (deadAt >= 0) return "Goldor dead";
        if (section == 5) return "Core open" + (everyoneInAt >= 0 ? ", Goldor flying" : "");
        return "S" + section + " " + count(section) + "/" + Station.total(section) + (!gateDown[Math.min(section, 3)] && section <= 3 ? ", gate up" : "");
    }

    public int deathsTaken() { return deaths; }

    public boolean gateIsDown(int s) { return s >= 0 && s < gateDown.length ? gateDown[s] : true; }

    public int sectionStartN(int s) { return sectionStart[s]; }

    public boolean doorIsOpen(int s) { return s >= 0 && s < doorOpen.length ? doorOpen[s] : true; }
}
