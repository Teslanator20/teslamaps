// Ported from ADM's p3sim/clear/bots/ClearSim.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.runstate.Dist;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** The headless gate for the ClearPlan: builds many plans and compares their percentiles with the recorded ones. No Minecraft. */
public final class ClearSim {
    private ClearSim() {}

    /** The percents every comparison reports. */
    public static final double[] PCT = new double[]{5.0, 25.0, 50.0, 75.0, 95.0};

    /** Threads the gate plans on (a shared machine: a few, not every core). */
    public static final int GATE_THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 4);

    /** One simulated plan, reduced to the numbers that are compared. */
    public static class Sample {
        public final double bloodOpen;
        public final double watcherDone;
        public final double maxor;
        public final double tilesDone;
        public final double greenFrac;

        public Sample(double bloodOpen, double watcherDone, double maxor, double tilesDone, double greenFrac) {
            this.bloodOpen = bloodOpen;
            this.watcherDone = watcherDone;
            this.maxor = maxor;
            this.tilesDone = tilesDone;
            this.greenFrac = greenFrac;
        }
    }

    /** One compared quantity. */
    public static class Row {
        public final String name;
        public final double[] recorded;
        public final double[] simulated;
        public final Tolerance tolerance;
        public final boolean pass;
        public final String note;

        public Row(String name, double[] recorded, double[] simulated, Tolerance tolerance, boolean pass, String note) {
            this.name = name;
            this.recorded = recorded;
            this.simulated = simulated;
            this.tolerance = tolerance;
            this.pass = pass;
            this.note = note;
        }
    }

    /** How far a simulated percentile may be from the recorded one: a fraction of it, but at least [abs]. */
    public static class Tolerance {
        public final double rel50;
        public final double rel25_75;
        public final double rel5_95;
        public final double abs;

        public Tolerance(double rel50, double rel25_75, double rel5_95, double abs) {
            this.rel50 = rel50;
            this.rel25_75 = rel25_75;
            this.rel5_95 = rel5_95;
            this.abs = abs;
        }

        public boolean ok(double rec, double sim, int index) {
            double rel = switch (index) { case 2 -> rel50; case 1, 3 -> rel25_75; default -> rel5_95; };
            return Math.abs(sim - rec) <= Math.max(rel * Math.abs(rec), abs);
        }
    }

    public static class Report {
        public final int runs;
        public final int layouts;
        public final List<Row> rows;
        public final List<String> problems;

        public Report(int runs, int layouts, List<Row> rows, List<String> problems) {
            this.runs = runs;
            this.layouts = layouts;
            this.rows = rows;
            this.problems = problems;
        }

        public boolean passed() {
            for (Row r : rows) if (!(r.pass || r.recorded == null)) return false;
            return true;
        }

        /** Plain lines for the chat or a log. */
        public List<String> lines() {
            ArrayList<String> out = new ArrayList<>();
            out.add("Bot plan check: " + runs + " plans over " + layouts + " layouts. Percents 5, 25, 50, 75, 95.");
            for (Row r : rows) {
                String rec = r.recorded != null ? join(r.recorded) : "no recorded numbers";
                out.add((r.pass || r.recorded == null ? "OK  " : "OFF ") + r.name + ": simulated " + join(r.simulated) + "; recorded " + rec + (!r.note.isEmpty() ? " (" + r.note + ")" : ""));
            }
            out.addAll(problems);
            out.add(passed() ? "All within tolerance." : "Some numbers are outside tolerance.");
            return out;
        }

        private static String join(double[] v) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < v.length; i++) { if (i > 0) sb.append(", "); sb.append(fmt(v[i])); }
            return sb.toString();
        }

        private static String fmt(double v) {
            return Math.abs(v) < 2.0 && v != 0.0 ? String.format(Locale.ROOT, "%.2f", v) : String.format(Locale.ROOT, "%.0f", v);
        }
    }

    public static List<Seat> seatsFor(List<String> classes) {
        ArrayList<Seat> out = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) out.add(new Seat(i, classes.get(i), false));
        return out;
    }

    /** The five classes in their usual order; the gate puts one bot on each. */
    public static final List<String> FIVE = List.of("HEALER", "MAGE", "BERSERK", "ARCHER", "TANK");

    /** Builds one plan for a dungeon with a whole party of bots. */
    public static ClearPlan planFor(DungeonPlan dungeon, BotTables tables, long seed, PartyStrategy strategy, RunStatePack rawTables, List<String> classes, double workScale,
                                    RoomAreas areas) {
        List<Seat> seats = seatsFor(classes);
        KRandom rnd = new KRandom(seed);
        BloodAssignment assignment = BotStrategies.assignment(strategy, new BotStrategyContext(dungeon, seats, new KRandom(rnd.nextLong()), rawTables, 0));
        return new ClearPlanner(new PlannerInput(dungeon, tables, seats, rnd, assignment, PlanState.initial(), 1.0, workScale, null, java.util.Map.of(), areas, List.of())).build();
    }

    public static ClearPlan planFor(DungeonPlan dungeon, BotTables tables, long seed, PartyStrategy strategy, RunStatePack rawTables, List<String> classes, double workScale) {
        return planFor(dungeon, tables, seed, strategy, rawTables, classes, workScale, null);
    }

    public static ClearPlan planFor(DungeonPlan dungeon, BotTables tables, long seed, PartyStrategy strategy, RunStatePack rawTables, List<String> classes) {
        return planFor(dungeon, tables, seed, strategy, rawTables, classes, BotTuning.ROOM_WORK_SCALE, null);
    }

    public static ClearPlan planFor(DungeonPlan dungeon, BotTables tables, long seed, PartyStrategy strategy, RunStatePack rawTables) {
        return planFor(dungeon, tables, seed, strategy, rawTables, FIVE, BotTuning.ROOM_WORK_SCALE, null);
    }

    public static ClearPlan planFor(DungeonPlan dungeon, BotTables tables, long seed, PartyStrategy strategy) {
        return planFor(dungeon, tables, seed, strategy, null, FIVE, BotTuning.ROOM_WORK_SCALE, null);
    }

    /** The numbers a plan implies at Maxor. */
    public static Sample sample(DungeonPlan dungeon, ClearPlan plan, BotTables tables, KRandom r) {
        PlanMilestones ms = plan.milestones;
        int maxor = ms.maxorT;
        int tiles = 0;
        int green = 0;
        boolean bloodGreen = r.nextDouble() < tables.bloodGreenFrac();
        for (PlacedRoom room : dungeon.rooms) {
            switch (room.type) {
                case ENTRANCE -> green++;
                case FAIRY -> { green++; tiles += room.tileCount(); }
                case BLOOD -> { if (bloodGreen) { green++; tiles += room.tileCount(); } }
                default -> {
                    RoomPlan rp = plan.rooms.get(room.index);
                    if (rp == null) continue;
                    Integer white = rp.whiteT != null ? rp.whiteT : rp.greenT;
                    if (white != null && white <= maxor) tiles += room.tileCount();
                    Integer g = rp.greenT != null ? rp.greenT : room.secretsMax == 0 ? rp.whiteT : null;
                    if (g != null && g <= maxor) green++;
                }
            }
        }
        return new Sample(ms.bloodOpenT, ms.watcherDoneT, maxor, tiles, (double) green / Math.max(dungeon.rooms.size(), 1));
    }

    /** Runs [runs] plans, cycling through [dungeons] with a fresh seed each, and compares the percentiles with the recorded ones in [tables]. */
    public static Report gate(List<DungeonPlan> dungeons, BotTables tables, int runs, long seed, PartyStrategy strategy, double workScale, RoomAreas areas) {
        if (dungeons.isEmpty()) throw new IllegalArgumentException("the gate needs at least one dungeon");
        KRandom r = new KRandom(seed);
        long[] seeds = new long[runs];
        for (int i = 0; i < runs; i++) seeds[i] = r.nextLong();
        ForkJoinPool pool = new ForkJoinPool(GATE_THREADS);
        List<Object> outcomes;
        try {
            outcomes = pool.submit((Callable<List<Object>>) () -> IntStream.range(0, runs).boxed().collect(Collectors.toList()).parallelStream().map(i -> {
                DungeonPlan d = dungeons.get(i % dungeons.size());
                try {
                    ClearPlan plan = planFor(d, tables, seeds[i], strategy, null, FIVE, workScale, areas);
                    return (Object) sample(d, plan, tables, new KRandom(seeds[i] ^ 0x5DEECE66DL));
                } catch (Exception e) {
                    return (Object) ("A plan failed for " + d.layoutId + ": " + e.getMessage());
                }
            }).collect(Collectors.toList())).get();
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }
        ArrayList<Sample> samples = new ArrayList<>();
        ArrayList<String> problems = new ArrayList<>();
        for (Object o : outcomes) if (o instanceof Sample s) samples.add(s);
        for (Object o : outcomes) if (o instanceof String s && problems.size() < 3) problems.add(s);
        ArrayList<Row> rows = new ArrayList<>();
        Tolerance ticks = new Tolerance(0.12, 0.2, 0.35, 15.0);
        row(rows, samples, "blood door open", tables.recordedBloodOpen(), s -> s.bloodOpen, ticks, "");
        row(rows, samples, "Watcher done", tables.recordedWatcherDone(), s -> s.watcherDone, ticks, "from the pack's milestone tables");
        row(rows, samples, "Maxor entry", tables.recordedMaxorEntry(), s -> s.maxor, ticks, "from the pack's milestone tables");
        row(rows, samples, "tiles done at Maxor", tables.recordedTilesDone(), s -> s.tilesDone, new Tolerance(0.04, 0.06, 0.1, 1.5), "of 35");
        row(rows, samples, "rooms green at Maxor", tables.recordedGreenFrac(), s -> s.greenFrac, new Tolerance(0.08, 0.1, 0.15, 0.06), "share of all rooms");
        return new Report(samples.size(), dungeons.size(), rows, problems);
    }

    public static Report gate(List<DungeonPlan> dungeons, BotTables tables, int runs, long seed, PartyStrategy strategy, double workScale) {
        return gate(dungeons, tables, runs, seed, strategy, workScale, null);
    }

    public static Report gate(List<DungeonPlan> dungeons, BotTables tables, int runs, long seed, PartyStrategy strategy) {
        return gate(dungeons, tables, runs, seed, strategy, BotTuning.ROOM_WORK_SCALE, null);
    }

    public static Report gate(List<DungeonPlan> dungeons, BotTables tables, int runs, long seed) {
        return gate(dungeons, tables, runs, seed, GateParking.INSTANCE, BotTuning.ROOM_WORK_SCALE, null);
    }

    public static Report gate(List<DungeonPlan> dungeons, BotTables tables, int runs) {
        return gate(dungeons, tables, runs, 7L, GateParking.INSTANCE, BotTuning.ROOM_WORK_SCALE, null);
    }

    public static Report gate(List<DungeonPlan> dungeons, BotTables tables) {
        return gate(dungeons, tables, 500, 7L, GateParking.INSTANCE, BotTuning.ROOM_WORK_SCALE, null);
    }

    private static void row(List<Row> rows, List<Sample> samples, String name, Dist rec, ToDoubleFunction<Sample> get, Tolerance tol, String note) {
        double[] xs = new double[samples.size()];
        for (int i = 0; i < xs.length; i++) xs[i] = get.applyAsDouble(samples.get(i));
        double[] sim = pctOf(xs);
        double[] recorded = null;
        if (rec != null) {
            recorded = new double[PCT.length];
            for (int i = 0; i < PCT.length; i++) recorded[i] = rec.at(PCT[i]);
        }
        boolean pass = true;
        if (recorded != null) for (int i = 0; i < sim.length; i++) if (!tol.ok(recorded[i], sim[i], i)) { pass = false; break; }
        rows.add(new Row(name, recorded, sim, tol, pass, note));
    }

    /** The 5, 25, 50, 75, 95 percentiles of [xs] (linear between the nearest ranks). */
    public static double[] pctOf(double[] xs) {
        if (xs.length == 0) return new double[PCT.length];
        double[] s = xs.clone();
        Arrays.sort(s);
        double[] out = new double[PCT.length];
        for (int i = 0; i < PCT.length; i++) {
            double pos = PCT[i] / 100.0 * (s.length - 1);
            int lo = (int) pos;
            int hi = Math.min(lo + 1, s.length - 1);
            out[i] = s[lo] + (s[hi] - s[lo]) * (pos - lo);
        }
        return out;
    }
}
