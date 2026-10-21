// Ported from ADM's p3sim/run/WorldEnv.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorldEnv {
    private WorldEnv() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String PACK_NAME = "ads-env";

    public static final String PACK_ID = "file/" + PACK_NAME;

    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;

    private static final String AMBIENT_SOUNDS_KEY = "minecraft:audio/ambient_sounds";

    public static String dimensionTypeJson(String vanilla) {
        JsonObject o = JsonParser.parseString(vanilla).getAsJsonObject();
        o.addProperty("has_skylight", false);
        o.addProperty("has_ceiling", false);
        o.addProperty("min_y", MIN_Y);
        o.addProperty("height", HEIGHT);
        o.addProperty("logical_height", HEIGHT);
        o.addProperty("ambient_light", 0.0);
        if (o.get("attributes") instanceof JsonObject a) a.remove(AMBIENT_SOUNDS_KEY);
        return GSON.toJson(o);
    }

    public static String packMetaJson(int major, int minor) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "ADS world settings");
        pack.add("min_format", GSON.toJsonTree(new int[]{major, minor}));
        pack.addProperty("max_format", major);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root);
    }

    public static Path installPack(Path worldDir, String vanillaDimensionJson, int packMajor, int packMinor) throws IOException {
        Path root = worldDir.resolve("datapacks").resolve(PACK_NAME);
        String dimension = dimensionTypeJson(vanillaDimensionJson);
        String meta = packMetaJson(packMajor, packMinor);
        Path dimFile = root.resolve("data").resolve("minecraft").resolve("dimension_type").resolve("overworld.json");
        Files.createDirectories(dimFile.getParent());
        Files.writeString(root.resolve("pack.mcmeta"), meta);
        Files.writeString(dimFile, dimension);
        return root;
    }

    public static List<Pair<Integer, Integer>> chunksOf(int minX, int minZ, int width, int depth) {
        if (width <= 0 || depth <= 0) return new ArrayList<>();
        ArrayList<Pair<Integer, Integer>> out = new ArrayList<>();
        for (int cx = minX >> 4; cx <= (minX + width - 1) >> 4; cx++) for (int cz = minZ >> 4; cz <= (minZ + depth - 1) >> 4; cz++) out.add(Pair.of(cx, cz));
        return out;
    }
}
