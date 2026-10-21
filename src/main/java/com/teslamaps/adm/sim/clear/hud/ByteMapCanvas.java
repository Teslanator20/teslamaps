// Ported from ADM's p3sim/clear/hud/MapModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.run.api.MapCanvas;

/** A MapCanvas over a 128 x 128 byte array of map colour ids. */
public class ByteMapCanvas implements MapCanvas {
    public final byte[] bytes;

    public ByteMapCanvas(byte[] bytes) { this.bytes = bytes; }

    public ByteMapCanvas() { this(new byte[MapModel.SIZE * MapModel.SIZE]); }

    @Override public int size() { return MapModel.SIZE; }

    @Override public void set(int x, int y, int color) {
        int size = size();
        if (x >= 0 && x < size && y >= 0 && y < size) bytes[y * size + x] = (byte) color;
    }

    @Override public int get(int x, int y) {
        int size = size();
        return x >= 0 && x < size && y >= 0 && y < size ? bytes[y * size + x] : 0;
    }
}
