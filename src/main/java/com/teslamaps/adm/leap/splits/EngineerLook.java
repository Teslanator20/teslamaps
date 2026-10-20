package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's splits/EngineerLook.kt
public final class EngineerLook {

    private EngineerLook() {}

    public record Row(String name, long ms, long ticks, boolean current) {}

    public enum Place { FLOOR7, DUNGEON, OTHER }

    public record Options(boolean bossEntry, boolean show0, boolean showTicks, boolean enterAfterEntry, boolean showPace, boolean showLag) {}

    public record Line(String label, String colour, String time, String ticks) {
        Line withColour(String c) { return new Line(label, c, time, ticks); }
    }

    public interface Grade { String of(String name, long ms, long ticks, boolean over); }

    public static final List<String> TARGET_SPLITS = List.of("Blood Open", "Blood Clear", "Portal Entry", "Maxor", "Storm", "Terminals", "Goldor", "Necron", "Cleared");

    public static List<String> targetLabels(boolean master) {
        List<String> l = new ArrayList<>(List.of("Open", "Blood", "Portal", "Maxor", "Storm", "Terms", "Goldor", "Necron"));
        if (master) l.add("Dragons");
        return l;
    }

    private static final Map<String, String> DUNGEON_NAMES = Map.of("Blood Open", "§aOpen", "Blood Clear", "§cBlood", "Portal Entry", "§dPortal", "Boss Entry", "§9Enter");
    private static final Map<String, String> FLOOR7_NAMES = Map.of("Maxor", "§5Maxor", "Storm", "§bStorm", "Terminals", "§6Terms", "Goldor", "§eGoldor", "Necron", "§cNecron");
    private static final String PACE = "§3Pace";
    public static final String BOSS_ENTRY = "§9Boss Entry";
    private static final String LAG = "§8Lag";

    static String strip(String s) { return s.replaceAll("§.", "").trim(); }

    public static String label(String odinName, Place place, boolean master) {
        String name = strip(odinName);
        if (place != Place.OTHER && DUNGEON_NAMES.containsKey(name)) return DUNGEON_NAMES.get(name);
        if (place == Place.FLOOR7) {
            if (FLOOR7_NAMES.containsKey(name)) return FLOOR7_NAMES.get(name);
            if (name.equals("Cleared") && master) return "§dDragons";
        }
        return odinName.startsWith("§") ? odinName : "§f" + odinName;
    }

    private static String colourOf(String label) {
        return label.length() >= 2 && label.charAt(0) == '§' ? label.substring(0, 2) : "§f";
    }

    public static List<Line> lines(List<Row> rows, Options opts, Place place, boolean master, List<Double> targets,
                                   Grade grade, long[] pace, Long lagMs) {
        if (rows.isEmpty()) return List.of();
        List<Row> segments = segments(rows, place, master);
        List<Line> out = new ArrayList<>();
        int current = -1;
        for (int i = 0; i < segments.size(); i++) if (segments.get(i).current) { current = i; break; }
        boolean anyMs = false;
        for (Row r : rows) if (r.ms > 0) anyMs = true;
        boolean started = current >= 0 || anyMs;
        if (opts.showLag && lagMs != null && started) out.add(new Line(LAG, lagColour(lagMs), Split.seconds(lagMs), null));
        if (opts.showPace && (started || opts.show0 || targets != null)) {
            long ms = 0, ticks = 0;
            for (int i = 0; i < segments.size(); i++) {
                Row s = segments.get(i);
                Double target = targetFor(s.name, targets);
                if (!started || (current >= 0 && i > current)) { ms += secondsMs(target); ticks += secondsTicks(target); }
                else if (i == current) { ms += Math.max(s.ms, secondsMs(target)); ticks += Math.max(s.ticks, secondsTicks(target)); }
                else { ms += s.ms; ticks += s.ticks; }
            }
            out.add(pace != null ? new Line(PACE, colourOf(PACE), SplitPace.mss(pace[0]), SplitPace.mss(pace[1] * 50))
                    : line(PACE, ms, ticks, opts, Split::minutes));
        }
        for (int i = 0; i < segments.size(); i++) {
            Row s = segments.get(i);
            if (s.ms != 0L || opts.show0) {
                Line l = line(label(s.name, place, master), s.ms, s.ticks, opts, Split::seconds);
                boolean over = current < 0 || i < current;
                String g = grade == null ? null : grade.of(s.name, s.ms, s.ticks, over && s.ms != 0L);
                out.add(g != null ? l.withColour(g) : l);
            }
            boolean entered = current > 2 || (current == -1 && started);
            if (opts.bossEntry && i == 2 && rows.size() > 3 && (entered || !opts.enterAfterEntry)) {
                long ms = 0, ticks = 0;
                for (int k = 0; k < Math.min(3, segments.size()); k++) { ms += segments.get(k).ms; ticks += segments.get(k).ticks; }
                if (ms != 0L || opts.show0) {
                    Line l = line(label(BOSS_ENTRY, place, master), ms, ticks, opts, Split::minutes);
                    String g = grade == null ? null : grade.of(BOSS_ENTRY, ms, ticks, entered && ms != 0L);
                    out.add(g != null ? l.withColour(g) : l);
                }
            }
        }
        return out;
    }

    public static List<String> allLabels(List<Row> rows, Options opts, Place place, boolean master) {
        List<String> out = new ArrayList<>();
        if (opts.showPace) out.add(PACE);
        if (opts.showLag) out.add(LAG);
        for (Row r : segments(rows, place, master)) out.add(label(r.name, place, master));
        if (opts.bossEntry && rows.size() > 3) out.add(label(BOSS_ENTRY, place, master));
        return out;
    }

    public static Line currentLine(List<Row> rows, Options opts, Place place, boolean master) {
        for (Row s : segments(rows, place, master)) if (s.current) return line(label(s.name, place, master), s.ms, s.ticks, opts, Split::seconds);
        return null;
    }

    private static List<Row> segments(List<Row> rows, Place place, boolean master) {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < rows.size() - 1; i++) {
            Row r = rows.get(i);
            if (place == Place.FLOOR7 && !master && strip(r.name).equals("Cleared")) continue;
            out.add(r);
        }
        return out;
    }

    private static String lagColour(long ms) {
        if (ms < 1000) return "§7";
        if (ms <= 3000) return "§e";
        return "§c";
    }

    public static String text(Line l) {
        return l.label + " §b> " + l.colour + l.time + (l.ticks != null ? " §8(§7" + l.ticks + "§8)" : "");
    }

    private static Line line(String label, long ms, long ticks, Options opts, LongFunction<String> format) {
        return new Line(label, colourOf(label), format.apply(ms), opts.showTicks ? format.apply(ticks * 50L) : null);
    }

    private static Double targetFor(String odinName, List<Double> targets) {
        if (targets == null) return null;
        int i = TARGET_SPLITS.indexOf(strip(odinName));
        return i < 0 || i >= targets.size() ? null : targets.get(i);
    }

    private static long secondsMs(Double s) { return s == null ? 0L : Math.round(s * 1000); }
    private static long secondsTicks(Double s) { return s == null ? 0L : Math.round(s * 20); }

    private static final Pattern MINUTES = Pattern.compile("^(\\d+)\\s*[:m]\\s*(\\d+(?:\\.\\d+)?)?\\s*s?$");
    private static final Pattern SECONDS = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*s?$");

    public static Double parseSeconds(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        Matcher m = SECONDS.matcher(t);
        if (m.matches()) return Double.parseDouble(m.group(1));
        m = MINUTES.matcher(t);
        if (m.matches()) {
            String sec = m.group(2);
            if (t.contains(":") && (sec == null || sec.isEmpty())) return null;
            return Double.parseDouble(m.group(1)) * 60 + (sec == null || sec.isEmpty() ? 0 : Double.parseDouble(sec));
        }
        return null;
    }

    public static String formatSeconds(double s) {
        String r = String.format(Locale.ROOT, "%.2f", s);
        if (r.contains(".")) {
            r = r.replaceAll("0+$", "");
            if (r.endsWith(".")) r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
