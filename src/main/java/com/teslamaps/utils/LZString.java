package com.teslamaps.utils;

import java.util.ArrayList;
import java.util.List;

/** lz-string decompressFromEncodedURIComponent, as used by SkyMutations links. Ported from Skyblocker's LZString. */
public final class LZString {

    private static final String URI_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$";

    private LZString() {}

    public static String decompressFromEncodedURIComponent(String input) {
        if (input == null || input.isEmpty()) return null;
        String s = input.replace(' ', '+');
        int[] values = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            int v = URI_SAFE.indexOf(s.charAt(i));
            if (v < 0) return null;
            values[i] = v;
        }
        return decompress(values, 32);
    }

    private static final class Reader {
        final int[] src;
        final int reset;
        int val, pos, index;

        Reader(int[] src, int reset) {
            this.src = src;
            this.reset = reset;
            this.val = src[0];
            this.pos = reset;
            this.index = 1;
        }

        int read(int n) {
            int bits = 0;
            for (int p = 0; p < n; p++) {
                int b = val & pos;
                pos >>= 1;
                if (pos == 0) {
                    pos = reset;
                    val = index < src.length ? src[index] : 0;
                    index++;
                }
                if (b > 0) bits |= 1 << p;
            }
            return bits;
        }
    }

    private static String decompress(int[] src, int reset) {
        Reader r = new Reader(src, reset);
        List<String> dict = new ArrayList<>(List.of("0", "1", "2"));
        int enlargeIn = 4, dictSize = 4, numBits = 3;

        String c = switch (r.read(2)) {
            case 0 -> String.valueOf((char) r.read(8));
            case 1 -> String.valueOf((char) r.read(16));
            default -> null;
        };
        if (c == null) return "";
        dict.add(c);
        String w = c;
        StringBuilder result = new StringBuilder(w);

        while (true) {
            if (r.index > src.length) return "";
            int cc = r.read(numBits);
            if (cc == 0 || cc == 1) {
                dict.add(String.valueOf((char) r.read(cc == 0 ? 8 : 16)));
                cc = dictSize++;
                enlargeIn--;
            } else if (cc == 2) {
                return result.toString();
            }
            if (enlargeIn == 0) {
                enlargeIn = 1 << numBits;
                numBits++;
            }

            String entry;
            if (cc < dict.size()) entry = dict.get(cc);
            else if (cc == dictSize) entry = w + w.charAt(0);
            else return null;
            result.append(entry);

            dict.add(w + entry.charAt(0));
            dictSize++;
            enlargeIn--;
            w = entry;

            if (enlargeIn == 0) {
                enlargeIn = 1 << numBits;
                numBits++;
            }
        }
    }
}
