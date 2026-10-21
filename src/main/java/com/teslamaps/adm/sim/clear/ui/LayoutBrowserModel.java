package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/LayoutBrowserModel.kt

import com.teslamaps.adm.sim.pack.layouts.Layout;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.pack.layouts.LayoutRoom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LayoutBrowserModel {
    private LayoutBrowserModel() {}

    public enum Sort {
        NEWEST("Newest", "The most recently recorded first."),
        FASTEST("Fastest", "The shortest recorded run first (runs with no recorded time last)."),
        PUZZLES("Puzzles", "The most puzzles first."),
        SECRETS("Secrets", "The most secrets first.");

        public final String label;
        public final String about;

        Sort(String label, String about) {
            this.label = label;
            this.about = about;
        }

        public Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static List<LayoutRow> rows(LayoutPack pack) {
        List<LayoutRow> out = new ArrayList<>();
        for (Layout l : pack.layouts) out.add(row(l));
        return out;
    }

    public static LayoutRow row(Layout l) {
        List<String> words = new ArrayList<>();
        words.add(l.id);
        words.addAll(l.puzzles);
        for (LayoutRoom r : l.rooms) words.add(r.name);
        Integer seconds = l.endTick != null && l.endTick > 0 ? l.endTick / 20 : null;
        return new LayoutRow(l.id, l.rooms.size(), l.puzzles, l.secretsTotal, seconds, l.inDefaultPool(), l.party,
                String.join(" ", words).toLowerCase(Locale.ROOT));
    }

    public static List<LayoutRow> view(List<LayoutRow> rows, String query, Sort sort, boolean poolOnly) {
        List<String> words = new ArrayList<>();
        for (String w : query.toLowerCase(Locale.ROOT).split("\\s+")) if (!w.isEmpty()) words.add(w);
        List<LayoutRow> kept = new ArrayList<>();
        for (LayoutRow r : rows) {
            if (poolOnly && !r.inPool) continue;
            boolean all = true;
            for (String w : words) if (!r.haystack.contains(w)) { all = false; break; }
            if (all) kept.add(r);
        }
        Comparator<LayoutRow> byIdDesc = Comparator.comparing((LayoutRow r) -> r.id).reversed();
        switch (sort) {
            case NEWEST -> kept.sort(byIdDesc);
            case FASTEST -> kept.sort(Comparator.comparing((LayoutRow r) -> r.seconds == null)
                    .thenComparingInt(r -> r.seconds != null ? r.seconds : 0)
                    .thenComparing(byIdDesc));
            case PUZZLES -> kept.sort(Comparator.comparingInt((LayoutRow r) -> r.puzzles.size()).reversed().thenComparing(byIdDesc));
            case SECRETS -> kept.sort(Comparator.comparingInt((LayoutRow r) -> r.secrets).reversed().thenComparing(byIdDesc));
        }
        return kept;
    }

    public static int pageCount(int total, int pageSize) { return Math.max(1, (total + pageSize - 1) / pageSize); }

    public static List<LayoutRow> page(List<LayoutRow> rows, int page, int pageSize) {
        int p = Math.clamp(page, 0, pageCount(rows.size(), pageSize) - 1);
        int from = Math.min(rows.size(), p * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        return new ArrayList<>(rows.subList(from, to));
    }

    public interface Resolved {
        final class One implements Resolved {
            public final String id;

            public One(String id) { this.id = id; }
        }

        final class Many implements Resolved {
            public final List<String> ids;

            public Many(List<String> ids) { this.ids = ids; }
        }

        final class Nothing implements Resolved {
            public static final Nothing INSTANCE = new Nothing();

            private Nothing() {}

            @Override
            public String toString() { return "Nothing"; }
        }
    }

    public static Resolved resolve(List<String> ids, String text) {
        if (ids.contains(text)) return new Resolved.One(text);
        String lower = text.toLowerCase(Locale.ROOT);
        List<Predicate<String>> matches = List.of(it -> it.toLowerCase(Locale.ROOT).startsWith(lower), it -> it.toLowerCase(Locale.ROOT).contains(lower));
        for (Predicate<String> match : matches) {
            List<String> hits = new ArrayList<>();
            for (String id : ids) if (match.test(id)) hits.add(id);
            if (hits.size() == 1) return new Resolved.One(hits.get(0));
            if (hits.size() > 1) return new Resolved.Many(hits);
        }
        return Resolved.Nothing.INSTANCE;
    }

    private static final Pattern TITLE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})_(\\d{2})-(\\d{2})-\\d{2}");

    public static String title(String id) {
        Matcher m = TITLE.matcher(id);
        if (!m.find()) return id;
        return m.group(1) + " " + m.group(2) + ":" + m.group(3);
    }

    public static String clock(int seconds) {
        int h = seconds / 3600;
        int m = seconds / 60 % 60;
        int s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }
}
