// Ported from ADM's p3sim/clear/replay/ReplayFile.kt
package com.teslamaps.adm.sim.clear.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The event array readers (Kotlin extension functions on JsonArray). */
public final class ReplayFileKt {
    private ReplayFileKt() {}

    public static Double d(JsonArray a, int i) {
        JsonElement e = a.get(i);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : null;
    }

    public static Integer i(JsonArray a, int i) {
        JsonElement e = a.get(i);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : null;
    }

    public static String s(JsonArray a, int i) {
        if (i >= a.size()) return null;
        JsonElement e = a.get(i);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public static JsonObject o(JsonArray a, int i) {
        if (i >= a.size()) return null;
        JsonElement e = a.get(i);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
