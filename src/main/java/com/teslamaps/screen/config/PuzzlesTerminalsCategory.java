package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.PuzzleDraw;
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
        String[] progressFormats = {"25%", "1/4"};
        b.dropdown("  Progress Format", progressFormats,
                () -> progressFormats[config.melodyProgressFormat == 1 ? 1 : 0],
                v -> config.melodyProgressFormat = v.equals(progressFormats[1]) ? 1 : 0);
        b.button("  Edit Melody Messages",
                () -> Minecraft.getInstance().setScreen(new MelodyMessageScreen(Minecraft.getInstance().screen)));
        b.toggle("Melody Display (party progress)", () -> config.melodyDisplay, v -> config.melodyDisplay = v);
        b.info("Shows \"<name> has 2/4 melody\" from party chat in P3 (/tmap gui)");
        b.slider("  Display Duration (s)", 0.5f, 5f,
                () -> config.melodyDisplayDuration, v -> config.melodyDisplayDuration = v, 1);
        b.toggle("  Display Sound", () -> config.melodyDisplaySound, v -> config.melodyDisplaySound = v);
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
        String[] slotStyles = {"Rounded", "Rect", "Bordered Rect", "Button"};
        b.dropdown("Custom GUI Slot Style", slotStyles,
                () -> slotStyles[Math.max(0, Math.min(3, config.terminalSlotStyle))],
                v -> config.terminalSlotStyle = Math.max(0, java.util.Arrays.asList(slotStyles).indexOf(v)));
        b.keybind("  Terminal Click Key", () -> config.terminalDropKey, v -> config.terminalDropKey = v)
                .desc("Clicks the hovered terminal slot, any render type. Mouse buttons work too.");
        b.toggle("Stop Melody Solver", () -> config.terminalCancelMelodySolver, v -> config.terminalCancelMelodySolver = v);
        b.toggle("  Stop Panes Solver", () -> config.terminalStopPanes, v -> config.terminalStopPanes = v);
        b.toggle("  Stop Rubix Solver", () -> config.terminalStopRubix, v -> config.terminalStopRubix = v);
        b.toggle("  Stop Click in Order Solver", () -> config.terminalStopNumbers, v -> config.terminalStopNumbers = v);
        b.toggle("  Stop Starts With Solver", () -> config.terminalStopStartsWith, v -> config.terminalStopStartsWith = v);
        b.toggle("  Stop Select All Solver", () -> config.terminalStopSelect, v -> config.terminalStopSelect = v);
        b.toggle("  Melody Keys (1-4)", () -> config.terminalMelodyKeys, v -> config.terminalMelodyKeys = v)
                .desc("Keys 1-4 click the button of that melody row.");
        b.toggle("  Hide Chat & Boss Bar in Terminals", () -> config.terminalHideClutter, v -> config.terminalHideClutter = v)
                .desc("Also hides the container background, titles, carried item and slot highlights.");
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
        b.slider("  Lag Protection Ticks", 7f, 16f,
                () -> (float) config.terminalLagProtectionTicks, v -> config.terminalLagProtectionTicks = Math.round(v), 0);
        b.toggle("Announce Solve Times", () -> config.terminalTimes, v -> config.terminalTimes = v);
        b.button("  Reset Terminal PBs", com.teslamaps.features.TerminalSolver::resetPbs);
        b.toggle("Terminal Sounds", () -> config.terminalSounds, v -> config.terminalSounds = v);
        b.info("Replaces Hypixel's terminal pling with your own click sound");
        b.soundDropdown("  Click Sound", com.teslamaps.utils.SoundOptions.keys(),
                () -> config.terminalClickSound, v -> config.terminalClickSound = v);
        b.slider("  Click Volume", 0f, 4f, () -> config.terminalClickVolume, v -> config.terminalClickVolume = v, 2);
        b.slider("  Click Pitch", 0.5f, 2f, () -> config.terminalClickPitch, v -> config.terminalClickPitch = v, 2);
        b.toggle("  Completion Sound", () -> config.terminalCompleteSound, v -> config.terminalCompleteSound = v);
        b.soundDropdown("    Completion Sound Type", com.teslamaps.utils.SoundOptions.keys(),
                () -> config.terminalCompleteSoundId, v -> config.terminalCompleteSoundId = v);
        b.slider("    Completion Volume", 0f, 4f, () -> config.terminalCompleteVolume, v -> config.terminalCompleteVolume = v, 2);
        b.slider("    Completion Pitch", 0.5f, 2f, () -> config.terminalCompletePitch, v -> config.terminalCompletePitch = v, 2);
        b.toggle("    Skip Click Sound On Last Click", () -> config.terminalCancelLastClick, v -> config.terminalCancelLastClick = v);
        b.toggle("  Pling On Gate / Core Open", () -> config.terminalGateSound, v -> config.terminalGateSound = v);
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
        b.toggle("  Modern Blaze Solver", () -> config.modernBlazeSolver, v -> config.modernBlazeSolver = v);
        b.toggle("  Blaze Done Message", () -> config.blazeDoneMessage, v -> config.blazeDoneMessage = v);
        b.toggle("  Blaze Tracer", () -> config.blazeTracer, v -> config.blazeTracer = v);
        b.toggle("  Blaze Same HP Warning", () -> config.blazeSameHpWarning, v -> config.blazeSameHpWarning = v);
        b.slider("  Blaze Lines", 0f, 10f,
                () -> (float) config.blazeLineAmount, v -> config.blazeLineAmount = Math.round(v), 0);
        b.slider("  Blaze Line Width", 0.5f, 5f,
                () -> config.blazeLineWidth, v -> config.blazeLineWidth = v, 1);
        b.toggle("  Blaze Standing Spot + Etherwarp", () -> config.blazeSpots, v -> config.blazeSpots = v);
        b.color("    Spot Outline", () -> config.blazeSpotOutline, v -> config.blazeSpotOutline = v);
        b.color("    Spot Fill", () -> config.blazeSpotFill, v -> config.blazeSpotFill = v);
        b.color("    Etherwarp Outline", () -> config.blazeEtherOutline, v -> config.blazeEtherOutline = v);
        b.color("    Etherwarp Fill", () -> config.blazeEtherFill, v -> config.blazeEtherFill = v);
        b.toggle("Three Weirdos Solver", () -> config.solveThreeWeirdos, v -> config.solveThreeWeirdos = v);
        b.dropdown("  Weirdos Style", PuzzleDraw.STYLES, () -> config.weirdosStyle, v -> config.weirdosStyle = v);
        b.color("  Weirdos: Correct", () -> config.colorWeirdosCorrect, v -> config.colorWeirdosCorrect = v);
        b.color("  Weirdos: Wrong", () -> config.colorWeirdosWrong, v -> config.colorWeirdosWrong = v);
        b.toggle("  Weirdos Solved Sound", () -> config.weirdosSolvedSound, v -> config.weirdosSolvedSound = v);
        b.soundDropdown("  Weirdos Sound", com.teslamaps.utils.SoundOptions.keys(),
                () -> config.weirdosSound, v -> config.weirdosSound = v);
        b.toggle("  Weirdos: Block Wrong Chests", () -> config.weirdosBlockWrongClicks, v -> config.weirdosBlockWrongClicks = v)
                .legit().desc("Right-clicks on a lying weirdo's chest do nothing once the answer is known. Sneak to click anyway. Nothing is sent to the server.");
        b.toggle("Tic Tac Toe Solver", () -> config.solveTicTacToe, v -> config.solveTicTacToe = v);
        b.toggle("  TTT: Show All Best Moves", () -> config.tttShowAllBest, v -> config.tttShowAllBest = v)
                .desc("Marks every equally good move instead of one");
        b.toggle("  TTT: Gamble Color", () -> config.tttGamble, v -> config.tttGamble = v)
                .desc("Uses the gamble color when no move is safe any more");
        b.toggle("  TTT: Prediction", () -> config.tttPrediction, v -> config.tttPrediction = v)
                .desc("Marks your likely next move after the AI answers (may be off)");
        b.toggle("  TTT: Done Message (/pc)", () -> config.tttDoneMessage, v -> config.tttDoneMessage = v)
                .cheat().desc("Sends \"Tic Tac Toe done\" to party chat when you tie it");
        b.toggle("  TTT: Block Wrong Buttons", () -> config.tttBlockWrongClicks, v -> config.tttBlockWrongClicks = v)
                .legit().desc("Right-clicks on board buttons that are not a best move do nothing. Sneak to click anyway. Nothing is sent to the server.");
        b.dropdown("  TTT Style", PuzzleDraw.STYLES, () -> config.tttStyle, v -> config.tttStyle = v);
        b.color("  TTT: Best Move", () -> config.colorTttBest, v -> config.colorTttBest = v);
        b.color("  TTT: Gamble", () -> config.colorTttGamble, v -> config.colorTttGamble = v);
        b.color("  TTT: Prediction Color", () -> config.colorTttPrediction, v -> config.colorTttPrediction = v);
        b.toggle("Creeper Beams Solver", () -> config.solveCreeperBeams, v -> config.solveCreeperBeams = v);
        b.toggle("  Creeper Beams Tracers", () -> config.creeperBeamsTracers, v -> config.creeperBeamsTracers = v);
        b.dropdown("  Creeper Beams Style", PuzzleDraw.STYLES_WITH_DEFAULT, () -> config.creeperBeamsStyle, v -> config.creeperBeamsStyle = v);
        b.slider("  Creeper Beams Alpha", 0.05f, 1f,
                () -> config.creeperBeamsAlpha, v -> config.creeperBeamsAlpha = v, 2);
        b.toggle("Creeper Beams Ding", () -> config.creeperBeamsDing, v -> config.creeperBeamsDing = v);
        b.soundDropdown("  Ding Sound", com.teslamaps.utils.SoundOptions.keys(),
                () -> config.creeperBeamsSound, v -> config.creeperBeamsSound = v);
        b.toggle("  Mute Creeper Hurt (room only)", () -> config.creeperBeamsMuteHurt, v -> config.creeperBeamsMuteHurt = v)
                .desc("Creeper hurt and primed sounds, only inside Creeper Beams");
        b.toggle("  Mute Explosion (room only)", () -> config.creeperBeamsMuteExplosion, v -> config.creeperBeamsMuteExplosion = v)
                .desc("The explosion when the puzzle is done, only inside Creeper Beams");
        b.toggle("Boulder Solver", () -> config.solveBoulder, v -> config.solveBoulder = v);
        b.toggle("  Show All Boulder Clicks", () -> config.showAllBoulderClicks, v -> config.showAllBoulderClicks = v);
        b.toggle("  Boulder: Highlight Button", () -> config.boulderClickBlock, v -> config.boulderClickBlock = v);
        b.toggle("Quiz (Trivia) Solver", () -> config.solveQuiz, v -> config.solveQuiz = v);
        b.toggle("  Quiz Beacon Beam", () -> config.quizBeacon, v -> config.quizBeacon = v);
        b.toggle("  Quiz Chat Highlight", () -> config.quizChatHighlight, v -> config.quizChatHighlight = v);
        b.toggle("  Quiz Hide Wrong Answers", () -> config.quizHideWrongAnswers, v -> config.quizHideWrongAnswers = v);
        b.toggle("  Quiz Question Timer", () -> config.quizTimer, v -> config.quizTimer = v);
        b.toggle("  Quiz Question Splits", () -> config.quizSplits, v -> config.quizSplits = v);
        b.dropdown("    Splits Time Format", com.teslamaps.dungeon.puzzle.QuizExtras.FORMATS,
                () -> config.quizSplitsFormat, v -> config.quizSplitsFormat = v);
        b.color("  Quiz Chat Color", () -> config.colorQuizChat, v -> config.colorQuizChat = v);
        b.dropdown("  Quiz Style", PuzzleDraw.STYLES_WITH_DEFAULT, () -> config.quizStyle, v -> config.quizStyle = v);
        b.toggle("  Quiz Depth Check", () -> config.quizDepthCheck, v -> config.quizDepthCheck = v)
                .desc("Hide the answer box behind walls");
        b.toggle("  Quiz: Block Wrong Answers", () -> config.quizBlockWrongClicks, v -> config.quizBlockWrongClicks = v)
                .legit().desc("Right-clicks next to a wrong answer do nothing once the right one is known. Sneak to click anyway. Nothing is sent to the server.");
        b.toggle("Teleport Maze Solver", () -> config.solveTPMaze, v -> config.solveTPMaze = v);
        b.toggle("  TP Maze Next-Portal Tracer", () -> config.tpMazeTracer, v -> config.tpMazeTracer = v);
        b.toggle("  TP Maze Prioritize Diagonal", () -> config.tpMazePrioritizeDiagonal, v -> config.tpMazePrioritizeDiagonal = v);
        b.toggle("Water Board Solver", () -> config.solveWaterBoard, v -> config.solveWaterBoard = v);
        b.toggle("  Water Board Tracers", () -> config.waterBoardTracers, v -> config.waterBoardTracers = v);
        b.toggle("  Water Board Colored Countdown", () -> config.waterBoardColorStages, v -> config.waterBoardColorStages = v);
        String[] waterSets = {"Standard", "Optimized (Odin)", "Efficient (Devonian)"};
        b.dropdown("  Water Board Solutions", waterSets,
                () -> waterSets[Math.max(0, Math.min(2, config.waterBoardSolutionSet))],
                v -> config.waterBoardSolutionSet = Math.max(0, java.util.Arrays.asList(waterSets).indexOf(v)));
        b.toggle("  Water Flow Preview", () -> config.waterBoardPreview, v -> config.waterBoardPreview = v);
        b.toggle("    Preview Lever You Look At", () -> config.waterBoardPreviewLevers, v -> config.waterBoardPreviewLevers = v);
        b.toggle("Ice Fill Solver", () -> config.solveIceFill, v -> config.solveIceFill = v);
        b.toggle("  Ice Fill Optimized", () -> config.iceFillOptimized, v -> config.iceFillOptimized = v);
        b.toggle("  Ice Fill: Solve From Blocks", () -> config.iceFillDynamic, v -> config.iceFillDynamic = v);
        b.toggle("  Ice Fill Color Gradient", () -> config.iceFillGradient, v -> config.iceFillGradient = v);
        b.slider("  Ice Fill Line Width", 1f, 8f,
                () -> config.iceFillLineWidth, v -> config.iceFillLineWidth = v, 1);
        b.toggle("Ice Path Solver", () -> config.icePathSolver, v -> config.icePathSolver = v);
        b.toggle("  Ice Path: Follow Silverfish", () -> config.icePathDynamic, v -> config.icePathDynamic = v);
        b.toggle("Arrow Align Solver", () -> config.solveArrowAlign, v -> config.solveArrowAlign = v);
        b.toggle("Puzzle Timers", () -> config.puzzleTimers, v -> config.puzzleTimers = v);
        b.toggle("Puzzle HUD", () -> config.puzzleHud, v -> config.puzzleHud = v);
        b.toggle("  Show Player Names", () -> config.puzzleHudPlayers, v -> config.puzzleHudPlayers = v);
        b.toggle("  Only Before Run Start", () -> config.puzzleHudPreRunOnly, v -> config.puzzleHudPreRunOnly = v);
        b.toggle("  Failed / Missing Count", () -> config.puzzleHudCounts, v -> config.puzzleHudCounts = v);
        b.toggle("  Only Failed / Missing", () -> config.puzzleHudCountsOnly, v -> config.puzzleHudCountsOnly = v);
        b.button("Reset Puzzle Solvers", () -> com.teslamaps.dungeon.puzzle.PuzzleReset.reset("all"))
                .desc("Restarts every solver, like /tmap puzzle reset. Water Board: flip the levers back first.");
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
