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

    /** Kotlin's String.trim(): strips whitespace and space characters (NBSP included). */
    public static String trim(String s) {
        int a = 0, b = s.length();
        while (a < b && ws(s.charAt(a))) a++;
        while (b > a && ws(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    /** Kotlin's String.isBlank(). */
    public static boolean isBlank(String s) { return trim(s).isEmpty(); }

    private static boolean ws(char c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    public static JsonObject o(JsonArray a, int i) {
        if (i >= a.size()) return null;
        JsonElement e = a.get(i);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
