// Ported from ADM's p3sim/clear/puzzles/QuizPuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Quiz in the world: the statue speaks when a player walks in, three questions follow, the party answers by pressing a stone button
 * beside one of three pillar anchors. A wrong answer: "Yikes", the tab shows Failed a little later, and the whole quiz starts over.
 * Buttons pressed while the statue is busy, or after the quiz is over, get a random rebuke.
 */
public class QuizPuzzle extends WorldPuzzle {
    private QuizLogic logic = null;
    private List<WorldPos> pillars = List.of();
    private final List<Pair<Integer, String>> queue = new ArrayList<>();
    private int starts = 0;
    private int restartAt = -1;
    private int failAt = -1;
    private UUID failBy = null;
    private final KRandom rebukes = KRandom.ofInt(0x0A11);

    public QuizPuzzle(PuzzleContext context) { super(PuzzleKinds.QUIZ, context); }

    @Override public boolean printsFailLine() { return false; }

    @Override protected void build() {
        List<WorldPos> ps = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            List<WorldPos> a = context().anchor("pillar" + i);
            if (!a.isEmpty()) ps.add(a.get(0));
        }
        pillars = ps;
        fresh();
    }

    private void fresh() {
        logic = QuizLogic.draw(run().subRandom("quiz:" + context().room.index + ":" + (starts++)));
        queue.clear();
        restartAt = -1;
        failAt = -1;
    }

    private void enqueue(List<QuizLogic.Line> lines, int from) {
        int t = from;
        for (QuizLogic.Line l : lines) { t += l.delay; queue.add(Pair.of(t, l.text)); }
    }

    @Override public void onTick() {
        QuizLogic q = logic;
        if (q == null) return;
        if (pillars.size() < 3) return;
        if (status() == PuzzleStatus.UNSOLVED && q.phase == QuizLogic.Phase.IDLE && restartAt < 0 && queue.isEmpty()) enqueue(q.start(), now());
        while (!queue.isEmpty() && queue.get(0).first <= now()) say(queue.remove(0).second);
        if (failAt >= 0 && failAt <= now()) { failAt = -1; fail(failBy, "answered wrong"); }
        if (restartAt >= 0 && restartAt <= now()) { restartAt = -1; PuzzlesModule.resetPuzzle(this); }
    }

    @Override public boolean onInteract(UUID player, int x, int y, int z) {
        QuizLogic q = logic;
        if (q == null) return false;
        int option = -1;
        for (int i = 0; i < pillars.size(); i++) {
            WorldPos it = pillars.get(i);
            if (it.y == y && Math.abs(it.x - x) + Math.abs(it.z - z) == 1) { option = i; break; }
        }
        if (option < 0) return false;
        if (status() != PuzzleStatus.UNSOLVED || q.phase != QuizLogic.Phase.ASKING || !queue.isEmpty()) {
            say(q.rebuke(rebukes));
            return true;
        }
        QuizLogic.Outcome out = q.answer(option, nameOf(player));
        enqueue(out.lines, now());
        if (out.solved) solve(player);
        else if (!out.correct) {
            failAt = now() + QuizLogic.Pace.YIKES_TO_TAB_FAILED;
            failBy = player;
            restartAt = now() + QuizLogic.Pace.YIKES_TO_WRONG + QuizLogic.Pace.WRONG_TO_RESTART;
        }
        return true;
    }

    @Override public void onReset() {
        // fresh() is also what a new start needs: new questions, nothing queued.
        fresh();
    }
}
