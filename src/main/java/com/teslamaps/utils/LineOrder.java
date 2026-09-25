package com.teslamaps.utils;

import com.teslamaps.config.TeslaMapsConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-chosen line order per HUD ({@code hudLineOrder}): keys in order, "-key" = hidden.
 * No entry = default order, everything shown. Keys the saved order does not know yet are
 * appended in their default position.
 */
public final class LineOrder {

    public record Def(String id, String[] keys, String[] labels) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private LineOrder() {}

    public static Def define(String id, String[] keys, String[] labels) {
        Def d = new Def(id, keys, labels);
        DEFS.put(id, d);
        return d;
    }

    public static Def def(String id) {
        return DEFS.get(id);
    }

    /** Resolved order, hidden entries prefixed with "-". */
    public static List<String> resolved(String id) {
        Def d = DEFS.get(id);
        List<String> saved = TeslaMapsConfig.get().hudLineOrder.get(id);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String s : saved) {
                String k = s.startsWith("-") ? s.substring(1) : s;
                if (d == null || indexOf(d.keys, k) >= 0) {
                    if (!out.contains(k) && !out.contains("-" + k)) out.add(s);
                }
            }
        }
        if (d != null) {
            for (int i = 0; i < d.keys.length; i++) {
                String k = d.keys[i];
                if (out.contains(k) || out.contains("-" + k)) continue;
                int at = out.size();
                if (i > 0) {
                    int prev = indexIn(out, d.keys[i - 1]);
                    if (prev >= 0) at = prev + 1;
                }
                out.add(at, k);
            }
        }
        return out;
    }

    public static void save(String id, List<String> order) {
        TeslaMapsConfig.get().hudLineOrder.put(id, new ArrayList<>(order));
        CACHE.remove(id);
    }

    public static void reset(String id) {
        TeslaMapsConfig.get().hudLineOrder.remove(id);
        CACHE.remove(id);
    }

    public static boolean customized(String id) {
        return TeslaMapsConfig.get().hudLineOrder.containsKey(id);
    }

    public static String label(Def d, String key) {
        int i = indexOf(d.keys, key);
        return i >= 0 && i < d.labels.length ? d.labels[i] : key;
    }

    private static final Map<String, List<String>> CACHE = new HashMap<>();
    private static Map<String, List<String>> cacheFor;

    /** Collects lines under keys, then flattens them in the user's order. */
    public static Lines lines(String id) {
        return new Lines(id);
    }

    public static final class Lines {
        private final String id;
        private final Map<String, List<String>> byKey = new HashMap<>();
        private List<String> current;

        private Lines(String id) {
            this.id = id;
        }

        public Lines key(String k) {
            current = byKey.computeIfAbsent(k, x -> new ArrayList<>(2));
            return this;
        }

        public Lines add(String line) {
            if (current == null) key("");
            current.add(line);
            return this;
        }

        public boolean shown(String k) {
            return !order().contains("-" + k);
        }

        public List<String> build() {
            if (!customized(id)) {
                List<String> out = new ArrayList<>();
                Def d = DEFS.get(id);
                if (d != null) for (String k : d.keys) { List<String> l = byKey.get(k); if (l != null) out.addAll(l); }
                return out;
            }
            List<String> out = new ArrayList<>();
            for (String k : order()) {
                if (k.startsWith("-")) continue;
                List<String> l = byKey.get(k);
                if (l != null) out.addAll(l);
            }
            return out;
        }

        private List<String> order() {
            Map<String, List<String>> m = TeslaMapsConfig.get().hudLineOrder;
            if (m != cacheFor) { CACHE.clear(); cacheFor = m; }
            return CACHE.computeIfAbsent(id, LineOrder::resolved);
        }
    }

    private static int indexOf(String[] a, String k) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(k)) return i;
        return -1;
    }

    private static int indexIn(List<String> l, String k) {
        int i = l.indexOf(k);
        return i >= 0 ? i : l.indexOf("-" + k);
    }
}
