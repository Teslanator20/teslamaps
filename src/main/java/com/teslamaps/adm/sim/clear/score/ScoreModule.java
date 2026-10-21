// Ported from ADM's p3sim/clear/score/ScoreModule.kt
package com.teslamaps.adm.sim.clear.score;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.runstate.ScoreModel;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.MobKilled;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PuzzleRecord;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.RunState;
import com.teslamaps.adm.sim.run.ScoreState;
import com.teslamaps.adm.sim.run.ScoreUpdated;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.EndScreen;
import com.teslamaps.adm.sim.run.api.ScoreService;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ScoreModule implements AdsModule, ScoreService {
    public static final ScoreModule INSTANCE = new ScoreModule();

    private ScoreModule() {}

    @Override public String id() { return "score"; }
    @Override public String owner() { return "secrets"; }

    public static final int DEFAULT_PERIOD = 200;
    public static final int DEFAULT_ORIGIN = 20;

    private static ScoreSchedule schedule = new ScoreSchedule(DEFAULT_ORIGIN, DEFAULT_PERIOD);
    private static final HashMap<UUID, Integer> killed = new HashMap<>();

    @Override public void onRunStart(Run run) {
        ScoreModel m = run.packs.runstate != null ? run.packs.runstate.score : null;
        schedule = new ScoreSchedule(m != null ? m.clockOriginTicksAfterT0 : DEFAULT_ORIGIN, Math.max(m != null ? m.periodTicks : DEFAULT_PERIOD, 20));
        killed.clear();
        run.state.score.score = 0;
        run.state.score.clearedPct = 0;
        run.state.score.lastUpdateT = Integer.MIN_VALUE;
        run.state.score.history.clear();
    }

    @Override public void onTick(Run run) {
        if (run.stage == RunStage.COUNTDOWN || run.stage == RunStage.END) return;
        if (schedule.poll(run.t())) recompute(run);
    }

    @Override public void onEvent(Run run, RunEvent event) {
        if (event instanceof MobKilled k) {
            UUID by = k.by;
            if (by == null) return;
            PlayerRecord p = run.state.players.get(by);
            if (p != null && !p.isBot) killed.merge(by, 1, Integer::sum);
        }
    }

    public static ScoreInputs inputs(Run run) {
        RunState s = run.state;
        int announced = Math.max(s.puzzles.size(), run.plan.puzzleNames.size());
        int solved = 0;
        for (PuzzleRecord p : s.puzzles) if (p.status == PuzzleStatus.SOLVED) solved++;
        return new ScoreInputs(
                s.counters.completed, s.secrets.teamFound, s.secrets.teamMax,
                s.secrets.crypts, s.secrets.mimicKilled, s.secrets.princeKilled, s.secrets.batKilled,
                s.deaths.teamDeaths, Math.max(announced - solved, 0), run.t() / 20.0
        );
    }

    public static ScoreBreakdown breakdown(Run run) {
        return ScoreFormula.evaluate(run.packs.runstate != null ? run.packs.runstate.score : null, inputs(run));
    }

    @Override public int recompute(Run run) {
        ScoreState s = run.state.score;
        int score = breakdown(run).total;
        s.score = score;
        s.clearedPct = run.state.counters.clearedPct();
        s.lastUpdateT = run.t();
        s.history.add(Pair.of(run.t(), score));
        run.bus.post(new ScoreUpdated(run.t(), score, s.clearedPct));
        return score;
    }

    @Override public EndScreen endScreen(Run run) {
        RunState s = run.state;
        return new EndScreen(s.score.score, Grades.letter(s.score.score), s.score.clearedPct, s.secrets.teamFound, s.secrets.teamMax, s.secrets.crypts,
                s.deaths.teamDeaths, run.t());
    }

    @Override public void showEndScreen(Run run) {
        EndScreen e = endScreen(run);
        List<PlayerRecord> humans = new ArrayList<>();
        for (PlayerRecord p : run.state.players.values()) if (!p.isBot) humans.add(p);
        if (humans.isEmpty()) { for (String line : EndScreenText.lines(e)) Services.channels().chat(line); return; }
        for (PlayerRecord h : humans) {
            for (String line : EndScreenText.lines(e, EndScreenText.DEFAULT_BOSSES, 0, EndScreenText.DEFAULT_FLOOR_NAME, playerNumbers(run, h))) {
                Services.channels().chat(line, new Audience.Only(h.uuid));
            }
        }
    }

    public static final double CATA_XP_MIN = 46_500.0;
    public static final double CATA_XP_MAX = 50_000.0;
    public static final long DAMAGE_MIN = 600_000_000L;
    public static final long DAMAGE_MAX = 900_000_000L;

    public static EndScreenPlayer playerNumbers(Run run, PlayerRecord rec) {
        KRandom r = run.subRandom("endscreen-" + rec.uuid);
        String cls = "Mage";
        for (String c : EndScreenText.CLASSES) if (c.equalsIgnoreCase(rec.dungeonClass)) { cls = c; break; }
        Object dt = rec.extras.get("damage.total");
        long damage = dt instanceof Number num ? num.longValue() : r.nextLong(DAMAGE_MIN, DAMAGE_MAX);
        long healing = cls.equals("Healer") ? r.nextLong(50_000L, 150_000L) : r.nextLong(800L, 4_000L);
        int bits = r.nextDouble() < 0.72 ? r.nextInt(45, 80) : 0;
        double cataXp = r.nextDouble(CATA_XP_MIN, CATA_XP_MAX);
        Integer k = killed.get(rec.uuid);
        return new EndScreenPlayer(cls, cataXp, bits, damage, healing, k != null ? k : 0, rec.secretsFound);
    }

    @Override public JsonObject snapshot(Run run) {
        JsonObject o = new JsonObject();
        for (Map.Entry<UUID, Integer> e : killed.entrySet()) o.addProperty(e.getKey().toString(), e.getValue());
        return o;
    }

    @Override public void restore(Run run, JsonObject data) {
        killed.clear();
        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            UUID u;
            try { u = UUID.fromString(e.getKey()); } catch (Throwable t) { u = null; }
            if (u != null) killed.put(u, e.getValue().getAsInt());
        }
        schedule.resetTo(run.t());
    }

    // ---- /ads dev ----

    private static Integer toIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    @Override public List<AdsCommand> devCommands() {
        return List.of(
                new AdsCommand("scorenow", "/ads dev scorenow", "Prints the score parts for the run as it stands, the last published score and when the next step is due.", true, null, ctx -> {
                    Run run = ctx.run();
                    if (run == null) ctx.reply("No run is on.");
                    else {
                        ScoreBreakdown b = breakdown(run); ScoreInputs i = inputs(run);
                        ctx.reply("Now: " + b.total + " (" + Grades.letter(b.total) + "); published " + run.state.score.score + ", next step at run tick " + schedule.nextT() + " (now " + run.t() + ").");
                        ctx.reply("  rooms " + f1(b.rooms) + " (" + i.completedTiles + "/36 tiles), secrets " + f1(b.secrets) + " (" + i.secretsFound + "/" + i.secretsTotal + "), crypts "
                                + f1(b.crypts) + " (" + i.crypts + "), bonus " + f1(b.bonus) + ", puzzles " + f1(b.puzzles) + " (" + i.unsolvedPuzzles + " unsolved), deaths "
                                + f1(b.deaths) + " (" + i.deaths + "), speed " + f1(b.speed));
                    }
                }),
                new AdsCommand("scorecalc", "/ads dev scorecalc <tiles> <found> <total> [crypts] [deaths] [unsolved] [mimic] [prince] [bat]", "Works out the score for numbers you type (no run needed): the letter and each part.", false, ctx -> {
                    List<Integer> n = new ArrayList<>();
                    for (String a : ctx.args) n.add(toIntOrNull(a));
                    boolean missing = n.size() < 3;
                    if (!missing) for (int j = 0; j < 3; j++) if (n.get(j) == null) missing = true;
                    if (missing) ctx.reply.accept("Say at least: scorecalc <completed tiles 0-36> <secrets found> <secrets total>.");
                    else {
                        ScoreInputs inp = new ScoreInputs(n.get(0), n.get(1), n.get(2), orZero(n, 3), orZero(n, 6) != 0, orZero(n, 7) != 0, orZero(n, 8) != 0,
                                orZero(n, 4), orZero(n, 5), 0.0);
                        PackSet cur = Packs.current;
                        ScoreModel model = cur != null && cur.runstate != null ? cur.runstate.score : null;
                        ScoreBreakdown b = ScoreFormula.evaluate(model, inp);
                        ctx.reply.accept(b.total + " (" + Grades.letter(b.total) + ") using "
                                + (model != null && model.roomsTable.length >= 2 ? "the pack's fit" : "the published formula (no run-state pack loaded: run /ads dev packs first)")
                                + ": rooms " + f1(b.rooms) + ", secrets " + f1(b.secrets) + ", crypts " + f1(b.crypts) + ", bonus " + f1(b.bonus) + ", puzzles "
                                + f1(b.puzzles) + ", deaths " + f1(b.deaths) + ".");
                    }
                }),
                new AdsCommand("scoreend", "/ads dev scoreend", "Prints the end screen for the run as it stands.", true, null, ctx -> {
                    Run run = ctx.run();
                    if (run == null) ctx.reply("No run is on."); else showEndScreen(run);
                })
        );
    }

    private static int orZero(List<Integer> n, int i) {
        Integer v = i >= 0 && i < n.size() ? n.get(i) : null;
        return v != null ? v : 0;
    }
}
