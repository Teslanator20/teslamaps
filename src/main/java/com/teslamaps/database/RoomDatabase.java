package com.teslamaps.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomDatabase {
    private static final RoomDatabase INSTANCE = new RoomDatabase();
    private static final Gson GSON = new Gson();

    private final Map<Integer, RoomData> coreToRoom = new HashMap<>();
    private final Map<Integer, RoomData> idToRoom = new HashMap<>();
    private final Map<String, RoomData> nameToRoom = new HashMap<>();
    private final List<RoomData> allRooms = new ArrayList<>();

    private boolean loaded = false;

    public static RoomDatabase getInstance() {
        return INSTANCE;
    }

    public void load() {
        if (loaded) return;

        try {
            InputStream is = getClass().getResourceAsStream("/assets/teslamaps/data/rooms.json");
            if (is == null) {
                TeslaMaps.LOGGER.error("Room database not found at /assets/teslamaps/data/rooms.json");
                return;
            }

            Type listType = new TypeToken<List<RoomData>>(){}.getType();
            List<RoomData> rooms = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), listType);

            if (rooms == null) {
                TeslaMaps.LOGGER.error("Failed to parse rooms.json");
                return;
            }

            for (RoomData room : rooms) {
                allRooms.add(room);
                idToRoom.put(room.getRoomID(), room);
                nameToRoom.put(room.getName().toLowerCase(), room);

                if (room.getCores() != null) {
                    for (Integer core : room.getCores()) {
                        coreToRoom.put(core, room);
                    }
                }
            }

            loaded = true;
            loadOverrides();
            TeslaMaps.LOGGER.info("Loaded {} room definitions with {} core hashes",
                    allRooms.size(), coreToRoom.size());

        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load room database", e);
        }
    }

    private static final java.nio.file.Path OVERRIDES = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("rooms_custom.json");
    private final List<RoomData> overrides = new ArrayList<>();

    // config/teslamaps/rooms_custom.json: same format as rooms.json; a known name only adds its cores
    private void loadOverrides() {
        if (!java.nio.file.Files.exists(OVERRIDES)) return;
        try (var r = java.nio.file.Files.newBufferedReader(OVERRIDES, StandardCharsets.UTF_8)) {
            List<RoomData> list = GSON.fromJson(r, new TypeToken<List<RoomData>>(){}.getType());
            if (list == null) return;
            for (RoomData o : list) {
                if (o == null || o.getName() == null) continue;
                overrides.add(o);
                apply(o);
            }
            TeslaMaps.LOGGER.info("Loaded {} custom room entries", overrides.size());
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load rooms_custom.json", e);
        }
    }

    private void apply(RoomData o) {
        RoomData base = nameToRoom.get(o.getName().toLowerCase());
        if (base == null) {
            base = o;
            allRooms.add(o);
            idToRoom.put(o.getRoomID(), o);
            nameToRoom.put(o.getName().toLowerCase(), o);
        } else if (base != o && o.getCores() != null) {
            List<Integer> cores = base.getCores() == null ? new ArrayList<>() : new ArrayList<>(base.getCores());
            for (Integer c : o.getCores()) if (!cores.contains(c)) cores.add(c);
            base.setCores(cores);
        }
        if (o.getCores() != null) for (Integer c : o.getCores()) coreToRoom.put(c, base);
    }

    /** Adds cores to a known room (by name) or a whole new room, and saves rooms_custom.json. */
    public void learn(RoomData entry) {
        RoomData existing = null;
        for (RoomData o : overrides) if (o.getName().equalsIgnoreCase(entry.getName())) existing = o;
        if (existing == null) {
            overrides.add(entry);
        } else if (entry.getCores() != null) {
            List<Integer> cores = existing.getCores() == null ? new ArrayList<>() : new ArrayList<>(existing.getCores());
            for (Integer c : entry.getCores()) if (!cores.contains(c)) cores.add(c);
            existing.setCores(cores);
        }
        apply(entry);
        try {
            java.nio.file.Files.createDirectories(OVERRIDES.getParent());
            java.nio.file.Files.writeString(OVERRIDES, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(overrides),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save rooms_custom.json", e);
        }
    }

    public int nextId() {
        int max = -1;
        for (RoomData r : allRooms) max = Math.max(max, r.getRoomID());
        return max + 1;
    }

    public RoomData findByCore(int coreHash) {
        return coreToRoom.get(coreHash);
    }

    public RoomData findById(int roomId) {
        return idToRoom.get(roomId);
    }

    public RoomData findByName(String name) {
        return nameToRoom.get(name.toLowerCase());
    }

    public List<RoomData> getAllRooms() {
        return allRooms;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
