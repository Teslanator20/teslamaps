package com.teslamaps.adm.map.clear;

// Ported from ADM's clearbreakdown/SecretCredit.kt
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SecretCredit {
    public record RoomSecrets(String room, int count, boolean estimate) {}

    public record PlayerSecrets(Integer total, List<RoomSecrets> rooms, int unknown) {}

    private static final long GAP_CATCH_MS = 3000L;

    private static final class Group {
        final String roomKey, roomName;
        Set<String> candidates;
        boolean exact;
        final long atMs;
        int units;
        final boolean gap;

        Group(String roomKey, String roomName, Set<String> candidates, boolean exact, long atMs, int units, boolean gap) {
            this.roomKey = roomKey; this.roomName = roomName; this.candidates = candidates; this.exact = exact;
            this.atMs = atMs; this.units = units; this.gap = gap;
        }
    }

    private record Credit(int group, String player, int count, boolean estimate) {}

    private final long mergeMs = 1000, exactMs = 3000;
    private final List<Group> groups = new ArrayList<>();
    private final HashMap<String, Group> byKey = new HashMap<>();
    private final HashMap<String, Integer> lastSeen = new HashMap<>();
    private final HashMap<String, Integer> directSince = new HashMap<>();
    private final HashMap<String, HashSet<String>> visited = new HashMap<>();
    private Map<String, String> present = Map.of();

    public void tick(Map<String, String> presence) {
        present = presence;
        presence.forEach((player, room) -> { if (room != null) visited.computeIfAbsent(room, k -> new HashSet<>()).add(player); });
    }

    public void sighting(String key, String roomKey, String roomName, Set<String> candidates, boolean exact, long atMs) {
        Group known = byKey.get(key);
        if (known != null) {
            if (exact && !known.exact && Math.abs(atMs - known.atMs) <= exactMs) { known.candidates = candidates; known.exact = true; }
            else if (!exact && !known.exact && Math.abs(atMs - known.atMs) <= mergeMs) {
                Set<String> s = new HashSet<>(known.candidates);
                s.addAll(candidates);
                known.candidates = s;
            }
            return;
        }
        Group gap = null;
        for (int i = groups.size() - 1; i >= 0; i--) {
            Group g = groups.get(i);
            if (g.gap && g.roomKey.equals(roomKey) && atMs - g.atMs >= 0 && atMs - g.atMs <= GAP_CATCH_MS && g.units > 0) { gap = g; break; }
        }
        if (gap != null) gap.units--;
        else directSince.merge(roomKey, 1, Integer::sum);
        Group g = new Group(roomKey, roomName, candidates, exact, atMs, 1, false);
        groups.add(g);
        byKey.put(key, g);
    }

    public void observe(String roomKey, String roomName, int found) {
        observe(roomKey, roomName, found, System.currentTimeMillis());
    }

    public void observe(String roomKey, String roomName, int found, long atMs) {
        int last = lastSeen.getOrDefault(roomKey, 0);
        if (found <= last) return;
        int gap = found - last - directSince.getOrDefault(roomKey, 0);
        if (gap > 0) groups.add(new Group(roomKey, roomName, new HashSet<>(visited.getOrDefault(roomKey, new HashSet<>())), false, atMs, gap, true));
        lastSeen.put(roomKey, found);
        directSince.put(roomKey, 0);
        HashSet<String> now = new HashSet<>();
        present.forEach((p, r) -> { if (roomKey.equals(r)) now.add(p); });
        visited.put(roomKey, now);
    }

    public Map<String, PlayerSecrets> resolve(Map<String, Integer> apiTotals) {
        Map<String, Integer> left = new HashMap<>();
        apiTotals.forEach((p, t) -> left.put(p, t != null ? t : Integer.MAX_VALUE));
        List<Credit> credits = new ArrayList<>();
        int[] remaining = new int[groups.size()];
        List<List<String>> cands = new ArrayList<>();
        for (Group g : groups) {
            List<String> l = new ArrayList<>();
            for (String c : g.candidates) if (left.containsKey(c)) l.add(c);
            Collections.sort(l);
            cands.add(l);
        }

        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (g.units <= 0) continue;
            if (cands.get(i).size() == 1) give(credits, left, i, cands.get(i).get(0), g.units, false);
            else if (cands.get(i).size() > 1) remaining[i] = g.units;
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < groups.size(); i++) {
                while (remaining[i] > 0) {
                    List<String> eligible = new ArrayList<>();
                    for (String p : cands.get(i)) if (left.get(p) > 0) eligible.add(p);
                    if (eligible.size() > 1) break;
                    if (eligible.size() == 1) give(credits, left, i, eligible.get(0), 1, false);
                    remaining[i]--;
                    changed = true;
                }
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            int n = remaining[i];
            if (n <= 0) continue;
            List<String> eligible = new ArrayList<>();
            for (String p : cands.get(i)) if (left.get(p) > 0) eligible.add(p);
            if (eligible.isEmpty()) continue;
            boolean unlimited = false;
            for (String p : eligible) if (left.get(p) == Integer.MAX_VALUE) unlimited = true;
            double[] weights = new double[eligible.size()];
            double sum = 0;
            for (int k = 0; k < weights.length; k++) { weights[k] = unlimited ? 1.0 : left.get(eligible.get(k)); sum += weights[k]; }
            double[] exact = new double[weights.length];
            int[] shares = new int[weights.length];
            int shareSum = 0;
            for (int k = 0; k < weights.length; k++) { exact[k] = n * weights[k] / sum; shares[k] = (int) exact[k]; shareSum += shares[k]; }
            Integer[] byRemainder = new Integer[eligible.size()];
            for (int k = 0; k < byRemainder.length; k++) byRemainder[k] = k;
            final double[] ex = exact;
            final int[] sh = shares;
            Arrays.sort(byRemainder, (a, b) -> {
                int c = Double.compare(ex[b] - sh[b], ex[a] - sh[a]);
                return c != 0 ? c : Integer.compare(a, b);
            });
            for (int k = 0; k < n - shareSum; k++) shares[byRemainder[k % eligible.size()]]++;
            for (int k = 0; k < eligible.size(); k++) {
                String p = eligible.get(k);
                give(credits, left, i, p, Math.min(shares[k], left.get(p)), true);
            }
        }

        Map<String, PlayerSecrets> out = new LinkedHashMap<>();
        apiTotals.forEach((p, total) -> {
            Map<String, RoomSecrets> rooms = new LinkedHashMap<>();
            credits.stream().filter(c -> c.player().equals(p)).sorted((a, b) -> Integer.compare(a.group(), b.group())).forEach(c -> {
                String name = groups.get(c.group()).roomName;
                RoomSecrets had = rooms.get(name);
                rooms.put(name, new RoomSecrets(name, (had != null ? had.count() : 0) + c.count(), (had != null && had.estimate()) || c.estimate()));
            });
            int placed = 0;
            for (RoomSecrets r : rooms.values()) placed += r.count();
            out.put(p, new PlayerSecrets(total, new ArrayList<>(rooms.values()), total != null ? Math.max(0, total - placed) : 0));
        });
        return out;
    }

    private static void give(List<Credit> credits, Map<String, Integer> left, int i, String p, int n, boolean estimate) {
        if (n <= 0) return;
        credits.add(new Credit(i, p, n, estimate));
        left.put(p, Math.max(0, left.get(p) - n));
    }
}
