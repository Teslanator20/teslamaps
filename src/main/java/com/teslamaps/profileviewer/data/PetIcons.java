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
    private static Map<String, String> held;
    private static Map<String, String> heldVanilla;
    private static final Map<String, ItemStack> built = new HashMap<>();

    private static synchronized void load() {
        if (textures != null) return;
        textures = new HashMap<>();
        skins = new HashMap<>();
        held = new HashMap<>();
        heldVanilla = new HashMap<>();
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
            if (root.has("held")) {
                JsonObject json = root.getAsJsonObject("held");
                for (String key : json.keySet()) held.put(key, json.get(key).getAsString());
            }
            if (root.has("heldVanilla")) {
                JsonObject json = root.getAsJsonObject("heldVanilla");
                for (String key : json.keySet()) heldVanilla.put(key, json.get(key).getAsString());
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

    /** Icon of a pet held item: bundled skull, then a vanilla stand-in, else a name tag. */
    public static ItemStack forHeldItem(String id) {
        load();
        ItemStack skull = skull("h:" + id, held.get(id));
        if (!skull.isEmpty()) return skull;
        String vanilla = heldVanilla.getOrDefault(id, "name_tag");
        ItemStack stack = com.teslamaps.profileviewer.ui.Kit.icon(vanilla);
        return stack.isEmpty() ? com.teslamaps.profileviewer.ui.Kit.icon("name_tag") : stack;
    }

    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final Map<String, String> HELD_NAMES = Map.ofEntries(
            Map.entry("TIER_BOOST", "Tier Boost"), Map.entry("TEXTBOOK", "Textbook"),
            Map.entry("QUICK_CLAW", "Quick Claw"), Map.entry("SHARPENED_CLAWS", "Sharpened Claws"),
            Map.entry("HARDENED_SCALES", "Hardened Scales"), Map.entry("BIG_TEETH", "Big Teeth"),
            Map.entry("LUCKY_CLOVER", "Lucky Clover"), Map.entry("IRON_CLAWS", "Iron Claws"),
            Map.entry("GOLD_CLAWS", "Gold Claws"), Map.entry("WASHED_UP_SOUVENIR", "Washed-up Souvenir"),
            Map.entry("DWARF_TURTLE_SHELMET", "Dwarf Turtle Shelmet"), Map.entry("MINOS_RELIC", "Minos Relic"),
            Map.entry("GREEN_BANDANA", "Green Bandana"), Map.entry("ALL_SKILLS_SUPER_BOOST", "All Skills Super Boost"),
            Map.entry("EXP_SHARE", "Exp Share"));

    /** Readable held item name with its rarity colour code, ported from pv's PetHeldItems. */
    public static String heldItemName(String id) {
        String key = id.startsWith("PET_ITEM_") ? id.substring("PET_ITEM_".length()) : id;
        String rarity = null;
        for (String r : RARITIES) {
            if (key.endsWith("_" + r)) {
                rarity = r;
                key = key.substring(0, key.length() - r.length() - 1);
                break;
            }
        }
        String name = HELD_NAMES.get(key);
        if (name == null) {
            name = key.endsWith("_SKILL_BOOST")
                    ? prettify(key.substring(0, key.length() - "_SKILL_BOOST".length())) + " Exp Boost"
                    : prettify(key);
        }
        String code = rarity == null ? "§f" : switch (rarity) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§f";
        };
        return code + name;
    }

    private static String prettify(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private static ItemStack skull(String cacheKey, String texture) {
        if (texture == null) return ItemStack.EMPTY;
        return built.computeIfAbsent(cacheKey, key -> SkyblockItems.headFromTexture(texture));
    }
}
