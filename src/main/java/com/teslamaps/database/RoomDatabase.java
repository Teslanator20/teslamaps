/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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
