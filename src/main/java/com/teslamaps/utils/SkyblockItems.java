package com.teslamaps.utils;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skyblock item table: display name -> item id, and item id -> an ItemStack that
 * looks like the real thing (skull texture or vanilla item).
 * <p>
 * Hypixel's own resource endpoint carries every item and its skin, but not the
 * dyes, so ids it does not know are pulled from the NEU repo one file at a time.
 */
public class SkyblockItems {

    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final String NEU_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/%s.json";

    /** Texture value inside an NEU nbttag. Signature is keyed separately, so this only takes the skin. */
    private static final Pattern NEU_TEXTURE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"");
    private static final Pattern NEU_ITEM_ID = Pattern.compile("\"itemid\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NEU_DISPLAYNAME = Pattern.compile("\"displayname\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private record ItemData(String material, String skin, String tier) {}

    private static final Map<String, ItemData> items = new ConcurrentHashMap<>();
    private static final Map<String, String> nameToId = new ConcurrentHashMap<>();
    private static final Map<String, Double> npcPrices = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> stacks = new ConcurrentHashMap<>();
    private static final Set<String> neuTried = ConcurrentHashMap.newKeySet();

    private static boolean loaded = false;
    private static boolean loading = false;

    /** 1.8 material names that no longer resolve on their own. */
    private static final Map<String, String> LEGACY = new HashMap<>();

    static {
        LEGACY.put("SKULL_ITEM", "player_head");
        LEGACY.put("INK_SACK", "ink_sac");
        LEGACY.put("NETHER_STALK", "nether_wart");
        LEGACY.put("SULPHUR", "gunpowder");
        LEGACY.put("RAW_FISH", "cod");
        LEGACY.put("COOKED_FISH", "cooked_cod");
        LEGACY.put("PORK", "porkchop");
        LEGACY.put("GRILLED_PORK", "cooked_porkchop");
        LEGACY.put("RAW_BEEF", "beef");
        LEGACY.put("RAW_CHICKEN", "chicken");
        LEGACY.put("SEEDS", "wheat_seeds");
        LEGACY.put("CARROT_ITEM", "carrot");
        LEGACY.put("POTATO_ITEM", "potato");
        LEGACY.put("MELON", "melon_slice");
        LEGACY.put("MELON_BLOCK", "melon");
        LEGACY.put("SPECKLED_MELON", "glistering_melon_slice");
        LEGACY.put("EXP_BOTTLE", "experience_bottle");
        LEGACY.put("FIREWORK", "firework_rocket");
        LEGACY.put("FIREWORK_CHARGE", "firework_star");
        LEGACY.put("WATCH", "clock");
        LEGACY.put("EMPTY_MAP", "map");
        LEGACY.put("MAP", "filled_map");
        LEGACY.put("SNOW_BALL", "snowball");
        LEGACY.put("CLAY_BRICK", "brick");
        LEGACY.put("BRICK", "bricks");
        LEGACY.put("HARD_CLAY", "terracotta");
        LEGACY.put("STAINED_CLAY", "white_terracotta");
        LEGACY.put("ENDER_STONE", "end_stone");
        LEGACY.put("ENDER_PORTAL_FRAME", "end_portal_frame");
        LEGACY.put("WOOD_SWORD", "wooden_sword");
        LEGACY.put("WOOD_AXE", "wooden_axe");
        LEGACY.put("WOOD_PICKAXE", "wooden_pickaxe");
        LEGACY.put("WOOD_SPADE", "wooden_shovel");
        LEGACY.put("WOOD_HOE", "wooden_hoe");
        LEGACY.put("GOLD_SWORD", "golden_sword");
        LEGACY.put("GOLD_AXE", "golden_axe");
        LEGACY.put("GOLD_PICKAXE", "golden_pickaxe");
        LEGACY.put("GOLD_SPADE", "golden_shovel");
        LEGACY.put("GOLD_HOE", "golden_hoe");
        LEGACY.put("GOLD_HELMET", "golden_helmet");
        LEGACY.put("GOLD_CHESTPLATE", "golden_chestplate");
        LEGACY.put("GOLD_LEGGINGS", "golden_leggings");
        LEGACY.put("GOLD_BOOTS", "golden_boots");
        LEGACY.put("STONE_SPADE", "stone_shovel");
        LEGACY.put("IRON_SPADE", "iron_shovel");
        LEGACY.put("DIAMOND_SPADE", "diamond_shovel");
        LEGACY.put("DIAMOND_BARDING", "diamond_horse_armor");
        LEGACY.put("IRON_BARDING", "iron_horse_armor");
        LEGACY.put("GOLD_BARDING", "golden_horse_armor");
        LEGACY.put("BOOK_AND_QUILL", "writable_book");
        LEGACY.put("ENCHANTMENT_TABLE", "enchanting_table");
        LEGACY.put("WORKBENCH", "crafting_table");
        LEGACY.put("MOB_SPAWNER", "spawner");
        LEGACY.put("MONSTER_EGG", "zombie_spawn_egg");
        LEGACY.put("WEB", "cobweb");
        LEGACY.put("LONG_GRASS", "short_grass");
        LEGACY.put("GRASS", "grass_block");
        LEGACY.put("YELLOW_FLOWER", "dandelion");
        LEGACY.put("RED_ROSE", "poppy");
        LEGACY.put("DOUBLE_PLANT", "sunflower");
        LEGACY.put("STEP", "stone_slab");
        LEGACY.put("SMOOTH_BRICK", "stone_bricks");
        LEGACY.put("SMOOTH_STAIRS", "stone_brick_stairs");
        LEGACY.put("THIN_GLASS", "glass_pane");
        LEGACY.put("IRON_FENCE", "iron_bars");
        LEGACY.put("COBBLE_WALL", "cobblestone_wall");
        LEGACY.put("WOOD", "oak_planks");
        LEGACY.put("LOG", "oak_log");
        LEGACY.put("LOG_2", "acacia_log");
        LEGACY.put("LEAVES", "oak_leaves");
        LEGACY.put("LEAVES_2", "acacia_leaves");
        LEGACY.put("SAPLING", "oak_sapling");
        LEGACY.put("WOOL", "white_wool");
        LEGACY.put("CARPET", "white_carpet");
        LEGACY.put("STAINED_GLASS", "white_stained_glass");
        LEGACY.put("STAINED_GLASS_PANE", "white_stained_glass_pane");
        LEGACY.put("PISTON_BASE", "piston");
        LEGACY.put("PISTON_STICKY_BASE", "sticky_piston");
        LEGACY.put("DIODE", "repeater");
        LEGACY.put("REDSTONE_COMPARATOR", "comparator");
        LEGACY.put("REDSTONE_TORCH_ON", "redstone_torch");
        LEGACY.put("REDSTONE_LAMP_OFF", "redstone_lamp");
        LEGACY.put("RAILS", "rail");
        LEGACY.put("STORAGE_MINECART", "chest_minecart");
        LEGACY.put("POWERED_MINECART", "furnace_minecart");
        LEGACY.put("EXPLOSIVE_MINECART", "tnt_minecart");
        LEGACY.put("LEASH", "lead");
        LEGACY.put("BREWING_STAND_ITEM", "brewing_stand");
        LEGACY.put("CAULDRON_ITEM", "cauldron");
        LEGACY.put("FLOWER_POT_ITEM", "flower_pot");
        LEGACY.put("WOOD_DOOR", "oak_door");
        LEGACY.put("SIGN", "oak_sign");
        LEGACY.put("BANNER", "white_banner");
        LEGACY.put("CARROT_STICK", "carrot_on_a_stick");
        LEGACY.put("TRAP_DOOR", "oak_trapdoor");
        LEGACY.put("WOOD_STEP", "oak_slab");
        LEGACY.put("WOOD_STAIRS", "oak_stairs");
        LEGACY.put("BIRCH_WOOD_STAIRS", "birch_stairs");
        LEGACY.put("JUNGLE_WOOD_STAIRS", "jungle_stairs");
        LEGACY.put("SPRUCE_WOOD_STAIRS", "spruce_stairs");
        LEGACY.put("NETHER_BRICK_ITEM", "nether_brick");
        LEGACY.put("NETHER_BRICK", "nether_bricks");
        LEGACY.put("MYCEL", "mycelium");
        LEGACY.put("HUGE_MUSHROOM_1", "brown_mushroom_block");
        LEGACY.put("HUGE_MUSHROOM_2", "red_mushroom_block");
        LEGACY.put("COMMAND", "command_block");
        LEGACY.put("FENCE", "oak_fence");
        LEGACY.put("FENCE_GATE", "oak_fence_gate");
        LEGACY.put("GOLD_PLATE", "light_weighted_pressure_plate");
        LEGACY.put("IRON_PLATE", "heavy_weighted_pressure_plate");
        LEGACY.put("STONE_PLATE", "stone_pressure_plate");
        LEGACY.put("WOOD_PLATE", "oak_pressure_plate");
        LEGACY.put("GOLD_RECORD", "music_disc_13");
        LEGACY.put("GREEN_RECORD", "music_disc_cat");
        LEGACY.put("RABBIT", "rabbit");
        LEGACY.put("SULPHUR", "gunpowder");
    }

    /** Kicks off the one-off item table load. Safe to call repeatedly. */
    public static void load() {
        if (loaded || loading) return;
        loading = true;

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject root = JsonParser.parseString(get(ITEMS_URL)).getAsJsonObject();
                if (!root.has("items")) return;

                for (var element : root.getAsJsonArray("items")) {
                    JsonObject item = element.getAsJsonObject();
                    if (!item.has("id")) continue;
                    String id = item.get("id").getAsString();

                    String material = item.has("material") ? item.get("material").getAsString() : "";
                    String skin = null;
                    if (item.has("skin") && item.get("skin").isJsonObject()) {
                        JsonObject skinObject = item.getAsJsonObject("skin");
                        if (skinObject.has("value")) skin = skinObject.get("value").getAsString();
                    }
                    String tier = item.has("tier") ? item.get("tier").getAsString() : "";
                    items.put(id, new ItemData(material, skin, tier));

                    if (item.has("name")) {
                        String display = item.get("name").getAsString();
                        nameToId.putIfAbsent(display.toLowerCase(Locale.ROOT), id);
                        originalNames.putIfAbsent(display.toLowerCase(Locale.ROOT), display);
                        idToName.putIfAbsent(id, display);
                    }
                    if (item.has("npc_sell_price")) {
                        npcPrices.put(id, item.get("npc_sell_price").getAsDouble());
                    }
                }
                loaded = true;
                TeslaMaps.LOGGER.info("Loaded {} skyblock items", items.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Item table fetch failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    public static boolean loaded() {
        return loaded;
    }

    /** Display names for the given Hypixel item ids, in Hypixel's own casing. */
    public static java.util.Set<String> displayNames(java.util.Set<String> ids) {
        load();
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Map.Entry<String, String> entry : nameToId.entrySet()) {
            if (ids.contains(entry.getValue())) out.add(originalName(entry.getKey()));
        }
        return out;
    }

    private static final Map<String, String> originalNames = new ConcurrentHashMap<>();

    private static String originalName(String lowercase) {
        return originalNames.getOrDefault(lowercase, lowercase);
    }

    private static final Map<String, String> idToName = new ConcurrentHashMap<>();

    private static final Map<String, String> TIER_COLOR = Map.ofEntries(
            Map.entry("COMMON", "§f"),
            Map.entry("UNCOMMON", "§a"),
            Map.entry("RARE", "§9"),
            Map.entry("EPIC", "§5"),
            Map.entry("LEGENDARY", "§6"),
            Map.entry("MYTHIC", "§d"),
            Map.entry("DIVINE", "§b"),
            Map.entry("SPECIAL", "§c"),
            Map.entry("VERY_SPECIAL", "§c"),
            Map.entry("SUPREME", "§4"),
            Map.entry("ULTIMATE", "§4"),
            Map.entry("ADMIN", "§4"));

    /** Hypixel's plain display name for an item id, null when unknown. */
    public static String name(String id) {
        load();
        return id == null ? null : idToName.get(id);
    }

    private static final Map<String, String> neuNames = new ConcurrentHashMap<>();
    private static final Set<String> nameTried = ConcurrentHashMap.newKeySet();

    /**
     * Display name prefixed with its rarity colour, null when the id is unknown.
     * Ids Hypixel does not carry (skins, dyes) are pulled from the NEU repo in the
     * background, so the first call for one of those still comes back null.
     */
    public static String coloredName(String id) {
        String plain = name(id);
        if (plain != null) return TIER_COLOR.getOrDefault(tier(id), "§f") + plain;

        String neu = neuNames.get(id);
        if (neu != null) return neu;
        if (loaded && id != null && !id.isEmpty() && nameTried.add(id)) prefetch(id);
        return null;
    }

    /** NEU json uses § escapes for the colour codes. */
    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            if (next == 'u' && i + 4 < raw.length()) {
                out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                i += 4;
            } else if (next == 'n') {
                out.append('\n');
            } else {
                out.append(next);
            }
        }
        return out.toString();
    }

    /** Hypixel's own name -> id mapping, null when the name is unknown. */
    public static String idForName(String displayName) {
        load();
        return nameToId.get(displayName.trim().toLowerCase(Locale.ROOT));
    }

    /** Skyblock rarity of an item id, empty when unknown. */
    public static String tier(String id) {
        ItemData data = items.get(id);
        return data == null ? "" : data.tier();
    }

    public static double npcPrice(String id) {
        return npcPrices.getOrDefault(id, 0.0);
    }

    public static boolean hasNpcPrice(String id) {
        return npcPrices.containsKey(id);
    }

    /**
     * Hands the stack for an item id to the callback on the main thread. Ids the
     * item table does not carry are fetched from the NEU repo first, so the
     * callback can run a moment later instead of right away.
     */
    public static void resolve(String id, Consumer<ItemStack> callback) {
        if (id == null || id.isEmpty()) return;

        ItemStack cached = stacks.get(id);
        if (cached != null) {
            if (!cached.isEmpty()) callback.accept(cached);
            return;
        }

        ItemData data = items.get(id);
        if (data != null) {
            ItemStack stack = build(data.material(), data.skin());
            stacks.put(id, stack);
            if (!stack.isEmpty()) callback.accept(stack);
            return;
        }

        if (!neuTried.add(id)) return;
        CompletableFuture.runAsync(() -> {
            String material = null;
            String skin = null;
            try {
                String body = get(String.format(NEU_URL, id));
                Matcher texture = NEU_TEXTURE.matcher(body);
                if (texture.find()) skin = texture.group(1);
                Matcher itemId = NEU_ITEM_ID.matcher(body);
                if (itemId.find()) material = itemId.group(1);
                Matcher displayName = NEU_DISPLAYNAME.matcher(body);
                if (displayName.find()) neuNames.put(id, unescape(displayName.group(1)));
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("NEU lookup for {} failed: {}", id, e.toString());
            }

            String finalMaterial = material;
            String finalSkin = skin;
            Minecraft.getInstance().execute(() -> {
                ItemStack stack = build(finalMaterial, finalSkin);
                if (stack.isEmpty()) {
                    // A dead id or a failed request: let a later call try again
                    // instead of caching the miss for the whole session.
                    neuTried.remove(id);
                    return;
                }
                stacks.put(id, stack);
                callback.accept(stack);
            });
        });
    }

    /** Warms the cache so a later resolve is instant. */
    public static void prefetch(String id) {
        resolve(id, stack -> {});
    }

    private static String get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static ItemStack build(String material, String skin) {
        if (skin != null && !skin.isEmpty()) {
            ItemStack head = skull(skin);
            if (head != null) return head;
        }
        Item item = itemFor(material);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Item itemFor(String material) {
        if (material == null || material.isEmpty()) return null;

        String path = material.contains(":") ? material.substring(material.indexOf(':') + 1)
                : LEGACY.getOrDefault(material, material.toLowerCase(Locale.ROOT));
        // NEU still says "skull" where the item is now a player head.
        if (path.equals("skull")) path = "player_head";

        Identifier identifier = Identifier.tryParse("minecraft:" + path);
        if (identifier == null) return null;
        return BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.NETHER_STAR);
    }

    /** Player head with a hardcoded texture. The value is the base64 the API hands out. */
    private static ItemStack skull(String textureValue) {
        try {
            Multimap<String, Property> properties = LinkedHashMultimap.create();
            properties.put("textures", new Property("textures", textureValue));
            GameProfile profile = new GameProfile(uuidFor(textureValue), "skull", new PropertyMap(properties));

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            return head;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Skull build failed: {}", e.toString());
            return null;
        }
    }

    /** Stable per-texture uuid so the same skin is not re-resolved as a new profile. */
    private static UUID uuidFor(String textureValue) {
        return UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8));
    }
}
