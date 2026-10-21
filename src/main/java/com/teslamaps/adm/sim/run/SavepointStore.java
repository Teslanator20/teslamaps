// Ported from ADM's p3sim/run/Savepoints.kt
package com.teslamaps.adm.sim.run;

import java.util.List;
import java.util.Objects;

/** Takes and restores savepoints. The engine installs the real one. */
public interface SavepointStore {
    /** Takes a savepoint now; null if not available. Posts SavepointTaken. */
    default Savepoint take(Run run, String label, Integer roomIndex) { return null; }

    default Savepoint take(Run run, String label) { return take(run, label, null); }

    /** Oldest first. */
    default List<Savepoint> list() { return List.of(); }

    /** Posts SavepointRestored last. */
    default boolean restore(Run run, String id) { return false; }

    /** The most recent savepoint taken on entering roomIndex, or null. */
    default Savepoint forRoom(int roomIndex) {
        List<Savepoint> l = list();
        for (int i = l.size() - 1; i >= 0; i--) {
            Savepoint s = l.get(i);
            if (Objects.equals(s.roomIndex, roomIndex)) return s;
        }
        return null;
    }
}
