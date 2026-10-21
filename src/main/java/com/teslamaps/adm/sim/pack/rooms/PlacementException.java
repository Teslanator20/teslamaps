// Ported from ADM's p3sim/pack/rooms/Placement.kt
package com.teslamaps.adm.sim.pack.rooms;

/** A placement the law refuses. The message is plain English. */
public class PlacementException extends RuntimeException {
    public PlacementException(String message) { super(message); }
}
