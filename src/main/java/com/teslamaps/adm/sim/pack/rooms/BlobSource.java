// Ported from ADM's p3sim/pack/rooms/RoomVolume.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.io.Closeable;

/** Random access to the compressed blobs. Production reads the file; tests hand in bytes. */
public interface BlobSource extends Closeable {
    long size();

    byte[] read(long off, int len);

    @Override void close();
}
