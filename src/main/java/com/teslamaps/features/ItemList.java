package com.teslamaps.features;

// Ported from Skyblocker's itemlist/recipebook and NEU's recipe types (crafting, forge, kat, npc shop), kept small
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.ItemListScreen;
import com.teslamaps.utils.RepoCache;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** All SkyBlock items with search, click one for its recipes from the NEU repo. */
public final class ItemList {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/%s.json";
    private static final String[] GRID = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

    public record Ing(String id, int count) {}

    /** inputs has 9 entries (nulls allowed) when grid is true. */
    public record Recipe(String kind, List<Ing> inputs, boolean grid, Ing output, String info) {}

    private static final Map<String, List<Recipe>> recipes = new ConcurrentHashMap<>();
    private static final java.util.Set<String> pending = ConcurrentHashMap.newKeySet();

    private ItemList() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean enabled() {
        return cfg().itemList && cfg().section("Inventory");
    }

    public static void register() {
        Feature.of("ItemList").section("Inventory")
                .config("Inventory", b -> {
                    b.label("Item List");
                    b.toggle("Item List & Recipe Book", () -> cfg().itemList, v -> cfg().itemList = v)
                            .desc("Every SkyBlock item with search; click one for its crafting, forge and Kat recipes. /tmap items [search]");
                    b.keybind("  Open Item List", () -> cfg().itemListKey, v -> cfg().itemListKey = v);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("items")
                        .executes(c -> open(""))
                        .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                .executes(c -> open(StringArgumentType.getString(c, "query")))))));
    }

    private static int open(String query) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled()) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Enable Inventory > Item List & Recipe Book first."));
            return 1;
        }
        mc.execute(() -> mc.gui.setScreen(new ItemListScreen(query)));
        return 1;
    }

    /** Hotkey from {@link ConfigHotkey}; toggles the screen outside of other screens. */
    public static boolean handleKey(long window, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || mc.getWindow() == null || window != mc.getWindow().handle()) return false;
        boolean ours = mc.gui.screen() instanceof ItemListScreen;
        if (mc.gui.screen() != null && !ours) return false;
        if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) {
            if (ours) {
                mc.gui.setScreen(null);
            } else {
                ItemListScreen s = new ItemListScreen("");
                s.swallowNextChar = true;
                mc.gui.setScreen(s);
            }
        }
        return true;
    }

    /** Recipes of an item (NEU id), null while loading. */
    public static List<Recipe> recipesOrNull(String id) {
        List<Recipe> r = recipes.get(id);
        if (r != null) return r;
        if (pending.add(id)) {
            CompletableFuture.runAsync(() -> {
                try {
                    recipes.put(id, load(id));
                } finally {
                    pending.remove(id);
                }
            });
        }
        return null;
    }

    private static List<Recipe> load(String id) {
        List<Recipe> out = new ArrayList<>();
        try {
            String neu = id.replace(':', '-');
            String file = URLEncoder.encode(neu, StandardCharsets.UTF_8).replace("+", "%20");
            JsonElement root = RepoCache.fetch("neu-item-" + neu, String.format(URL, file), 15);
            if (!root.isJsonObject()) return out;
            JsonObject item = root.getAsJsonObject();
            if (item.has("recipe") && item.get("recipe").isJsonObject()) {
                Recipe r = crafting(item.getAsJsonObject("recipe"), neu);
                if (r != null) out.add(r);
            }
            if (item.has("recipes") && item.get("recipes").isJsonArray()) {
                for (JsonElement el : item.getAsJsonArray("recipes")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String type = str(o, "type", "crafting");
                    Recipe r = switch (type) {
                        case "crafting" -> crafting(o, neu);
                        case "forge" -> forge(o, neu);
                        case "katgrade" -> kat(o);
                        case "npc_shop" -> shop(o);
                        default -> null;
                    };
                    if (r != null) out.add(r);
                }
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("[ItemList] recipes for {} failed: {}", id, e.toString());
        }
        return out;
    }

    private static Recipe crafting(JsonObject o, String self) {
        Ing[] grid = new Ing[9];
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            String v = str(o, GRID[i], "");
            if (v.isEmpty()) continue;
            grid[i] = ing(v);
            any = true;
        }
        if (!any) return null;
        String outId = str(o, "overrideOutputId", self);
        return new Recipe("Crafting", Arrays.asList(grid), true, new Ing(outId, intOf(o, "count", 1)), null);
    }

    private static Recipe forge(JsonObject o, String self) {
        List<Ing> in = list(o, "inputs");
        String outId = str(o, "overrideOutputId", self);
        long secs = o.has("duration") ? o.get("duration").getAsLong() : 0;
        return new Recipe("Forge", in, false, new Ing(outId, intOf(o, "count", 1)), "§7Duration: §e" + ForgeRecipeViewer.time(secs));
    }

    private static Recipe kat(JsonObject o) {
        List<Ing> in = new ArrayList<>();
        String input = str(o, "input", "");
        if (!input.isEmpty()) in.add(ing(input));
        in.addAll(list(o, "items"));
        long coins = o.has("coins") ? o.get("coins").getAsLong() : 0;
        long secs = o.has("time") ? o.get("time").getAsLong() : 0;
        String info = "§7Coins: §6" + String.format(Locale.ROOT, "%,d", coins) + " §7· Time: §e" + ForgeRecipeViewer.time(secs);
        return new Recipe("Kat Upgrade", in, false, ing(str(o, "output", "?")), info);
    }

    private static Recipe shop(JsonObject o) {
        String result = str(o, "result", "");
        if (result.isEmpty()) return null;
        return new Recipe("NPC Shop", list(o, "cost"), false, ing(result), null);
    }

    private static List<Ing> list(JsonObject o, String key) {
        List<Ing> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                if (e.isJsonPrimitive()) out.add(ing(e.getAsString()));
            }
        }
        return out;
    }

    private static Ing ing(String v) {
        int colon = v.lastIndexOf(':');
        if (colon > 0) {
            try {
                return new Ing(v.substring(0, colon), (int) Math.round(Double.parseDouble(v.substring(colon + 1))));
            } catch (NumberFormatException ignored) {}
        }
        return new Ing(v, 1);
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static int intOf(JsonObject o, String key, int def) {
        try {
            return o.has(key) ? Math.max(1, o.get(key).getAsInt()) : def;
        } catch (Exception e) {
            return def;
        }
    }
}
