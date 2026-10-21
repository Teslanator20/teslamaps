package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/PracticeCode.kt

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PracticeCode {
    private PracticeCode() {}

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!$%&()+-./=?[]^{}";
    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length());
    private static final int VERSION = 0;

    private static final double X0 = -8.0;
    private static final double Y0 = 0.0;
    private static final double Z0 = -8.0;
    private static final int POS_BITS = 13;
    private static final int YAW_BITS = 11;
    private static final int PITCH_BITS = 10;

    public static class Custom {
        public final Spots.Spot start;
        public final Set<String> jobs;
        public final List<Spots.Spot> checkpoints;
        public final List<Spots.Spot> leaps;
        public final double delay;

        public Custom(Spots.Spot start, Set<String> jobs, List<Spots.Spot> checkpoints, List<Spots.Spot> leaps, double delay) {
            this.start = start;
            this.jobs = jobs;
            this.checkpoints = checkpoints;
            this.leaps = leaps;
            this.delay = delay;
        }
    }

    public static String encode(Custom c) {
        Bits w = new Bits();
        w.put(VERSION, 2);
        w.put(c.start != null ? 1 : 0, 1);
        if (c.start != null) w.spot(c.start, true);
        w.put(Math.clamp((int) Math.round(c.delay * 10), 0, 63), 6);
        List<String> all = P3Plan.allJobs();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) if (c.jobs.contains(all.get(i))) idx.add(i);
        int width = bitsFor(all.size());
        Bits asList = new Bits();
        asList.gamma(idx.size() + 1);
        for (int it : idx) asList.put(it, width);
        if (asList.size() < all.size()) { w.put(1, 1); w.append(asList); }
        else { w.put(0, 1); for (String it : all) w.put(c.jobs.contains(it) ? 1 : 0, 1); }
        w.gamma(c.checkpoints.size() + 1);
        for (Spots.Spot it : c.checkpoints) w.spot(it, false);
        w.put(Math.min(c.leaps.size(), 4), 3);
        for (int i = 0; i < Math.min(4, c.leaps.size()); i++) w.spot(c.leaps.get(i), true);
        BigInteger n = BigInteger.ONE;
        for (boolean b : w.bits) n = n.shiftLeft(1).or(b ? BigInteger.ONE : BigInteger.ZERO);
        StringBuilder out = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(BASE);
            out.append(ALPHABET.charAt(qr[1].intValue()));
            n = qr[0];
        }
        return out.reverse().toString();
    }

    public static Custom decode(String code) {
        try {
            BigInteger n = BigInteger.ZERO;
            for (int ci = 0; ci < code.length(); ci++) {
                char ch = code.charAt(ci);
                if (Character.isWhitespace(ch) || Character.isSpaceChar(ch)) continue;
                int d = ALPHABET.indexOf(ch);
                if (d < 0) throw new IllegalArgumentException("Failed requirement.");
                n = n.multiply(BASE).add(BigInteger.valueOf(d));
            }
            if (n.bitLength() < 2) throw new IllegalArgumentException("Failed requirement.");
            List<Boolean> bits = new ArrayList<>();
            for (int i = n.bitLength() - 2; i >= 0; i--) bits.add(n.testBit(i));
            Reader r = new Reader(bits);
            if (r.get(2) != VERSION) throw new IllegalArgumentException("Failed requirement.");
            Spots.Spot start = r.get(1) == 1 ? r.spot("Start", true) : null;
            double delay = r.get(6) / 10.0;
            List<String> all = P3Plan.allJobs();
            Set<String> jobs = new LinkedHashSet<>();
            if (r.get(1) == 1) {
                int cnt = r.count();
                for (int i = 0; i < cnt; i++) jobs.add(all.get(r.get(bitsFor(all.size()))));
            } else {
                for (String it : all) if (r.get(1) == 1) jobs.add(it);
            }
            int cpCount = r.count();
            List<Spots.Spot> cps = new ArrayList<>();
            for (int i = 0; i < cpCount; i++) cps.add(r.spot("Checkpoint " + (i + 1), false));
            int leapCount = Math.min(r.get(3), 4);
            List<Spots.Spot> leaps = new ArrayList<>();
            for (int i = 0; i < leapCount; i++) leaps.add(r.spot("Leap " + (i + 1), true));
            return new Custom(start, jobs, cps, leaps, Math.clamp(delay, 0.0, 5.0));
        } catch (Throwable t) {
            return null;
        }
    }

    private static int bitsFor(int n) { return 32 - Integer.numberOfLeadingZeros(Math.max(n - 1, 1)); }

    private static final class Bits {
        final ArrayList<Boolean> bits = new ArrayList<>();

        int size() { return bits.size(); }

        void put(int v, int n) { for (int i = n - 1; i >= 0; i--) bits.add(((v >> i) & 1) == 1); }

        void append(Bits o) { bits.addAll(o.bits); }

        void gamma(int v) { int n = 32 - Integer.numberOfLeadingZeros(v); put(0, n - 1); put(v, n); }

        void pos(double v, double o) { put(Math.clamp((int) Math.round((v - o) * 32), 0, (1 << POS_BITS) - 1), POS_BITS); }

        void spot(Spots.Spot s, boolean facing) {
            pos(s.x, X0); pos(s.y, Y0); pos(s.z, Z0);
            if (!facing) return;
            put(Math.floorMod(Math.round(s.yaw * 4), 1440), YAW_BITS);
            put(Math.round((Math.clamp(s.pitch, -90f, 90f) + 90f) * 4), PITCH_BITS);
        }
    }

    private static final class Reader {
        final List<Boolean> bits;
        int i = 0;

        Reader(List<Boolean> bits) { this.bits = bits; }

        int get(int n) {
            int v = 0;
            for (int k = 0; k < n; k++) v = (v << 1) | (bits.get(i++) ? 1 : 0);
            return v;
        }

        int gamma() {
            int z = 0;
            while (!bits.get(i)) { z++; i++; }
            if (z >= 31) throw new IllegalArgumentException("Failed requirement.");
            return get(z + 1);
        }

        int count() {
            int c = gamma() - 1;
            if (c > bits.size() - i) throw new IllegalArgumentException("Failed requirement.");
            return c;
        }

        double pos(double o) { return get(POS_BITS) / 32.0 + o; }

        Spots.Spot spot(String name, boolean facing) {
            double x = pos(X0), y = pos(Y0), z = pos(Z0);
            if (!facing) return new Spots.Spot(name, x, y, z, 0f);
            float yaw = get(YAW_BITS) / 4f;
            float pitch = get(PITCH_BITS) / 4f - 90f;
            return new Spots.Spot(name, x, y, z, yaw > 180f ? yaw - 360f : yaw, pitch);
        }
    }
}
