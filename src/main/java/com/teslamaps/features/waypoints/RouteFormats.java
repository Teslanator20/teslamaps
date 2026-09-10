package com.teslamaps.features.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Coleweight / Skytils / Skyblocker route data, in the wire formats those mods share. */
public class RouteFormats {

    public record Route(String name, List<OrderedWaypoints.Wp> waypoints) {}
    public record Result(List<Route> routes, String format) {}

    private static final String SKYTILS_V1 = "<Skytils-Waypoint-Data>(V";
    private static final String SKYBLOCKER = "[Skyblocker-Waypoint-Data-V1]";
    private static final String SKYBLOCKER_LEGACY = "[Skyblocker::OrderedWaypoints::v1]";

    private static final Pattern PLAIN_LINE =
            Pattern.compile("(-?\\d+)[,\\s]+(-?\\d+)[,\\s]+(-?\\d+)");

    public static List<String> names() { return List.of("coleweight", "skytils", "skyblocker"); }

    public static Result load(String data) {
        if (data == null || data.isBlank()) return null;
        String s = data.trim();

        if (s.startsWith(SKYBLOCKER) || s.startsWith(SKYBLOCKER_LEGACY)) {
            List<Route> r = loadSkyblocker(s);
            return r == null || r.isEmpty() ? null : new Result(r, "skyblocker");
        }
        if (s.startsWith(SKYTILS_V1)) {
            List<Route> r = loadSkytils(s);
            return r == null || r.isEmpty() ? null : new Result(r, "skytils");
        }

        String json = s.startsWith("{") || s.startsWith("[") ? s : decodeBase64(s);
        if (json != null) {
            List<Route> cole = loadColeweight(json);
            if (cole != null && !cole.isEmpty()) return new Result(cole, "coleweight");
            List<Route> categories = loadSkytilsJson(json);
            if (categories != null && !categories.isEmpty()) return new Result(categories, "skytils");
        }

        List<OrderedWaypoints.Wp> plain = loadPlainText(s);
        if (plain != null && !plain.isEmpty()) {
            return new Result(List.of(new Route("Imported", plain)), "coordinate list");
        }
        return null;
    }

    public static String export(List<OrderedWaypoints.Wp> route, String name, String format) {
        if (format == null) return null;
        return switch (format.toLowerCase()) {
            case "coleweight" -> exportColeweight(route);
            case "skytils" -> exportSkytils(route, name);
            case "skyblocker" -> exportSkyblocker(route, name);
            default -> null;
        };
    }

    // { "waypoints": [ { x, y, z, r, g, b, options: { name: "1" } } ] } - also eats a bare list
    private static List<Route> loadColeweight(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray arr;
            if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("waypoints")) {
                arr = root.getAsJsonObject().getAsJsonArray("waypoints");
            } else {
                return null;
            }
            List<OrderedWaypoints.Wp> out = new ArrayList<>();
            int fallback = 1;
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("x") || !o.has("y") || !o.has("z")) return null;
                int number = fallback;
                if (o.has("options") && o.getAsJsonObject("options").has("name")) {
                    number = parseNumber(o.getAsJsonObject("options").get("name").getAsString(), fallback);
                } else if (o.has("name") && o.get("name").isJsonPrimitive()) {
                    number = parseNumber(o.get("name").getAsString(), fallback);
                }
                out.add(new OrderedWaypoints.Wp(
                        (int) Math.floor(o.get("x").getAsDouble()),
                        (int) Math.floor(o.get("y").getAsDouble()),
                        (int) Math.floor(o.get("z").getAsDouble()),
                        number));
                fallback++;
            }
            sort(out);
            return List.of(new Route("Imported", out));
        } catch (Exception e) {
            return null;
        }
    }

    // "<Skytils-Waypoint-Data>(V1):" + base64(gzip(json))
    private static List<Route> loadSkytils(String s) {
        try {
            String body = s.substring(s.indexOf(':') + 1).trim();
            byte[] bytes = Base64.getDecoder().decode(body);
            String json;
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            List<Route> categories = loadSkytilsJson(json);
            if (categories != null && !categories.isEmpty()) return categories;
            return loadColeweight(json);
        } catch (Exception e) {
            return null;
        }
    }

    // { "categories": [ { name, island, waypoints: [ { name, x, y, z } ] } ] }
    private static List<Route> loadSkytilsJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("categories")) return null;
            List<Route> routes = new ArrayList<>();
            for (JsonElement catEl : root.getAsJsonObject().getAsJsonArray("categories")) {
                JsonObject cat = catEl.getAsJsonObject();
                String name = cat.has("name") ? cat.get("name").getAsString() : "Imported";
                List<OrderedWaypoints.Wp> out = new ArrayList<>();
                int i = 1;
                for (JsonElement el : cat.getAsJsonArray("waypoints")) {
                    JsonObject o = el.getAsJsonObject();
                    int number = o.has("name") && o.get("name").isJsonPrimitive()
                            ? parseNumber(o.get("name").getAsString(), i) : i;
                    out.add(new OrderedWaypoints.Wp(
                            (int) Math.floor(o.get("x").getAsDouble()),
                            (int) Math.floor(o.get("y").getAsDouble()),
                            (int) Math.floor(o.get("z").getAsDouble()),
                            number));
                    i++;
                }
                sort(out);
                if (!out.isEmpty()) routes.add(new Route(name, out));
            }
            return routes;
        } catch (Exception e) {
            return null;
        }
    }

    // "[Skyblocker-Waypoint-Data-V1]" / "[Skyblocker::OrderedWaypoints::v1]" + base64(gzip(json))
    private static List<Route> loadSkyblocker(String s) {
        boolean legacy = s.startsWith(SKYBLOCKER_LEGACY);
        String body = s.substring((legacy ? SKYBLOCKER_LEGACY : SKYBLOCKER).length()).trim();
        String json;
        try {
            byte[] bytes = Base64.getDecoder().decode(body);
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }

        try {
            JsonElement root = JsonParser.parseString(json);
            List<Route> routes = new ArrayList<>();
            if (legacy) {
                // { "<group>": { name, enabled, waypoints: [...] } }
                for (var e : root.getAsJsonObject().entrySet()) {
                    JsonObject group = e.getValue().getAsJsonObject();
                    String name = group.has("name") ? group.get("name").getAsString() : e.getKey();
                    List<OrderedWaypoints.Wp> wps = skyblockerWaypoints(group.getAsJsonArray("waypoints"));
                    if (!wps.isEmpty()) routes.add(new Route(name, wps));
                }
            } else {
                for (JsonElement el : root.getAsJsonArray()) {
                    JsonObject group = el.getAsJsonObject();
                    String name = group.has("name") ? group.get("name").getAsString() : "Imported";
                    List<OrderedWaypoints.Wp> wps = skyblockerWaypoints(group.getAsJsonArray("waypoints"));
                    if (!wps.isEmpty()) routes.add(new Route(name, wps));
                }
            }
            return routes;
        } catch (Exception e) {
            return null;
        }
    }

    // Skyblocker stores the position as a BlockPos codec list: "pos": [x, y, z]
    private static List<OrderedWaypoints.Wp> skyblockerWaypoints(JsonArray arr) {
        List<OrderedWaypoints.Wp> out = new ArrayList<>();
        int i = 1;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            int x, y, z;
            if (o.has("pos") && o.get("pos").isJsonArray()) {
                JsonArray p = o.getAsJsonArray("pos");
                x = p.get(0).getAsInt(); y = p.get(1).getAsInt(); z = p.get(2).getAsInt();
            } else if (o.has("x")) {
                x = (int) Math.floor(o.get("x").getAsDouble());
                y = (int) Math.floor(o.get("y").getAsDouble());
                z = (int) Math.floor(o.get("z").getAsDouble());
            } else {
                continue;
            }
            out.add(new OrderedWaypoints.Wp(x, y, z, i));
            i++;
        }
        return out;
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

    private static String decodeBase64(String s) {
        try {
            String decoded = new String(Base64.getDecoder().decode(s.replaceAll("\\s", "")), StandardCharsets.UTF_8);
            return decoded.startsWith("{") || decoded.startsWith("[") ? decoded : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseNumber(String raw, int fallback) {
        try { return Integer.parseInt(raw.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static void sort(List<OrderedWaypoints.Wp> list) {
        list.sort((a, b) -> Integer.compare(a.number, b.number));
        for (int i = 0; i < list.size(); i++) list.get(i).number = i + 1;
    }

    private static String exportColeweight(List<OrderedWaypoints.Wp> route) {
        JsonArray arr = new JsonArray();
        for (OrderedWaypoints.Wp wp : route) {
            JsonObject o = new JsonObject();
            o.addProperty("x", wp.x);
            o.addProperty("y", wp.y);
            o.addProperty("z", wp.z);
            o.addProperty("r", 0.0);
            o.addProperty("g", 1.0);
            o.addProperty("b", 0.0);
            JsonObject opt = new JsonObject();
            opt.addProperty("name", String.valueOf(wp.number));
            o.add("options", opt);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("waypoints", arr);
        return root.toString();
    }

    private static String exportSkytils(List<OrderedWaypoints.Wp> route, String name) {
        JsonArray waypoints = new JsonArray();
        for (OrderedWaypoints.Wp wp : route) {
            JsonObject o = new JsonObject();
            o.addProperty("name", String.valueOf(wp.number));
            o.addProperty("x", wp.x);
            o.addProperty("y", wp.y);
            o.addProperty("z", wp.z);
            o.addProperty("enabled", true);
            o.addProperty("color", 0x8000FF00);
            waypoints.add(o);
        }
        JsonObject category = new JsonObject();
        category.addProperty("name", name == null || name.isBlank() ? "TeslaMaps" : name);
        category.addProperty("island", "unknown");
        category.add("waypoints", waypoints);
        JsonArray categories = new JsonArray();
        categories.add(category);
        JsonObject root = new JsonObject();
        root.add("categories", categories);
        return "<Skytils-Waypoint-Data>(V1):" + gzipBase64(root.toString());
    }

    private static String exportSkyblocker(List<OrderedWaypoints.Wp> route, String name) {
        JsonArray waypoints = new JsonArray();
        for (OrderedWaypoints.Wp wp : route) {
            JsonArray pos = new JsonArray();
            pos.add(wp.x); pos.add(wp.y); pos.add(wp.z);
            JsonArray color = new JsonArray();
            color.add(0.0); color.add(1.0); color.add(0.0);
            JsonObject o = new JsonObject();
            o.add("pos", pos);
            o.addProperty("name", String.valueOf(wp.number));
            o.add("colorComponents", color);
            o.addProperty("alpha", 0.5);
            o.addProperty("shouldRender", true);
            waypoints.add(o);
        }
        JsonObject group = new JsonObject();
        group.addProperty("name", name == null || name.isBlank() ? "TeslaMaps" : name);
        group.addProperty("island", "unknown");
        group.add("waypoints", waypoints);
        group.addProperty("ordered", true);
        group.addProperty("renderThroughWalls", true);
        group.addProperty("waypointType", "WAYPOINT");
        JsonArray root = new JsonArray();
        root.add(group);
        return SKYBLOCKER + gzipBase64(root.toString());
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
