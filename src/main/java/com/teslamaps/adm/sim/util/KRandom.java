package com.teslamaps.adm.sim.util;

import java.util.List;

/** Kotlin's kotlin.random.Random (XorWow), so a seed gives the same draws as ADM. */
public class KRandom {
    public static final KRandom DEFAULT = new KRandom(System.nanoTime()) {
        @Override public synchronized int nextInt() { return super.nextInt(); }
    };

    private int x, y, z, w, v, addend;

    public KRandom(long seed) { this((int) seed, (int) (seed >> 32)); }

    public static KRandom ofInt(int seed) { return new KRandom(seed, seed >> 31); }

    private KRandom(int seed1, int seed2) {
        x = seed1; y = seed2; z = 0; w = 0; v = ~seed1;
        addend = (seed1 << 10) ^ (seed2 >>> 4);
        if ((x | y | z | w | v) == 0) throw new IllegalArgumentException("Initial state must have at least one non-zero element.");
        for (int i = 0; i < 64; i++) nextInt();
    }

    public int nextInt() {
        int t = x;
        t = t ^ (t >>> 2);
        x = y; y = z; z = w;
        int v0 = v;
        w = v0;
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
        v = t;
        addend += 362437;
        return t + addend;
    }

    public int nextBits(int bitCount) {
        return (nextInt() >>> (32 - bitCount)) & (-bitCount >> 31);
    }

    public int nextInt(int until) { return nextInt(0, until); }

    public int nextInt(int from, int until) {
        if (until <= from) throw new IllegalArgumentException("Random range is empty: [" + from + ", " + until + ").");
        int n = until - from;
        if (n > 0 || n == Integer.MIN_VALUE) {
            int rnd;
            if ((n & -n) == n) {
                rnd = nextBits(31 - Integer.numberOfLeadingZeros(n));
            } else {
                int bits, val;
                do {
                    bits = nextInt() >>> 1;
                    val = bits % n;
                } while (bits - val + (n - 1) < 0);
                rnd = val;
            }
            return from + rnd;
        }
        while (true) {
            int rnd = nextInt();
            if (rnd >= from && rnd < until) return rnd;
        }
    }

    public long nextLong() { return ((long) nextInt() << 32) + nextInt(); }

    public long nextLong(long until) { return nextLong(0, until); }

    public long nextLong(long from, long until) {
        if (until <= from) throw new IllegalArgumentException("Random range is empty: [" + from + ", " + until + ").");
        long n = until - from;
        if (n > 0) {
            long rnd;
            if ((n & -n) == n) {
                int nLow = (int) n;
                int nHigh = (int) (n >>> 32);
                if (nLow != 0) {
                    int bitCount = 31 - Integer.numberOfLeadingZeros(nLow);
                    rnd = nextBits(bitCount) & 0xFFFFFFFFL;
                } else if (nHigh == 1) {
                    rnd = nextInt() & 0xFFFFFFFFL;
                } else {
                    int bitCount = 31 - Integer.numberOfLeadingZeros(nHigh);
                    rnd = ((long) nextBits(bitCount) << 32) + (nextInt() & 0xFFFFFFFFL);
                }
            } else {
                long bits, val;
                do {
                    bits = nextLong() >>> 1;
                    val = bits % n;
                } while (bits - val + (n - 1) < 0);
                rnd = val;
            }
            return from + rnd;
        }
        while (true) {
            long rnd = nextLong();
            if (rnd >= from && rnd < until) return rnd;
        }
    }

    public boolean nextBoolean() { return nextBits(1) != 0; }

    public double nextDouble() {
        long hi = nextBits(26), lo = nextBits(27);
        return ((hi << 27) + lo) / (double) (1L << 53);
    }

    public double nextDouble(double until) { return nextDouble(0.0, until); }

    public double nextDouble(double from, double until) {
        if (!(until > from)) throw new IllegalArgumentException("Random range is empty: [" + from + ", " + until + ").");
        double size = until - from;
        double r;
        if (Double.isInfinite(size) && Double.isFinite(from) && Double.isFinite(until)) {
            double r1 = nextDouble() * (until / 2 - from / 2);
            r = from + r1 + r1;
        } else {
            r = from + nextDouble() * size;
        }
        return r >= until ? Math.nextDown(until) : r;
    }

    public float nextFloat() { return nextBits(24) / (float) (1 << 24); }

    public static <T> void shuffle(List<T> list, KRandom r) {
        for (int i = list.size() - 1; i >= 1; i--) {
            int j = r.nextInt(i + 1);
            list.set(j, list.set(i, list.get(j)));
        }
    }

    public static <T> T pick(List<T> list, KRandom r) {
        if (list.isEmpty()) throw new java.util.NoSuchElementException("Collection is empty.");
        return list.get(r.nextInt(list.size()));
    }
}
