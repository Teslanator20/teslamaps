package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/LayoutBrowserModel.kt

import java.util.List;

public class LayoutRow {
    public final String id;
    public final int rooms;
    public final List<String> puzzles;
    public final int secrets;
    public final Integer seconds;
    public final boolean inPool;
    public final List<String> party;
    public final String haystack;

    public LayoutRow(String id, int rooms, List<String> puzzles, int secrets, Integer seconds, boolean inPool, List<String> party, String haystack) {
        this.id = id;
        this.rooms = rooms;
        this.puzzles = puzzles;
        this.secrets = secrets;
        this.seconds = seconds;
        this.inPool = inPool;
        this.party = party;
        this.haystack = haystack;
    }

    public String title() { return LayoutBrowserModel.title(id); }

    public String time() { return seconds != null ? LayoutBrowserModel.clock(seconds) : "?"; }

    public String line() {
        return title() + "  " + rooms + " rooms  " + puzzles.size() + " puzzles  " + secrets + " secrets  " + time() + (inPool ? "" : "  (not complete)");
    }
}
