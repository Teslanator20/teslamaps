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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteStorage {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("ordered_routes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, List<OrderedWaypoints.Wp>> routes = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static void load() {
        routes.clear();
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                List<OrderedWaypoints.Wp> list = new ArrayList<>();
                int i = 1;
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    int number = o.has("n") ? o.get("n").getAsInt() : i;
                    list.add(new OrderedWaypoints.Wp(o.get("x").getAsInt(), o.get("y").getAsInt(),
                            o.get("z").getAsInt(), number));
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
                arr.add(o);
            }
            root.add(e.getKey(), arr);
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
        for (OrderedWaypoints.Wp wp : route) copy.add(new OrderedWaypoints.Wp(wp.x, wp.y, wp.z, wp.number));
        routes.put(name, copy);
        save();
    }

    public static boolean remove(String name) {
        ensureLoaded();
        if (routes.remove(name) == null) return false;
        save();
        return true;
    }

    public static List<String> names() {
        ensureLoaded();
        return new ArrayList<>(routes.keySet());
    }
}
