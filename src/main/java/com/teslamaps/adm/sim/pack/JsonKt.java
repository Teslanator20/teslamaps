// Ported from ADM's p3sim/pack/Json.kt
package com.teslamaps.adm.sim.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class JsonKt {
    private JsonKt() {}

    public static JsonObject asObj(JsonElement e) { return e != null && e.isJsonObject() ? e.getAsJsonObject() : null; }
    public static JsonArray asArr(JsonElement e) { return e != null && e.isJsonArray() ? e.getAsJsonArray() : null; }
    public static String asStr(JsonElement e) { return e != null && e.isJsonPrimitive() ? e.getAsString() : null; }
    public static Integer asIntOrNull(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? (int) e.getAsDouble() : null;
    }
    public static Long asLongOrNull(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : null;
    }
    public static Double asDoubleOrNull(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : null;
    }
    public static Boolean asBoolOrNull(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
    }

    public static JsonObject obj(JsonObject o, String k) { return asObj(o.get(k)); }
    public static JsonArray arr(JsonObject o, String k) { return asArr(o.get(k)); }
    public static String str(JsonObject o, String k) { return asStr(o.get(k)); }
    /** Kotlin `JsonObject.int(k)` ({@code int} is a Java keyword). */
    public static Integer int_(JsonObject o, String k) { return asIntOrNull(o.get(k)); }
    /** Kotlin `JsonObject.long(k)` ({@code long} is a Java keyword). */
    public static Long long_(JsonObject o, String k) { return asLongOrNull(o.get(k)); }
    public static Double dbl(JsonObject o, String k) { return asDoubleOrNull(o.get(k)); }
    public static Boolean bool(JsonObject o, String k) { return asBoolOrNull(o.get(k)); }

    public static List<JsonObject> objects(JsonArray a) {
        List<JsonObject> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) { JsonObject o = asObj(e); if (o != null) out.add(o); }
        return out;
    }

    public static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) { String s = asStr(e); if (s != null) out.add(s); }
        return out;
    }

    public static double[] doubles(JsonArray a) {
        if (a == null) return new double[0];
        List<Double> out = new ArrayList<>();
        for (JsonElement e : a) { Double d = asDoubleOrNull(e); if (d != null) out.add(d); }
        double[] r = new double[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    public static int[] ints(JsonArray a) {
        if (a == null) return new int[0];
        List<Integer> out = new ArrayList<>();
        for (JsonElement e : a) { Integer d = asIntOrNull(e); if (d != null) out.add(d); }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    public static Tile asTile(JsonElement e) {
        JsonArray a = asArr(e);
        if (a == null) return null;
        if (a.size() < 2) return null;
        Integer x = asIntOrNull(a.get(0));
        if (x == null) return null;
        Integer z = asIntOrNull(a.get(1));
        if (z == null) return null;
        return new Tile(x, z);
    }

    public static Pos asPos(JsonElement e) {
        JsonArray a = asArr(e);
        if (a == null) return null;
        if (a.size() < 3) return null;
        Integer x = asIntOrNull(a.get(0));
        if (x == null) return null;
        Integer y = asIntOrNull(a.get(1));
        if (y == null) return null;
        Integer z = asIntOrNull(a.get(2));
        if (z == null) return null;
        return new Pos(x, y, z);
    }

    public static List<Tile> asTiles(JsonElement e) {
        List<Tile> out = new ArrayList<>();
        JsonArray a = asArr(e);
        if (a == null) return out;
        for (JsonElement x : a) { Tile t = asTile(x); if (t != null) out.add(t); }
        return out;
    }

    public static List<Pos> asPosList(JsonElement e) {
        List<Pos> out = new ArrayList<>();
        JsonArray a = asArr(e);
        if (a == null) return out;
        for (JsonElement x : a) { Pos p = asPos(x); if (p != null) out.add(p); }
        return out;
    }

    public static JsonObject readGzipJson(InputStream stream) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(stream), 1 << 16)) {
            JsonObject o = asObj(JsonParser.parseReader(new InputStreamReader(gz, StandardCharsets.UTF_8)));
            if (o == null) throw new IllegalStateException("the top level is not a JSON object");
            return o;
        }
    }

    public static JsonObject parseJson(String text) {
        JsonObject o = asObj(JsonParser.parseString(text));
        if (o == null) throw new IllegalStateException("the top level is not a JSON object");
        return o;
    }
}
