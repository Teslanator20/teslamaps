package com.teslamaps.profileviewer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.Ui;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Greenhouse mutations, ported from skyblock-pv's FarmingPage.greenhouse(). */
public class Greenhouse {
    public record Mutation(String id, String name, String rarity, boolean analyzable) {}

    private static final List<String> RARITY_ORDER = List.of("LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON");

    private static List<Mutation> mutations;
    private static Map<String, JsonObject> renders;
    private static final Map<String, ItemStack> icons = new HashMap<>();

    private static JsonElement read(String file) {
        try (InputStream stream = Greenhouse.class.getResourceAsStream("/assets/teslamaps/pv/" + file)) {
            if (stream == null) return null;
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read {}: {}", file, e.toString());
            return null;
        }
    }

    public static List<Mutation> mutations() {
        if (mutations != null) return mutations;
        List<Mutation> out = new ArrayList<>();
        JsonElement root = read("mutations.json");
        if (root != null && root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                out.add(new Mutation(o.get("id").getAsString(), o.get("name").getAsString(),
                        o.get("rarity").getAsString(), !o.has("analyzable") || o.get("analyzable").getAsBoolean()));
            }
        }
        out.sort(java.util.Comparator.comparingInt(m -> RARITY_ORDER.indexOf(m.rarity())));
        mutations = List.copyOf(out);
        return mutations;
    }

    private static ItemStack render(String id) {
        ItemStack cached = icons.get(id);
        if (cached != null) return cached;
        if (renders == null) {
            renders = new HashMap<>();
            JsonElement root = read("mutation_renders.json");
            if (root != null && root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonObject()) renders.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        }
        JsonObject spec = renders.get(id);
        ItemStack stack = ItemStack.EMPTY;
        if (spec != null && spec.has("tex")) stack = SkyblockItems.headFromTexture(spec.get("tex").getAsString());
        else if (spec != null && spec.has("item")) stack = Kit.icon(spec.get("item").getAsString());
        if (stack == null || stack.isEmpty()) stack = Kit.icon("gray_dye");
        icons.put(id, stack);
        return stack;
    }

    private static String color(String rarity) {
        return switch (rarity) {
            case "LEGENDARY" -> "§6";
            case "EPIC" -> "§5";
            case "RARE" -> "§9";
            case "UNCOMMON" -> "§a";
            default -> "§f";
        };
    }

    private static Set<String> set(JsonObject parent, String key) {
        Set<String> out = new HashSet<>();
        if (parent != null && parent.has(key) && parent.get(key).isJsonArray()) {
            JsonArray array = parent.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) out.add(element.getAsString().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static long num(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()) return 0;
        try {
            return parent.get(key).getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Tiles plus one row of mutation icons per rarity. */
    public static Ui build(JsonObject member, int width) {
        JsonObject data = member != null && member.has("garden_player_data") && member.get("garden_player_data").isJsonObject()
                ? member.getAsJsonObject("garden_player_data") : null;
        Set<String> analyzed = set(data, "analyzed_greenhouse_crops");
        Set<String> discovered = set(data, "discovered_greenhouse_crops");

        List<Mutation> all = mutations();
        int analyzable = 0;
        int analyzedCount = 0;
        for (Mutation m : all) {
            if (!m.analyzable()) continue;
            analyzable++;
            if (analyzed.contains(m.id())) analyzedCount++;
        }

        int slot = 18;
        int perRow = Math.max(1, (width + 2) / (slot + 2));
        List<Ui> rows = new ArrayList<>();
        for (String rarity : RARITY_ORDER) {
            List<Ui> icons = new ArrayList<>();
            for (Mutation m : all) {
                if (!m.rarity().equals(rarity)) continue;
                boolean shown = !m.analyzable() || analyzed.contains(m.id());
                String status = !m.analyzable() ? "§7Not analysable"
                        : analyzed.contains(m.id()) ? "§6§lANALYZED"
                        : discovered.contains(m.id()) ? "§a§lDISCOVERED"
                        : "§c§lUndiscovered";
                ItemStack icon = shown ? render(m.id()) : Kit.icon("gray_dye");
                icons.add(new Ui.Tip(new Ui.Frame(slot, slot, new Ui.Icon(icon, 16, false, null),
                        (ctx, x, y, w, h) -> com.teslamaps.profileviewer.screen.ItemGrid.slot(ctx, x, y, w),
                        Ui.H.CENTER, Ui.V.CENTER),
                        List.of(color(m.rarity()) + m.name(), "", status)));
            }
            for (int i = 0; i < icons.size(); i += perRow) {
                rows.add(Ui.row(2, Ui.V.TOP, new ArrayList<>(icons.subList(i, Math.min(i + perRow, icons.size())))));
            }
        }

        return Ui.col(6,
                Kit.tileRow(width, List.of(
                        new Kit.TileData("Analyzed", analyzedCount + "/" + analyzable, NeuStyle.TEXT_GOLD),
                        new Kit.TileData("Discovered", discovered.size() + "/" + all.size(), NeuStyle.TEXT_GREEN),
                        new Kit.TileData("Copper", Kit.compact(num(data, "copper")), 0xFFCD7F32),
                        new Kit.TileData("Larva Consumed", Kit.dots(num(data, "larva_consumed")), NeuStyle.TEXT_GREEN))),
                Ui.col(2, rows));
    }
}
