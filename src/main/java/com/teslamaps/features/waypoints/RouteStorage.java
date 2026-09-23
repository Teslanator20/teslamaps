package com.teslamaps.features.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteStorage {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("ordered_routes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, List<OrderedWaypoints.Wp>> routes = new LinkedHashMap<>();
    private static final Map<String, String> islands = new HashMap<>();
    private static boolean loaded = false;

    public static void load() {
        routes.clear();
        islands.clear();
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonArray arr;
                // old files: name -> [wps]; routes with an island: name -> { island, waypoints }
                if (e.getValue().isJsonObject()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (o.has("island")) islands.put(e.getKey(), o.get("island").getAsString());
                    arr = o.getAsJsonArray("waypoints");
                } else {
                    arr = e.getValue().getAsJsonArray();
                }
                List<OrderedWaypoints.Wp> list = new ArrayList<>();
                int i = 1;
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    int number = o.has("n") ? o.get("n").getAsInt() : i;
                    String name = o.has("name") ? o.get("name").getAsString() : null;
                    int color = o.has("color") ? o.get("color").getAsInt() : -1;
                    list.add(new OrderedWaypoints.Wp(o.get("x").getAsInt(), o.get("y").getAsInt(),
                            o.get("z").getAsInt(), number, name, color));
                    i++;
                }
                if (!list.isEmpty()) routes.put(e.getKey(), list);
            }
            TeslaMaps.LOGGER.info("[OrderedWaypoints] Loaded {} saved routes", routes.size());
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[OrderedWaypoints] Failed to load routes", ex);
        }
    }

    private static void ensureLoaded() { if (!loaded) load(); }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<OrderedWaypoints.Wp>> e : routes.entrySet()) {
            JsonArray arr = new JsonArray();
            for (OrderedWaypoints.Wp wp : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("x", wp.x);
                o.addProperty("y", wp.y);
                o.addProperty("z", wp.z);
                o.addProperty("n", wp.number);
                if (wp.name != null) o.addProperty("name", wp.name);
                if (wp.color != -1) o.addProperty("color", wp.color);
                arr.add(o);
            }
            String island = islands.get(e.getKey());
            if (island == null) {
                root.add(e.getKey(), arr);
            } else {
                JsonObject o = new JsonObject();
                o.addProperty("island", island);
                o.add("waypoints", arr);
                root.add(e.getKey(), o);
            }
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[OrderedWaypoints] Failed to save routes", ex);
        }
    }

    public static List<OrderedWaypoints.Wp> get(String name) {
        ensureLoaded();
        return routes.get(name);
    }

    public static void put(String name, List<OrderedWaypoints.Wp> route) {
        ensureLoaded();
        List<OrderedWaypoints.Wp> copy = new ArrayList<>();
        for (OrderedWaypoints.Wp wp : route) copy.add(wp.copy());
        routes.put(name, copy);
        save();
    }

    public static String island(String name) {
        ensureLoaded();
        return islands.get(name);
    }

    public static void setIsland(String name, String island) {
        ensureLoaded();
        if (island == null || island.isBlank() || island.equalsIgnoreCase("unknown")) islands.remove(name);
        else islands.put(name, island);
        save();
    }

    public static boolean remove(String name) {
        ensureLoaded();
        if (routes.remove(name) == null) return false;
        islands.remove(name);
        save();
        return true;
    }

    public static List<String> names() {
        ensureLoaded();
        return new ArrayList<>(routes.keySet());
    }
}
