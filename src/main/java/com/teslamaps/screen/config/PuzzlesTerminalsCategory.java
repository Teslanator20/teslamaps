package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class PuzzlesTerminalsCategory {

    private PuzzlesTerminalsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Terminals");
        b.toggle("Melody Message (/pc on open)", () -> config.melodyMessage, v -> config.melodyMessage = v);
        b.toggle("Melody Progress (/pc 25/50/75%)", () -> config.melodyProgress, v -> config.melodyProgress = v);
        b.toggle("Terminal Solver", () -> config.terminalSolver, v -> config.terminalSolver = v);
        String[] renderTypes = {"Odin", "Normal", "Custom GUI"};
        b.dropdown("Render Type", renderTypes,
                () -> renderTypes[Math.max(0, Math.min(2, config.terminalRenderType))],
                v -> config.terminalRenderType = switch (v) {
                    case "Normal" -> 1;
                    case "Custom GUI" -> 2;
                    default -> 0;
                });
        b.slider("Normal Term Size", 1f, 6f,
                () -> (float) config.terminalNormalSize, v -> config.terminalNormalSize = Math.round(v), 0);
        b.slider("Custom GUI Size", 1f, 3f,
                () -> config.terminalCustomSize, v -> config.terminalCustomSize = v, 1);
        b.slider("Custom GUI Roundness", 0f, 15f,
                () -> config.terminalRoundness, v -> config.terminalRoundness = v, 0);
        b.slider("Custom GUI Slot Gap", 0f, 8f,
                () -> (float) config.terminalSlotGap, v -> config.terminalSlotGap = Math.round(v), 0);
        b.toggle("Stop Melody Solver", () -> config.terminalCancelMelodySolver, v -> config.terminalCancelMelodySolver = v);
        b.slider("Melody GUI Size", 1f, 3f,
                () -> config.terminalMelodySize, v -> config.terminalMelodySize = v, 1);
        b.toggle("Show Numbers (Click in Order)", () -> config.terminalShowNumbers, v -> config.terminalShowNumbers = v);
        String[] rubixModes = {"Fewest Clicks", "Left Clicks Only"};
        b.dropdown("Rubix Mode", rubixModes,
                () -> config.terminalRubixMode == 0 ? rubixModes[0] : rubixModes[1],
                v -> config.terminalRubixMode = v.equals(rubixModes[0]) ? 0 : 1);
        b.toggle("Client Prediction", () -> config.terminalHideClicked, v -> config.terminalHideClicked = v);
        b.slider("Resolve Timeout (ms)", 300f, 1200f,
                () -> (float) config.terminalReloadThreshold, v -> config.terminalReloadThreshold = Math.round(v), 0);
        b.slider("First Click Prot (ms)", 350f, 800f,
                () -> (float) config.terminalFirstClickProt, v -> config.terminalFirstClickProt = Math.round(v), 0);
        b.toggle("Account For Server Lag", () -> config.terminalLagProtection, v -> config.terminalLagProtection = v);
        b.slider("Lag Protection Ticks", 7f, 16f,
                () -> (float) config.terminalLagProtectionTicks, v -> config.terminalLagProtectionTicks = Math.round(v), 0);
        b.toggle("Announce Solve Times", () -> config.terminalTimes, v -> config.terminalTimes = v);
        b.toggle("Solver Debug Overlay", () -> config.terminalSolverDebug, v -> config.terminalSolverDebug = v);
        b.label("Terminal Simulator");
        b.slider("Simulated Ping (ms)", 0f, 500f,
                () -> (float) config.termSimPing, v -> config.termSimPing = Math.round(v), 0);
        b.toggle("Skip First Click Prot In Termsim", () -> config.termSimDisableFirstClickProt, v -> config.termSimDisableFirstClickProt = v);
        b.button("Open Terminal Simulator",
                () -> com.teslamaps.features.TerminalSimulator.open());
        b.label("Dungeon Puzzles");
        b.toggle("Toggle All Puzzles", () -> config.solveBlaze && config.blazeDoneMessage && config.solveThreeWeirdos && config.solveTicTacToe && config.solveCreeperBeams && config.creeperBeamsTracers && config.solveBoulder && config.showAllBoulderClicks && config.solveQuiz && config.quizBeacon && config.solveTPMaze && config.solveWaterBoard && config.waterBoardTracers, v -> {
            config.solveBlaze = v;
            config.blazeDoneMessage = v;
            config.solveThreeWeirdos = v;
            config.solveTicTacToe = v;
            config.solveCreeperBeams = v;
            config.creeperBeamsTracers = v;
            config.solveBoulder = v;
            config.showAllBoulderClicks = v;
            config.solveQuiz = v;
            config.quizBeacon = v;
            config.solveTPMaze = v;
            config.solveWaterBoard = v;
            config.waterBoardTracers = v;
        });
        b.toggle("Blaze Solver", () -> config.solveBlaze, v -> config.solveBlaze = v);
        b.toggle("Modern Blaze Solver", () -> config.modernBlazeSolver, v -> config.modernBlazeSolver = v);
        b.toggle("Blaze Done Message", () -> config.blazeDoneMessage, v -> config.blazeDoneMessage = v);
        b.toggle("Blaze Tracer", () -> config.blazeTracer, v -> config.blazeTracer = v);
        b.toggle("Blaze Same HP Warning", () -> config.blazeSameHpWarning, v -> config.blazeSameHpWarning = v);
        b.slider("Blaze Lines", 0f, 10f,
                () -> (float) config.blazeLineAmount, v -> config.blazeLineAmount = Math.round(v), 0);
        b.slider("Blaze Line Width", 0.5f, 5f,
                () -> config.blazeLineWidth, v -> config.blazeLineWidth = v, 1);
        b.toggle("Three Weirdos Solver", () -> config.solveThreeWeirdos, v -> config.solveThreeWeirdos = v);
        b.toggle("Tic Tac Toe Solver", () -> config.solveTicTacToe, v -> config.solveTicTacToe = v);
        b.toggle("Creeper Beams Solver", () -> config.solveCreeperBeams, v -> config.solveCreeperBeams = v);
        b.toggle("Creeper Beams Tracers", () -> config.creeperBeamsTracers, v -> config.creeperBeamsTracers = v);
        b.toggle("Creeper Beams Ding", () -> config.creeperBeamsDing, v -> config.creeperBeamsDing = v);
        b.toggle("Boulder Solver", () -> config.solveBoulder, v -> config.solveBoulder = v);
        b.toggle("Show All Boulder Clicks", () -> config.showAllBoulderClicks, v -> config.showAllBoulderClicks = v);
        b.toggle("Boulder: Highlight Button", () -> config.boulderClickBlock, v -> config.boulderClickBlock = v);
        b.toggle("Quiz (Trivia) Solver", () -> config.solveQuiz, v -> config.solveQuiz = v);
        b.toggle("Quiz Beacon Beam", () -> config.quizBeacon, v -> config.quizBeacon = v);
        b.toggle("Quiz Chat Highlight", () -> config.quizChatHighlight, v -> config.quizChatHighlight = v);
        b.toggle("Quiz Hide Wrong Answers", () -> config.quizHideWrongAnswers, v -> config.quizHideWrongAnswers = v);
        b.toggle("Teleport Maze Solver", () -> config.solveTPMaze, v -> config.solveTPMaze = v);
        b.toggle("TP Maze Next-Portal Tracer", () -> config.tpMazeTracer, v -> config.tpMazeTracer = v);
        b.toggle("TP Maze Prioritize Diagonal", () -> config.tpMazePrioritizeDiagonal, v -> config.tpMazePrioritizeDiagonal = v);
        b.toggle("Water Board Solver", () -> config.solveWaterBoard, v -> config.solveWaterBoard = v);
        b.toggle("Water Board Tracers", () -> config.waterBoardTracers, v -> config.waterBoardTracers = v);
        b.toggle("Water Board Colored Countdown", () -> config.waterBoardColorStages, v -> config.waterBoardColorStages = v);
        String[] waterSets = {"Standard", "Optimized (Odin)", "Efficient (Devonian)"};
        b.dropdown("Water Board Solutions", waterSets,
                () -> waterSets[Math.max(0, Math.min(2, config.waterBoardSolutionSet))],
                v -> config.waterBoardSolutionSet = Math.max(0, java.util.Arrays.asList(waterSets).indexOf(v)));
        b.toggle("Ice Fill Solver", () -> config.solveIceFill, v -> config.solveIceFill = v);
        b.toggle("Ice Fill Optimized", () -> config.iceFillOptimized, v -> config.iceFillOptimized = v);
        b.toggle("Ice Fill: Solve From Blocks", () -> config.iceFillDynamic, v -> config.iceFillDynamic = v);
        b.toggle("Ice Fill Color Gradient", () -> config.iceFillGradient, v -> config.iceFillGradient = v);
        b.slider("Ice Fill Line Width", 1f, 8f,
                () -> config.iceFillLineWidth, v -> config.iceFillLineWidth = v, 1);
        b.toggle("Ice Path Solver", () -> config.icePathSolver, v -> config.icePathSolver = v);
        b.toggle("Ice Path: Follow Silverfish", () -> config.icePathDynamic, v -> config.icePathDynamic = v);
        b.toggle("Arrow Align Solver", () -> config.solveArrowAlign, v -> config.solveArrowAlign = v);
        b.toggle("Puzzle Timers", () -> config.puzzleTimers, v -> config.puzzleTimers = v);
        b.toggle("Terracotta Spawn Timers (F6/M6)", () -> config.terracottaTimer, v -> config.terracottaTimer = v);
        b.label("Puzzle Colors");
        b.color("Boulder Click", () -> config.colorBoulder, v -> config.colorBoulder = v);
        b.color("Boulder Button", () -> config.colorBoulderClickBlock, v -> config.colorBoulderClickBlock = v);
        b.color("Blaze: First", () -> config.blazeColorFirst, v -> config.blazeColorFirst = v);
        b.color("Blaze: Second", () -> config.blazeColorSecond, v -> config.blazeColorSecond = v);
        b.color("Blaze: Third", () -> config.blazeColorThird, v -> config.blazeColorThird = v);
        b.color("Blaze: Wrong", () -> config.blazeColorRest, v -> config.blazeColorRest = v);
        b.color("Quiz Correct", () -> config.colorQuiz, v -> config.colorQuiz = v);
        b.color("Ice Fill Path", () -> config.colorIceFill, v -> config.colorIceFill = v);
        b.color("Ice Fill Path End", () -> config.colorIceFillEnd, v -> config.colorIceFillEnd = v);
        b.color("Ice Path: Next", () -> config.colorIcePathNext, v -> config.colorIcePathNext = v);
        b.color("Ice Path: Rest", () -> config.colorIcePathRest, v -> config.colorIcePathRest = v);
        b.color("TP Maze: Single", () -> config.colorTPMazeOne, v -> config.colorTPMazeOne = v);
        b.color("TP Maze: Multiple", () -> config.colorTPMazeMultiple, v -> config.colorTPMazeMultiple = v);
        b.color("TP Maze: Visited", () -> config.colorTPMazeVisited, v -> config.colorTPMazeVisited = v);
        b.color("TP Maze: Tracer", () -> config.colorTPMazeTracer, v -> config.colorTPMazeTracer = v);
        b.color("Puzzle Unexplored", () -> config.colorPuzzleUnexplored, v -> config.colorPuzzleUnexplored = v);
        b.color("Simon Says: First", () -> config.colorSimonSaysFirst, v -> config.colorSimonSaysFirst = v);
        b.color("Simon Says: Second", () -> config.colorSimonSaysSecond, v -> config.colorSimonSaysSecond = v);
        b.color("Simon Says: Third+", () -> config.colorSimonSaysThird, v -> config.colorSimonSaysThird = v);
        b.color("Water Board: First", () -> config.colorWaterFirst, v -> config.colorWaterFirst = v);
        b.color("Water Board: Second", () -> config.colorWaterSecond, v -> config.colorWaterSecond = v);
        b.label("Terminal Colors");
        b.color("Background", () -> config.terminalBackgroundColor, v -> config.terminalBackgroundColor = v);
        b.color("Panes", () -> config.terminalPanesColor, v -> config.terminalPanesColor = v);
        b.color("Rubix 1", () -> config.terminalRubixColor1, v -> config.terminalRubixColor1 = v);
        b.color("Rubix 2", () -> config.terminalRubixColor2, v -> config.terminalRubixColor2 = v);
        b.color("Rubix -1", () -> config.terminalRubixColorNeg1, v -> config.terminalRubixColorNeg1 = v);
        b.color("Rubix -2", () -> config.terminalRubixColorNeg2, v -> config.terminalRubixColorNeg2 = v);
        b.color("Order 1", () -> config.terminalOrderColor1, v -> config.terminalOrderColor1 = v);
        b.color("Order 2", () -> config.terminalOrderColor2, v -> config.terminalOrderColor2 = v);
        b.color("Order 3", () -> config.terminalOrderColor3, v -> config.terminalOrderColor3 = v);
        b.color("Starts With", () -> config.terminalStartsWithColor, v -> config.terminalStartsWithColor = v);
        b.color("Select", () -> config.terminalSelectColor, v -> config.terminalSelectColor = v);
        b.color("Melody Column", () -> config.terminalMelodyColumnColor, v -> config.terminalMelodyColumnColor = v);
        b.color("Melody Pointer", () -> config.terminalMelodyPointerColor, v -> config.terminalMelodyPointerColor = v);
        b.color("Melody Background", () -> config.terminalMelodyBackgroundColor, v -> config.terminalMelodyBackgroundColor = v);
    }
}
