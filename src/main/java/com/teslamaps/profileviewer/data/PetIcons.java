package com.teslamaps.profileviewer.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Pet and pet skin skull textures from the bundled pets.json (schrumbo's pv), no network. */
public class PetIcons {
    private static Map<String, String> textures;
    private static Map<String, String> skins;
    private static final Map<String, ItemStack> built = new HashMap<>();

    private static synchronized void load() {
        if (textures != null) return;
        textures = new HashMap<>();
        skins = new HashMap<>();
        try (InputStream stream = PetIcons.class.getResourceAsStream("/assets/teslamaps/pv/pets.json")) {
            if (stream == null) return;
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("textures")) {
                JsonObject json = root.getAsJsonObject("textures");
                for (String key : json.keySet()) textures.put(key, json.get(key).getAsString());
            }
            if (root.has("skins")) {
                JsonObject json = root.getAsJsonObject("skins");
                for (String key : json.keySet()) skins.put(key, json.get(key).getAsString());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read pets.json: {}", e.toString());
        }
    }

    /** The pet type's skull, EMPTY when the bundle does not know it. */
    public static ItemStack forType(String type) {
        load();
        return skull("t:" + type, textures.get(type));
    }

    /** The skin's skull; accepts ids with or without the PET_SKIN_ prefix. */
    public static ItemStack forSkin(String skin) {
        load();
        String key = skin.startsWith("PET_SKIN_") ? skin.substring("PET_SKIN_".length()) : skin;
        return skull("s:" + key, skins.get(key));
    }

    private static ItemStack skull(String cacheKey, String texture) {
        if (texture == null) return ItemStack.EMPTY;
        return built.computeIfAbsent(cacheKey, key -> SkyblockItems.headFromTexture(texture));
    }
}
