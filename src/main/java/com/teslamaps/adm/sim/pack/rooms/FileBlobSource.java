// Ported from ADM's p3sim/pack/rooms/RoomVolume.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.PackFormatException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;

/** {@link BlobSource} over {@code rooms.blocks.bin}. Thread-safe: reads are serialised. */
public class FileBlobSource implements BlobSource {
    private final RandomAccessFile file;
    private final long size;

    public FileBlobSource(java.nio.file.Path path) throws IOException {
        this.file = new RandomAccessFile(path.toFile(), "r");
        this.size = file.length();
    }

    @Override public long size() { return size; }

    @Override public synchronized byte[] read(long off, int len) {
        if (off < 0 || len < 0 || off + len > size) throw new PackFormatException("blob " + off + "+" + len + " is outside the file (" + size + " bytes)");
        byte[] b = new byte[len];
        try {
            file.seek(off);
            file.readFully(b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return b;
    }

    @Override public void close() {
        try {
            file.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
