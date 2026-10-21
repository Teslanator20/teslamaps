package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/ProbeReport.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProbeReport {
    private ProbeReport() {}

    public static List<ProbeLine> compare(List<ExpectedRoom> expected, List<SeenRoom> seen) {
        List<ProbeLine> out = new ArrayList<>();
        for (ExpectedRoom e : expected) {
            SeenRoom same = null;
            for (SeenRoom s : seen) if (Objects.equals(s.name, e.name)) { same = s; break; }
            if (same == null) {
                List<SeenRoom> here = new ArrayList<>();
                for (SeenRoom s : seen) {
                    boolean any = false;
                    for (Pair<Integer, Integer> t : s.tiles) if (e.tiles.contains(t)) { any = true; break; }
                    if (any) here.add(s);
                }
                String tail;
                if (here.isEmpty()) tail = " (it has no room on tiles " + tilesText(e.tiles) + " yet).";
                else tail = " (on tiles " + tilesText(e.tiles) + " it holds " + here.stream().map(s -> s.name != null ? s.name : "an unnamed room").collect(Collectors.joining(", ")) + ").";
                out.add(new ProbeLine(false, e.name + ": Odin has not named it" + tail));
            } else if (same.rotation == null) {
                out.add(new ProbeLine(false, e.name + ": Odin named it but found no rotation yet (expected " + e.rotation + ")."));
            } else if (!same.rotation.equals(e.rotation)) {
                out.add(new ProbeLine(false, e.name + ": Odin says rotation " + same.rotation + ", built " + e.rotation + "."));
            } else if (!same.tiles.equals(e.tiles)) {
                out.add(new ProbeLine(false, e.name + ": Odin has it on tiles " + tilesText(same.tiles) + ", built on " + tilesText(e.tiles) + "."));
            } else {
                out.add(new ProbeLine(true, e.name + ": Odin names it, rotation " + same.rotation + ", " + (same.shape != null ? same.shape : "?") + " on tiles " + tilesText(e.tiles) + "."));
            }
        }
        return out;
    }

    public static boolean allOk(List<ProbeLine> lines) {
        if (lines.isEmpty()) return false;
        for (ProbeLine l : lines) if (!l.ok) return false;
        return true;
    }

    private static String tilesText(Set<Pair<Integer, Integer>> t) {
        List<Pair<Integer, Integer>> sorted = new ArrayList<>(t);
        sorted.sort(Comparator.<Pair<Integer, Integer>>comparingInt(p -> p.second).thenComparingInt(p -> p.first));
        return sorted.stream().map(p -> "(" + p.first + "," + p.second + ")").collect(Collectors.joining(" "));
    }
}
