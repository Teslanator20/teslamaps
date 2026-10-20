package com.teslamaps.adm.misc;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Ported from ADM's misc/ScoreboardLines.kt: Clean Scoreboard's line hider
public final class ScoreboardLines {
    public static volatile boolean hideLines;
    public static volatile boolean hideCatacombsLocation;
    public static volatile boolean hideTimeElapsed = true;
    public static volatile boolean hideCleared = true;

    enum Kind {
        BLANK, DATE, SEASON, CLOCK, LOCATION_CATACOMBS, LOCATION_OTHER, PURSE, BITS, OBJECTIVE,
        OBJECTIVE_TASK, HYPE, PROTOTYPE_LOBBY, FOOTER, STARTING, TEAMMATE_LOBBY, TEAMMATE_RUN, SOLO,
        TIME_ELAPSED, KEYS, CLEARED,
    }

    private static final Set<Kind> ALWAYS_HIDDEN = EnumSet.of(
            Kind.BLANK, Kind.DATE, Kind.SEASON, Kind.CLOCK, Kind.LOCATION_OTHER, Kind.OBJECTIVE,
            Kind.OBJECTIVE_TASK, Kind.HYPE, Kind.PROTOTYPE_LOBBY, Kind.FOOTER, Kind.STARTING, Kind.SOLO,
            Kind.KEYS);

    private static final String NUM = "\\d[\\d,.]*";
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private record K(Kind kind, Pattern p) {}

    private static final List<K> KINDS = List.of(
            new K(Kind.DATE, Pattern.compile("^\\d{2}/\\d{2}/\\d{2}\\s+\\S+.*$")),
            new K(Kind.SEASON, Pattern.compile("^(?:Early |Late )?(?:Spring|Summer|Autumn|Winter) \\d{1,2}(?:st|nd|rd|th)$")),
            new K(Kind.CLOCK, Pattern.compile("^\\d{1,2}:\\d{2}(?:am|pm)(?: [☀☽])?$")),
            new K(Kind.LOCATION_CATACOMBS, Pattern.compile("^\\W*The Catacombs \\((?:[FM][1-7]|E)\\)$")),
            new K(Kind.LOCATION_OTHER, Pattern.compile("^ .+$")),
            new K(Kind.PURSE, Pattern.compile("^(?:Purse|Piggy): " + NUM + "(?: \\(\\+" + NUM + "\\))?$")),
            new K(Kind.BITS, Pattern.compile("^Bits: " + NUM + "(?: \\(\\+" + NUM + "\\))?$")),
            new K(Kind.OBJECTIVE, Pattern.compile("^Objective(?: \\S)?$")),
            new K(Kind.HYPE, Pattern.compile("^Hype: " + NUM + "/" + NUM + "$")),
            new K(Kind.PROTOTYPE_LOBBY, Pattern.compile("^(?:Games in this lobby are|under heavy development!|Report bugs and leave|feedback at|hypixel\\.net/ptl)$")),
            new K(Kind.FOOTER, Pattern.compile("^www\\.hypixel\\.net$")),
            new K(Kind.STARTING, Pattern.compile("^(?:Starting in|Auto-closing in): .+$")),
            new K(Kind.TEAMMATE_LOBBY, Pattern.compile("^\\[[MHABT]\\] \\w{1,16} \\[Lv\\d*\\]?$")),
            new K(Kind.TEAMMATE_RUN, Pattern.compile("^\\[[MHABT]\\] \\w{1,16} " + NUM + "❤?$")),
            new K(Kind.SOLO, Pattern.compile("^Solo$")),
            new K(Kind.TIME_ELAPSED, Pattern.compile("^Time Elapsed: .+$")),
            new K(Kind.KEYS, Pattern.compile("^Keys: .+$")),
            new K(Kind.CLEARED, Pattern.compile("^Cleared: " + NUM + "% \\(" + NUM + "\\)$")));

    private static final Comparator<PlayerScoreEntry> DISPLAY_ORDER =
            Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private ScoreboardLines() {}

    static Kind kindOf(String text, Kind previous) {
        String t = text.trim();
        if (t.isEmpty()) return Kind.BLANK;
        for (K k : KINDS) if (k.p.matcher(t).matches()) return k.kind;
        return previous == Kind.OBJECTIVE ? Kind.OBJECTIVE_TASK : null;
    }

    static boolean hidesKind(Kind kind) {
        if (kind == null) return false;
        return switch (kind) {
            case LOCATION_CATACOMBS -> hideCatacombsLocation;
            case TIME_ELAPSED -> hideTimeElapsed;
            case CLEARED -> hideCleared;
            default -> ALWAYS_HIDDEN.contains(kind);
        };
    }

    public static boolean hidesTitle() {
        return hideLines;
    }

    public static Collection<PlayerScoreEntry> visibleEntries(Scoreboard scoreboard, Objective objective, Collection<PlayerScoreEntry> all) {
        if (!hideLines) return all;
        List<PlayerScoreEntry> sorted = new ArrayList<>();
        for (PlayerScoreEntry e : all) if (!e.isHidden()) sorted.add(e);
        sorted.sort(DISPLAY_ORDER);
        Map<PlayerScoreEntry, Boolean> hidden = new IdentityHashMap<>();
        Kind previous = null;
        for (PlayerScoreEntry e : sorted) {
            Kind kind = kindOf(plain(lineText(scoreboard, e)), previous);
            if (hidesKind(kind)) hidden.put(e, true);
            previous = kind;
        }
        if (hidden.isEmpty()) return all;
        List<PlayerScoreEntry> out = new ArrayList<>(all.size());
        for (PlayerScoreEntry e : all) if (!hidden.containsKey(e)) out.add(e);
        return out;
    }

    private static Component lineText(Scoreboard scoreboard, PlayerScoreEntry entry) {
        return PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
    }

    private static String plain(Component c) {
        return FORMATTING.matcher(c.getString()).replaceAll("").trim();
    }
}
