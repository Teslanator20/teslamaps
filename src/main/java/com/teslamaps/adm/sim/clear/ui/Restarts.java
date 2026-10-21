package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/Restarts.kt

import com.teslamaps.adm.sim.run.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Restarts {
    private Restarts() {}

    public interface Choice {
        final class Use implements Choice {
            public final Savepoint savepoint;
            public final String note;

            public Use(Savepoint savepoint, String note) {
                this.savepoint = savepoint;
                this.note = note;
            }

            public Use(Savepoint savepoint) { this(savepoint, null); }
        }

        final class None implements Choice {
            public final String message;

            public None(String message) { this.message = message; }
        }
    }

    public static final String START_LABEL = "Start";

    public static Choice start(List<Savepoint> all) {
        Savepoint sp = null;
        for (Savepoint s : all) if (s.label.equals(START_LABEL)) { sp = s; break; }
        if (sp == null && !all.isEmpty()) sp = all.get(0);
        if (sp == null) return new Choice.None("There are no savepoints yet, so there is nothing to go back to.");
        return new Choice.Use(sp);
    }

    public static Choice room(List<Savepoint> all, Integer roomIndex) {
        if (all.isEmpty()) return new Choice.None("There are no savepoints yet, so there is nothing to go back to.");
        if (roomIndex != null) {
            for (int i = all.size() - 1; i >= 0; i--) if (Objects.equals(all.get(i).roomIndex, roomIndex)) return new Choice.Use(all.get(i));
        }
        Savepoint recent = null;
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).roomIndex != null) { recent = all.get(i); break; }
        if (recent != null) return new Choice.Use(recent, "You are not in a room with a savepoint, so this goes back to the latest one: " + recent.label + ".");
        Choice first = start(all);
        return first instanceof Choice.Use u ? new Choice.Use(u.savepoint, "No room has a savepoint yet, so this goes back to the start.") : first;
    }

    public static List<String> lines(List<Savepoint> all) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Savepoint sp = all.get(i);
            out.add((i + 1) + ". " + sp.label + " (" + clock(sp.t) + ")");
        }
        return out;
    }

    public static Savepoint find(List<Savepoint> all, String word) {
        Integer n;
        try { n = Integer.parseInt(word); } catch (NumberFormatException e) { n = null; }
        if (n != null && n - 1 >= 0 && n - 1 < all.size()) return all.get(n - 1);
        for (Savepoint s : all) if (s.id.equals(word)) return s;
        return null;
    }

    public static String clock(int ticks) {
        int tenths = (int) Math.round(ticks / 2.0);
        String sign = tenths < 0 ? "-" : "";
        int a = Math.abs(tenths);
        return sign + (a / 10) + "." + (a % 10) + " s";
    }
}
