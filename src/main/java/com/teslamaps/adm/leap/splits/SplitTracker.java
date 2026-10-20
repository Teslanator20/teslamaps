package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// Ported from ADM's splits/SplitsModel.kt
public final class SplitTracker {

    public static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    public static final String OPEN = "&aOpen";
    public static final String BLOOD = "&cBlood";
    public static final String PORTAL = "&dPortal";
    public static final String MAXOR = "&5Maxor";
    public static final String STORM = "&bStorm";
    public static final String TERMS = "&6Terms";
    public static final String GOLDOR = "&eGoldor";
    public static final String NECRON = "&cNecron";
    public static final String DRAGONS = "&dDragons";
    public static final String RELICS_PLACED = "[BOSS] Wither King: You... again?";
    public static final Pattern EXTRA_STATS = Pattern.compile("^ +> EXTRA STATS <$");

    public record Clock(long ms, int ticks) {}

    private record Phase(String label, Predicate<String> starts) {}

    private static final List<Phase> PHASES = List.of(
            new Phase(OPEN, m -> false),
            new Phase(BLOOD, m -> m.startsWith("[BOSS] The Watcher: ")),
            new Phase(PORTAL, m -> m.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")),
            new Phase(MAXOR, m -> m.equals("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")),
            new Phase(STORM, m -> m.equals("[BOSS] Storm: Pathetic Maxor, just like expected.")),
            new Phase(TERMS, m -> m.equals("[BOSS] Goldor: Who dares trespass into my domain?")),
            new Phase(GOLDOR, m -> m.equals("The Core entrance is opening!")),
            new Phase(NECRON, m -> m.equals("[BOSS] Necron: You went further than any human before, congratulations.")),
            new Phase(DRAGONS, m -> m.equals(RELICS_PLACED)));

    private Stamp start;
    private final Stamp[] starts = new Stamp[PHASES.size()];
    private Stamp end;

    private record Pending(int phase, Function<String, Clock> before, Clock head) {}
    private Pending pending;

    public void reset() {
        start = null;
        end = null;
        Arrays.fill(starts, null);
        pending = null;
    }

    public void startAt(String label, Function<String, Clock> before, Stamp now, Clock head) {
        reset();
        int i = -1;
        for (int k = 0; k < PHASES.size(); k++) if (PHASES.get(k).label.equals(label)) i = k;
        if (i < 0) return;
        pending = new Pending(i, before, head == null ? new Clock(0, 0) : head);
        if (now != null) fill(now);
    }

    private void fill(Stamp at) {
        Pending p = pending;
        if (p == null) return;
        pending = null;
        Stamp s = new Stamp(at.realMs() - p.head.ms(), at.tick() - p.head.ticks());
        starts[p.phase] = s;
        for (int j = p.phase - 1; j >= 0; j--) {
            Clock c = p.before.apply(PHASES.get(j).label);
            s = new Stamp(s.realMs() - c.ms(), s.tick() - c.ticks());
            starts[j] = s;
        }
        start = starts[0];
    }

    public void onChat(String msg, Stamp at) {
        if (pending != null) {
            if (PHASES.get(pending.phase).starts.test(msg)) fill(at);
            return;
        }
        if (start == null) {
            if (msg.equals(MORT)) { start = at; starts[0] = at; }
            return;
        }
        if (end != null) return;
        if (EXTRA_STATS.matcher(msg).matches()) { end = at; return; }
        for (int i = 1; i < PHASES.size(); i++) {
            if (starts[i] == null && PHASES.get(i).starts.test(msg)) starts[i] = at;
        }
    }

    public List<Split> splits() {
        List<Split> out = new ArrayList<>();
        for (int i = 0; i < PHASES.size(); i++) {
            Stamp from = starts[i];
            if (from == null) continue;
            Stamp next = null;
            for (int k = i + 1; k < PHASES.size(); k++) if (starts[k] != null) { next = starts[k]; break; }
            out.add(new Split(PHASES.get(i).label, from, next != null ? next : end));
        }
        return out;
    }

    public Split split(String label) {
        for (Split s : splits()) if (s.label().equals(label)) return s;
        return null;
    }
}
