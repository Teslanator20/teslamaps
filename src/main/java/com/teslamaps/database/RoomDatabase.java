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

    // Index core hash -> room data
    private final Map<Integer, RoomData> coreToRoom = new HashMap<>();
    // Index room ID -> room data
    private final Map<Integer, RoomData> idToRoom = new HashMap<>();
    // Index name -> room data
    private final Map<String, RoomData> nameToRoom = new HashMap<>();
    // All rooms
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

                // Index by all core variations
                if (room.getCores() != null) {
                    for (Integer core : room.getCores()) {
                        coreToRoom.put(core, room);
                    }
                }
            }

            loaded = true;
            TeslaMaps.LOGGER.info("Loaded {} room definitions with {} core hashes",
                    allRooms.size(), coreToRoom.size());

        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load room database", e);
        }
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
