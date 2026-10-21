// Ported from ADM's p3sim/clear/puzzles/PuzzleKinds.kt
package com.teslamaps.adm.sim.clear.puzzles;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The ten puzzles of an F7 dungeon by the code the packs and Odin use, with the words each is called by. Pure data. */
public final class PuzzleKinds {
    private PuzzleKinds() {}

    public static final String BLAZE = "BLAZE";
    public static final String BOULDER = "BOULDER";
    public static final String BEAMS = "BEAMS";
    public static final String WATER_BOARD = "WATER_BOARD";
    public static final String TP_MAZE = "TP_MAZE";
    public static final String ICE_PATH = "ICE_PATH";
    public static final String WEIRDOS = "WEIRDOS";
    public static final String QUIZ = "QUIZ";
    public static final String TTT = "TTT";
    public static final String ICE_FILL = "ICE_FILL";

    /** The name on the tab's `Puzzles` list (Odin matches these exact words). */
    public static final Map<String, String> tabNames = new LinkedHashMap<>();

    /** The room pack's `puzzle.kind` word to the code. */
    private static final Map<String, String> byPackKind = new LinkedHashMap<>();

    static {
        tabNames.put(BLAZE, "Higher Or Lower");
        tabNames.put(BEAMS, "Creeper Beams");
        tabNames.put(WEIRDOS, "Three Weirdos");
        tabNames.put(TTT, "Tic Tac Toe");
        tabNames.put(WATER_BOARD, "Water Board");
        tabNames.put(TP_MAZE, "Teleport Maze");
        tabNames.put(BOULDER, "Boulder");
        tabNames.put(ICE_FILL, "Ice Fill");
        tabNames.put(ICE_PATH, "Ice Path");
        tabNames.put(QUIZ, "Quiz");

        byPackKind.put("blaze_lower", BLAZE);
        byPackKind.put("blaze_upper", BLAZE);
        byPackKind.put("boulder", BOULDER);
        byPackKind.put("creeper_beams", BEAMS);
        byPackKind.put("ice_fill", ICE_FILL);
        byPackKind.put("quiz", QUIZ);
        byPackKind.put("three_weirdos", WEIRDOS);
        byPackKind.put("tic_tac_toe", TTT);
        byPackKind.put("water_board", WATER_BOARD);
        byPackKind.put("teleport_maze", TP_MAZE);
        byPackKind.put("ice_path", ICE_PATH);
    }

    /** The code for a room design's `puzzle.kind`, or null (unknown word, `bomb_defuse`: F7 never has it). */
    public static String fromPackKind(String kind) {
        return kind == null ? null : byPackKind.get(kind.toLowerCase(Locale.ROOT));
    }

    /** The code a rooms-pack family belongs to (`Blaze_Lower` and `Blaze_Upper` are both BLAZE), or null. */
    public static String fromFamily(String family) {
        return family == null ? null : byPackKind.get(family.toLowerCase(Locale.ROOT));
    }

    /** Puzzles that go wrong and can be reset with the Architect's First Draft. The Quiz starts itself over after a wrong answer. */
    public static final Set<String> resettable = Set.of(BLAZE, WEIRDOS, TTT);
}
