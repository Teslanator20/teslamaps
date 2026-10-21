// Ported from ADM's p3sim/clear/puzzles/TttLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tic Tac Toe against the room: the ROOM plays X and moves first, the party plays O and can only tie (solve) or lose (fail). The room's
 * first mark follows the measured odds; later moves are minimax-best, a random one of the equally good. Cells are 0..8, row by row from
 * the top left. Pure: no Minecraft.
 */
public class TttLogic {
    /** X is the room's mark, O the party's. */
    public enum Mark { EMPTY, X, O }

    /** From the party's side: LOST when the room has a line, TIE when the board is full, WON when the party has one. */
    public enum Result { PLAYING, TIE, LOST, WON }

    private final KRandom random;
    private final Mark[] board = new Mark[9];

    public Result result = Result.PLAYING;

    /** True while the room is to move (it moves first, and after each accepted press of the party). */
    public boolean roomToMove = true;

    public TttLogic(KRandom random) {
        this.random = random;
        Arrays.fill(board, Mark.EMPTY);
    }

    public TttLogic() { this(KRandom.DEFAULT); }

    public Mark mark(int cell) { return board[cell]; }

    /** How many cells are marked. */
    public int marks() {
        int n = 0;
        for (Mark m : board) if (m != Mark.EMPTY) n++;
        return n;
    }

    /** The room places its first mark: on at when given (tests), else by the measured odds. Returns the cell, or -1. */
    public int roomOpens(int at) {
        if (result != Result.PLAYING || !roomToMove || marks() != 0) return -1;
        int cell = at >= 0 && at <= 8 ? at : pick(OPENING_ODDS);
        place(cell, Mark.X);
        return cell;
    }

    public int roomOpens() { return roomOpens(-1); }

    /** The party presses cell: accepted only on an empty cell while it is the party's move. */
    public boolean press(int cell) {
        if (result != Result.PLAYING || roomToMove || cell < 0 || cell > 8 || board[cell] != Mark.EMPTY) return false;
        place(cell, Mark.O);
        return true;
    }

    /** The room answers with a best move (a random one of the equally good). Returns the cell, or -1. */
    public int roomReplies() {
        if (result != Result.PLAYING || !roomToMove) return -1;
        List<Integer> best = bestMoves(board.clone());
        if (best.isEmpty()) return -1;
        int cell = best.get(random.nextInt(best.size()));
        place(cell, Mark.X);
        return cell;
    }

    private void place(int cell, Mark m) {
        board[cell] = m;
        roomToMove = m == Mark.O;
        if (wins(board, Mark.X)) result = Result.LOST;
        else if (wins(board, Mark.O)) result = Result.WON;
        else if (!hasEmpty(board)) result = Result.TIE;
        else result = Result.PLAYING;
    }

    private int pick(double[] odds) {
        double sum = 0;
        for (double o : odds) sum += o;
        double roll = random.nextDouble() * sum;
        for (int i = 0; i < odds.length; i++) { roll -= odds[i]; if (roll < 0) return i; }
        return odds.length - 1;
    }

    public void reset() { Arrays.fill(board, Mark.EMPTY); result = Result.PLAYING; roomToMove = true; }

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6},
    };

    /** The room's first cell, as weights over the nine cells (centre 39 %, corners 14.85 % each, edges 0.4 % each). */
    public static final double[] OPENING_ODDS = {0.1485, 0.004, 0.1485, 0.004, 0.39, 0.004, 0.1485, 0.004, 0.1485};

    /** The room waits this long after the party's press before it answers. */
    public static final int THINK_TICKS = 60;

    /** The room's first mark appears this long after a player steps into the room. */
    public static final int OPENING_TICKS = 24;

    public static boolean wins(Mark[] b, Mark m) {
        for (int[] l : LINES) {
            boolean all = true;
            for (int i : l) if (b[i] != m) { all = false; break; }
            if (all) return true;
        }
        return false;
    }

    private static boolean hasEmpty(Mark[] b) {
        for (Mark m : b) if (m == Mark.EMPTY) return true;
        return false;
    }

    private static int played(Mark[] b) {
        int n = 0;
        for (Mark m : b) if (m != Mark.EMPTY) n++;
        return n;
    }

    /** Every cell the room (X) may play that keeps the best result for it, scanning 0..8. */
    public static List<Integer> bestMoves(Mark[] b) {
        int best = -100;
        List<Integer> cells = new ArrayList<>();
        int played = played(b);
        for (int i = 0; i <= 8; i++) if (b[i] == Mark.EMPTY) {
            b[i] = Mark.X;
            int s = score(b, true, played + 1);
            b[i] = Mark.EMPTY;
            if (s > best) { best = s; cells.clear(); }
            if (s == best) cells.add(i);
        }
        return cells;
    }

    /** Every cell the party (O) may play that keeps the best result for it. Same scoring as bestMoves. */
    public static List<Integer> bestPartyMoves(Mark[] b) {
        int best = 100;
        List<Integer> cells = new ArrayList<>();
        int played = played(b);
        for (int i = 0; i <= 8; i++) if (b[i] == Mark.EMPTY) {
            b[i] = Mark.O;
            int s = score(b, false, played + 1);
            b[i] = Mark.EMPTY;
            if (s < best) { best = s; cells.clear(); }
            if (s == best) cells.add(i);
        }
        return cells;
    }

    private static int score(Mark[] b, boolean oToMove, int played) {
        if (wins(b, Mark.X)) return 10 - played;
        if (wins(b, Mark.O)) return played - 10;
        if (!hasEmpty(b)) return 0;
        Mark m = oToMove ? Mark.O : Mark.X;
        int best = oToMove ? 100 : -100;
        for (int i = 0; i <= 8; i++) if (b[i] == Mark.EMPTY) {
            b[i] = m;
            int s = score(b, !oToMove, played + 1);
            b[i] = Mark.EMPTY;
            best = oToMove ? Math.min(best, s) : Math.max(best, s);
        }
        return best;
    }
}
