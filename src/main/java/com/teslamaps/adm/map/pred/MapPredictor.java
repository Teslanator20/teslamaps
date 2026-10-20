package com.teslamaps.adm.map.pred;

// Ported from ADM's mappred/MapPredictor.kt
import com.teslamaps.adm.map.pred.Pred.BigRoom;
import com.teslamaps.adm.map.pred.Pred.Cls;
import com.teslamaps.adm.map.pred.Pred.Edge;
import com.teslamaps.adm.map.pred.Pred.Kind;
import com.teslamaps.adm.map.pred.Pred.MapView;
import com.teslamaps.adm.map.pred.Pred.NamedRoom;
import com.teslamaps.adm.map.pred.Pred.PredictedDoor;
import com.teslamaps.adm.map.pred.Pred.Prediction;
import com.teslamaps.adm.map.pred.Pred.RoomName;
import com.teslamaps.adm.map.pred.Pred.Shape;
import com.teslamaps.adm.map.pred.Pred.Split;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MapPredictor {
    private static final double GROW = 0.3;
    private static final long LEVEL_TRIES = 30_000L;

    private static final int[][][] PLACEMENTS = {
            {{0, 0, 1, 0}, {0, 0, 0, 1}},
            {{0, 0, 1, 0, 2, 0}, {0, 0, 0, 1, 0, 2}},
            {{0, 0, 1, 0, 2, 0, 3, 0}, {0, 0, 0, 1, 0, 2, 0, 3}},
            {{0, 0, 1, 0, 0, 1, 1, 1}},
            {{0, 0, 1, 0, 0, 1}, {0, 0, 1, 0, 1, 1}, {0, 0, 0, 1, 1, 1}, {1, 0, 0, 1, 1, 1}},
    };

    private static final Map<RoomName, Integer> NAME_INDEX = new HashMap<>();
    static {
        for (int i = 0; i < MapStats.NAMES.size(); i++) NAME_INDEX.put(MapStats.NAMES.get(i), i);
    }

    private static int[] sym(int s, int x, int z) {
        return switch (s) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{5 - z, x};
            case 2 -> new int[]{5 - x, 5 - z};
            case 3 -> new int[]{z, 5 - x};
            case 4 -> new int[]{5 - x, z};
            case 5 -> new int[]{z, x};
            case 6 -> new int[]{x, 5 - z};
            default -> new int[]{5 - z, 5 - x};
        };
    }

    private final MapView view;
    private final int w, h, n;
    private final int[] unknown;
    private final boolean[] isUnk;
    private final int[] tileIdx;
    private final byte[] entryDoor;
    private final int[] entries;
    private final int[] entryIndex;
    private boolean bad;

    private final int[] found = new int[Cls.COUNT];
    private final int[] knownBig = new int[5];
    private int entTile = -1, head = -1, headDist = -1, fairyDist = -1;
    private final int remP, needDead;
    private final List<int[]> combos = new ArrayList<>();
    private final int[] frame;

    private final double[][] counts;
    private final double[] splitRooms, splitTiles;
    private final double[][] splitCls;
    private final HashMap<Long, Double> bigCounts = new HashMap<>();
    private SecretNames secrets;
    private boolean secretsOn;
    private final List<Integer> unnamedKnown = new ArrayList<>();
    private final HashMap<Long, Double> nameCounts = new HashMap<>();
    private final HashMap<Integer, double[]> doorCounts = new HashMap<>();
    private double weight;
    public int accepted;
    public int level;
    private long levelTries;

    public MapPredictor(MapView view) {
        this.view = view;
        w = view.w; h = view.h; n = w * h;
        isUnk = new boolean[n];
        tileIdx = new int[n];
        Arrays.fill(tileIdx, -1);
        entryDoor = new byte[n];
        entryIndex = new int[n];
        Arrays.fill(entryIndex, -1);
        frame = new int[n];
        for (int i = 0; i < n; i++) frame[i] = i;

        List<Integer> unk = new ArrayList<>();
        for (int t = 0; t < n; t++) if (view.roomAt[t] < 0) unk.add(t);
        unknown = unk.stream().mapToInt(Integer::intValue).toArray();
        for (int i = 0; i < unknown.length; i++) { isUnk[unknown[i]] = true; tileIdx[unknown[i]] = i; }
        List<Integer> ents = new ArrayList<>();
        for (int t : unknown) for (int nb : neighbours(t)) {
            if (isUnk[nb]) continue;
            byte k = view.edge(t, nb);
            if (!Edge.isDoor(k)) continue;
            if (entryDoor[t] != 0) bad = true;
            entryDoor[t] = k;
            if (!ents.contains(t)) ents.add(t);
        }
        entries = ents.stream().mapToInt(Integer::intValue).toArray();
        for (int i = 0; i < entries.length; i++) entryIndex[entries[i]] = i;

        int entId = -1;
        for (int i = 0; i < view.kinds.size(); i++) {
            int c = clsOf(view.kinds.get(i), view.shapes.get(i));
            found[c]++;
            if (view.shapes.get(i) != Shape.ONE) knownBig[view.shapes.get(i).ordinal()]++;
            if (view.kinds.get(i) == Kind.ENTRANCE) { entId = i; entTile = view.roomTiles.get(i)[0]; }
        }
        if (entId >= 0) walkKnownPath(entId);
        remP = Math.max(0, view.puzzles - found[Cls.PUZZLE]);
        needDead = remP + (found[Cls.TRAP] > 0 ? 0 : 1) + (found[Cls.YELLOW] > 0 ? 0 : 1) + (found[Cls.BLOOD] > 0 ? 0 : 1);
        for (int[] c : MapStats.COMBOS) {
            boolean ok = true;
            for (int i = 0; i < 5; i++) if (c[i] < knownBig[i]) { ok = false; break; }
            if (ok) combos.add(c);
        }
        setUpSecrets();
        if (w == 6 && h == 6 && entTile >= 0) {
            int ex = entTile % w, ez = entTile / w;
            int f = 0;
            for (int s = 0; s < 8; s++) {
                int[] p = sym(s, ex, ez);
                if (p[1] == 0 && p[0] <= 2) { f = s; break; }
            }
            for (int t = 0; t < n; t++) {
                int[] p = sym(f, t % w, t / w);
                frame[t] = p[1] * w + p[0];
            }
        }
        counts = new double[unknown.length][Cls.COUNT];
        splitRooms = new double[entries.length];
        splitTiles = new double[entries.length];
        splitCls = new double[entries.length][Cls.COUNT];
    }

    private void setUpSecrets() {
        if (view.secretTotals.length == 0 || n > 64) return;
        Map<String, RoomName> byName = new HashMap<>();
        for (RoomName r : MapStats.NAMES) byName.put(r.name(), r);
        HashSet<String> used = new HashSet<>();
        int known = 0;
        List<Integer> unnamed = new ArrayList<>();
        for (int i = 0; i < view.kinds.size(); i++) {
            String name = view.names.get(i) != null ? view.names.get(i) : fixedName(view.kinds.get(i));
            if (name != null) {
                RoomName rn = byName.get(name);
                if (rn == null) return;
                if (!used.add(name)) return;
                known += rn.secrets();
            } else unnamed.add(poolOfKnown(i));
        }
        unnamedKnown.addAll(unnamed);
        List<Integer> t = new ArrayList<>();
        for (int s : view.secretTotals) if (s - known >= 0) t.add(s - known);
        if (t.isEmpty()) return;
        secrets = new SecretNames(used, t.stream().mapToInt(Integer::intValue).toArray());
        secretsOn = true;
    }

    private static String fixedName(Kind k) {
        return switch (k) {
            case ENTRANCE -> "Entrance";
            case BLOOD -> "Blood";
            case FAIRY -> "Fairy";
            default -> null;
        };
    }

    private int poolOfKnown(int i) {
        Shape shape = view.shapes.get(i);
        return switch (view.kinds.get(i)) {
            case NORMAL, RARE -> {
                if (shape != Shape.ONE) yield shape.ordinal();
                int t = view.roomTiles.get(i)[0];
                int doors = 0;
                for (int nb : neighbours(t)) if (Edge.isDoor(view.edge(t, nb))) doors++;
                yield view.kinds.get(i) == Kind.RARE || doors == 1 ? MapStats.POOL_RARE : MapStats.POOL_1X1;
            }
            case PUZZLE -> MapStats.POOL_PUZZLE;
            case TRAP -> MapStats.POOL_TRAP;
            case YELLOW -> MapStats.POOL_YELLOW;
            case BLOOD -> MapStats.POOL_BLOOD;
            case FAIRY -> MapStats.POOL_FAIRY;
            case ENTRANCE -> MapStats.POOL_ENTRANCE;
        };
    }

    private static int poolOfCls(int c, Shape shape) {
        return switch (c) {
            case Cls.BIG -> shape.ordinal();
            case Cls.NORMAL -> MapStats.POOL_1X1;
            case Cls.PUZZLE -> MapStats.POOL_PUZZLE;
            case Cls.TRAP -> MapStats.POOL_TRAP;
            case Cls.YELLOW -> MapStats.POOL_YELLOW;
            case Cls.BLOOD -> MapStats.POOL_BLOOD;
            case Cls.FAIRY -> MapStats.POOL_FAIRY;
            default -> MapStats.POOL_RARE;
        };
    }

    private int[] neighbours(int t) {
        int x = t % w, z = t / w;
        int[] out = new int[4];
        int k = 0;
        if (x + 1 < w) out[k++] = t + 1;
        if (x > 0) out[k++] = t - 1;
        if (z + 1 < h) out[k++] = t + w;
        if (z > 0) out[k++] = t - w;
        return Arrays.copyOf(out, k);
    }

    private int inRoom(int[] tiles, int s, int t) {
        if (s == t) return 0;
        int[] dist = new int[tiles.length];
        Arrays.fill(dist, -1);
        int si = -1;
        for (int i = 0; i < tiles.length; i++) if (tiles[i] == s) { si = i; break; }
        if (si < 0) return 99;
        dist[si] = 0;
        int[] q = new int[tiles.length];
        int qh = 0, qt = 0;
        q[qt++] = si;
        while (qh < qt) {
            int u = q[qh++];
            for (int j = 0; j < tiles.length; j++) {
                if (dist[j] < 0 && isAdjacent(tiles[u], tiles[j])) {
                    dist[j] = dist[u] + 1;
                    if (tiles[j] == t) return dist[j];
                    q[qt++] = j;
                }
            }
        }
        return 99;
    }

    private boolean isAdjacent(int a, int b) {
        int d = Math.abs(a - b);
        return (d == 1 && a / w == b / w) || d == w;
    }

    private void walkKnownPath(int entId) {
        int cur = entId, at = entTile, dist = 0;
        HashSet<Integer> onPath = new HashSet<>();
        onPath.add(entId);
        for (int step = 0; step < 40; step++) {
            if (view.kinds.get(cur) == Kind.FAIRY) fairyDist = dist;
            int[] next = null;
            loop:
            for (int q : view.roomTiles.get(cur)) for (int nb : neighbours(q)) {
                int o = view.roomAt[nb];
                if (o == cur) continue;
                byte k = view.edge(q, nb);
                if (!Edge.isDoor(k)) continue;
                if (step > 0 && k != Edge.WITHER && k != Edge.BLOOD) continue;
                if (o >= 0 && onPath.contains(o)) continue;
                next = new int[]{o, q, nb};
                break loop;
            }
            if (next == null) break;
            int nd = dist + inRoom(view.roomTiles.get(cur), at, next[1]) + 1;
            if (next[0] < 0) { head = next[2]; headDist = nd; break; }
            onPath.add(next[0]);
            cur = next[0]; at = next[2]; dist = nd;
        }
    }

    public int run(int samples, int maxTries, Random rnd) {
        if (bad || unknown.length == 0) return 0;
        int kept = 0, tries = 0;
        while (kept < samples && tries < maxTries && level <= 2) {
            tries++; levelTries++;
            if (tryOne(rnd)) { kept++; accepted++; }
            else if (accepted == 0 && levelTries >= LEVEL_TRIES) {
                if (secretsOn) secretsOn = false; else level++;
                levelTries = 0;
            }
        }
        return kept;
    }

    private static int clsOf(Kind k, Shape s) {
        return switch (k) {
            case NORMAL, ENTRANCE -> s == Shape.ONE ? Cls.NORMAL : Cls.BIG;
            case PUZZLE -> Cls.PUZZLE;
            case TRAP -> Cls.TRAP;
            case YELLOW -> Cls.YELLOW;
            case BLOOD -> Cls.BLOOD;
            case FAIRY -> Cls.FAIRY;
            case RARE -> Cls.RARE;
        };
    }

    private static int pick(double[] weights, int count, Random rnd) {
        double total = 0;
        for (int i = 0; i < count; i++) total += weights[i];
        double s = rnd.nextDouble() * total;
        for (int i = 0; i < count; i++) {
            if (s < weights[i]) return i;
            s -= weights[i];
        }
        return count - 1;
    }

    private static float getOr(float[] a, int i) {
        return i >= 0 && i < a.length ? a[i] : 0f;
    }

    private boolean tryOne(Random rnd) {
        List<Shape> todo = new ArrayList<>();
        if (level < 2 && !combos.isEmpty()) {
            double[] cw = new double[combos.size()];
            for (int i = 0; i < cw.length; i++) cw[i] = combos.get(i)[5];
            int[] c = combos.get(pick(cw, combos.size(), rnd));
            for (int i = 0; i < 5; i++) for (int r = 0; r < c[i] - knownBig[i]; r++) todo.add(Shape.values()[i]);
        } else {
            List<Shape> pool = new ArrayList<>();
            int kb = 0;
            for (int i = 0; i < 5; i++) {
                kb += knownBig[i];
                for (int r = 0; r < Math.max(0, 2 - knownBig[i]); r++) pool.add(Shape.values()[i]);
            }
            Collections.shuffle(pool, rnd);
            int take = Math.min(pool.size(), Math.max(0, 7 - kb));
            for (int i = 0; i < take; i++) todo.add(pool.get(i));
        }
        Collections.shuffle(todo, rnd);
        todo.sort((a, b) -> Integer.compare(b.size, a.size));
        int[] owner = new int[n];
        Arrays.fill(owner, -1);
        List<int[]> rooms = new ArrayList<>();
        List<Shape> roomShape = new ArrayList<>();
        List<int[]> candTiles = new ArrayList<>();
        double[] cw = new double[64];
        for (Shape shape : todo) {
            candTiles.clear();
            for (int[] cells : PLACEMENTS[shape.ordinal()]) for (int t0 : unknown) {
                int x0 = t0 % w, z0 = t0 / w;
                int[] tiles = new int[cells.length / 2];
                boolean ok = true;
                int ents = 0;
                for (int c = 0; c < tiles.length; c++) {
                    int x = x0 + cells[c * 2], z = z0 + cells[c * 2 + 1];
                    if (x >= w || z >= h) { ok = false; break; }
                    int t = z * w + x;
                    if (!isUnk[t] || owner[t] >= 0) { ok = false; break; }
                    if (entryDoor[t] != 0) ents++;
                    tiles[c] = t;
                }
                if (!ok || ents > 1) continue;
                if (candTiles.size() == cw.length) cw = Arrays.copyOf(cw, cw.length * 2);
                double wt = 1.0;
                if (w == 6 && h == 6) for (int t : tiles) wt *= MapStats.BIG_TILE[frame[t]];
                cw[candTiles.size()] = wt;
                candTiles.add(tiles);
            }
            if (candTiles.isEmpty()) return false;
            int[] tiles = candTiles.get(pick(cw, candTiles.size(), rnd));
            for (int t : tiles) owner[t] = rooms.size();
            rooms.add(tiles);
            roomShape.add(shape);
        }
        for (int t : unknown) if (owner[t] < 0) { owner[t] = rooms.size(); rooms.add(new int[]{t}); roomShape.add(Shape.ONE); }
        int r = rooms.size();

        @SuppressWarnings("unchecked") List<Integer>[] nbs = new List[r];
        @SuppressWarnings("unchecked") List<List<Integer>>[] slots = new List[r];
        for (int i = 0; i < r; i++) { nbs[i] = new ArrayList<>(); slots[i] = new ArrayList<>(); }
        for (int t : unknown) {
            int[] nts = {t % w + 1 < w ? t + 1 : -1, t / w + 1 < h ? t + w : -1};
            for (int nt : nts) {
                if (nt < 0 || !isUnk[nt] || owner[nt] == owner[t]) continue;
                int a = owner[t], b = owner[nt];
                int ia = nbs[a].indexOf(b);
                if (ia < 0) {
                    nbs[a].add(b); slots[a].add(new ArrayList<>());
                    nbs[b].add(a); slots[b].add(new ArrayList<>());
                    ia = nbs[a].size() - 1;
                }
                slots[a].get(ia).add(t * n + nt);
                slots[b].get(nbs[b].indexOf(a)).add(nt * n + t);
            }
        }

        int[] parent = new int[r];
        Arrays.fill(parent, -2);
        int[] inTile = new int[r], outTile = new int[r], root = new int[r];
        Arrays.fill(root, -1);
        List<Integer> order = new ArrayList<>(r);
        for (int t : entries) {
            int o = owner[t];
            parent[o] = -1; inTile[o] = t; root[o] = entryIndex[t];
            order.add(o);
        }
        while (order.size() < r) {
            int u = -1, v = -1;
            if (rnd.nextDouble() < GROW) {
                int last = order.get(order.size() - 1);
                List<Integer> fr = new ArrayList<>();
                for (int b : nbs[last]) if (parent[b] == -2) fr.add(b);
                if (!fr.isEmpty()) { u = last; v = fr.get(rnd.nextInt(fr.size())); }
            }
            if (u < 0) {
                int cnt = 0;
                for (int a : order) for (int b : nbs[a]) if (parent[b] == -2) {
                    cnt++;
                    if (rnd.nextDouble() * cnt < 1) { u = a; v = b; }
                }
            }
            if (u < 0) return false;
            List<Integer> sl = slots[u].get(nbs[u].indexOf(v));
            int p = sl.get(rnd.nextInt(sl.size()));
            parent[v] = u; outTile[v] = p / n; inTile[v] = p % n; root[v] = root[u];
            order.add(v);
        }
        int[] deg = new int[r];
        for (int i = 0; i < r; i++) { deg[i]++; if (parent[i] >= 0) deg[parent[i]]++; }
        List<Integer> dead = new ArrayList<>();
        for (int i = 0; i < r; i++) if (roomShape.get(i) == Shape.ONE && deg[i] == 1) dead.add(i);
        if (level < 2 && dead.size() != needDead) {
            int extra = dead.size() - needDead;
            if (!(view.puzzles == 5 && extra >= 1 && extra <= 2 && rnd.nextDouble() < MapStats.RARE_RATE)) return false;
        }

        int[] type = new int[r];
        Arrays.fill(type, -1);
        double wgt = 1.0;
        if (found[Cls.BLOOD] == 0) {
            int hb = head >= 0 ? entryIndex[head] : -1;
            int hr = head >= 0 ? owner[head] : -1;
            int[] dist = new int[r];
            Arrays.fill(dist, -1);
            if (hb >= 0) for (int o : order) {
                if (root[o] != hb) continue;
                dist[o] = o == hr ? headDist : dist[parent[o]] + inRoom(rooms.get(parent[o]), inTile[parent[o]], outTile[o]) + 1;
            }
            int[] cand = new int[dead.size()], fair = new int[dead.size()];
            double[] ws = new double[dead.size()];
            int nc = 0;
            boolean bloodDoor = head >= 0 && entryDoor[head] == Edge.BLOOD;
            for (int b : dead) {
                if (level == 0 && hb < 0) continue;
                if (hb >= 0 && root[b] != hb) continue;
                if (bloodDoor != (b == hr) && (bloodDoor || level == 0)) continue;
                int d = dist[b];
                double wb = level == 0 ? getOr(MapStats.WALK, d) : 1.0;
                if (wb <= 0) continue;
                int f = -1;
                if (found[Cls.FAIRY] > 0) {
                    if (level == 0 && d != fairyDist + MapStats.FAIRY_BEFORE_BLOOD) continue;
                } else {
                    int u = parent[b];
                    while (u >= 0) {
                        if (roomShape.get(u) == Shape.ONE && (level == 0 ? dist[u] == d - MapStats.FAIRY_BEFORE_BLOOD : deg[u] >= 2)) { f = u; break; }
                        u = parent[u];
                    }
                    if (f < 0 && level == 0) continue;
                }
                wb *= getOr(MapStats.BLOOD_FROM_ENTRANCE, manhattan(rooms.get(b)[0]));
                if (f >= 0) wb *= getOr(MapStats.FAIRY_FROM_ENTRANCE, manhattan(rooms.get(f)[0]));
                if (wb <= 0) continue;
                cand[nc] = b; fair[nc] = f; ws[nc] = wb; nc++;
            }
            if (nc == 0) {
                if (level < 2) return false;
                if (bloodDoor) type[hr] = Cls.BLOOD;
            } else {
                double total = 0;
                for (int i = 0; i < nc; i++) total += ws[i];
                wgt = level == 0 ? total : 1.0;
                int k = pick(ws, nc, rnd);
                type[cand[k]] = Cls.BLOOD;
                if (fair[k] >= 0) type[fair[k]] = Cls.FAIRY;
            }
        } else if (found[Cls.FAIRY] == 0 && level == 0) return false;

        List<Integer> rest = new ArrayList<>();
        for (int d : dead) if (type[d] < 0) rest.add(d);
        Collections.shuffle(rest, rnd);
        int ri = 0;
        if (found[Cls.TRAP] == 0 && ri < rest.size()) type[rest.get(ri++)] = Cls.TRAP;
        if (found[Cls.YELLOW] == 0 && ri < rest.size()) type[rest.get(ri++)] = Cls.YELLOW;
        for (int i = 0; i < remP; i++) if (ri < rest.size()) type[rest.get(ri++)] = Cls.PUZZLE;
        while (ri < rest.size()) type[rest.get(ri++)] = Cls.RARE;

        int[] cls = new int[r];
        for (int i = 0; i < r; i++) cls[i] = type[i] >= 0 ? type[i] : roomShape.get(i) == Shape.ONE ? Cls.NORMAL : Cls.BIG;
        SecretNames.Result names = null;
        SecretNames sn = secrets;
        if (secretsOn && sn != null && unnamedKnown.size() + r <= SecretNames.MAX_ROOMS) {
            int uk = unnamedKnown.size();
            int[] pools = new int[uk + r];
            for (int i = 0; i < pools.length; i++) pools[i] = i < uk ? unnamedKnown.get(i) : poolOfCls(cls[i - uk], roomShape.get(i - uk));
            names = sn.solve(pools);
            if (names != null) {
                if (names.likelihood() <= 0.0) return false;
                wgt *= names.likelihood();
            }
        }

        weight += wgt;
        int blood = -1;
        for (int i = 0; i < r; i++) if (type[i] == Cls.BLOOD) { blood = i; break; }
        boolean[] onPath = new boolean[r];
        if (blood >= 0) { int u = blood; while (u >= 0) { onPath[u] = true; u = parent[u]; } }
        for (int v = 0; v < r; v++) {
            int u = parent[v];
            if (u < 0) continue;
            int a = Math.min(outTile[v], inTile[v]), b = Math.max(outTile[v], inTile[v]);
            double[] d = doorCounts.computeIfAbsent(a * n + b, k -> new double[4]);
            d[0] += wgt;
            if (onPath[u] && onPath[v]) { if (v == blood) d[2] += wgt; else d[1] += wgt; }
            if (type[u] == Cls.FAIRY || type[v] == Cls.FAIRY) d[3] += wgt;
        }
        for (int o = 0; o < r; o++) {
            int c = cls[o];
            if (names != null && sn != null) {
                long m = 0L;
                for (int t : rooms.get(o)) m |= 1L << t;
                int pool = poolOfCls(c, roomShape.get(o));
                double[] shares = names.byPool().get(pool);
                if (shares != null) for (int k = 0; k < shares.length; k++) {
                    if (shares[k] > 0) nameCounts.merge(m * 256 + NAME_INDEX.get(sn.avail[pool].get(k)), wgt * shares[k], Double::sum);
                }
            }
            int b = root[o];
            if (b >= 0) { splitRooms[b] += wgt; splitTiles[b] += wgt * rooms.get(o).length; splitCls[b][c] += wgt; }
            for (int t : rooms.get(o)) counts[tileIdx[t]][c] += wgt;
            if (roomShape.get(o) != Shape.ONE && n <= 64) {
                long m = 0L;
                for (int t : rooms.get(o)) m |= 1L << t;
                bigCounts.merge(m, wgt, Double::sum);
            }
        }
        return true;
    }

    private int manhattan(int t) {
        return entTile < 0 ? 0 : Math.abs(t % w - entTile % w) + Math.abs(t / w - entTile / w);
    }

    private int[] maskTiles(long m) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) if (((m >> i) & 1L) == 1L) l.add(i);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    public Prediction prediction() {
        if (accepted == 0 || weight <= 0) return null;
        float[][] probs = new float[n][];
        for (int i = 0; i < unknown.length; i++) {
            double[] c = counts[i];
            double s = 0;
            for (double v : c) s += v;
            if (s > 0) {
                float[] p = new float[Cls.COUNT];
                for (int k = 0; k < Cls.COUNT; k++) p[k] = (float) (c[k] / s);
                probs[unknown[i]] = p;
            }
        }
        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            float[] ex = new float[Cls.COUNT];
            for (int k = 0; k < Cls.COUNT; k++) ex[k] = (float) (splitCls[i][k] / weight);
            splits.add(new Split(entries[i], entryDoor[entries[i]], (float) (splitRooms[i] / weight), (float) (splitTiles[i] / weight), ex));
        }
        List<BigRoom> big = new ArrayList<>();
        bigCounts.entrySet().stream().filter(e -> e.getValue() / weight >= 0.05)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> big.add(new BigRoom(maskTiles(e.getKey()), (float) (e.getValue() / weight))));
        List<NamedRoom> named = new ArrayList<>();
        nameCounts.entrySet().stream().filter(e -> e.getValue() / weight >= 0.001)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> named.add(new NamedRoom(maskTiles(e.getKey() / 256), MapStats.NAMES.get((int) (e.getKey() % 256)), (float) (e.getValue() / weight))));
        List<PredictedDoor> doors = new ArrayList<>();
        doorCounts.entrySet().stream().filter(e -> e.getValue()[0] / weight >= 0.05)
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> {
                    double[] d = e.getValue();
                    doors.add(new PredictedDoor(e.getKey() / n, e.getKey() % n, (float) (d[0] / weight), (float) (d[1] / weight), (float) (d[2] / weight), (float) (d[3] / weight)));
                });
        return new Prediction(w, h, probs, splits, accepted, level, big, named, doors);
    }
}
