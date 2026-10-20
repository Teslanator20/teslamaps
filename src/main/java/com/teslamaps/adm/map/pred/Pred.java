package com.teslamaps.adm.map.pred;

// Ported from ADM's mappred/MapPredictor.kt (the model types)
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;

public final class Pred {
    private Pred() {}

    public enum Kind { NORMAL, ENTRANCE, PUZZLE, TRAP, YELLOW, BLOOD, FAIRY, RARE }

    public enum Shape {
        TWO(2), THREE(3), FOUR(4), BOX(4), L(3), ONE(1);
        public final int size;
        Shape(int size) { this.size = size; }
    }

    public static final class Cls {
        public static final int BIG = 0, NORMAL = 1, PUZZLE = 2, TRAP = 3, YELLOW = 4, BLOOD = 5, FAIRY = 6, RARE = 7, COUNT = 8;
        private Cls() {}
    }

    public static final class Edge {
        public static final byte UNKNOWN = 0, NONE = 1, DOOR = 2, WITHER = 3, BLOOD = 4, SAME = 5;
        private Edge() {}
        public static boolean isDoor(byte k) { return k == DOOR || k == WITHER || k == BLOOD; }
    }

    public static final class MapView {
        public final int w, h, puzzles;
        public final int[] roomAt;
        public final List<Kind> kinds = new ArrayList<>();
        public final List<Shape> shapes = new ArrayList<>();
        public final List<int[]> roomTiles = new ArrayList<>();
        public final List<String> names = new ArrayList<>();
        public int[] secretTotals = new int[0];
        private final byte[] edges;

        public MapView(int w, int h, int puzzles) {
            this.w = w; this.h = h; this.puzzles = puzzles;
            roomAt = new int[w * h];
            Arrays.fill(roomAt, -1);
            edges = new byte[w * h * 2];
        }

        /** Null when the tiles aren't a room shape. */
        public Integer addRoom(Kind kind, int[] tiles, String name) {
            Shape shape = shapeOf(tiles, w);
            if (shape == null) return null;
            for (int t : tiles) if (t < 0 || t >= w * h || roomAt[t] >= 0) return null;
            int id = kinds.size();
            kinds.add(kind); shapes.add(shape); roomTiles.add(tiles.clone()); names.add(name);
            for (int t : tiles) roomAt[t] = id;
            return id;
        }

        private int edgeId(int a, int b) {
            int d = b - a;
            if (d == 1) return a * 2;
            if (d == w) return a * 2 + 1;
            if (d == -1) return b * 2;
            return b * 2 + 1;
        }

        public byte edge(int a, int b) { return edges[edgeId(a, b)]; }
        public void setEdge(int a, int b, byte k) { edges[edgeId(a, b)] = k; }

        public void closeEdges(BiFunction<Integer, Integer, Byte> doorAt) {
            for (int t = 0; t < w * h; t++) {
                int[] ns = {t % w + 1 < w ? t + 1 : -1, t / w + 1 < h ? t + w : -1};
                for (int n : ns) {
                    if (n < 0) continue;
                    int a = roomAt[t], b = roomAt[n];
                    byte k;
                    if (a < 0 && b < 0) k = Edge.UNKNOWN;
                    else if (a == b) k = Edge.SAME;
                    else {
                        Byte d = doorAt.apply(t, n);
                        k = d != null ? d : Edge.NONE;
                    }
                    setEdge(t, n, k);
                }
            }
        }

        public String key() {
            StringBuilder sb = new StringBuilder();
            sb.append(w).append('x').append(h).append(':').append(puzzles).append(':');
            for (int i = 0; i < kinds.size(); i++) {
                sb.append(kinds.get(i).ordinal());
                int[] t = roomTiles.get(i);
                for (int j = 0; j < t.length; j++) { if (j > 0) sb.append(','); sb.append(t[j]); }
                String n = names.get(i);
                sb.append(n == null ? "" : n).append(';');
            }
            for (int i = 0; i < secretTotals.length; i++) { if (i > 0) sb.append(','); sb.append(secretTotals[i]); }
            sb.append(':');
            for (byte e : edges) sb.append(e);
            return sb.toString();
        }

        public static Shape shapeOf(int[] tiles, int w) {
            if (tiles.length == 0) return null;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            HashSet<Integer> set = new HashSet<>();
            for (int t : tiles) {
                set.add(t);
                minX = Math.min(minX, t % w); maxX = Math.max(maxX, t % w);
                minZ = Math.min(minZ, t / w); maxZ = Math.max(maxZ, t / w);
            }
            int bw = maxX - minX + 1, bh = maxZ - minZ + 1;
            if (set.size() != tiles.length) return null;
            return switch (tiles.length) {
                case 1 -> Shape.ONE;
                case 2 -> bw * bh == 2 ? Shape.TWO : null;
                case 3 -> bw == 2 && bh == 2 ? Shape.L : bw * bh == 3 ? Shape.THREE : null;
                case 4 -> bw == 2 && bh == 2 ? Shape.BOX : bw * bh == 4 ? Shape.FOUR : null;
                default -> null;
            };
        }
    }

    public record Split(int tile, byte door, float rooms, float tiles, float[] expect) {}

    public record BigRoom(int[] tiles, float chance) {}

    public record PredictedDoor(int a, int b, float chance, float wither, float blood, float fairy) {}

    public record NamedRoom(int[] tiles, RoomName name, float chance) {}

    public record RoomName(String name, int pool, int secrets, int seen) {}

    public record Prediction(int w, int h, float[][] probs, List<Split> splits, int samples, int level,
                             List<BigRoom> bigRooms, List<NamedRoom> namedRooms, List<PredictedDoor> doors) {}
}
