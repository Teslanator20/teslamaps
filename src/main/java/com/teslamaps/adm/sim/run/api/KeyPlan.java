// Ported from ADM's p3sim/run/api/DoorApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.KeyKind;
import java.util.List;

/** Which rooms drop which key; a pure function of the plan. */
public class KeyPlan {
    /** Rooms that drop a wither key, in path order. */
    public final List<Integer> witherRooms;
    /** The last path room, or null. */
    public final Integer bloodRoom;

    public KeyPlan(List<Integer> witherRooms, Integer bloodRoom) {
        this.witherRooms = witherRooms;
        this.bloodRoom = bloodRoom;
    }

    public KeyKind keyFor(int room) {
        if (bloodRoom != null && room == bloodRoom) return KeyKind.BLOOD;
        if (witherRooms.contains(room)) return KeyKind.WITHER;
        return null;
    }
}
