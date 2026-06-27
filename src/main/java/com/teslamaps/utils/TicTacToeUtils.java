/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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
