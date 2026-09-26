package com.teslamaps.features.events;

// Ported from SkyHanni's FruitDiggingSolver.kt and FruitDiggingBelief.kt
import com.teslamaps.features.events.FruitDigging.Fruit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Monte-Carlo belief over the hidden cells, then scores each diggable cell by expected points minus risk. */
final class FruitDiggingSolver {

    record CellInput(Fruit content, boolean diggable, boolean ghost, Integer mines, Fruit treasure, Fruit anchor) {}

    record Recommendation(int row, int col, String shovel, double expected, double pBomb, double pRum, int samples) {}

    private static final double RISK_WEIGHT = 1.0;
    private static final double DESTRUCTION_WEIGHT = 0.10;
    private static final double SETUP_WEIGHT = 0.5;
    private static final double INFO_WEIGHT = 15.0;
    private static final int TARGET_SAMPLES = 500;
    private static final int MAX_ATTEMPTS = 20_000;

    private final int size;
    private final int[][] neighbors;
    private final Random random = new Random();

    FruitDiggingSolver(int size) {
        this.size = size;
        neighbors = new int[size * size][];
        for (int id = 0; id < size * size; id++) {
            int row = id / size, col = id % size;
            List<Integer> list = new ArrayList<>();
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int r = row + dr, c = col + dc;
                    if (r >= 0 && r < size && c >= 0 && c < size) list.add(r * size + c);
                }
            }
            neighbors[id] = list.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    static int rank(Fruit f) {
        return f == Fruit.APPLE ? 0 : f.points;
    }

    private static boolean pooled(Fruit f) {
        return f.edible || f == Fruit.BOMB || f == Fruit.RUM;
    }

    Recommendation recommend(CellInput[][] grid, int digsUsed, double nextMultiplier, boolean coconutProtection) {
        return new Advisor(grid, digsUsed, nextMultiplier, coconutProtection).recommend();
    }

    // ---- clues ----

    private sealed interface Clue permits BombCount, FruitFloor, FruitCeil, NoFruit, MustFruit {}
    private record BombCount(int[] cells, int bombs) implements Clue {}
    private record FruitFloor(int[] cells, int minRank, Fruit named, boolean satisfied) implements Clue {}
    private record FruitCeil(int[] cells, int maxRank, Fruit named, boolean satisfied) implements Clue {}
    private record NoFruit(int[] cells) implements Clue {}
    private record MustFruit(int[] cells) implements Clue {}

    private final class Advisor {
        final int digsUsed;
        final double nextMultiplier;
        final boolean coconutProtection;
        final Fruit[] known = new Fruit[size * size];
        final boolean[] diggable = new boolean[size * size];
        final boolean[] ghost = new boolean[size * size];
        final List<Integer> candidates = new ArrayList<>();
        final List<Integer> openCells = new ArrayList<>();
        final Set<Integer> openOnBoard = new HashSet<>();
        int apples, cherries;
        final Map<Integer, Map<Fruit, Double>> beliefs;
        int samples;

        Advisor(CellInput[][] grid, int digsUsed, double nextMultiplier, boolean coconutProtection) {
            this.digsUsed = digsUsed;
            this.nextMultiplier = nextMultiplier;
            this.coconutProtection = coconutProtection;
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    int id = row * size + col;
                    CellInput cell = grid[row][col];
                    Fruit content = cell.content() == null || cell.content() == Fruit.UNKNOWN || cell.content() == Fruit.NO_FRUIT ? null : cell.content();
                    known[id] = content;
                    diggable[id] = cell.diggable();
                    ghost[id] = cell.ghost() && content == null;
                    if (cell.diggable()) {
                        candidates.add(id);
                        if (content == null) {
                            openCells.add(id);
                            openOnBoard.add(id);
                        }
                    } else if (content == null) {
                        openCells.add(id);
                    }
                    if (content != null && !cell.diggable()) {
                        if (content == Fruit.APPLE) apples++;
                        if (content == Fruit.CHERRY) cherries++;
                    }
                }
            }
            beliefs = estimate(remainingBag(), clues(grid));
        }

        List<Fruit> remainingBag() {
            Map<Fruit, Integer> counts = new EnumMap<>(Fruit.class);
            for (Fruit f : Fruit.values()) if (pooled(f)) counts.put(f, f.count);
            for (Fruit f : known) if (f != null) counts.merge(f, -1, Integer::sum);
            List<Fruit> out = new ArrayList<>();
            for (Map.Entry<Fruit, Integer> e : counts.entrySet()) for (int i = 0; i < e.getValue(); i++) out.add(e.getKey());
            return out;
        }

        List<Clue> clues(CellInput[][] grid) {
            List<Clue> out = new ArrayList<>();
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    int id = row * size + col;
                    CellInput cell = grid[row][col];
                    List<Integer> hidden = new ArrayList<>();
                    for (int nb : neighbors[id]) if (openOnBoard.contains(nb)) hidden.add(nb);
                    int[] h = hidden.stream().mapToInt(Integer::intValue).toArray();
                    if (cell.mines() != null) out.add(new BombCount(h, cell.mines()));
                    if (cell.treasure() != null) out.add(order(id, h, cell.treasure(), false));
                    if (cell.anchor() != null) out.add(order(id, h, cell.anchor(), true));
                    if (ghost[id]) out.add(new MustFruit(new int[]{id}));
                }
            }
            return out;
        }

        Clue order(int center, int[] hidden, Fruit named, boolean anchor) {
            if (named == Fruit.NO_FRUIT) return new NoFruit(hidden);
            boolean satisfied = false;
            for (int nb : neighbors[center]) if (known[nb] == named && diggable[nb]) satisfied = true;
            return anchor ? new FruitFloor(hidden, rank(named), named, satisfied) : new FruitCeil(hidden, rank(named), named, satisfied);
        }

        Map<Integer, Map<Fruit, Double>> estimate(List<Fruit> pool, List<Clue> clues) {
            Map<Integer, Map<Fruit, Double>> out = new HashMap<>();
            if (openCells.isEmpty() || openCells.size() != pool.size()) {
                samples = 0;
                if (!openCells.isEmpty() && !pool.isEmpty()) {
                    Map<Fruit, Double> m = baseRate(pool);
                    for (int c : openCells) out.put(c, m);
                }
                return out;
            }
            if (clues.isEmpty()) {
                samples = TARGET_SAMPLES;
                Map<Fruit, Double> m = baseRate(pool);
                for (int c : openCells) out.put(c, m);
                return out;
            }
            Map<Integer, Integer> slotOf = new HashMap<>();
            for (int i = 0; i < openCells.size(); i++) slotOf.put(openCells.get(i), i);
            List<int[]> slots = new ArrayList<>();
            for (Clue c : clues) slots.add(toSlots(c, slotOf));

            Fruit[] deal = pool.toArray(new Fruit[0]);
            int n = Fruit.values().length;
            int[][] hits = new int[deal.length][n];
            int accepted = 0, attempts = 0;
            while (accepted < TARGET_SAMPLES && attempts < MAX_ATTEMPTS) {
                attempts++;
                for (int i = deal.length - 1; i > 0; i--) {
                    int j = random.nextInt(i + 1);
                    Fruit t = deal[i];
                    deal[i] = deal[j];
                    deal[j] = t;
                }
                boolean ok = true;
                for (int i = 0; i < clues.size() && ok; i++) ok = satisfies(clues.get(i), slots.get(i), deal);
                if (!ok) continue;
                accepted++;
                for (int s = 0; s < deal.length; s++) hits[s][deal[s].ordinal()]++;
            }
            samples = accepted;
            if (accepted == 0) {
                Map<Fruit, Double> m = baseRate(pool);
                for (int c : openCells) out.put(c, m);
                return out;
            }
            Fruit[] all = Fruit.values();
            for (int s = 0; s < openCells.size(); s++) {
                Map<Fruit, Double> m = new EnumMap<>(Fruit.class);
                for (int k = 0; k < n; k++) if (hits[s][k] > 0) m.put(all[k], hits[s][k] / (double) accepted);
                out.put(openCells.get(s), m);
            }
            return out;
        }

        int[] toSlots(Clue clue, Map<Integer, Integer> slotOf) {
            int[] cells = switch (clue) {
                case BombCount b -> b.cells();
                case FruitFloor f -> f.cells();
                case FruitCeil f -> f.cells();
                case NoFruit f -> f.cells();
                case MustFruit f -> f.cells();
            };
            List<Integer> out = new ArrayList<>();
            for (int c : cells) {
                Integer s = slotOf.get(c);
                if (s != null) out.add(s);
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }

        boolean satisfies(Clue clue, int[] slots, Fruit[] deal) {
            switch (clue) {
                case BombCount b -> {
                    int bombs = 0;
                    for (int s : slots) if (deal[s] == Fruit.BOMB) bombs++;
                    return bombs == b.bombs();
                }
                case NoFruit f -> {
                    for (int s : slots) if (deal[s].edible) return false;
                    return true;
                }
                case FruitFloor f -> {
                    boolean found = f.satisfied();
                    for (int s : slots) {
                        Fruit x = deal[s];
                        if (!x.edible) continue;
                        if (rank(x) < f.minRank()) return false;
                        if (x == f.named()) found = true;
                    }
                    return found;
                }
                case FruitCeil f -> {
                    boolean found = f.satisfied();
                    for (int s : slots) {
                        Fruit x = deal[s];
                        if (!x.edible) continue;
                        if (rank(x) > f.maxRank()) return false;
                        if (x == f.named()) found = true;
                    }
                    return found;
                }
                case MustFruit f -> {
                    return slots.length == 0 || deal[slots[0]].edible;
                }
            }
        }

        Map<Fruit, Double> baseRate(List<Fruit> pool) {
            Map<Fruit, Double> m = new EnumMap<>(Fruit.class);
            for (Fruit f : pool) m.merge(f, 1.0 / pool.size(), Double::sum);
            return m;
        }

        double pointValue(Fruit f) {
            return switch (f) {
                case APPLE -> 100.0 * (apples + 1);
                case CHERRY -> 200.0 + 300.0 * (cherries + 1);
                case BOMB, RUM, UNKNOWN, NO_FRUIT -> 0.0;
                default -> f.points;
            };
        }

        Map<Fruit, Double> dist(int cell) {
            Map<Fruit, Double> m = beliefs.get(cell);
            return m == null ? Map.of() : m;
        }

        double prob(int cell, Fruit f) {
            if (known[cell] != null) return known[cell] == f ? 1.0 : 0.0;
            return dist(cell).getOrDefault(f, 0.0);
        }

        double expected(int cell) {
            double raw;
            if (known[cell] != null) raw = withMelon(known[cell], cell);
            else {
                raw = 0;
                for (Map.Entry<Fruit, Double> e : dist(cell).entrySet()) raw += e.getValue() * withMelon(e.getKey(), cell);
            }
            return raw * nextMultiplier;
        }

        double withMelon(Fruit f, int cell) {
            if (f != Fruit.WATERMELON) return pointValue(f);
            double nearby = nearbyValue(cell);
            return pointValue(f) + 0.5 * (nearby > 0 ? nearby : avgRemaining());
        }

        double nearbyValue(int cell) {
            double value = 0, weight = 0;
            for (int nb : neighbors[cell]) {
                if (!diggable[nb]) continue;
                if (known[nb] != null) {
                    if (known[nb].edible) {
                        value += pointValue(known[nb]);
                        weight += 1;
                    }
                } else {
                    for (Map.Entry<Fruit, Double> e : dist(nb).entrySet()) {
                        if (!e.getKey().edible) continue;
                        value += e.getValue() * pointValue(e.getKey());
                        weight += e.getValue();
                    }
                }
            }
            return weight > 0 ? value / weight : 0;
        }

        double avgRemaining() {
            int count = 0;
            double value = 0;
            for (Fruit f : Fruit.values()) {
                if (!f.edible) continue;
                int n = f.count;
                for (Fruit k : known) if (k == f) n--;
                count += n;
                value += pointValue(f) * n;
            }
            return count > 0 ? value / count : 0;
        }

        double atRisk(int cell) {
            double total = 0;
            for (int nb : neighbors[cell]) {
                if (!diggable[nb]) continue;
                if (known[nb] != null) {
                    if (known[nb].edible) total += pointValue(known[nb]);
                } else {
                    for (Map.Entry<Fruit, Double> e : dist(nb).entrySet()) if (e.getKey().edible) total += e.getValue() * pointValue(e.getKey());
                }
            }
            return total;
        }

        double opportunityCost() {
            double best = 0;
            for (int c : candidates) if (prob(c, Fruit.BOMB) < 0.05 && prob(c, Fruit.RUM) < 0.05) best = Math.max(best, expected(c));
            return best > 0 ? best : avgRemaining();
        }

        double score(int cell, double opp) {
            double risk = (prob(cell, Fruit.BOMB) + prob(cell, Fruit.RUM)) * opp * RISK_WEIGHT;
            if (!coconutProtection) risk += prob(cell, Fruit.BOMB) * DESTRUCTION_WEIGHT * atRisk(cell);
            double setup = (prob(cell, Fruit.POMEGRANATE) - prob(cell, Fruit.DURIAN)) * SETUP_WEIGHT * opp;
            int hidden = 0;
            for (int nb : neighbors[cell]) if (openOnBoard.contains(nb)) hidden++;
            return expected(cell) - risk + setup + hidden * INFO_WEIGHT;
        }

        String shovel(int cell) {
            double fruit = 0;
            for (int nb : neighbors[cell]) {
                if (!diggable[nb]) continue;
                if (known[nb] != null) {
                    if (known[nb].edible) fruit += 1;
                } else {
                    for (Map.Entry<Fruit, Double> e : dist(nb).entrySet()) if (e.getKey().edible) fruit += e.getValue();
                }
            }
            return fruit >= 0.25 ? "Anchor" : "Mines";
        }

        Recommendation recommend() {
            if (candidates.isEmpty()) return null;
            double opp = opportunityCost();
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int c : candidates) {
                double s = score(c, opp);
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
            if (nextMultiplier < 1.0 && FruitDigging.MAX_DIGS - digsUsed > 1) {
                int absorber = -1;
                double low = Double.POSITIVE_INFINITY;
                for (int c : candidates) {
                    if (prob(c, Fruit.BOMB) >= 0.15 || prob(c, Fruit.RUM) >= 0.15) continue;
                    double e = expected(c);
                    if (e < low) {
                        low = e;
                        absorber = c;
                    }
                }
                if (absorber >= 0) best = absorber;
            }
            return new Recommendation(best / size, best % size, shovel(best), expected(best),
                    prob(best, Fruit.BOMB), prob(best, Fruit.RUM), samples);
        }
    }
}
