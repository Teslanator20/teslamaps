// Ported from ADM's p3sim/pack/rooms/RoomVolume.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.PackFormatException;

import java.util.Arrays;

public class BytesBlobSource implements BlobSource {
    private final byte[] bytes;

    public BytesBlobSource(byte[] bytes) { this.bytes = bytes; }

    @Override public long size() { return bytes.length; }

    @Override public byte[] read(long off, int len) {
        if (off < 0 || len < 0 || off + len > bytes.length) throw new PackFormatException("blob " + off + "+" + len + " is outside the data (" + bytes.length + " bytes)");
        return Arrays.copyOfRange(bytes, (int) off, (int) (off + len));
    }

    @Override public void close() {}
}
