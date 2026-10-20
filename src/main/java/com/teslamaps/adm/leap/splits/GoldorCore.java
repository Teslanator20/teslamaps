package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Ported from ADM's splits/GoldorCore.kt
public final class GoldorCore {

    public record Entry(int tick, boolean exact, boolean beforeS4, boolean died) {}

    public static final int GRACE = 10;

    private Integer startTick;
    private Integer openTick;
    private Integer goldorMoved;
    public boolean pasted = false;
    private final LinkedHashMap<String, Entry> inside = new LinkedHashMap<>();
    private final Map<String, Integer> seen = new HashMap<>();
    private final Set<String> deadAtStart = new HashSet<>();
    private int round = 0;
    private boolean necron = false;
    private int now = 0;

    public Integer startTick() { return startTick; }
    public Integer openTick() { return openTick; }
    public Integer goldorMoved() { return goldorMoved; }

    public boolean watching() {
        return startTick != null && !necron && (goldorMoved == null || now < openTick + goldorMoved + GRACE);
    }

    public boolean active() { return startTick != null && !necron; }

    public void reset() {
        startTick = null; openTick = null; goldorMoved = null; pasted = false;
        inside.clear(); seen.clear(); deadAtStart.clear(); round = 0; necron = false;
    }

    public void onS4(int tick) { if (startTick == null) startTick = tick; }

    public void onCoreOpen(int tick) {
        if (openTick != null) return;
        if (startTick == null) startTick = tick;
        openTick = tick;
    }

    public boolean look(int tick) {
        now = tick;
        if (!watching()) return false;
        round++;
        return true;
    }

    public void observe(String name, Boolean inCore, int tick) {
        if (!watching() || inCore == null) return;
        Integer before = seen.put(name, round);
        if (!inCore || inside.containsKey(name)) return;
        boolean first = round == 1;
        inside.put(name, new Entry(tick, first || (before != null && before == round - 1), first, false));
    }

    public void onDeath(String name, int tick) {
        if (!watching() || inside.containsKey(name) || deadAtStart.contains(name)) return;
        if (round == 1) { deadAtStart.add(name); return; }
        inside.put(name, new Entry(tick, true, false, true));
    }

    public List<String> counted(Collection<String> alive) {
        LinkedHashSet<String> out = new LinkedHashSet<>(alive);
        for (Map.Entry<String, Entry> e : inside.entrySet()) if (e.getValue().died) out.add(e.getKey());
        return new ArrayList<>(out);
    }

    public void onGoldorMoved(int tick) {
        if (openTick == null) return;
        if (goldorMoved == null) goldorMoved = tick - openTick;
    }

    public void onNecron() { if (startTick != null) necron = true; }

    public Entry entry(String name) { return inside.get(name); }

    public Map.Entry<String, Entry> lastIn(Collection<String> alive) {
        if (alive.isEmpty()) return null;
        for (String a : alive) if (!inside.containsKey(a)) return null;
        Map.Entry<String, Entry> best = null;
        for (String a : alive) {
            Entry e = inside.get(a);
            if (best == null || e.tick > best.getValue().tick) best = Map.entry(a, e);
        }
        return best;
    }

    private List<String> lastNames(Collection<String> alive) {
        Map.Entry<String, Entry> last = lastIn(alive);
        if (last == null) return List.of();
        int t = last.getValue().tick;
        List<String> out = new ArrayList<>();
        for (String a : alive) if (inside.get(a).tick == t) out.add(a);
        return out;
    }

    private String time(Entry e) {
        if (e.beforeS4) return "already in";
        if (openTick == null) return "in";
        return (e.exact ? "" : "~") + secs(e.tick - openTick);
    }

    public List<String> lines(int nowTick, String me, Collection<String> alive) {
        if (startTick == null) return List.of();
        Integer open = openTick;
        List<String> out = new ArrayList<>();
        out.add(open == null ? "§eCore §8(S4)" : "§eCore");
        Map.Entry<String, Entry> last = lastIn(alive);
        List<String> lastNames = lastNames(alive);
        for (Map.Entry<String, Entry> en : inside.entrySet()) {
            String name = en.getKey();
            Entry e = en.getValue();
            String colour;
            if (lastNames.contains(name)) colour = "§6";
            else if (!e.exact) colour = "§8";
            else if (open == null || e.tick < open) colour = "§3";
            else colour = "§a";
            out.add((name.equals(me) ? "§f" : "§7") + name + " " + colour + (e.died ? "☠ " : "") + time(e));
        }
        for (String name : alive) if (!inside.containsKey(name)) out.add((name.equals(me) ? "§f" : "§7") + name + " §8...");
        if (open != null) {
            String gap = "";
            if (goldorMoved != null && last != null) gap = " §8(" + signed(open + goldorMoved - last.getValue().tick) + " after the last in)";
            out.add("§eGoldor moved " + (goldorMoved != null ? "§f" + secs(goldorMoved) + gap : "§8..."));
        }
        return out;
    }

    public String pasteLine(Collection<String> alive) {
        if (openTick == null) return null;
        Map.Entry<String, Entry> last = lastIn(alive);
        if (last == null) return null;
        for (String a : alive) if (!inside.get(a).exact) return null;
        if (last.getValue().beforeS4) return null;
        int t = last.getValue().tick - openTick;
        return "Last in core: " + String.join(" & ", lastNames(alive)) + " " + secs(t) + (t < 0 ? " (before the core opened)" : "");
    }

    public static String secs(int ticks) { return String.format(Locale.ROOT, "%.2fs", ticks / 20.0); }

    private static String signed(int ticks) { return (ticks >= 0 ? "+" : "-") + secs(Math.abs(ticks)); }
}
