// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.PackReport;

public final class RoomPackKt {
    private RoomPackKt() {}

    /** Shared header check for every pack: {@code format} must match and {@code version} must be the supported one. */
    public static boolean checkHeader(JsonObject root, String format, int supported, String file, PackReport report) {
        String f = JsonKt.str(root, "format");
        if (!format.equals(f)) {
            report.error(file + " is not a '" + format + "' pack (its format says '" + (f != null ? f : "nothing") + "').");
            return false;
        }
        Integer v = JsonKt.int_(root, "version");
        if (v == null) { report.error(file + " has no version."); return false; }
        if (v > supported) { report.error(file + " is version " + v + " but this ADS reads version " + supported + ". Update the mod or use an older pack."); return false; }
        if (v < supported) { report.error(file + " is version " + v + ", older than the version " + supported + " this ADS reads."); return false; }
        return true;
    }
}
