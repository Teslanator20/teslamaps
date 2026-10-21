package com.teslamaps.adm.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The bits of Odin the sim relied on. */
public final class OdinCompat {
    private OdinCompat() {}

    public static final SimDungeonPlayer EMPTY = new SimDungeonPlayer("Empty", DungeonClass.EMPTY);

    // Ported from Odin's LeapMenu.odinSorting
    public static SimDungeonPlayer[] odinSorting(List<SimDungeonPlayer> players) {
        SimDungeonPlayer[] result = {EMPTY, EMPTY, EMPTY, EMPTY};
        List<SimDungeonPlayer> secondRound = new ArrayList<>();
        List<SimDungeonPlayer> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparingInt(p -> p.clazz.priority));
        for (SimDungeonPlayer p : sorted) {
            if (result[p.clazz.defaultQuadrant] == EMPTY) result[p.clazz.defaultQuadrant] = p;
            else secondRound.add(p);
        }
        if (secondRound.isEmpty()) return result;
        for (int i = 0; i < result.length; i++) {
            if (result[i] == EMPTY) {
                result[i] = secondRound.remove(0);
                if (secondRound.isEmpty()) return result;
            }
        }
        return result;
    }
}
