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
    private static final Pattern NEU_ITEM_MODEL = Pattern.compile("ItemModel:\\\\?\"([a-z0-9_:/.\\-]+)\\\\?\"");
    private static final Pattern NEU_DAMAGE = Pattern.compile("\"damage\"\\s*:\\s*(\\d+)");

    private record ItemData(String material, int damage, String skin, String tier, String model) {}

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

    /**
     * 1.8 materials where the damage value picks the item. Without this every dye
     * is an ink sac and every wool is white.
     */
    private static final Map<String, String[]> DAMAGE = new HashMap<>();

    private static String[] colored(String prefix, String suffix) {
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        String[] out = new String[16];
        for (int i = 0; i < 16; i++) out[i] = prefix + colors[i] + suffix;
        return out;
    }

    static {
        DAMAGE.put("INK_SACK", new String[]{"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli",
                "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
                "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"});
        DAMAGE.put("WOOL", colored("", "_wool"));
        DAMAGE.put("CARPET", colored("", "_carpet"));
        DAMAGE.put("STAINED_CLAY", colored("", "_terracotta"));
        DAMAGE.put("STAINED_GLASS", colored("", "_stained_glass"));
        DAMAGE.put("STAINED_GLASS_PANE", colored("", "_stained_glass_pane"));
        DAMAGE.put("BANNER", colored("", "_banner"));
        DAMAGE.put("LOG", new String[]{"oak_log", "spruce_log", "birch_log", "jungle_log"});
        DAMAGE.put("LOG_2", new String[]{"acacia_log", "dark_oak_log"});
        DAMAGE.put("LEAVES", new String[]{"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves"});
        DAMAGE.put("LEAVES_2", new String[]{"acacia_leaves", "dark_oak_leaves"});
        DAMAGE.put("SAPLING", new String[]{"oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling",
                "acacia_sapling", "dark_oak_sapling"});
        DAMAGE.put("WOOD", new String[]{"oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
                "acacia_planks", "dark_oak_planks"});
        DAMAGE.put("WOOD_STEP", new String[]{"oak_slab", "spruce_slab", "birch_slab", "jungle_slab",
                "acacia_slab", "dark_oak_slab"});
        DAMAGE.put("STEP", new String[]{"smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab",
                "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab"});
        DAMAGE.put("STONE", new String[]{"stone", "granite", "polished_granite", "diorite", "polished_diorite",
                "andesite", "polished_andesite"});
        DAMAGE.put("SMOOTH_BRICK", new String[]{"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
                "chiseled_stone_bricks"});
        DAMAGE.put("SANDSTONE", new String[]{"sandstone", "chiseled_sandstone", "cut_sandstone"});
        DAMAGE.put("RED_SANDSTONE", new String[]{"red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone"});
        DAMAGE.put("DIRT", new String[]{"dirt", "coarse_dirt", "podzol"});
        DAMAGE.put("SAND", new String[]{"sand", "red_sand"});
        DAMAGE.put("SPONGE", new String[]{"sponge", "wet_sponge"});
        DAMAGE.put("COAL", new String[]{"coal", "charcoal"});
        DAMAGE.put("GOLDEN_APPLE", new String[]{"golden_apple", "enchanted_golden_apple"});
        DAMAGE.put("RAW_FISH", new String[]{"cod", "salmon", "tropical_fish", "pufferfish"});
        DAMAGE.put("COOKED_FISH", new String[]{"cooked_cod", "cooked_salmon"});
        DAMAGE.put("RED_ROSE", new String[]{"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"});
        DAMAGE.put("DOUBLE_PLANT", new String[]{"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"});
        DAMAGE.put("LONG_GRASS", new String[]{"dead_bush", "short_grass", "fern"});
        DAMAGE.put("PRISMARINE", new String[]{"prismarine", "prismarine_bricks", "dark_prismarine"});
        DAMAGE.put("QUARTZ_BLOCK", new String[]{"quartz_block", "chiseled_quartz_block", "quartz_pillar"});
        DAMAGE.put("COBBLE_WALL", new String[]{"cobblestone_wall", "mossy_cobblestone_wall"});
        DAMAGE.put("ANVIL", new String[]{"anvil", "chipped_anvil", "damaged_anvil"});
    }

    /** NEU's 1.8 material names for the damage-split materials above. */
    private static final Map<String, String> NEU_MATERIAL = new HashMap<>();

    static {
        NEU_MATERIAL.put("dye", "INK_SACK");
        NEU_MATERIAL.put("wool", "WOOL");
        NEU_MATERIAL.put("carpet", "CARPET");
        NEU_MATERIAL.put("stained_hardened_clay", "STAINED_CLAY");
        NEU_MATERIAL.put("stained_glass", "STAINED_GLASS");
        NEU_MATERIAL.put("stained_glass_pane", "STAINED_GLASS_PANE");
        NEU_MATERIAL.put("log", "LOG");
        NEU_MATERIAL.put("log2", "LOG_2");
        NEU_MATERIAL.put("leaves", "LEAVES");
        NEU_MATERIAL.put("leaves2", "LEAVES_2");
        NEU_MATERIAL.put("sapling", "SAPLING");
        NEU_MATERIAL.put("planks", "WOOD");
        NEU_MATERIAL.put("wooden_slab", "WOOD_STEP");
        NEU_MATERIAL.put("stone_slab", "STEP");
        NEU_MATERIAL.put("stone", "STONE");
        NEU_MATERIAL.put("stonebrick", "SMOOTH_BRICK");
        NEU_MATERIAL.put("sandstone", "SANDSTONE");
        NEU_MATERIAL.put("red_sandstone", "RED_SANDSTONE");
        NEU_MATERIAL.put("dirt", "DIRT");
        NEU_MATERIAL.put("sand", "SAND");
        NEU_MATERIAL.put("sponge", "SPONGE");
        NEU_MATERIAL.put("coal", "COAL");
        NEU_MATERIAL.put("golden_apple", "GOLDEN_APPLE");
        NEU_MATERIAL.put("fish", "RAW_FISH");
        NEU_MATERIAL.put("cooked_fish", "COOKED_FISH");
        NEU_MATERIAL.put("red_flower", "RED_ROSE");
        NEU_MATERIAL.put("double_plant", "DOUBLE_PLANT");
        NEU_MATERIAL.put("tallgrass", "LONG_GRASS");
        NEU_MATERIAL.put("prismarine", "PRISMARINE");
        NEU_MATERIAL.put("quartz_block", "QUARTZ_BLOCK");
        NEU_MATERIAL.put("cobble_wall", "COBBLE_WALL");
        NEU_MATERIAL.put("anvil", "ANVIL");
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
                    String model = item.has("item_model") ? item.get("item_model").getAsString() : null;
                    int damage = item.has("durability") ? item.get("durability").getAsInt() : 0;
                    items.put(id, new ItemData(material, damage, skin, tier, model));

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

    /** Every display name Hypixel knows, in its own casing. */
    public static java.util.Collection<String> allNames() {
        load();
        return originalNames.values();
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

    /**
     * Adds a name for an id the item table does not carry, e.g. the bazaar-only
     * shards and enchanted books. Never overwrites a name Hypixel handed out.
     */
    public static void registerName(String id, String displayName) {
        if (id == null || displayName == null || id.isEmpty() || displayName.isBlank()) return;
        String key = displayName.toLowerCase(Locale.ROOT);
        nameToId.putIfAbsent(key, id);
        originalNames.putIfAbsent(key, displayName);
        idToName.putIfAbsent(id, displayName);
    }

    private static final Map<String, String> neuAliases = new ConcurrentHashMap<>();

    /** NEU files a few ids under its own name: SHARD_GROVE is ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1. */
    public static void registerNeuAlias(String id, String neuId) {
        if (id == null || neuId == null || id.isEmpty() || neuId.isEmpty()) return;
        neuAliases.put(id, neuId);
    }

    private static String neuIdFor(String id) {
        String alias = neuAliases.get(id);
        if (alias == null) return id;
        return java.net.URLEncoder.encode(alias, StandardCharsets.UTF_8).replace("+", "%20");
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
            ItemStack stack = build(data.material(), data.damage(), data.skin(), data.model());
            stacks.put(id, stack);
            if (!stack.isEmpty()) callback.accept(stack);
            return;
        }

        if (!neuTried.add(id)) return;
        CompletableFuture.runAsync(() -> {
            String material = null;
            String skin = null;
            String model = null;
            int damage = 0;
            try {
                String body = get(String.format(NEU_URL, neuIdFor(id)));
                Matcher texture = NEU_TEXTURE.matcher(body);
                if (texture.find()) skin = texture.group(1);
                Matcher itemId = NEU_ITEM_ID.matcher(body);
                if (itemId.find()) material = itemId.group(1);
                Matcher itemModel = NEU_ITEM_MODEL.matcher(body);
                if (itemModel.find()) model = itemModel.group(1);
                Matcher neuDamage = NEU_DAMAGE.matcher(body);
                if (neuDamage.find()) damage = Integer.parseInt(neuDamage.group(1));
                Matcher displayName = NEU_DISPLAYNAME.matcher(body);
                if (displayName.find()) neuNames.put(id, unescape(displayName.group(1)));
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("NEU lookup for {} failed: {}", id, e.toString());
            }

            String finalMaterial = material;
            String finalSkin = skin;
            String finalModel = model;
            int finalDamage = damage;
            Minecraft.getInstance().execute(() -> {
                ItemStack stack = build(finalMaterial, finalDamage, finalSkin, finalModel);
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

    /**
     * The stack for an item id right now, EMPTY while a NEU lookup is still running.
     * For render paths that cannot wait for a callback.
     */
    public static ItemStack stackNow(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        ItemStack cached = stacks.get(id);
        if (cached == null) {
            resolve(id, stack -> {});
            cached = stacks.get(id);
        }
        return cached == null ? ItemStack.EMPTY : cached;
    }

    private static String get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static ItemStack build(String material, int damage, String skin, String model) {
        if (skin != null && !skin.isEmpty()) {
            ItemStack head = skull(skin);
            if (head != null) return head;
        }
        Item item = itemFor(material, damage);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        // Hypixel skins many items via the item-model component, and that model lives in the
        // server resource pack. Without it (no pack, no skyback) the item renders as the missing
        // model - the magenta and black cube - so the component is only set when the model is
        // actually loaded and the plain vanilla material is used otherwise.
        if (model != null && !model.isEmpty()) {
            Identifier modelId = Identifier.tryParse(model);
            if (modelId != null && modelLoaded(modelId)) stack.set(DataComponents.ITEM_MODEL, modelId);
        }
        return stack;
    }

    /** True when a model with that id is actually baked, i.e. some loaded pack provides it. */
    private static boolean modelLoaded(Identifier id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getModelManager() == null) return false;
        return ((com.teslamaps.mixin.ModelManagerAccessor) mc.getModelManager())
                .getBakedItemStackModels().containsKey(id);
    }

    /** A resource reload can add or drop the server pack, so the built stacks are thrown away. */
    public static void onResourceReload() {
        stacks.clear();
    }

    private static Item itemFor(String material, int damage) {
        if (material == null || material.isEmpty()) return null;

        // NEU spells the same 1.8 material "minecraft:dye" where Hypixel says INK_SACK
        String key = material.contains(":") ? material.substring(material.indexOf(':') + 1) : material;
        key = NEU_MATERIAL.getOrDefault(key.toLowerCase(Locale.ROOT), key.toUpperCase(Locale.ROOT));

        String[] variants = DAMAGE.get(key);
        String path;
        if (variants != null && damage >= 0 && damage < variants.length) path = variants[damage];
        else if (material.contains(":")) path = material.substring(material.indexOf(':') + 1);
        else path = LEGACY.getOrDefault(material, material.toLowerCase(Locale.ROOT));
        // NEU still says "skull" where the item is now a player head.
        if (path.equals("skull")) path = "player_head";

        Identifier identifier = Identifier.tryParse("minecraft:" + path);
        if (identifier == null) return null;
        return BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.NETHER_STAR);
    }

    /** A skyblock item that is a player head, with the base64 skin the API hands out. */
    public record HeadTexture(String name, String texture, String id) {}

    /** Every player-head item in the table, sorted by id. Empty until {@link #load()} finished. */
    public static java.util.List<HeadTexture> headTextures() {
        load();
        java.util.List<HeadTexture> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Map.Entry<String, ItemData> entry : items.entrySet()) {
            String skin = entry.getValue().skin();
            if (skin == null || skin.isEmpty() || !seen.add(skin)) continue;
            out.add(new HeadTexture(name(entry.getKey()), skin, entry.getKey()));
        }
        out.sort(java.util.Comparator.comparing(HeadTexture::id));
        return out;
    }

    /** Cached profile for a base64 skin, for the custom helmet textures. */
    public static ResolvableProfile profileFor(String textureValue) {
        return profileCache.computeIfAbsent(textureValue, value -> {
            Multimap<String, Property> properties = LinkedHashMultimap.create();
            properties.put("textures", new Property("textures", value));
            return ResolvableProfile.createResolved(
                    new GameProfile(uuidFor(value), "custom", new PropertyMap(properties)));
        });
    }

    private static final Map<String, ResolvableProfile> profileCache = new ConcurrentHashMap<>();

    /** Player head with a hardcoded texture. The value is the base64 the API hands out. */
    /** A player head with a base64 skin value, used by the bestiary icons too. */
    public static ItemStack headFromTexture(String textureValue) {
        ItemStack head = skull(textureValue);
        return head == null ? ItemStack.EMPTY : head;
    }

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
