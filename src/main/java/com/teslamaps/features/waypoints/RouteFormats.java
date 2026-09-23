package com.teslamaps.features.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.features.waypoints.codec.WaypointerDecoder;
import com.teslamaps.features.waypoints.codec.WaypointerRouteData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Coleweight / Skytils / Skyblocker / Firmament / Waypointer / Soopy route data, in the wire formats
 * those mods share. Format detection ported from Skysoft's WaypointImportCodec / WaypointJsonImport.
 */
public class RouteFormats {

    public record Route(String name, String island, List<OrderedWaypoints.Wp> waypoints) {}
    public record Result(List<Route> routes, String format) {}

    private static final String SKYTILS = "<Skytils-Waypoint-Data>(V";
    private static final String SKYBLOCKER = "[Skyblocker-Waypoint-Data-V1]";
    private static final String SKYBLOCKER_LEGACY = "[Skyblocker::OrderedWaypoints::v1]";
    private static final String FIRMAMENT = "FIRM_WAYPOINTS/";

    private static final Pattern PLAIN_LINE =
            Pattern.compile("(-?\\d+)[,\\s]+(-?\\d+)[,\\s]+(-?\\d+)");
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private static String lastError = null;

    /** Why the last {@link #load} returned null, if it knows. */
    public static String lastError() { return lastError; }

    public static List<String> names() { return List.of("coleweight", "skytils", "skyblocker"); }

    public static Result load(String data) {
        lastError = null;
        if (data == null || data.isBlank()) return null;
        String s = data.trim();
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        if (s.startsWith("```") && s.endsWith("```") && s.length() > 6) {
            int nl = s.indexOf('\n');
            s = (nl < 0 ? s.substring(3) : s.substring(nl + 1)).replaceAll("```$", "").trim();
        }

        if (s.startsWith(SKYBLOCKER) || s.startsWith(SKYBLOCKER_LEGACY)) {
            String body = s.substring((s.startsWith(SKYBLOCKER) ? SKYBLOCKER : SKYBLOCKER_LEGACY).length()).trim();
            return result(fromJson(gunzipText(base64(body))), "skyblocker");
        }
        if (s.startsWith(SKYTILS)) {
            if (!s.startsWith(SKYTILS + "1)")) {
                lastError = "Skytils V2 shares are Brotli compressed and not supported yet. Export as V1 or JSON.";
                return null;
            }
            String json = gunzipText(base64(s.substring(s.indexOf(':') + 1).trim()));
            return result(fromJson(json), "skytils");
        }
        if (s.startsWith("WP:")) return waypointer(s);
        if (s.startsWith("WPL:") || s.startsWith("WPC:") || s.startsWith("WPD:")) {
            lastError = "Only ordinary Waypointer V10 route shares (WP:) are supported.";
            return null;
        }
        if (s.startsWith(FIRMAMENT)) return result(fromJson(s.substring(FIRMAMENT.length())), "firmament");
        if (s.startsWith("{") || s.startsWith("[")) {
            Result r = result(fromJson(s), detect(s));
            if (r != null) return r;
        } else {
            byte[] bytes = base64(s.replaceAll("\\s", ""));
            if (bytes != null && bytes.length > 0) {
                if (bytes.length >= 2 && bytes[0] == 0x1f && bytes[1] == (byte) 0x8b) {
                    String json = gunzipText(bytes);
                    Result r = result(fromJson(json), json == null ? "json" : detect(json));
                    if (r != null) return r;
                } else if (bytes[0] == 1) {
                    Result r = soopy(bytes);
                    if (r != null) return r;
                } else {
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    if (text.startsWith(FIRMAMENT)) return result(fromJson(text.substring(FIRMAMENT.length())), "firmament");
                    if (text.startsWith("{") || text.startsWith("[")) {
                        Result r = result(fromJson(text), detect(text));
                        if (r != null) return r;
                    }
                }
            }
        }

        List<OrderedWaypoints.Wp> plain = loadPlainText(s);
        if (plain != null && !plain.isEmpty()) {
            return new Result(List.of(new Route("Imported", null, plain)), "coordinate list");
        }
        return null;
    }

    private static Result result(List<Route> routes, String format) {
        if (routes == null) return null;
        routes.removeIf(r -> r.waypoints().isEmpty());
        return routes.isEmpty() ? null : new Result(routes, format);
    }

    private static String detect(String json) {
        if (json.contains("\"categories\"")) return "skytils";
        if (json.contains("\"isOrdered\"")) return "firmament";
        if (json.contains("\"pos\"")) return "skyblocker";
        return "coleweight";
    }

    // ---- JSON (port of Skysoft's WaypointJsonImport) ----

    private static List<Route> fromJson(String json) {
        if (json == null) return null;
        try {
            JsonElement root = JsonParser.parseString(json);
            List<Route> out = new ArrayList<>();
            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                if (arr.isEmpty()) return null;
                JsonObject first = arr.get(0).getAsJsonObject();
                if (first.has("waypoints") || first.has("points")) {
                    for (JsonElement el : arr) out.add(group(el.getAsJsonObject(), null, "Imported"));
                } else {
                    out.add(new Route("Imported", null, points(arr)));
                }
            } else if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                if (o.has("categories")) {
                    for (JsonElement el : o.getAsJsonArray("categories")) out.add(group(el.getAsJsonObject(), null, "Imported"));
                } else if (o.has("groups")) {
                    for (JsonElement el : o.getAsJsonArray("groups")) out.add(group(el.getAsJsonObject(), null, "Imported"));
                } else if (o.has("waypoints") || o.has("points")) {
                    out.add(group(o, null, "Imported"));
                } else {
                    // Skyblocker legacy { "<group>": {...} } or { "<island>": [groups] }
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                        if (e.getValue().isJsonArray()) {
                            for (JsonElement el : e.getValue().getAsJsonArray()) out.add(group(el.getAsJsonObject(), e.getKey(), "Imported"));
                        } else if (e.getValue().isJsonObject()) {
                            out.add(group(e.getValue().getAsJsonObject(), null, e.getKey()));
                        } else {
                            return null;
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Route group(JsonObject json, String zone, String defaultName) {
        if (json.has("isRelativeTo") && !json.get("isRelativeTo").isJsonNull()) {
            lastError = "Relative Firmament waypoints need an origin. Export absolute waypoints instead.";
            throw new IllegalArgumentException(lastError);
        }
        JsonElement pts = json.has("waypoints") ? json.get("waypoints") : json.get("points");
        String name = string(json, "name");
        if (name == null) name = string(json, "label");
        String island = string(json, "island");
        if (island == null) island = string(json, "zone");
        if (island == null) island = zone;
        if (island != null && island.equalsIgnoreCase("unknown")) island = null;
        return new Route(name == null || name.isBlank() ? defaultName : clean(name), island, points(pts.getAsJsonArray()));
    }

    private static List<OrderedWaypoints.Wp> points(JsonArray arr) {
        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement el : arr) objects.add(el.getAsJsonObject());
        boolean numbered = !objects.isEmpty();
        for (JsonObject o : objects) if (parseInt(nameOf(o)) == null) { numbered = false; break; }

        List<OrderedWaypoints.Wp> out = new ArrayList<>();
        int i = 1;
        for (JsonObject o : objects) {
            double[] pos = position(o);
            if (pos == null) continue;
            String name = nameOf(o);
            Integer num = parseInt(name);
            int number = numbered ? num : i;
            out.add(new OrderedWaypoints.Wp((int) Math.floor(pos[0]), (int) Math.floor(pos[1]), (int) Math.floor(pos[2]),
                    number, num != null ? null : clean(name), color(o)));
            i++;
        }
        if (numbered) out.sort((a, b) -> Integer.compare(a.number, b.number));
        for (int k = 0; k < out.size(); k++) out.get(k).number = k + 1;
        return out;
    }

    private static double[] position(JsonObject o) {
        for (String key : new String[]{"pos", "coords", "position", "location"}) {
            JsonElement p = o.get(key);
            if (p == null) continue;
            if (p.isJsonArray() && p.getAsJsonArray().size() == 3) {
                JsonArray a = p.getAsJsonArray();
                return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
            }
            if (p.isJsonObject()) {
                JsonObject po = p.getAsJsonObject();
                return new double[]{po.get("x").getAsDouble(), po.get("y").getAsDouble(), po.get("z").getAsDouble()};
            }
        }
        if (o.has("x") && o.has("y") && o.has("z")) {
            return new double[]{o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()};
        }
        return null;
    }

    private static String nameOf(JsonObject o) {
        if (o.has("options") && o.get("options").isJsonObject() && o.getAsJsonObject("options").has("name")) {
            return o.getAsJsonObject("options").get("name").getAsString();
        }
        JsonElement n = o.has("name") ? o.get("name") : o.get("label");
        return componentText(n);
    }

    private static String componentText(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) return e.getAsString();
        StringBuilder sb = new StringBuilder();
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) sb.append(componentText(c));
        } else {
            JsonObject o = e.getAsJsonObject();
            if (o.has("text")) sb.append(o.get("text").getAsString());
            sb.append(componentText(o.get("extra")));
        }
        return sb.toString();
    }

    private static int color(JsonObject o) {
        try {
            JsonElement c = o.get("color");
            if (c != null && c.isJsonPrimitive()) {
                if (c.getAsJsonPrimitive().isNumber()) return (int) c.getAsLong() & 0xFFFFFF;
                String text = c.getAsString().replaceFirst("^#", "").replaceFirst("^0x", "");
                String[] parts = text.split(":");
                if (parts.length == 5) {
                    return (Integer.parseInt(parts[2], 16) << 16) | (Integer.parseInt(parts[3], 16) << 8) | Integer.parseInt(parts[4], 16);
                }
                if (text.length() == 6 || text.length() == 8) return (int) Long.parseLong(text, 16) & 0xFFFFFF;
                return -1;
            }
            if (o.has("colorComponents")) {
                JsonArray a = o.getAsJsonArray("colorComponents");
                return rgb(norm(a.get(0).getAsDouble()), norm(a.get(1).getAsDouble()), norm(a.get(2).getAsDouble()));
            }
            if (o.has("r") && o.has("g") && o.has("b")) {
                double r = o.get("r").getAsDouble(), g = o.get("g").getAsDouble(), b = o.get("b").getAsDouble();
                if (o.has("options") || (r <= 1 && g <= 1 && b <= 1)) return rgb(norm(r), norm(g), norm(b));
                return rgb((int) r, (int) g, (int) b);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static int norm(double v) { return (int) Math.round(Math.clamp(v, 0.0, 1.0) * 255); }

    private static int rgb(int r, int g, int b) {
        return (Math.clamp(r, 0, 255) << 16) | (Math.clamp(g, 0, 255) << 8) | Math.clamp(b, 0, 255);
    }

    // ---- Waypointer V10 (WP:) and Soopy V1 ----

    private static Result waypointer(String s) {
        try {
            WaypointerRouteData data = WaypointerDecoder.decode(s);
            List<Route> out = new ArrayList<>();
            int gi = 1;
            for (WaypointerRouteData.Group g : data.groups) {
                List<OrderedWaypoints.Wp> wps = new ArrayList<>();
                int n = 1;
                for (int pi = 0; pi < g.points.size(); pi++) {
                    WaypointerRouteData.Point p = g.points.get(pi);
                    Integer num = parseInt(p.name);
                    wps.add(new OrderedWaypoints.Wp((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z),
                            n++, num != null ? null : clean(p.name), waypointerColor(g, p, pi)));
                }
                String name = !g.name.isBlank() ? g.name : !data.label.isBlank() ? data.label : "Imported route " + gi;
                out.add(new Route(clean(name), g.zone.equals("unknown") ? null : g.zone, wps));
                gi++;
            }
            return result(out, "waypointer");
        } catch (IllegalArgumentException e) {
            lastError = e.getMessage();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int waypointerColor(WaypointerRouteData.Group g, WaypointerRouteData.Point p, int index) {
        if ((p.flags & 8L) != 0) return p.color;
        if (g.gradient == 2) return g.color;
        if (g.gradient == 1) {
            double ratio = g.points.size() <= 1 ? 0.0 : index / (double) (g.points.size() - 1);
            int color = 0;
            for (int shift : new int[]{16, 8, 0}) {
                int start = (g.startColor >>> shift) & 255, end = (g.endColor >>> shift) & 255;
                color |= ((int) (start + (end - start) * ratio)) << shift;
            }
            return color;
        }
        return p.color;
    }

    private static Result soopy(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != 1) return null;
            int count = in.readInt();
            if (count < 1 || count > 100_000) return null;
            Map<String, List<OrderedWaypoints.Wp>> areas = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                double x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                int r = in.readByte() * 2, g = in.readByte() * 2, b = in.readByte() * 2;
                if (r < 0 || g < 0 || b < 0) return null;
                String area = in.readUTF();
                String name = in.readUTF();
                if (name.isBlank()) name = id;
                List<OrderedWaypoints.Wp> list = areas.computeIfAbsent(area, k -> new ArrayList<>());
                list.add(new OrderedWaypoints.Wp((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                        list.size() + 1, clean(name), rgb(r, g, b)));
            }
            if (in.available() != 0) return null;
            List<Route> out = new ArrayList<>();
            areas.forEach((area, wps) -> out.add(new Route("Soopy " + (area.isBlank() ? "waypoints" : area),
                    area.isBlank() ? null : area, wps)));
            return result(out, "soopy");
        } catch (Exception e) {
            return null;
        }
    }

    /** One "x y z" or "x, y, z" per line - what a hand written list or /tmap route copy looks like. */
    private static List<OrderedWaypoints.Wp> loadPlainText(String s) {
        List<OrderedWaypoints.Wp> out = new ArrayList<>();
        int i = 1;
        for (String line : s.split("\\r?\\n")) {
            Matcher m = PLAIN_LINE.matcher(line);
            if (!m.find()) continue;
            out.add(new OrderedWaypoints.Wp(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), i));
            i++;
        }
        return out;
    }

    private static byte[] base64(String s) {
        try {
            return Base64.getDecoder().decode(s.trim().replace('-', '+').replace('_', '/'));
        } catch (Exception e) {
            return null;
        }
    }

    private static String gunzipText(byte[] bytes) {
        if (bytes == null) return null;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(in.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static Integer parseInt(String raw) {
        if (raw == null) return null;
        try { return Integer.parseInt(raw.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String clean(String s) {
        if (s == null) return null;
        String c = FORMATTING.matcher(s).replaceAll("").trim();
        if (c.length() > 64) c = c.substring(0, 64);
        return c.isEmpty() ? null : c;
    }

    // ---- export ----

    public static String export(List<OrderedWaypoints.Wp> route, String name, String format) {
        return exportAll(List.of(new Route(name, RouteStorage.island(name), route)), format);
    }

    /** Several routes in one share; Coleweight has no groups, so only the first one goes there. */
    public static String exportAll(List<Route> routes, String format) {
        if (format == null || routes.isEmpty()) return null;
        return switch (format.toLowerCase()) {
            case "coleweight" -> exportColeweight(routes.get(0).waypoints());
            case "skytils" -> exportSkytils(routes);
            case "skyblocker" -> exportSkyblocker(routes);
            default -> null;
        };
    }

    /** Plain JSON library (Skyblocker group shape), for a file backup. */
    public static String exportJson(List<Route> routes) {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(skyblockerGroups(routes));
    }

    private static float[] rgbF(OrderedWaypoints.Wp wp) {
        if (wp.color == -1) return new float[]{0f, 1f, 0f};
        return new float[]{((wp.color >> 16) & 255) / 255f, ((wp.color >> 8) & 255) / 255f, (wp.color & 255) / 255f};
    }

    private static String exportColeweight(List<OrderedWaypoints.Wp> route) {
        JsonArray arr = new JsonArray();
        for (OrderedWaypoints.Wp wp : route) {
            float[] c = rgbF(wp);
            JsonObject o = new JsonObject();
            o.addProperty("x", wp.x);
            o.addProperty("y", wp.y);
            o.addProperty("z", wp.z);
            o.addProperty("r", (double) c[0]);
            o.addProperty("g", (double) c[1]);
            o.addProperty("b", (double) c[2]);
            JsonObject opt = new JsonObject();
            opt.addProperty("name", String.valueOf(wp.number));
            o.add("options", opt);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("waypoints", arr);
        return root.toString();
    }

    private static String exportSkytils(List<Route> routes) {
        JsonArray categories = new JsonArray();
        for (Route r : routes) {
            JsonArray waypoints = new JsonArray();
            for (OrderedWaypoints.Wp wp : r.waypoints()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", wp.name == null ? String.valueOf(wp.number) : wp.name);
                o.addProperty("x", wp.x);
                o.addProperty("y", wp.y);
                o.addProperty("z", wp.z);
                o.addProperty("enabled", true);
                o.addProperty("color", wp.color == -1 ? 0x8000FF00 : 0x80000000 | wp.color);
                waypoints.add(o);
            }
            JsonObject category = new JsonObject();
            category.addProperty("name", r.name() == null || r.name().isBlank() ? "TeslaMaps" : r.name());
            category.addProperty("island", r.island() == null ? "unknown" : r.island());
            category.add("waypoints", waypoints);
            categories.add(category);
        }
        JsonObject root = new JsonObject();
        root.add("categories", categories);
        return "<Skytils-Waypoint-Data>(V1):" + gzipBase64(root.toString());
    }

    private static JsonArray skyblockerGroups(List<Route> routes) {
        JsonArray root = new JsonArray();
        for (Route r : routes) {
            JsonArray waypoints = new JsonArray();
            for (OrderedWaypoints.Wp wp : r.waypoints()) {
                JsonArray pos = new JsonArray();
                pos.add(wp.x); pos.add(wp.y); pos.add(wp.z);
                float[] c = rgbF(wp);
                JsonArray color = new JsonArray();
                color.add(c[0]); color.add(c[1]); color.add(c[2]);
                JsonObject o = new JsonObject();
                o.add("pos", pos);
                o.addProperty("name", wp.name == null ? String.valueOf(wp.number) : wp.name);
                o.add("colorComponents", color);
                o.addProperty("alpha", 0.5);
                o.addProperty("shouldRender", true);
                waypoints.add(o);
            }
            JsonObject group = new JsonObject();
            group.addProperty("name", r.name() == null || r.name().isBlank() ? "TeslaMaps" : r.name());
            group.addProperty("island", r.island() == null ? "unknown" : r.island());
            group.add("waypoints", waypoints);
            group.addProperty("ordered", true);
            group.addProperty("renderThroughWalls", true);
            group.addProperty("waypointType", "WAYPOINT");
            root.add(group);
        }
        return root;
    }

    private static String exportSkyblocker(List<Route> routes) {
        return SKYBLOCKER + gzipBase64(skyblockerGroups(routes).toString());
    }

    private static String gzipBase64(String json) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "";
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
