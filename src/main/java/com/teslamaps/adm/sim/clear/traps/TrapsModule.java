package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapsModule.kt

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Debris;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.clear.puzzles.hard.BlockEdit;
import com.teslamaps.adm.sim.clear.puzzles.hard.Frames;
import com.teslamaps.adm.sim.clear.puzzles.hard.HardBlocks;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomCrusher;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomTripwire;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.pack.runstate.SbStatPreset;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.HurtResult;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.run.api.TrapService;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The trap rooms (crushers, tripwires), the arrow banks and flamethrowers of ordinary rooms, and the rooms that disable abilities.
 * Hits are 15 percent of max HP true damage through the damage service (a crusher 30) with Hypixel's red line.
 */
public final class TrapsModule implements AdsModule, TrapService {
    public static final TrapsModule INSTANCE = new TrapsModule();

    private TrapsModule() {}

    @Override public String id() { return "traps"; }

    @Override public String owner() { return "traps"; }

    private static BoolSetting abilitiesOffInPuzzlesS;
    private static BoolSetting abilitiesOffInTrapsS;

    @Override
    public void registerSettings() {
        abilitiesOffInTrapsS = SimSettings.bool("Abilities Off In Trap Rooms", true,
                "Teleports, blinks and the Wither abilities do nothing while you stand in the Old Trap or the New Trap, as on Hypixel.");
        abilitiesOffInPuzzlesS = SimSettings.bool("Abilities Off In Puzzle Rooms", false,
                "The same for the puzzle rooms. Which puzzle rooms switch abilities off on Hypixel is not known, so this is off.");
    }

    public static boolean abilitiesOffInTrapRooms() { return abilitiesOffInTrapsS != null ? abilitiesOffInTrapsS.get() : true; }

    public static boolean abilitiesOffInPuzzleRooms() { return abilitiesOffInPuzzlesS != null ? abilitiesOffInPuzzlesS.get() : false; }

    /** One room with hazards, in a run. */
    private static class Entry {
        final PlacedRoom room;
        final Placement placement;
        final RoomHazards hazards;
        final List<ArrowBank> banks;
        final double[] wake;
        /** The arrow banks' own clock: runs while a player is inside wake (world x0, z0, x1, z1). */
        int bankClock = 0;

        Entry(PlacedRoom room, Placement placement, RoomHazards hazards, List<ArrowBank> banks, double[] wake) {
            this.room = room;
            this.placement = placement;
            this.hazards = hazards;
            this.banks = banks;
            this.wake = wake;
        }
    }

    private static List<Entry> entries = List.of();

    /** While a replay runs the recording moves the crushers: the arms keep clocks, hits and sounds, the blocks are the recording's. */
    public static volatile boolean recordingMovesCrushers = false;

    /** Whether the canonical cell is in the reach of one of e's crushers. */
    private static boolean inArm(Entry e, int x, int y, int z) {
        for (Crusher c : e.hazards.crushers) {
            int[] b = c.armBox(c.sweep);
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true;
        }
        return false;
    }

    /** Whether the world cell is in the reach of a crusher of this run. */
    public static boolean isCrusherCell(int x, int y, int z) {
        for (Entry e : entries) {
            Pair<Integer, Integer> c = e.placement.toCanonical(x, z);
            if (c != null && inArm(e, c.first, y, c.second)) return true;
        }
        return false;
    }

    private static TrapSites sites = TrapSites.NONE;
    private static Set<UUID> humanIds = Set.of();
    private static final ArrayList<UUID> arrowHits = new ArrayList<>();
    private static double trapPercent = 15.0;
    private static double maxHp = 11351.0;

    @Override
    public void onRunStart(Run run) {
        TrapTimings timings = TrapTimings.load(run.packs.source);
        CrusherPlan plan = timings.roll(run.subRandom("traps"));
        double tp = run.packs.runstate != null && run.packs.runstate.damage != null ? run.packs.runstate.damage.trapPctOfMaxHp * 100.0 : 0.0;
        trapPercent = tp > 0.0 ? tp : 15.0;
        List<SbStatPreset> presets = run.packs.runstate != null && run.packs.runstate.sbStatPresets != null ? run.packs.runstate.sbStatPresets : List.of();
        SbStatPreset preset = null;
        for (SbStatPreset p : presets) if (p.id.equals(run.config.statsPreset)) { preset = p; break; }
        if (preset == null) for (SbStatPreset p : presets) if (p.id.equals("dev-p50")) { preset = p; break; }
        if (preset == null && !presets.isEmpty()) preset = presets.get(0);
        maxHp = preset != null && preset.maxHp > 0 ? preset.maxHp : 11351.0;
        sites = timings.sites;
        arrowHits.clear();
        List<Entry> out = new ArrayList<>();
        for (PlacedRoom room : run.plan.rooms) {
            RoomDesign design = room.design;
            if (design == null) continue;
            Placement pl = room.placement;
            if (pl == null) continue;
            List<ArrowRule> arrowRules = new ArrayList<>();
            for (ArrowRule r : sites.arrows) if (r.design.equals(design.id)) arrowRules.add(r);
            List<FlameRule> flameRules = new ArrayList<>();
            for (FlameRule r : sites.flames) if (r.design.equals(design.id)) flameRules.add(r);
            if (design.crushers.isEmpty() && design.tripwires.isEmpty() && arrowRules.isEmpty() && flameRules.isEmpty()) continue;
            List<Crusher> crushers = new ArrayList<>();
            for (RoomCrusher it : design.crushers) {
                CrusherTiming t = plan.timing(it.id);
                char axis = it.axis != null && !it.axis.isEmpty() ? it.axis.charAt(0) : 'x';
                int sweep = t.sweep != null ? t.sweep : it.sweep;
                String mat = it.material != null ? it.material : "minecraft:polished_granite";
                if (!t.pincer) crushers.add(new Crusher(it.id, it.slab, axis, it.sign, sweep, mat, t, plan.startAt(it.id)));
                else {
                    // the far arm's home is the cell past the sweep, growing back towards this one
                    int d = it.sign * (sweep + 1);
                    int[] far = Arrays.copyOf(it.slab, it.slab.length);
                    if (axis == 'x') { far[0] += d; far[3] += d; } else { far[2] += d; far[5] += d; }
                    crushers.add(new Crusher(it.id, it.slab, axis, it.sign, sweep / 2, mat, t, plan.startAt(it.id)));
                    crushers.add(new Crusher(it.id + "/far", far, axis, -it.sign, sweep - sweep / 2, mat, t, plan.startAt(it.id)));
                }
            }
            List<Hook> hooks = new ArrayList<>();
            for (RoomTripwire it : design.tripwires) hooks.add(new Hook(it.pos.x, it.pos.y, it.pos.z, it.facing != null ? it.facing : ""));
            List<TripLine> lines = TripLine.pair(hooks);
            boolean hurts = timings.tripwiresHurtIn(room.name);
            List<Hazard> extras = new ArrayList<>();
            List<int[]> falling = new ArrayList<>();
            for (Pos p : design.fallingBlocks) falling.add(new int[]{p.x, p.y, p.z});
            for (FallRule rule : sites.falls) if (rule.design.equals(design.id)) {
                FallingFloor f = fallingFloor(rule, falling, lines);
                if (f != null) extras.add(f);
            }
            for (int i = 0; i < flameRules.size(); i++) extras.add(new FlameBank(flameRules.get(i), sites.flameStream, run.subRandom("traps.flame." + room.index + "." + i).nextLong()));
            List<ArrowBank> banks = new ArrayList<>();
            for (int i = 0; i < arrowRules.size(); i++) {
                ArrowRule r = arrowRules.get(i);
                banks.add(new ArrowBank(r, run.subRandom("traps.arrow." + room.index + "." + i).nextInt(r.period)));
            }
            out.add(new Entry(room, pl, new RoomHazards(crushers, lines, hurts, hurts || timings.tripwiresClickIn(room.name), extras), banks,
                    wakeBox(pl, arrowRules, sites.arrowFlight.range)));
        }
        entries = out;
    }

    /** The world box (x0, z0, x1, z1) around the dispensers of rules, range wider on every side; null when there are none. */
    private static double[] wakeBox(Placement pl, List<ArrowRule> rules, double range) {
        List<WorldPos> ws = new ArrayList<>();
        for (ArrowRule r : rules) for (Emitter d : r.dispensers) ws.add(pl.toWorld(d.x, d.y, d.z));
        if (ws.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (WorldPos w : ws) { minX = Math.min(minX, w.x); minZ = Math.min(minZ, w.z); maxX = Math.max(maxX, w.x); maxZ = Math.max(maxZ, w.z); }
        return new double[]{minX - range, minZ - range, maxX + 1 + range, maxZ + 1 + range};
    }

    /** Builds the floor a FallRule describes for a room, or null when the rule does not fit it. */
    static FallingFloor fallingFloor(FallRule rule, List<int[]> falling, List<TripLine> lines) {
        List<int[]> cells = FallingFloor.cluster(falling, rule.cluster);
        if (cells.isEmpty()) return null;
        List<int[]> extra = new ArrayList<>();
        for (int[] b : rule.also) for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) for (int x = b[0]; x <= b[3]; x++) extra.add(new int[]{x, y, z});
        FloorTrigger trigger;
        if (rule.trigger.equals("string")) {
            int[] h = rule.hook;
            if (h == null) return null;
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).hasHook(h[0], h[1], h[2])) { idx = i; break; }
            if (idx < 0) return null;
            trigger = new FloorTrigger.StringTouched(idx);
        } else trigger = FloorTrigger.StandOn.INSTANCE;
        return new FallingFloor(cells, extra, trigger);
    }

    @Override
    public List<AdsCommand> devCommands() {
        return List.of(new AdsCommand(
                "traps", "/ads dev traps <list|tp [room number]|status|arrows [room number]>",
                "Lists the rooms with crushers, tripwires, arrow banks or flamethrowers in this run, takes you to the first hazard of one, shows each room's clock, crushers, floors, banks and flames, or fires a room's arrow banks now.",
                true, null, TrapsModule::devTraps));
    }

    private static Integer toIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static Entry entryByIndex(Integer n) {
        if (n == null) return null;
        for (Entry e : entries) if (e.room.index == n) return e;
        return null;
    }

    private static void devTraps(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on. Start one with /ads run."); return; }
        if (entries.isEmpty()) { ctx.reply("This layout has no room with crushers, tripwires, arrow banks or flamethrowers."); return; }
        String a0 = ctx.arg(0);
        if (a0 == null || a0.equals("list")) {
            for (Entry e : entries) {
                int flamesN = 0;
                for (Hazard h : e.hazards.extras) if (h instanceof FlameBank) flamesN++;
                ctx.reply("room " + e.room.index + ": " + e.room.name + " (" + e.room.type + ") " + e.hazards.crushers.size() + " crushers, " + e.hazards.lines.size() + " tripwires, "
                        + e.banks.size() + " arrow banks, " + flamesN + " flamethrower groups");
                for (Crusher c : e.hazards.crushers) {
                    int[] b = c.armBox(c.sweep);
                    WorldPos p = e.placement.toWorld(b[0], b[1], b[2]); WorldPos q = e.placement.toWorld(b[3], b[4], b[5]);
                    WorldPos h = e.placement.toWorld(c.slab[0], c.slab[1], c.slab[2]);
                    ctx.reply("  " + c.id + ": arm " + Math.min(p.x, q.x) + ".." + Math.max(p.x, q.x) + " " + p.y + ".." + q.y + " " + Math.min(p.z, q.z) + ".." + Math.max(p.z, q.z)
                            + ", home at " + h.x + " " + h.y + " " + h.z + ", sweep " + c.sweep + ", " + c.timing.ticksPerBlock + "/" + c.timing.stillTicks);
                }
            }
            return;
        }
        switch (a0) {
            case "status" -> {
                for (Entry e : entries) {
                    StringBuilder sb = new StringBuilder("room " + e.room.index + ": clock " + e.hazards.clock + ", crushers out ");
                    for (int i = 0; i < e.hazards.crushers.size(); i++) {
                        Crusher c = e.hazards.crushers.get(i);
                        if (i > 0) sb.append(", ");
                        int slash = c.id.indexOf('/');
                        sb.append(slash < 0 ? c.id : c.id.substring(slash + 1)).append("=").append(c.extensionAt(e.hazards.clock)).append("/").append(c.sweep);
                    }
                    for (Hazard hz : e.hazards.extras) if (hz instanceof FallingFloor f) {
                        sb.append(", floor of ").append(f.cells.size()).append(" blocks ").append(f.fired ? "gone" : "standing");
                    }
                    if (!e.banks.isEmpty()) {
                        sb.append(", bank clock ").append(e.bankClock).append(", banks ");
                        for (int i = 0; i < e.banks.size(); i++) {
                            ArrowBank b = e.banks.get(i);
                            if (i > 0) sb.append(", ");
                            sb.append(b.rule.dispensers.size()).append("x").append(b.rule.perDispenser).append(" every ").append(b.rule.period).append(" (phase ").append(b.phase).append(")");
                        }
                    }
                    ctx.reply(sb.toString());
                }
            }
            case "arrows" -> {
                Entry e = entryByIndex(toIntOrNull(ctx.arg(1)));
                if (e == null) for (Entry x : entries) if (!x.banks.isEmpty()) { e = x; break; }
                if (e == null || e.banks.isEmpty()) { ctx.reply("No room in this run has an arrow bank."); return; }
                for (ArrowBank b : e.banks) fire(e, b.rule);
                ctx.reply("Fired the " + e.banks.size() + " arrow bank(s) of " + e.room.name + " (room " + e.room.index + ") now.");
            }
            case "tp" -> {
                Entry e = entryByIndex(toIntOrNull(ctx.arg(1)));
                if (e == null) for (Entry x : entries) if (x.room.type == RoomType.TRAP) { e = x; break; }
                if (e == null) e = entries.get(0);
                TripLine line = e.hazards.lines.isEmpty() ? null : e.hazards.lines.get(0);
                Crusher crusher = e.hazards.crushers.isEmpty() ? null : e.hazards.crushers.get(0);
                Emitter em = null;
                if (!e.banks.isEmpty() && !e.banks.get(0).rule.dispensers.isEmpty()) em = e.banks.get(0).rule.dispensers.get(0);
                if (em == null) {
                    for (Hazard hz : e.hazards.extras) if (hz instanceof FlameBank fb) {
                        if (!fb.rule.emitters.isEmpty()) em = fb.rule.emitters.get(0);
                        break;
                    }
                }
                Triple<Double, Double, Double> spot;
                if (line != null) spot = Triple.of(line.axis == 'x' ? line.from + 1.5 : line.fixed + 0.5, (double) line.y, line.axis == 'x' ? line.fixed + 0.5 : line.from + 1.5);
                else if (crusher != null) spot = Triple.of(crusher.slab[0] + 0.5, (double) crusher.slab[1], crusher.slab[2] - 1.5);
                else if (em != null) spot = Triple.of(em.x + 0.5 + em.dx() * 3.0, (double) em.y, em.z + 0.5 + em.dz() * 3.0);
                else { ctx.reply(e.room.name + " has no hazard to go to."); return; }
                Pair<Double, Double> w = Frames.toWorld(e.placement, spot.first, spot.third);
                Sim.tp(ctx.player, w.first, spot.second, w.second, Frames.yawToWorld(e.placement, 0f), 0f);
                ctx.reply("Beside the first " + (line != null ? "tripwire" : crusher != null ? "crusher" : "dispenser") + " of " + e.room.name + " (room " + e.room.index + ").");
            }
            default -> ctx.reply("Usage: /ads dev traps <list|tp [room number]|status|arrows [room number]>");
        }
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) { entries = List.of(); }

    @Override
    public void onTick(Run run) {
        if (run.stage != RunStage.CLEAR && run.stage != RunStage.BLOOD) return;
        if (entries.isEmpty()) return;
        // bots count for the clocks; only people are hurt
        List<PlayerRecord> present = new ArrayList<>();
        for (PlayerRecord p : run.state.players.values()) if (!p.ghost) present.add(p);
        HashSet<UUID> humans = new HashSet<>();
        for (PlayerRecord p : run.state.players.values()) if (!p.isBot) humans.add(p.uuid);
        humanIds = humans;
        if (!arrowHits.isEmpty()) {
            List<UUID> ids = new ArrayList<>(arrowHits);
            arrowHits.clear();
            for (UUID id : ids) strike(run, new HazardHit(id, HazardKind.ARROW));
        }
        for (Entry e : entries) {
            List<PlayerPos> inside = new ArrayList<>();
            for (PlayerRecord p : present) {
                RoomRecord rr = run.state.roomAt(p.x, p.z);
                if (rr == null || rr.index() != e.room.index) continue;
                Pair<Double, Double> c = Frames.toCanonical(e.placement, p.x, p.z);
                if (c == null) continue;
                inside.add(new PlayerPos(p.uuid, c.first, p.y, c.second));
            }
            if (!inside.isEmpty()) {
                RoomHazards.Result r = e.hazards.tick(inside);
                handle(run, e, r.edits, humanHits(r.hits), r.cues, r.debris, r.volleys, r.flames);
            }
            double[] w = e.wake;
            if (w != null) {
                boolean near = false;
                for (PlayerRecord p : present) if (p.x >= w[0] && p.x <= w[2] && p.z >= w[1] && p.z <= w[3]) { near = true; break; }
                if (near) {
                    e.bankClock++;
                    HazardOut out = new HazardOut();
                    for (ArrowBank b : e.banks) b.tick(e.bankClock, List.of(), out);
                    handle(run, e, out.edits, humanHits(out.hits), out.cues, out.debris, out.volleys, out.flames);
                }
            }
        }
    }

    private static List<HazardHit> humanHits(List<HazardHit> hits) {
        List<HazardHit> out = new ArrayList<>();
        for (HazardHit h : hits) if (humanIds.contains(h.player)) out.add(h);
        return out;
    }

    /** Does what a room's hazards asked for this tick. */
    private static void handle(Run run, Entry e, List<BlockEdit> edits, List<HazardHit> hits, List<SoundCue> cues, List<int[]> debris, List<ArrowRule> volleys, List<FlameFx> flames) {
        if (!debris.isEmpty()) spawnDebris(e, debris);
        List<BlockEdit> mine;
        if (recordingMovesCrushers) {
            mine = new ArrayList<>();
            for (BlockEdit b : edits) if (!inArm(e, b.x, b.y, b.z)) mine.add(b);
        } else mine = edits;
        if (!mine.isEmpty()) HardBlocks.apply(e.placement, mine);
        for (SoundCue cue : cues) play(e, cue);
        for (ArrowRule rule : volleys) fire(e, rule);
        for (FlameFx fx : flames) draw(e, fx);
        for (HazardHit hit : hits) strike(run, hit);
    }

    /** One hit: 15 percent true damage (a crusher 30) through the damage service, and Hypixel's red line. */
    private static void strike(Run run, HazardHit hit) {
        double percent = hit.kind == HazardKind.CRUSHER ? CRUSHER_PERCENT : trapPercent;
        HurtResult res = Services.damage().hurtPlayer(run, hit.player, percent, hit.kind == HazardKind.CRUSHER ? DamageSource.CRUSH : DamageSource.TRAP, null, true);
        if (res.applied <= 0.0) return;
        double damage = res.applied / 100.0 * maxHp;
        String line = switch (hit.kind) {
            case CRUSHER -> crusherLine(damage);
            case TRIPWIRE -> tripwireLine(damage);
            case ARROW -> arrowTrapLine(damage);
            case FLAME -> flamethrowerLine(damage);
        };
        Services.channels().chat(line, new Audience.Only(hit.player));
    }

    /** Every dispenser of rule shoots now, 0.7 blocks in front of it, along the facing and a tenth up. Silent. */
    private static void fire(Entry e, ArrowRule rule) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return;
        ArrowFlight flight = sites.arrowFlight;
        for (Emitter d : rule.dispensers) {
            WorldPos w = e.placement.toWorld(d.x, d.y, d.z);
            if (!RunBlocks.isLoaded(w.x, w.z)) continue;
            Pair<Double, Double> p0 = Frames.toWorld(e.placement, d.x + 0.5, d.z + 0.5);
            Pair<Double, Double> p1 = Frames.toWorld(e.placement, d.x + 0.5 + d.dx(), d.z + 0.5 + d.dz());
            double x0 = p0.first, z0 = p0.second;
            double dx = p1.first - x0; double dz = p1.second - z0; double dy = d.dy();
            for (int k = 0; k < rule.perDispenser; k++) {
                TrapArrow a = new TrapArrow(level, x0 + 0.7 * dx, w.y + 0.5 + 0.7 * dy, z0 + 0.7 * dz, flight.maxTicks,
                        ent -> humanIds.contains(ent.getUUID()), pl -> arrowHits.add(pl.getUUID()));
                a.shoot(dx, dy + 0.1, dz, flight.speed, flight.inaccuracy);
                Sim.spawn(a);
            }
        }
    }

    /** The flame particles of one stretch of a stream. */
    private static void draw(Entry e, FlameFx fx) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return;
        Emitter em = fx.emitter;
        KRandom r = KRandom.DEFAULT;
        for (int k = 0; k < 5; k++) {
            double along = fx.from + (fx.to - fx.from) * r.nextDouble();
            double sx = em.dx() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0.0;
            double sz = em.dz() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0.0;
            double cx = em.x + 0.5 + em.dx() * (0.5 + along) + sx;
            double cz = em.z + 0.5 + em.dz() * (0.5 + along) + sz;
            double cy = em.y + 0.2 + r.nextDouble() * 0.8 + em.dy() * (0.5 + along);
            Pair<Double, Double> w = Frames.toWorld(e.placement, cx, cz);
            level.sendParticles(ParticleTypes.FLAME, w.first, cy, w.second, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Hypixel's damage figure: a thousands comma and at most one decimal ("1,336" when whole, "1,336.5" otherwise). */
    public static String damageText(double damage) {
        String s = String.format(Locale.US, "%,.1f", damage);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Hypixel's red line for a tripwire hit. */
    public static String tripwireLine(double damage) { return "§cThe Tripwire Trap hit you for " + damageText(damage) + " damage!"; }

    public static String crusherLine(double damage) { return "§cThe Crusher hit you for " + damageText(damage) + " damage!"; }

    public static String arrowTrapLine(double damage) { return "§cThe Arrow Trap hit you for " + damageText(damage) + " damage!"; }

    public static String flamethrowerLine(double damage) { return "§cThe Flamethrower hit you for " + damageText(damage) + " damage!"; }

    /** Percent of max HP a crusher takes (one recorded hit). */
    public static final double CRUSHER_PERCENT = 30.0;

    /** Plays one of a room's sounds at its canonical position. */
    private static void play(Entry e, SoundCue cue) {
        Pair<Double, Double> w = Frames.toWorld(e.placement, cue.x, cue.z);
        Pair<SoundEvent, SoundSource> s = soundFor(cue.sound);
        Sim.sound(s.first, cue.sound.volume, cue.sound.pitch, new Vec3(w.first, cue.y, w.second), s.second);
    }

    private static Pair<SoundEvent, SoundSource> soundFor(HazardSound s) {
        return switch (s) {
            case LEVER_ON, LEVER_OFF -> Pair.of(SoundEvents.LEVER_CLICK, SoundSource.BLOCKS);
            case TRIPWIRE_BLAST -> Pair.of(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS);
            case CRUSHER_SLAM -> Pair.of(SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL);
            case FLOOR_COLLAPSE -> Pair.of(SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS);
            case FLAME_SHOOT -> Pair.of(SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE);
        };
    }

    /** One falling-block entity (no motion yet) per canonical cell, made from the block standing there now. */
    private static void spawnDebris(Entry e, List<int[]> cells) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return;
        for (int[] c : cells) {
            WorldPos w = e.placement.toWorld(c[0], c[1], c[2]);
            BlockState st = RunBlocks.get(w.x, w.y, w.z);
            if (st == null) continue;
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            Debris d = new Debris(level, st);
            d.snapTo(w.x + 0.5, w.y + 0.5, w.z + 0.5, 0f, 0f);
            d.setDeltaMovement(Vec3.ZERO);
            Sim.spawn(d);
        }
    }

    @Override
    public JsonObject snapshot(Run run) {
        JsonObject o = new JsonObject();
        for (Entry e : entries) {
            JsonObject h = e.hazards.snapshot();
            h.addProperty("bankClock", e.bankClock);
            o.add(String.valueOf(e.room.index), h);
        }
        return o;
    }

    @Override
    public void restore(Run run, JsonObject data) {
        for (Entry e : entries) {
            JsonObject o = data.getAsJsonObject(String.valueOf(e.room.index));
            if (o != null) {
                e.hazards.restore(o);
                JsonElement bc = o.get("bankClock");
                e.bankClock = bc != null ? bc.getAsInt() : 0;
            }
            HardBlocks.apply(e.placement, e.hazards.currentEdits());
        }
    }

    @Override
    public boolean abilitiesDisabled(Run run, UUID player) {
        PlayerRecord rec = run.state.players.get(player);
        if (rec == null) return false;
        RoomRecord rr = run.state.roomAt(rec.x, rec.z);
        if (rr == null) return false;
        return abilitiesOffIn(rr.plan.type);
    }

    /** Whether abilities are off in a room of this type under the current settings. */
    public static boolean abilitiesOffIn(RoomType type) {
        return switch (type) {
            case TRAP -> abilitiesOffInTrapRooms();
            case PUZZLE -> abilitiesOffInPuzzleRooms();
            default -> false;
        };
    }
}
