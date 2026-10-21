// Ported from ADM's p3sim/clear/puzzles/PuzzleLines.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.run.PuzzleStatus;

/** The words the puzzle frame says, as Hypixel prints them. Pure functions, no Minecraft. */
public final class PuzzleLines {
    private PuzzleLines() {}

    /** The tab's headline: `Puzzles: (3)`. */
    public static String tabHeadline(int count) { return "Puzzles: (" + count + ")"; }

    /** The tab glyphs by status: not done, done, failed. */
    public static String glyph(PuzzleStatus status) {
        return switch (status) {
            case UNSOLVED -> "✦";
            case SOLVED -> "✔";
            case FAILED -> "✖";
        };
    }

    /** One tab row: `???` until the puzzle is revealed, then its name; the solver's name in brackets once someone solved it. */
    public static String tabRow(String tabName, boolean revealed, PuzzleStatus status, String solver) {
        String name = revealed ? tabName : "???";
        String who = status == PuzzleStatus.SOLVED && solver != null && !solver.isEmpty() ? " (" + solver + ")" : "";
        return " " + name + ": [" + glyph(status) + "]" + who;
    }

    /** "Good job!" with one colour per letter. */
    public static final String GOOD_JOB = "§4G§r§co§r§6o§r§ed§r§a §r§2j§r§bo§r§3b§r§5!";

    /** "Yikes!" with one colour per letter. */
    public static final String YIKES = "§4Y§r§ci§r§6k§r§ee§r§as§r§2!";

    /** "That's a whoopsie!" with one colour per letter: the Ice Path's own ending. */
    public static final String WHOOPSIE = "§4T§r§ch§r§6a§r§et§r§a'§r§2s§r§b §r§3a§r§5 §r§dw§r§4h§r§co§r§6o§r§ep§r§as§r§2i§r§be§r§3!";

    private static final String TRAP_REASON_END = "That's a whoopsie!";

    /** `PUZZLE SOLVED! {player} {what} Good job!`. */
    public static String solved(String player, String what) {
        return "§a§lPUZZLE SOLVED! §r§b" + player + " §r§e" + what + " §r" + GOOD_JOB;
    }

    /** `PUZZLE FAIL! {player} {reason} Yikes!`; the Ice Path trap reason takes the whoopsie rainbow instead. */
    public static String failed(String player, String reason) {
        if (reason.endsWith(TRAP_REASON_END)) {
            String lead = reason.substring(0, reason.length() - TRAP_REASON_END.length()).stripTrailing().replace("Trap!", "§r§aTrap§r§e!");
            return "§c§lPUZZLE FAIL! §r§b" + player + " §r§e" + lead + " §r" + WHOOPSIE;
        }
        return "§c§lPUZZLE FAIL! §r§b" + player + " §r§e" + reason + " §r" + YIKES;
    }

    /** The line the Architect's First Draft prints. */
    public static String reset(String tabName) {
        return "§r§aYou used the §r§5Architect's First Draft §r§ato reset §r§6" + tabName + "§r§a!";
    }

    /** What the sack command says when a player fetches the draft after a failed puzzle. */
    public static final String SACK_MOVED = "§aMoved §e1 Architect's First Draft§a from your Sacks to your inventory.";

    /** Ice Fill: the cell under you is already covered. */
    public static final String ICE_WRONG_BLOCK = "§cOops! You stepped on the wrong block!";

    /** Ice Fill: the cell is not next to the one you stepped on last. */
    public static final String ICE_DIAGONAL = "§cDon't move diagonally! Bad!";
}
