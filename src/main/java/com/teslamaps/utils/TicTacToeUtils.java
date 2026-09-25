package com.teslamaps.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class TicTacToeUtils {

    public record BoardIndex(int row, int column) {}

    public static BoardIndex getBestMove(char[][] board) {
        Map<BoardIndex, Integer> moves = new HashMap<>();

        for (int row = 0; row < board.length; row++) {
            for (int column = 0; column < board[row].length; column++) {
                if (board[row][column] != '\0') continue;
                board[row][column] = 'O';
                int score = alphabeta(board, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, false);
                board[row][column] = '\0';

                moves.put(new BoardIndex(row, column), score);
            }
        }

        if (moves.isEmpty()) {
            return new BoardIndex(0, 0);
        }

        return Collections.max(moves.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey();
    }

    /** Minimax value for O (the player), X = AI. Win 10-depth, loss depth-10, tie 0. */
    public static int value(char[][] board, boolean oTurn, int depth) {
        int s = getScore(board);
        if (s == 10) return 10 - depth;
        if (s == -10) return depth - 10;
        if (!hasMovesAvailable(board)) return 0;
        int best = oTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (board[r][c] != '\0') continue;
                board[r][c] = oTurn ? 'O' : 'X';
                int v = value(board, !oTurn, depth + 1);
                board[r][c] = '\0';
                best = oTurn ? Math.max(best, v) : Math.min(best, v);
            }
        }
        return best;
    }

    /** Every move with the best value for the side to move; out[0] receives that value. */
    public static java.util.List<BoardIndex> bestMoves(char[][] board, boolean oTurn, int[] out) {
        java.util.List<BoardIndex> moves = new java.util.ArrayList<>();
        int best = oTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (board[r][c] != '\0') continue;
                board[r][c] = oTurn ? 'O' : 'X';
                int v = value(board, !oTurn, 1);
                board[r][c] = '\0';
                if (oTurn ? v > best : v < best) {
                    best = v;
                    moves.clear();
                }
                if (v == best) moves.add(new BoardIndex(r, c));
            }
        }
        if (out != null && out.length > 0) out[0] = best;
        return moves;
    }

    private static boolean hasMovesAvailable(char[][] board) {
        for (char[] row : board) {
            for (char c : row) {
                if (c == '\0') return true;
            }
        }
        return false;
    }

    private static int getScore(char[][] board) {
        for (int row = 0; row < 3; row++) {
            if (board[row][0] == board[row][1] && board[row][0] == board[row][2]) {
                switch (board[row][0]) {
                    case 'X': return -10;
                    case 'O': return 10;
                }
            }
        }

        for (int column = 0; column < 3; column++) {
            if (board[0][column] == board[1][column] && board[0][column] == board[2][column]) {
                switch (board[0][column]) {
                    case 'X': return -10;
                    case 'O': return 10;
                }
            }
        }

        if (board[0][0] == board[1][1] && board[0][0] == board[2][2]) {
            switch (board[0][0]) {
                case 'X': return -10;
                case 'O': return 10;
            }
        }

        if (board[0][2] == board[1][1] && board[0][2] == board[2][0]) {
            switch (board[0][2]) {
                case 'X': return -10;
                case 'O': return 10;
            }
        }

        return 0;
    }

    private static int alphabeta(char[][] board, int alpha, int beta, int depth, boolean maximizePlayer) {
        int score = getScore(board);

        if (score == 10 || score == -10) return score;
        if (!hasMovesAvailable(board)) return 0;

        if (maximizePlayer) {
            int bestScore = Integer.MIN_VALUE;

            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    if (board[row][column] == '\0') {
                        board[row][column] = 'O';
                        bestScore = Math.max(bestScore, alphabeta(board, alpha, beta, depth + 1, false));
                        board[row][column] = '\0';
                        alpha = Math.max(alpha, bestScore);

                        if (beta <= alpha) break; // Pruning
                    }
                }
            }

            return bestScore - depth;
        } else {
            int bestScore = Integer.MAX_VALUE;

            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    if (board[row][column] == '\0') {
                        board[row][column] = 'X';
                        bestScore = Math.min(bestScore, alphabeta(board, alpha, beta, depth + 1, true));
                        board[row][column] = '\0';
                        beta = Math.min(beta, bestScore);

                        if (beta <= alpha) break; // Pruning
                    }
                }
            }

            return bestScore + depth;
        }
    }
}
