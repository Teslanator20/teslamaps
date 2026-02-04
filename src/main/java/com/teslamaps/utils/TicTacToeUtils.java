package com.teslamaps.utils;

/**
 * TicTacToe AI solver using minimax algorithm.
 * Adapted from Skyblocker's TicTacToeUtils.
 */
public class TicTacToeUtils {

    /**
     * Represents a position on the board.
     */
    public record BoardIndex(int row, int column) {}

    /**
     * Get the best move for the current board state.
     * Returns the optimal move for 'O' (player).
     */
    public static BoardIndex getBestMove(char[][] board) {
        int bestScore = Integer.MIN_VALUE;
        BoardIndex bestMove = new BoardIndex(0, 0);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (board[row][col] == '\0') {
                    board[row][col] = 'O';
                    int score = minimax(board, 0, false);
                    board[row][col] = '\0';

                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new BoardIndex(row, col);
                    }
                }
            }
        }

        return bestMove;
    }

    /**
     * Minimax algorithm to find optimal move.
     * @param board Current board state
     * @param depth Current depth in game tree
     * @param isMaximizing True if maximizing player (O), false if minimizing (X)
     * @return Score of the board state
     */
    private static int minimax(char[][] board, int depth, boolean isMaximizing) {
        char winner = checkWinner(board);

        if (winner == 'O') return 10 - depth;
        if (winner == 'X') return depth - 10;
        if (isBoardFull(board)) return 0;

        if (isMaximizing) {
            int bestScore = Integer.MIN_VALUE;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    if (board[row][col] == '\0') {
                        board[row][col] = 'O';
                        int score = minimax(board, depth + 1, false);
                        board[row][col] = '\0';
                        bestScore = Math.max(score, bestScore);
                    }
                }
            }
            return bestScore;
        } else {
            int bestScore = Integer.MAX_VALUE;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    if (board[row][col] == '\0') {
                        board[row][col] = 'X';
                        int score = minimax(board, depth + 1, true);
                        board[row][col] = '\0';
                        bestScore = Math.min(score, bestScore);
                    }
                }
            }
            return bestScore;
        }
    }

    /**
     * Check if there's a winner on the board.
     * @return 'X', 'O', or '\0' if no winner
     */
    private static char checkWinner(char[][] board) {
        // Check rows
        for (int row = 0; row < 3; row++) {
            if (board[row][0] != '\0' &&
                board[row][0] == board[row][1] &&
                board[row][1] == board[row][2]) {
                return board[row][0];
            }
        }

        // Check columns
        for (int col = 0; col < 3; col++) {
            if (board[0][col] != '\0' &&
                board[0][col] == board[1][col] &&
                board[1][col] == board[2][col]) {
                return board[0][col];
            }
        }

        // Check diagonals
        if (board[0][0] != '\0' &&
            board[0][0] == board[1][1] &&
            board[1][1] == board[2][2]) {
            return board[0][0];
        }

        if (board[0][2] != '\0' &&
            board[0][2] == board[1][1] &&
            board[1][1] == board[2][0]) {
            return board[0][2];
        }

        return '\0';
    }

    /**
     * Check if the board is completely filled.
     */
    private static boolean isBoardFull(char[][] board) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (board[row][col] == '\0') {
                    return false;
                }
            }
        }
        return true;
    }
}
