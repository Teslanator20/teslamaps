// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

/** A 128x128 canvas of the dungeon map item (map palette colour indexes). */
public interface MapCanvas {
    int size();

    void set(int x, int y, int color);

    int get(int x, int y);
}
