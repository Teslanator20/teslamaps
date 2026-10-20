package com.teslamaps.adm.map.clear;

// Ported from ADM's clearbreakdown/ClearCredit.kt
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClearCredit {
    public enum Mark { WHITE, GREEN, RED, OTHER }

    public record Clear(String room, boolean solo) {}

    private record Credited(String room, Set<String> helpers) {}

    private final int helpTicks;
    private final HashMap<String, HashMap<String, Integer>> ticks = new HashMap<>();
    private final HashSet<String> credited = new HashSet<>();
    private final List<Credited> order = new ArrayList<>();
    private Map<String, String> present = Map.of();

    public ClearCredit() { this(40); }

    public ClearCredit(int helpTicks) { this.helpTicks = helpTicks; }

    public void tick(Map<String, String> presence) {
        present = presence;
        for (Map.Entry<String, String> e : presence.entrySet()) {
            String room = e.getValue();
            if (room == null || credited.contains(room)) continue;
            ticks.computeIfAbsent(room, k -> new HashMap<>()).merge(e.getKey(), 1, Integer::sum);
        }
    }

    public void onCheckmark(String roomKey, String roomName, Mark mark) {
        if (credited.contains(roomKey)) return;
        switch (mark) {
            case RED -> ticks.remove(roomKey);
            case WHITE, GREEN -> {
                LinkedHashSet<String> helpers = new LinkedHashSet<>();
                HashMap<String, Integer> in = ticks.get(roomKey);
                if (in != null) in.forEach((p, n) -> { if (n > helpTicks) helpers.add(p); });
                present.forEach((p, room) -> { if (roomKey.equals(room)) helpers.add(p); });
                credited.add(roomKey);
                ticks.remove(roomKey);
                if (!helpers.isEmpty()) order.add(new Credited(roomName, helpers));
            }
            default -> {}
        }
    }

    public Map<String, List<Clear>> clears() {
        Map<String, List<Clear>> out = new LinkedHashMap<>();
        for (Credited c : order) for (String p : c.helpers()) out.computeIfAbsent(p, k -> new ArrayList<>()).add(new Clear(c.room(), c.helpers().size() == 1));
        return out;
    }
}
