// Ported from ADM's p3sim/pack/rooms/RoomVolume.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.PackFormatException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Decoder for the {@code rooms.blocks.bin} blobs. Pure functions; every malformed input is a {@link PackFormatException}. */
public final class RoomBlobs {
    private RoomBlobs() {}

    /** Upper bound on a decoded volume so a corrupt header cannot ask for gigabytes. */
    private static final int MAX_CELLS = 64 * 1024 * 1024;

    public static String crc32Hex(byte[] bytes) { return crc32Hex(bytes, 0); }

    public static String crc32Hex(byte[] bytes, int off) { return crc32Hex(bytes, off, bytes.length); }

    public static String crc32Hex(byte[] bytes, int off, int len) {
        CRC32 c = new CRC32();
        c.update(bytes, off, len);
        return String.format(Locale.ROOT, "%08x", c.getValue());
    }

    /** Inflates one blob (a complete zlib stream). */
    public static byte[] inflate(byte[] compressed) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
            byte[] buf = new byte[1 << 16];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw new PackFormatException("the blob ends early");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new PackFormatException("the blob is not a zlib stream: " + e.getMessage(), e);
        } finally {
            inf.end();
        }
    }

    /** Decodes a {@code kind: "volume"} blob. */
    public static RoomVolume decodeVolume(byte[] compressed) {
        Reader r = new Reader(inflate(compressed));
        r.magic("RVL1");
        int w = r.u16(); int d = r.u16(); int h = r.u16(); int y0 = r.u16();
        long cellCount = (long) w * d * h;
        if (cellCount > MAX_CELLS) throw new PackFormatException("volume " + w + "x" + d + "x" + h + " is implausibly large");
        List<String> palette = r.palette();
        if (palette.isEmpty() || !palette.get(0).equals(RoomVolume.AIR)) throw new PackFormatException("palette entry 0 must be minecraft:air");
        short[] cells = new short[(int) cellCount];
        int i = 0;
        while (i < cells.length) {
            int run = r.varint();
            int pal = r.varint();
            if (run < 1) throw new PackFormatException("run length " + run);
            if (pal >= palette.size()) throw new PackFormatException("palette index " + pal + " of " + palette.size());
            if (run > cells.length - i) throw new PackFormatException("runs cover more than " + cellCount + " cells");
            java.util.Arrays.fill(cells, i, i + run, (short) pal);
            i += run;
        }
        return new RoomVolume(w, d, h, y0, palette, cells);
    }

    /** Decodes a {@code kind: "patch"} blob. */
    public static RoomPatch decodePatch(byte[] compressed) {
        Reader r = new Reader(inflate(compressed));
        r.magic("RPT1");
        int w = r.u16(); int d = r.u16(); int h = r.u16(); int y0 = r.u16();
        long cellCount = (long) w * d * h;
        List<String> palette = r.palette();
        int k = r.varint();
        if (k > cellCount) throw new PackFormatException("patch has " + k + " changes in a box of " + cellCount + " cells");
        int[] indices = new int[k];
        int[] values = new int[k];
        long prev = -1L;
        for (int n = 0; n < k; n++) {
            long delta = r.varint();
            long idx = n == 0 ? delta : prev + delta;
            if (n > 0 && delta < 1) throw new PackFormatException("patch indices are not increasing");
            if (idx >= cellCount) throw new PackFormatException("patch index " + idx + " outside the box");
            int pal = r.varint();
            if (pal >= palette.size()) throw new PackFormatException("patch palette index " + pal + " of " + palette.size());
            indices[n] = (int) idx;
            values[n] = pal;
            prev = idx;
        }
        return new RoomPatch(w, d, h, y0, palette, indices, values);
    }

    private static final class Reader {
        final byte[] b;
        int p = 0;

        Reader(byte[] b) { this.b = b; }

        void need(int n) { if (p + n > b.length) throw new PackFormatException("the blob ends early"); }

        void magic(String m) {
            need(4);
            String got = new String(b, 0, 4, StandardCharsets.US_ASCII);
            if (!got.equals(m)) throw new PackFormatException("bad magic '" + got + "', expected '" + m + "'");
            p = 4;
        }

        int u16() {
            need(2);
            int v = (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
            p += 2;
            return v;
        }

        int varint() {
            long v = 0L;
            int shift = 0;
            while (true) {
                need(1);
                int x = b[p++] & 0xFF;
                v = v | ((long) (x & 0x7F) << shift);
                if ((x & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) throw new PackFormatException("varint too long");
            }
            if (v > Integer.MAX_VALUE) throw new PackFormatException("varint too large");
            return (int) v;
        }

        List<String> palette() {
            int n = varint();
            if (n > 0xFFFF) throw new PackFormatException("palette has " + n + " entries");
            ArrayList<String> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int len = varint();
                need(len);
                out.add(new String(b, p, len, StandardCharsets.UTF_8));
                p += len;
            }
            return out;
        }
    }
}
