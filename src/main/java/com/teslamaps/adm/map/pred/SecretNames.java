package com.teslamaps.adm.map.pred;

// Ported from ADM's mappred/SecretNames.kt
import com.teslamaps.adm.map.pred.Pred.RoomName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SecretNames {
    public static final int MAX_ROOMS = 4;
    private static final int BUDGET = 3_000_000;

    public final List<RoomName>[] avail;
    private final int[] targets;
    private final double[] poolWeight = new double[MapStats.POOLS];
    private final int[] minSec = new int[MapStats.POOLS];
    private final int[] maxSec = new int[MapStats.POOLS];
    private final HashMap<String, Result> cache = new HashMap<>();
    private final int minT, maxT;

    public record Result(double likelihood, Map<Integer, double[]> byPool) {}

    private static final class TooMany extends RuntimeException {
        TooMany() { super(null, null, false, false); }
    }

    @SuppressWarnings("unchecked")
    public SecretNames(Set<String> used, int[] targets) {
        this.targets = targets;
        avail = new List[MapStats.POOLS];
        for (int p = 0; p < MapStats.POOLS; p++) {
            List<RoomName> l = new ArrayList<>();
            for (RoomName n : MapStats.NAMES) if (n.pool() == p && !used.contains(n.name())) l.add(n);
            avail[p] = l;
            double w = 0;
            int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
            for (RoomName n : l) { w += n.seen(); mn = Math.min(mn, n.secrets()); mx = Math.max(mx, n.secrets()); }
            poolWeight[p] = w;
            minSec[p] = l.isEmpty() ? 0 : mn;
            maxSec[p] = l.isEmpty() ? 0 : mx;
        }
        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        for (int t : targets) { mn = Math.min(mn, t); mx = Math.max(mx, t); }
        minT = targets.length == 0 ? 0 : mn;
        maxT = targets.length == 0 ? -1 : mx;
    }

    /** Null when there are too many rooms to work out exactly. */
    public Result solve(int[] pools) {
        if (pools.length > MAX_ROOMS || targets.length == 0) return null;
        int[] sorted = pools.clone();
        Arrays.sort(sorted);
        String key = Arrays.toString(sorted);
        if (cache.containsKey(key)) return cache.get(key);
        Result res;
        try {
            res = enumerate(sorted);
        } catch (TooMany e) {
            res = null;
        }
        cache.put(key, res);
        return res;
    }

    private int[] pools, pick, minRest, maxRest;
    private double[][] marg;
    private double total;
    private int steps;

    private Result enumerate(int[] pools) {
        int m = pools.length;
        if (m == 0) return new Result(contains(0) ? 1.0 : 0.0, Map.of());
        this.pools = pools;
        minRest = new int[m + 1];
        maxRest = new int[m + 1];
        for (int i = m - 1; i >= 0; i--) {
            minRest[i] = minRest[i + 1] + minSec[pools[i]];
            maxRest[i] = maxRest[i + 1] + maxSec[pools[i]];
        }
        pick = new int[m];
        marg = new double[m][];
        for (int i = 0; i < m; i++) marg[i] = new double[avail[pools[i]].size()];
        total = 0;
        steps = 0;
        dfs(0, 0, 1.0);
        Map<Integer, double[]> byPool = new HashMap<>();
        for (int j = 0; j < m; j++) {
            if (byPool.containsKey(pools[j])) continue;
            double[] d = new double[marg[j].length];
            for (int k = 0; k < d.length; k++) d[k] = total > 0 ? marg[j][k] / total : 0.0;
            byPool.put(pools[j], d);
        }
        return new Result(total, byPool);
    }

    private boolean contains(int s) {
        for (int t : targets) if (t == s) return true;
        return false;
    }

    private void dfs(int i, int sum, double w) {
        if (++steps > BUDGET) throw new TooMany();
        int m = pools.length;
        if (i == m) {
            if (contains(sum)) {
                total += w;
                for (int j = 0; j < m; j++) marg[j][pick[j]] += w;
            }
            return;
        }
        int p = pools[i];
        List<RoomName> names = avail[p];
        for (int k = 0; k < names.size(); k++) {
            boolean taken = false;
            for (int j = 0; j < i; j++) if (pools[j] == p && pick[j] == k) { taken = true; break; }
            if (taken) continue;
            int s = sum + names.get(k).secrets();
            if (s + minRest[i + 1] > maxT || s + maxRest[i + 1] < minT) continue;
            pick[i] = k;
            dfs(i + 1, s, w * names.get(k).seen() / poolWeight[p]);
        }
    }

    /** Totals the tab list allows: found secrets shown as percent%. Empty when nothing is found yet. */
    public static int[] totals(int found, float percent) {
        if (found <= 0 || percent <= 0f) return new int[0];
        if (percent >= 100f) return new int[]{found};
        int lo = (int) Math.ceil(100.0 * found / (percent + 0.05));
        int hi = (int) Math.floor(100.0 * found / Math.max(percent - 0.05, 0.001));
        if (hi < lo || hi - lo > 40) return new int[0];
        int from = Math.max(lo, found);
        if (hi < from) return new int[0];
        int[] out = new int[hi - from + 1];
        for (int i = 0; i < out.length; i++) out[i] = from + i;
        return out;
    }
}
