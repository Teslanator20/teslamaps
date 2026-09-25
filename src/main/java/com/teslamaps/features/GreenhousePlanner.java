package com.teslamaps.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.Inflater;

// Greenhouse layout ghost: decode a planner code (v1_ + base64url + zlib json),
// find the 10x10 plot the player stands on and draw what goes where.
// Two passes: first the floor block under every cell, then the plants on top.
public class GreenhousePlanner {

    public static final int PLOT = 10;

    public static final int PHASE_AUTO = 0, PHASE_FLOOR = 1, PHASE_PLANTS = 2;

    // anything that can act as the greenhouse floor - the plot has to stay findable
    // both before and after the ground is converted
    private static final Set<Block> FLOOR = Set.of(
            Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.FARMLAND,
            Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD,
            Blocks.MOSS_BLOCK, Blocks.DIRT_PATH, Blocks.END_STONE, Blocks.SAND,
            Blocks.RED_SAND, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.GRAVEL,
            Blocks.CLAY, Blocks.NETHERRACK, Blocks.SNOW_BLOCK, Blocks.MAGMA_BLOCK);

    // growth surface per plant, keyed by norm() so the planner's id spelling does not matter.
    // mutation surfaces from the wiki mutation table, base crops from their vanilla soil.
    private static final Map<String, String> DEFAULT_SOIL = new java.util.HashMap<>();
    static {
        String farmland = "minecraft:farmland", soulSand = "minecraft:soul_sand",
                sand = "minecraft:sand", mycelium = "minecraft:mycelium", endStone = "minecraft:end_stone";
        for (String s : new String[]{"CHORUSFRUIT", "TIMESTALK"}) DEFAULT_SOIL.put(s, endStone);
        for (String s : new String[]{"ASHWREATH", "WITHERBLOOM", "CINDERSHADE", "ZOMBUD",
                "PHANTOMLEAF", "NETHERWART"}) DEFAULT_SOIL.put(s, soulSand);
        for (String s : new String[]{"BLASTBERRY", "MAGICJELLYBEAN", "ALLINALOE", "GLASSCORN",
                "CACTUS", "SUGARCANE"}) DEFAULT_SOIL.put(s, sand);
        for (String s : new String[]{"VEILSHROOM", "REDMUSHROOM", "BROWNMUSHROOM"}) DEFAULT_SOIL.put(s, mycelium);
        DEFAULT_SOIL.put("COCOABEAN", farmland);
        for (String s : new String[]{"CHOCONUT", "DUSTGRAIN", "GLOOMGOURD", "LONELILY", "SCOURROOT",
                "SHADEVINE", "CHOCOBERRY", "COALROOT", "CREAMBLOOM", "DUSKBLOOM", "THORNSHADE",
                "CHEESEBITE", "CHLORONITE", "DONOTEATSHROOM", "FLESHTRAP", "NOCTILUME", "SNOOZLING",
                "SOGGYBUD", "TURTLELLINI", "PLANTBOYADVANCE", "PUFFERCLOUD", "SHELLFRUIT",
                "STARTLEVINE", "STOPLIGHTPETAL", "THUNDERLING", "DEVOURER", "GODSEED", "JERRYFLOWER",
                "WHEAT", "POTATO", "CARROT", "PUMPKIN", "MELON", "COCOABEANS", "SUNFLOWER",
                "MOONFLOWER", "WILDROSE", "FERMENTO"}) DEFAULT_SOIL.put(s, farmland);
    }

    // skull textures seen in game, so the plants are identified without learning anything
    private static final Map<String, String> DEFAULT_HEADS = new java.util.HashMap<>();
    static {
        DEFAULT_HEADS.put("72d802cd207f1971a2eb826f1a7477740833c920db00cd5c992176c67672dbf5", "SHELLFRUIT");
        DEFAULT_HEADS.put("c5214cc92140cdf9b402b3e7ed1fa0bac7fb4b39e39b46d11b06301caf0f9c3d", "CHORUSFRUIT");
        DEFAULT_HEADS.put("e044238503102f64f30f4d58f0843b215d8dd54fc9f1f70cc17d8dd891bb3e5e", "CHORUSFRUIT");
        DEFAULT_HEADS.put("4c2b797e7172a05169e313739908515864d6b372f9a5ecc772f81d9c4e402a54", "STOPLIGHTPETAL");
        DEFAULT_HEADS.put("18bd4aa55673e90a3c611117277d94f6ce185b5d13d2a862a3376f50a6139c4f", "PUMPKIN");
        DEFAULT_HEADS.put("360549bf880605bba628e89b1cca4b8a0e428b61d879f45edd9f45469d87aec4", "MELON");
        DEFAULT_HEADS.put("d4b3ea5cb6b6f046e326621ca11ffb7d6aec22d66c0d81e5039b19ee4400309f", "CACTUS");
        DEFAULT_HEADS.put("e1f5cb495ba97bf9c05c15b8c9cc866c14c1fe14807fed5802a0bf68deec8912", "COCOABEAN");
        DEFAULT_HEADS.put("162f0e8d32f3db02ffdf1a16efb21d614a3f0417d49138fdc18bfe52ead705b9", "REDMUSHROOM");
        DEFAULT_HEADS.put("b40d6fc1e1b67c58d7f82350bcac083f9e9547f9131236463164417fbdd3bee4", "SUNFLOWER");
        DEFAULT_HEADS.put("f341905af17c74a1c6181a56c88d8f91853f2cff0a9a33aaa16c0d835fdceece", "WILDROSE");
    }

    // longest first, so CHORUSFRUITSTEM does not lose to a shorter key
    private static final List<String> PLANT_KEYS = new ArrayList<>();
    static {
        PLANT_KEYS.addAll(DEFAULT_SOIL.keySet());
        PLANT_KEYS.sort((a, b) -> b.length() - a.length());
    }

    // stages the hover name actually carries, not the wiki growth count
    private static final Map<String, Integer> MAX_STAGE = Map.of("TIMESTALK", 2);

    // "timestalk2" -> 2. A leftover that is not pure digits is a model part
    // (chorusFruitStem) or a colour variant (stoplightpetalGreen1), not a stage.
    private static int stageOf(String rawName, String plant) {
        if (plant == null || rawName == null) return -1;
        String n = norm(rawName);
        if (!n.startsWith(plant)) return -1;
        String rest = n.substring(plant.length());
        if (rest.isEmpty() || rest.length() > 3) return -1;
        for (int i = 0; i < rest.length(); i++) {
            if (!Character.isDigit(rest.charAt(i))) return -1;
        }
        return Integer.parseInt(rest);
    }

    // hover names carry a suffix per model part and growth stage: chorusFruitStem,
    // stoplightpetalGreen1, ... so match on the longest known plant the name starts with
    private static String resolvePlant(String rawName) {
        if (rawName == null || rawName.isEmpty()) return null;
        String n = norm(rawName);
        if (n.isEmpty()) return null;
        for (String key : PLANT_KEYS) {
            if (n.startsWith(key)) return key;
        }
        return null;
    }

    // planner ids are inconsistent (CHORUSFRUIT but STOPLIGHT-PETAL), so match on letters only
    private static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(Character.toUpperCase(c));
        }
        return b.toString();
    }

    private static final Map<String, Integer> COLORS = Map.of(
            "TIMESTALK", 0xFFFFAA00,
            "SHELLFRUIT", 0xFF55FFFF,
            "CHORUSFRUIT", 0xFFAA00AA,
            "STOPLIGHT-PETAL", 0xFFFF5555);

    private record Cell(int gx, int gy, String item) {}

    private static JsonObject parsed;
    private static int plotCount = 1;
    private static final List<Cell> cells = new ArrayList<>();

    // resolved render lists, rebuilt on tick
    public static final int TODO = 0, OK = 1, WRONG = 2, UNKNOWN = 3, MUTATION = 4;
    private record Ghost(AABB box, int color, String label, int state, boolean focus, boolean model) {}
    public record ModelCell(int x, int y, int z, String plant) {}
    public static final List<ModelCell> modelCells = new ArrayList<>();
    private record Todo(int wx, int wz, int gx, int gy, String item) {}
    private static final List<Todo> todoCells = new ArrayList<>();
    private static final List<Ghost> floorGhosts = new ArrayList<>();
    private static final List<Ghost> plantGhosts = new ArrayList<>();
    private static int floorOk, floorTotal, placed, total, wrong, mutGrown, mutTotal;

    private static BlockPos anchor;      // min corner of the plot, y = floor level
    private static int tickCounter;

    // ---------------------------------------------------------------- code

    public static boolean hasLayout() {
        return parsed != null && !cells.isEmpty();
    }

    public static String status() {
        if (!hasLayout()) return "§7no layout loaded";
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return "§a" + cells.size() + " plants§7, plot §e" + cfg.greenhousePlot
                + "§7/" + plotCount + ", rotation §e" + (cfg.greenhouseRotation * 90) + "°"
                + (cfg.greenhouseMirror ? " §7mirrored" : "")
                + "§7, phase §e" + phaseName(cfg.greenhousePhase)
                + (anchor == null ? " §c· no 10x10 plot found"
                        : " §7· floor §f" + floorOk + "/" + floorTotal + "§7, plants §f" + placed + "/" + total
                                + (mutTotal > 0 ? "§7, mutation §d" + mutGrown + "/" + mutTotal : ""));
    }

    public static String phaseName(int phase) {
        return switch (phase) {
            case PHASE_FLOOR -> "Floor";
            case PHASE_PLANTS -> "Plants";
            default -> "Auto";
        };
    }

    // Planner links from SkyShards and SkyMutations, ported from Skyblocker's GreenhousePaste / SkyShardsLayout.
    // Index = Skyblocker crop id: 1..40 mutations, then the base crops.
    private static final String[] CROP_BY_ID = {null,
            "ASHWREATH", "CHOCONUT", "DUSTGRAIN", "GLOOMGOURD", "LONELILY", "SCOURROOT", "SHADEVINE", "VEILSHROOM",
            "WITHERBLOOM", "CHOCOBERRY", "CINDERSHADE", "COALROOT", "CREAMBLOOM", "DUSKBLOOM", "THORNSHADE",
            "BLASTBERRY", "CHEESEBITE", "CHLORONITE", "DONOTEATSHROOM", "FLESHTRAP", "MAGICJELLYBEAN", "NOCTILUME",
            "SNOOZLING", "SOGGYBUD", "CHORUSFRUIT", "PLANTBOYADVANCE", "PUFFERCLOUD", "SHELLFRUIT", "STARTLEVINE",
            "STOPLIGHTPETAL", "THUNDERLING", "TURTLELLINI", "ZOMBUD", "ALLINALOE", "DEVOURER", "GLASSCORN", "GODSEED",
            "JERRYFLOWER", "PHANTOMLEAF", "TIMESTALK",
            "COCOABEAN", "WILDROSE", "MOONFLOWER", "SUNFLOWER", "CACTUS", "MELON", "PUMPKIN", "BROWNMUSHROOM",
            "REDMUSHROOM", "WHEAT", "CARROT", "POTATO", "NETHERWART", "SUGARCANE", "DEADPLANTS", "FIRE",
            "HELIANTHUS", "FERMENTO", "SQUASH", "CROPIE"};
    // SkyShards palette order before the 40 mutations
    private static final int[] SHARDS_CROPS = {50, 52, 51, 47, 46, 41, 54, 45, 53, 49, 48, 43, 44, 42, 56, 55, 58};

    private static String cropName(String name) {
        String n = norm(name);
        return switch (n) {
            case "WHEATSEEDS" -> "WHEAT";
            case "PUMPKINSEEDS" -> "PUMPKIN";
            case "MELONSEEDS" -> "MELON";
            case "COCOABEANS" -> "COCOABEAN";
            default -> n;
        };
    }

    private static String linkCode(String text) {
        String t = text.strip();
        int i = t.indexOf("layout=");
        if (i >= 0) t = t.substring(i + 7);
        else if ((i = t.indexOf("/share/")) >= 0) t = t.substring(i + 7);
        else return null;
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            boolean ok = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '=' || c == '+' || c == '$';
            if (!ok) return t.substring(0, k);
        }
        return t;
    }

    private static void addCell(JsonArray m, int gx, int gy, String item, List<String> mutations) {
        if (gx < 0 || gy < 0 || gx >= PLOT || gy >= PLOT || item == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("x", gx);
        o.addProperty("y", gy);
        o.addProperty("i", item);
        o.addProperty("p", 0);
        m.add(o);
        if (mutations != null && !mutations.contains(item)) mutations.add(item);
    }

    private static JsonObject importShards(String code) {
        try {
            byte[] compressed = Base64.getUrlDecoder().decode(code.replace('+', '-').replace('/', '_'));
            String s;
            try (var in = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(compressed),
                    new Inflater(true))) {
                byte[] out = in.readNBytes(4096);
                if (in.read() != -1) return null;
                s = new String(out, java.nio.charset.StandardCharsets.UTF_8);
            }
            String[] parts = s.split("\\|", -1);
            if (parts.length != 3) return null;
            int[] inputs = palette(parts[0]), targets = palette(parts[1]);
            if (inputs == null || targets == null) return null;
            String grid = parts[2];
            int w = grid.length() == 100 ? 1 : grid.length() == 200 ? 2 : 0;
            if (w == 0) return null;
            JsonArray m = new JsonArray();
            List<String> mutations = new ArrayList<>();
            for (int cell = 0; cell < 100; cell++) {
                String chars = grid.substring(cell * w, (cell + 1) * w);
                boolean upper = chars.equals(chars.toUpperCase(java.util.Locale.ENGLISH));
                if (!upper && !chars.equals(chars.toLowerCase(java.util.Locale.ENGLISH))) continue;
                int idx = 0;
                boolean valid = true;
                for (int k = 0; k < chars.length(); k++) {
                    char c = Character.toLowerCase(chars.charAt(k));
                    if (c < 'a' || c > 'z') { valid = false; break; }
                    idx = idx * 26 + (c - 'a');
                }
                int[] pal = upper ? targets : inputs;
                if (!valid || idx >= pal.length) continue;
                int id = pal[idx];
                int row = cell / 10, col = cell % 10;
                int gx = col, gy = row;
                if (id == 23) { gx += 1; gy += 2; }
                else if (id == 37) { gx += 1; gy += 1; }
                addCell(m, gx, gy, id < CROP_BY_ID.length ? CROP_BY_ID[id] : null, upper ? mutations : null);
            }
            if (m.isEmpty()) return null;
            addMutations(mutations);
            JsonObject obj = new JsonObject();
            obj.add("m", m);
            obj.addProperty("p", 1);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] palette(String s) {
        if (s.isEmpty()) return new int[0];
        String[] idx = s.split(",", -1);
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {
            int v;
            try {
                v = Integer.parseInt(idx[i], 36);
            } catch (NumberFormatException e) {
                return null;
            }
            if (v < 0 || v >= SHARDS_CROPS.length + 40) return null;
            out[i] = v < SHARDS_CROPS.length ? SHARDS_CROPS[v] : v - SHARDS_CROPS.length + 1;
        }
        return out;
    }

    private static JsonObject importMutations(String code) {
        try {
            String json = com.teslamaps.utils.LZString.decompressFromEncodedURIComponent(code);
            if (json == null || json.isEmpty()) return null;
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            JsonArray m = new JsonArray();
            List<String> mutations = new ArrayList<>();
            for (var el : arr) {
                JsonArray e = el.getAsJsonArray();
                int x = e.get(0).getAsInt(), z = e.get(1).getAsInt();
                String item = cropName(e.get(2).getAsString());
                boolean target = e.get(3).getAsInt() == 0;
                addCell(m, 9 - x, z, item, target ? mutations : null);
            }
            if (m.isEmpty()) return null;
            addMutations(mutations);
            JsonObject obj = new JsonObject();
            obj.add("m", m);
            obj.addProperty("p", 1);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static void addMutations(List<String> targets) {
        List<String> list = TeslaMapsConfig.get().greenhouseMutations;
        for (String t : targets) if (!list.contains(t)) list.add(t);
    }

    private static JsonObject importLink(String text) {
        String code = linkCode(text);
        if (code == null) return null;
        String host = text.toLowerCase(java.util.Locale.ENGLISH);
        if (host.contains("skymutations")) return importMutations(code);
        if (host.contains("skyshards")) return importShards(code);
        JsonObject o = importShards(code);
        return o != null ? o : importMutations(code);
    }

    public static String savePreset(String name) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (cfg.greenhouseCode == null || cfg.greenhouseCode.isBlank()) return "§cno layout loaded";
        cfg.greenhousePresets.put(name.trim(), cfg.greenhouseCode);
        TeslaMapsConfig.save();
        return "§fSaved preset §e" + name.trim();
    }

    public static String loadPreset(String name) {
        String code = null;
        for (var e : TeslaMapsConfig.get().greenhousePresets.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name.trim())) code = e.getValue();
        }
        if (code == null) return "§cno preset named " + name.trim();
        String err = load(code);
        rescan();
        return err == null ? "§fLoaded preset §e" + name.trim() + " §7· " + status() : "§c" + err;
    }

    public static String deletePreset(String name) {
        boolean removed = TeslaMapsConfig.get().greenhousePresets.keySet().removeIf(k -> k.equalsIgnoreCase(name.trim()));
        TeslaMapsConfig.save();
        return removed ? "§fDeleted preset §e" + name.trim() : "§cno preset named " + name.trim();
    }

    public static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPresets(
            com.mojang.brigadier.suggestion.SuggestionsBuilder sb) {
        String typed = sb.getRemaining();
        for (String n : presetNames()) {
            if (n.toLowerCase().startsWith(typed.toLowerCase()) && !n.equalsIgnoreCase(typed)) sb.suggest(n);
        }
        return sb.buildFuture();
    }

    public static java.util.Set<String> presetNames() {
        return TeslaMapsConfig.get().greenhousePresets.keySet();
    }

    // returns null on success, otherwise an error message
    public static String load(String code) {
        JsonObject linked = code.contains("layout=") || code.contains("/share/") ? importLink(code) : null;
        if (linked != null) {
            parsed = linked;
            plotCount = 1;
            TeslaMapsConfig cfg = TeslaMapsConfig.get();
            cfg.greenhousePlot = 0;
            cfg.greenhouseCode = code.trim();
            rebuild();
            TeslaMapsConfig.save();
            return cells.isEmpty() ? "that link holds no plants" : null;
        }
        try {
            String body = code.trim();
            int us = body.indexOf('_');
            if (us > 0 && us <= 4 && body.charAt(0) == 'v') body = body.substring(us + 1);
            body = body.replace('-', '+').replace('_', '/');
            while (body.length() % 4 != 0) body += "=";
            byte[] raw = Base64.getDecoder().decode(body);
            String json = inflate(raw);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("m")) return "no plant list in the code";
            parsed = obj;
            plotCount = obj.has("p") && obj.get("p").isJsonPrimitive() ? Math.max(1, obj.get("p").getAsInt()) : 1;
            TeslaMapsConfig cfg = TeslaMapsConfig.get();
            if (cfg.greenhousePlot >= plotCount) cfg.greenhousePlot = 0;
            cfg.greenhouseCode = code.trim();
            rebuild();
            TeslaMapsConfig.save();
            return cells.isEmpty() ? "plot " + cfg.greenhousePlot + " is empty in this code" : null;
        } catch (Exception e) {
            parsed = null;
            cells.clear();
            floorGhosts.clear();
            plantGhosts.clear();
            return "could not read that code (" + e.getClass().getSimpleName() + ")";
        }
    }

    public static void clear() {
        parsed = null;
        cells.clear();
        floorGhosts.clear();
        plantGhosts.clear();
        anchor = null;
        TeslaMapsConfig.get().greenhouseCode = "";
        TeslaMapsConfig.save();
    }

    public static void loadStored() {
        String code = TeslaMapsConfig.get().greenhouseCode;
        if (code != null && !code.isBlank()) load(code);
    }

    public static int plotCount() {
        return plotCount;
    }

    private static String inflate(byte[] raw) throws Exception {
        Inflater inf = new Inflater();
        inf.setInput(raw);
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length * 4);
        byte[] buf = new byte[4096];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            out.write(buf, 0, n);
        }
        inf.end();
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    // pull the plants of the selected plot out of the parsed json
    public static void rebuild() {
        cells.clear();
        floorGhosts.clear();
        plantGhosts.clear();
        if (parsed == null) return;
        int plot = TeslaMapsConfig.get().greenhousePlot;
        JsonArray arr = parsed.getAsJsonArray("m");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            int p = o.has("p") ? o.get("p").getAsInt() : 0;
            if (p != plot) continue;
            int gx = o.get("x").getAsInt();
            int gy = o.get("y").getAsInt();
            if (gx < 0 || gy < 0 || gx >= PLOT || gy >= PLOT) continue;
            String item = o.has("i") ? o.get("i").getAsString() : "?";
            cells.add(new Cell(gx, gy, item));
        }
        pickFocus();
    }

    // the plant worth looking at. The mutation is the one the rest of the plot feeds,
    // so it is the rarest kind in the layout - overridable when that guess is wrong.
    private static String focusPlant;

    private static void pickFocus() {
        focusPlant = null;
        String set = TeslaMapsConfig.get().greenhouseFocus;
        if (set != null && !set.isBlank()) {
            if (set.equalsIgnoreCase("all")) return;
            focusPlant = norm(set);
            return;
        }
        // a mutation in the layout is the whole point of the plot, so it wins the focus
        for (Cell c : cells) {
            if (isMutation(c.item)) {
                focusPlant = norm(c.item);
                return;
            }
        }
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Cell c : cells) counts.merge(norm(c.item), 1, Integer::sum);
        int fewest = Integer.MAX_VALUE;
        for (var e : counts.entrySet()) {
            if (e.getValue() < fewest) {
                fewest = e.getValue();
                focusPlant = e.getKey();
            }
        }
        if (counts.size() < 2) focusPlant = null;
    }

    public static String focusName() {
        return focusPlant == null ? "all" : pretty(focusPlant);
    }

    // ---------------------------------------------------------------- mutations

    // plants the player never places - they appear on their own once the neighbours are right.
    // Marked by hand because the same plant can be planted in one layout and mutated in the next.
    public static boolean isMutation(String item) {
        List<String> m = TeslaMapsConfig.get().greenhouseMutations;
        return m != null && m.contains(norm(item));
    }

    public static String toggleMutation(String plant) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        String p = norm(plant);
        String msg;
        if (p.equals("CLEAR") || p.equals("NONE")) {
            cfg.greenhouseMutations.clear();
            msg = "§fMutations cleared";
        } else if (cfg.greenhouseMutations.remove(p)) {
            msg = "§f" + pretty(p) + " §7is planted by hand again";
        } else {
            cfg.greenhouseMutations.add(p);
            msg = "§f" + pretty(p) + " §7counts as a §dmutation§7 now";
        }
        TeslaMapsConfig.save();
        pickFocus();
        tickCounter = 0;
        return msg;
    }

    // every plant of the loaded layout, mutations marked
    public static List<String> mutationList() {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Cell c : cells) seen.add(norm(c.item));
        if (seen.isEmpty()) {
            out.add("§7no layout loaded");
            return out;
        }
        for (String p : seen) {
            out.add(isMutation(p) ? "§d  " + pretty(p) + " §8· mutation" : "§7  " + pretty(p));
        }
        for (String p : TeslaMapsConfig.get().greenhouseMutations) {
            if (!seen.contains(p)) out.add("§d  " + pretty(p) + " §8· mutation (not in this plot)");
        }
        return out;
    }

    public static String setFocus(String value) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        cfg.greenhouseFocus = value == null || value.equalsIgnoreCase("auto") ? "" : value.trim();
        TeslaMapsConfig.save();
        pickFocus();
        tickCounter = 0;
        return "§fFocus: §e" + focusName();
    }

    // ---------------------------------------------------------------- floor requirements

    private static String soilId(String plant) {
        String key = norm(plant);
        Map<String, String> user = TeslaMapsConfig.get().greenhouseSoil;
        if (user != null) {
            String id = user.get(key);
            if (id != null && !id.isBlank()) return id;
        }
        return DEFAULT_SOIL.get(key);
    }

    private static Block soilBlock(String plant) {
        String id = soilId(plant);
        if (id == null) return null;
        Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        return b == Blocks.AIR ? null : b;
    }

    // returns an error message, or null on success
    public static String setSoil(String plant, String blockId) {
        String p = norm(plant);
        String id = blockId.trim().toLowerCase(Locale.ROOT);
        if (id.equals("none") || id.equals("clear")) {
            TeslaMapsConfig.get().greenhouseSoil.remove(p);
            TeslaMapsConfig.save();
            return null;
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        Block b;
        try {
            b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        } catch (Exception e) {
            return "not a block id: " + blockId;
        }
        if (b == Blocks.AIR) return "unknown block: " + id;
        TeslaMapsConfig.get().greenhouseSoil.put(p, id);
        TeslaMapsConfig.save();
        return null;
    }

    // only what the loaded layout actually uses, plus every hand-set override
    public static Map<String, String> soilTable() {
        Map<String, String> out = new TreeMap<>();
        for (Cell c : cells) {
            String id = soilId(c.item);
            out.put(pretty(c.item), id == null ? "§c- not set -" : id);
        }
        Map<String, String> user = TeslaMapsConfig.get().greenhouseSoil;
        if (user != null) user.forEach((k, v) -> out.put(pretty(k), v));
        return out;
    }

    // read the floor under every already planted cell and remember it per plant.
    // returns a chat-ready line; errors are prefixed with a red colour code
    public static String learn() {
        if (anchor == null) return "§cno plot found - stand on the greenhouse";
        if (!hasLayout()) return "§cno layout loaded";
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "§cno world";
        scanPlants();
        Map<String, Map<String, Integer>> votes = new LinkedHashMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Cell c : cells) {
            int[] off = mapCell(c.gx, c.gy);
            int wx = anchor.getX() + off[0], wz = anchor.getZ() + off[1];
            m.set(wx, anchor.getY() + 1, wz);
            // nothing planted here, the floor proves nothing - except on a mutation cell,
            // where the floor is all the player ever places
            if (!isMutation(c.item) && !standAt.containsKey(key(wx, wz))
                    && mc.level.getBlockState(m).isAir()) continue;
            m.set(wx, anchor.getY(), wz);
            Block floor = mc.level.getBlockState(m).getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(floor).toString();
            votes.computeIfAbsent(norm(c.item), k -> new LinkedHashMap<>()).merge(id, 1, Integer::sum);
        }
        if (votes.isEmpty()) return "§cnothing planted yet - plant one of each first, then learn";
        StringBuilder sb = new StringBuilder();
        for (var e : votes.entrySet()) {
            String best = null;
            int bestN = 0;
            for (var v : e.getValue().entrySet()) {
                if (v.getValue() > bestN) { bestN = v.getValue(); best = v.getKey(); }
            }
            TeslaMapsConfig.get().greenhouseSoil.put(e.getKey(), best);
            if (sb.length() > 0) sb.append("§7, ");
            sb.append("§f").append(pretty(e.getKey())).append(" §7= §a").append(prettyBlock(best));
        }
        TeslaMapsConfig.save();
        tickCounter = 0;
        return "§fLearned: " + sb;
    }

    // ---------------------------------------------------------------- tick

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.greenhousePlanner || !hasLayout()) {
            if (!floorGhosts.isEmpty() || !plantGhosts.isEmpty()) {
                floorGhosts.clear();
                plantGhosts.clear();
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // the greenhouse only exists in the Garden - never scan for dirt anywhere else
        if (!com.teslamaps.utils.SkyblockUtils.isInGarden()) {
            if (anchor != null || !plantGhosts.isEmpty() || !floorGhosts.isEmpty()) {
                anchor = null;
                floorGhosts.clear();
                plantGhosts.clear();
                standAt.clear();
            }
            tickCounter = 20;
            return;
        }

        // right after arriving the chunk entities may not be there yet, so keep re-checking
        // the orientation for a moment instead of trusting the very first frame
        if (pendingOrient > 0 && anchor != null && !cfg.greenhouseManual) {
            pendingOrient--;
            scanPlants();
            int[] r = bestOrientation();
            if (r[2] > r[3] && r[2] != r[4]) {
                cfg.greenhouseRotation = r[0];
                cfg.greenhouseMirror = r[1] == 1;
                cfg.greenhouseRotations.put(anchorKey(), r[0] + "," + (r[1] == 1));
                TeslaMapsConfig.save();
            }
        }

        if (--tickCounter > 0) return;

        if (cfg.greenhouseManual) {
            anchor = new BlockPos(cfg.greenhouseAnchorX, cfg.greenhouseAnchorY, cfg.greenhouseAnchorZ);
            tickCounter = 5;
        } else {
            BlockPos before = anchor;
            if (anchor == null || !verifyAnchor()) findPlot();
            if (anchor != null && !anchor.equals(before)) onPlotChanged();
            tickCounter = anchor == null ? 60 : 5;   // back off while there is no plot in range
        }
        buildGhosts();
    }

    public static void rescan() {
        anchor = null;
        tickCounter = 0;
    }

    // ---------------------------------------------------------------- manual corners

    private static BlockPos pendingCorner;

    private static BlockPos targetBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return bhr.getBlockPos();
        }
        if (mc.player == null) return null;
        return mc.player.blockPosition().below();
    }

    public static String corner1() {
        BlockPos p = targetBlock();
        if (p == null) return "§cno block under the cursor";
        pendingCorner = p;
        return "§fCorner 1 set to §e" + p.getX() + " " + p.getY() + " " + p.getZ()
                + "§7 · now look at the opposite corner and run /tmap greenhouse corner2";
    }

    public static String corner2() {
        if (pendingCorner == null) return "§cset corner1 first";
        BlockPos p = targetBlock();
        if (p == null) return "§cno block under the cursor";
        int minX = Math.min(pendingCorner.getX(), p.getX());
        int minZ = Math.min(pendingCorner.getZ(), p.getZ());
        int sx = Math.abs(pendingCorner.getX() - p.getX()) + 1;
        int sz = Math.abs(pendingCorner.getZ() - p.getZ()) + 1;
        int y = Math.min(pendingCorner.getY(), p.getY());
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        cfg.greenhouseManual = true;
        cfg.greenhouseAnchorX = minX;
        cfg.greenhouseAnchorY = y;
        cfg.greenhouseAnchorZ = minZ;
        TeslaMapsConfig.save();
        pendingCorner = null;
        anchor = new BlockPos(minX, y, minZ);
        tickCounter = 0;
        String warn = (sx != PLOT || sz != PLOT) ? " §c(" + sx + "x" + sz + ", expected 10x10 - using the min corner)" : "";
        return "§fPlot pinned at §e" + minX + " " + y + " " + minZ + "§7 (floor level)" + warn;
    }

    // scan for the plot right now, then match the orientation against the floor
    public static String autoPlot() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!com.teslamaps.utils.SkyblockUtils.isInGarden()) return "§cnot in the Garden";
        cfg.greenhouseManual = false;
        pendingCorner = null;
        anchor = null;
        findPlot();
        tickCounter = 0;
        if (anchor == null) {
            TeslaMapsConfig.save();
            return "§cno 10x10 plot found nearby - stand on the greenhouse";
        }
        String rot = autoRotate();
        TeslaMapsConfig.save();
        return "§fPlot at §e" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ() + "§7 · " + rot;
    }

    private static boolean isFloor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState st = mc.level.getBlockState(pos);
        return FLOOR.contains(st.getBlock());
    }

    private static int pendingOrient;

    private static String anchorKey() {
        return anchor.getX() + "," + anchor.getY() + "," + anchor.getZ();
    }

    // every greenhouse can sit at its own angle, so the rotation belongs to the plot,
    // not to the player. Remembered ones apply instantly, the rest is measured.
    private static void onPlotChanged() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        String stored = cfg.greenhouseRotations.get(anchorKey());
        if (stored != null) {
            String[] parts = stored.split(",");
            try {
                cfg.greenhouseRotation = Integer.parseInt(parts[0].trim()) & 3;
                cfg.greenhouseMirror = parts.length > 1 && Boolean.parseBoolean(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        pendingOrient = 30;
    }

    // a hand-set rotation wins over anything measured, and sticks to this plot
    public static void pinRotation() {
        pendingOrient = 0;
        if (anchor == null) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        cfg.greenhouseRotations.put(anchorKey(), cfg.greenhouseRotation + "," + cfg.greenhouseMirror);
    }

    private static boolean verifyAnchor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        BlockPos p = mc.player.blockPosition();
        if (Math.abs(p.getX() - anchor.getX()) > 20 || Math.abs(p.getZ() - anchor.getZ()) > 20
                || Math.abs(p.getY() - anchor.getY()) > 20) return false;
        // a few samples are enough to notice we left the plot / it changed
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int hits = 0;
        for (int dx = 0; dx < PLOT; dx += 3) {
            for (int dz = 0; dz < PLOT; dz += 3) {
                m.set(anchor.getX() + dx, anchor.getY(), anchor.getZ() + dz);
                if (isFloor(m)) hits++;
            }
        }
        return hits >= 12;
    }

    // best 10x10 all-floor square near the player; prefers one whose surrounding ring is not floor
    private static void findPlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        anchor = null;
        BlockPos pp = mc.player.blockPosition();
        final int R = 18;
        final int W = R * 2 + 1;
        int baseX = pp.getX() - R, baseZ = pp.getZ() - R;

        boolean[] soil = new boolean[W * W];
        int[] sum = new int[(W + 1) * (W + 1)];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bestScore = Integer.MAX_VALUE;
        BlockPos best = null;

        for (int dy = 2; dy >= -6; dy--) {
            int y = pp.getY() + dy;
            if (y < mc.level.getMinY() || y > mc.level.getMaxY()) continue;
            boolean any = false;
            for (int ix = 0; ix < W; ix++) {
                for (int iz = 0; iz < W; iz++) {
                    m.set(baseX + ix, y, baseZ + iz);
                    boolean s = isFloor(m);
                    soil[ix * W + iz] = s;
                    any |= s;
                }
            }
            if (!any) continue;

            // summed area table over the floor mask
            for (int ix = 0; ix < W; ix++) {
                for (int iz = 0; iz < W; iz++) {
                    sum[(ix + 1) * (W + 1) + iz + 1] = (soil[ix * W + iz] ? 1 : 0)
                            + sum[ix * (W + 1) + iz + 1] + sum[(ix + 1) * (W + 1) + iz] - sum[ix * (W + 1) + iz];
                }
            }

            // the ring has to fit inside the scan window, otherwise it cannot be checked
            for (int ix = 1; ix + PLOT + 1 <= W; ix++) {
                for (int iz = 1; iz + PLOT + 1 <= W; iz++) {
                    if (rect(sum, W, ix, iz, PLOT, PLOT) != PLOT * PLOT) continue;
                    // every one of the 44 blocks around the plot must NOT be floor material,
                    // so a patch inside a bigger dirt field never counts as a greenhouse
                    if (rect(sum, W, ix - 1, iz - 1, PLOT + 2, PLOT + 2) != PLOT * PLOT) continue;
                    int cx = baseX + ix + PLOT / 2, cz = baseZ + iz + PLOT / 2;
                    int dist = Math.abs(cx - pp.getX()) + Math.abs(cz - pp.getZ());
                    int score = dist + Math.abs(dy) * 2;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(baseX + ix, y, baseZ + iz);
                    }
                }
            }
        }
        anchor = best;
    }

    private static int rect(int[] sum, int w, int x, int z, int sx, int sz) {
        int s = w + 1;
        return sum[(x + sx) * s + z + sz] - sum[x * s + z + sz] - sum[(x + sx) * s + z] + sum[x * s + z];
    }

    // planner (gx,gy) -> world offset inside the plot
    private static int[] mapCell(int gx, int gy) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return mapCell(gx, gy, cfg.greenhouseRotation, cfg.greenhouseMirror);
    }

    private static int[] mapCell(int gx, int gy, int rotation, boolean mirror) {
        int x = mirror ? PLOT - 1 - gx : gx;
        int z = gy;
        return switch (rotation & 3) {
            case 1 -> new int[]{PLOT - 1 - z, x};
            case 2 -> new int[]{PLOT - 1 - x, PLOT - 1 - z};
            case 3 -> new int[]{z, PLOT - 1 - x};
            default -> new int[]{x, z};
        };
    }

    // how well the layout fits the plot under one orientation. An identified plant is worth
    // far more than a floor block, and a mismatch counts against it - otherwise an all-farmland
    // plot would score the same everywhere.
    private static int scoreOrientation(int rotation, boolean mirror) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || anchor == null) return 0;
        int fy = anchor.getY();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int score = 0;
        for (Cell c : cells) {
            int[] off = mapCell(c.gx, c.gy, rotation, mirror);
            int wx = anchor.getX() + off[0], wz = anchor.getZ() + off[1];

            Block need = soilBlock(c.item);
            if (need != null) {
                m.set(wx, fy, wz);
                score += mc.level.getBlockState(m).is(need) ? 1 : -1;
            }
            String is = plantOf(standAt.get(key(wx, wz)));
            if (is != null) score += is.equals(norm(c.item)) ? 6 : -6;
        }
        return score;
    }

    // {rotation, mirror, best score, runner up, worst score}
    private static int[] bestOrientation() {
        int bestRot = 0, best = Integer.MIN_VALUE, second = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
        int bestMir = 0;
        for (int rot = 0; rot < 4; rot++) {
            for (int mi = 0; mi < 2; mi++) {
                int s = scoreOrientation(rot, mi == 1);
                min = Math.min(min, s);
                if (s > best) {
                    second = best;
                    best = s;
                    bestRot = rot;
                    bestMir = mi;
                } else if (s > second) {
                    second = s;
                }
            }
        }
        return new int[]{bestRot, bestMir, best, second, min};
    }

    // pick the orientation whose floor (and plants, if any) match the layout best
    public static String autoRotate() {
        if (!hasLayout()) return "§cno layout loaded";
        if (anchor == null) return "§cno plot found";
        scanPlants();

        int[] r = bestOrientation();
        int bestRot = r[0], best = r[2], second = r[3];
        boolean bestMir = r[1] == 1;
        if (best == r[4]) return "§7floor is still untouched - nothing to match, rotation unchanged";

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        cfg.greenhouseRotation = bestRot;
        cfg.greenhouseMirror = bestMir;
        cfg.greenhouseRotations.put(anchorKey(), bestRot + "," + bestMir);
        TeslaMapsConfig.save();
        tickCounter = 0;
        pendingOrient = 0;
        String warn = best == second ? " §c(tie - check the yellow 0,0 marker)" : "";
        return "§fRotation §e" + (bestRot * 90) + "°" + (bestMir ? " mirrored" : "")
                + " §7(score " + best + " vs " + second + ")" + warn;
    }

    // Hypixel renders greenhouse plants as armor stands with a head item, not as blocks.
    // Per cell we keep the lowest such stand plus an identity we can compare against the layout.
    private record Stand(String id, String name, String plant, int stage, double y) {}
    private static final Map<Long, Stand> standAt = new java.util.HashMap<>();

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static void scanPlants() {
        standAt.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || anchor == null) return;
        AABB area = new AABB(anchor.getX(), anchor.getY() - 1, anchor.getZ(),
                anchor.getX() + PLOT, anchor.getY() + 4, anchor.getZ() + PLOT);
        for (net.minecraft.world.entity.decoration.ArmorStand stand
                : mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, area)) {
            var head = stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            if (head.isEmpty()) continue;
            long k = key(net.minecraft.util.Mth.floor(stand.getX()), net.minecraft.util.Mth.floor(stand.getZ()));
            String hash = HeadESP.hashOf(head);
            String name = com.teslamaps.utils.StringUtil.stripTrim(
                    stand.hasCustomName() ? stand.getName().getString() : head.getHoverName().getString());
            String plant = resolvePlant(name);
            if (plant == null && hash != null) plant = DEFAULT_HEADS.get(hash);
            int stage = stageOf(name, plant);

            // a cell holds several stands (stem, fruit, model parts). Identified beats
            // unidentified, a stage number beats none, otherwise take the lowest.
            Stand prev = standAt.get(k);
            boolean better;
            if (prev == null) better = true;
            else if ((plant != null) != (prev.plant != null)) better = plant != null;
            else if ((stage >= 0) != (prev.stage >= 0)) better = stage >= 0;
            else better = stand.getY() < prev.y;
            if (!better) continue;

            String id = hash != null ? "h:" + hash : "n:" + norm(name);
            standAt.put(k, new Stand(id, name, plant, stage, stand.getY()));
        }
    }

    // what plant an identity belongs to, or null while we have never seen it
    private static String plantOf(Stand s) {
        if (s == null) return null;
        if (s.plant != null) return s.plant;
        Map<String, String> learned = TeslaMapsConfig.get().greenhousePlants;
        return learned == null ? null : learned.get(s.id);
    }

    // treat the plot as correct and remember every head that is in it
    public static String learnPlants() {
        if (anchor == null) return "§cno plot found";
        if (!hasLayout()) return "§cno layout loaded";
        scanPlants();
        int n = 0;
        for (Cell c : cells) {
            int[] off = mapCell(c.gx, c.gy);
            Stand s = standAt.get(key(anchor.getX() + off[0], anchor.getZ() + off[1]));
            if (s == null) continue;
            TeslaMapsConfig.get().greenhousePlants.put(s.id, norm(c.item));
            n++;
        }
        TeslaMapsConfig.save();
        tickCounter = 0;
        return n == 0 ? "§cnothing planted in the plot"
                : "§fLearned §a" + n + "§f cells · wrong plants now show red";
    }

    // /tmap greenhouse todo - the cells the plant counter is still missing, nearest first
    public static List<String> todoReport() {
        List<String> out = new ArrayList<>();
        if (anchor == null) {
            out.add("§cno plot found");
            return out;
        }
        if (!hasLayout()) {
            out.add("§cno layout loaded");
            return out;
        }
        buildGhosts();
        if (todoCells.isEmpty()) {
            out.add("§anothing missing" + (mutTotal > 0 ? " §7· " + (mutTotal - mutGrown) + " mutation cells still empty" : ""));
            return out;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.position();
        List<Todo> sorted = new ArrayList<>(todoCells);
        sorted.sort((a, b) -> Double.compare(
                eye.distanceToSqr(a.wx + 0.5, eye.y, a.wz + 0.5),
                eye.distanceToSqr(b.wx + 0.5, eye.y, b.wz + 0.5)));
        out.add("§f" + sorted.size() + " missing §7(nearest first)");
        for (Todo t : sorted) {
            double d = Math.sqrt(eye.distanceToSqr(t.wx + 0.5, eye.y, t.wz + 0.5));
            out.add("§7  " + pretty(t.item) + " §8at §e" + t.wx + " " + (anchor.getY() + 1) + " " + t.wz
                    + " §8cell " + t.gx + "," + t.gy + " §7" + String.format(Locale.ROOT, "%.0f", d) + "m");
        }
        return out;
    }

    // /tmap greenhouse debug - what is actually standing in the plot
    public static List<String> debugDump() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || anchor == null) {
            out.add("§cno plot found");
            return out;
        }
        out.add("§7plot §f" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ()
                + "§7 floor=§f" + BuiltInRegistries.BLOCK.getKey(
                        mc.level.getBlockState(anchor).getBlock()));
        AABB area = new AABB(anchor.getX(), anchor.getY() - 2, anchor.getZ(),
                anchor.getX() + PLOT, anchor.getY() + 5, anchor.getZ() + PLOT);

        // one line per distinct head instead of one per stand - a full plot is ~300 stands
        // and a per-stand dump gets cut off long before the interesting ones show up
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        Map<String, String> hashes = new java.util.HashMap<>();
        Map<String, String> firstCell = new java.util.HashMap<>();
        int n = 0, headless = 0;
        for (net.minecraft.world.entity.decoration.ArmorStand stand
                : mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, area)) {
            var head = stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            if (head.isEmpty()) {
                headless++;
                continue;
            }
            n++;
            String hover = com.teslamaps.utils.StringUtil.stripTrim(head.getHoverName().getString());
            counts.merge(hover, 1, Integer::sum);
            hashes.putIfAbsent(hover, HeadESP.hashOf(head));
            firstCell.putIfAbsent(hover, (net.minecraft.util.Mth.floor(stand.getX()) - anchor.getX())
                    + "," + (net.minecraft.util.Mth.floor(stand.getZ()) - anchor.getZ())
                    + " dy=" + String.format(Locale.ROOT, "%.2f", stand.getY() - anchor.getY()));
        }
        if (n == 0) {
            out.add("§7no armor stands with a head in the plot (" + headless + " without)");
            return out;
        }
        out.add("§7" + n + " heads, " + counts.size() + " kinds, " + headless + " stands without a head");
        for (var e : counts.entrySet()) {
            String hash = hashes.get(e.getKey());
            out.add("§f" + e.getKey() + " §7x" + e.getValue()
                    + " §8first " + firstCell.get(e.getKey())
                    + " §7-> §e" + (resolvePlant(e.getKey()) == null ? "unknown" : resolvePlant(e.getKey()))
                    + " §8stage " + stageOf(e.getKey(), resolvePlant(e.getKey()))
                    + " §b" + (hash == null ? "-" : hash));
        }
        return out;
    }

    private static void buildGhosts() {
        floorGhosts.clear();
        plantGhosts.clear();
        floorOk = 0;
        floorTotal = 0;
        placed = 0;
        total = 0;
        wrong = 0;
        mutGrown = 0;
        mutTotal = 0;
        todoCells.clear();
        modelCells.clear();
        if (anchor == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        JsonArray unlocked = null;
        if (parsed != null && parsed.has("u") && parsed.get("u").isJsonArray()) {
            JsonArray u = parsed.getAsJsonArray("u");
            int plot = TeslaMapsConfig.get().greenhousePlot;
            if (plot < u.size() && u.get(plot).isJsonArray()) unlocked = u.get(plot).getAsJsonArray();
        }

        scanPlants();

        int fy = anchor.getY();
        int py = fy + 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Cell c : cells) {
            if (unlocked != null && !isUnlocked(unlocked, c.gx, c.gy)) continue;
            int[] off = mapCell(c.gx, c.gy);
            int wx = anchor.getX() + off[0];
            int wz = anchor.getZ() + off[1];

            Block need = soilBlock(c.item);
            if (need != null) {
                floorTotal++;
                m.set(wx, fy, wz);
                if (mc.level.getBlockState(m).is(need)) {
                    floorOk++;
                } else {
                    String id = BuiltInRegistries.BLOCK.getKey(need).toString();
                    AABB box = new AABB(wx + 0.06, fy + 0.06, wz + 0.06, wx + 0.94, fy + 0.94, wz + 0.94);
                    floorGhosts.add(new Ghost(box, color(id), prettyBlock(id), TODO, true, false));
                }
            }

            m.set(wx, py, wz);
            Stand stand = standAt.get(key(wx, wz));
            boolean here = stand != null || !mc.level.getBlockState(m).isAir();
            boolean mutation = isMutation(c.item);
            if (mutation) mutTotal++;
            else total++;
            boolean focus = focusPlant == null || focusPlant.equals(norm(c.item));

            int state;
            String label = pretty(c.item);
            int col = color(c.item);
            if (!here) {
                state = mutation ? MUTATION : TODO;
                if (mutation) label = pretty(c.item) + " §8· mutation";
                else todoCells.add(new Todo(wx, wz, c.gx, c.gy, c.item));
            } else {
                if (mutation) mutGrown++;
                else placed++;
                String is = plantOf(stand);
                if (is == null) {
                    state = UNKNOWN;
                } else if (is.equals(norm(c.item))) {
                    state = OK;
                    if (focusPlant != null && focus && stand != null && stand.stage >= 0
                            && TeslaMapsConfig.get().greenhouseStages) {
                        int max = maxStage(is);
                        col = stageColor(stand.stage, max);
                        label = pretty(c.item) + " §7" + stand.stage + "§8/§7" + max;
                    }
                } else {
                    state = WRONG;
                    wrong++;
                    col = 0xFFFF3030;
                    label = "§c" + pretty(is) + " §7-> §f" + pretty(c.item);
                }
            }
            AABB box = new AABB(wx + 0.06, py + 0.01, wz + 0.06, wx + 0.94, py + 0.95, wz + 0.94);
            boolean model = state == TODO && com.teslamaps.features.garden.GreenhouseModels.has(norm(c.item));
            if (model) modelCells.add(new ModelCell(wx, py, wz, norm(c.item)));
            plantGhosts.add(new Ghost(box, col, label, state, focus, model));
        }
    }

    private static boolean isUnlocked(JsonArray layer, int gx, int gy) {
        if (gx >= layer.size() || !layer.get(gx).isJsonArray()) return true;
        JsonArray row = layer.get(gx).getAsJsonArray();
        if (gy >= row.size()) return true;
        return row.get(gy).getAsBoolean();
    }

    // config value wins, then the wiki number, then the ripest stand standing in the plot
    private static int maxStage(String plant) {
        int set = TeslaMapsConfig.get().greenhouseStageMax;
        if (set > 0) return set;
        int seen = 1;
        for (Stand s : standAt.values()) {
            if (plant.equals(s.plant)) seen = Math.max(seen, s.stage);
        }
        Integer known = MAX_STAGE.get(plant);
        return known != null ? Math.max(known, seen) : seen;
    }

    // red while young, through yellow, to green when ripe
    private static int stageColor(int stage, int max) {
        float t = max <= 1 ? 1f : Math.min(1f, Math.max(0f, (stage - 1) / (float) (max - 1)));
        if (TeslaMapsConfig.get().greenhouseStageInverted) t = 1f - t;
        int r = t < 0.5f ? 255 : (int) (255 * (1 - (t - 0.5f) * 2));
        int g = t < 0.5f ? (int) (255 * t * 2) : 255;
        return 0xFF000000 | (r << 16) | (g << 8) | 0x30;
    }

    // per plant: how many stand in the plot and how they spread across the stages
    public static List<String> stageReport() {
        List<String> out = new ArrayList<>();
        if (anchor == null) {
            out.add("§cno plot found");
            return out;
        }
        scanPlants();
        Map<String, java.util.TreeMap<Integer, Integer>> byPlant = new TreeMap<>();
        Map<String, Integer> totals = new TreeMap<>();
        int unknown = 0;
        for (Stand s : standAt.values()) {
            if (s.plant == null) {
                unknown++;
                continue;
            }
            totals.merge(s.plant, 1, Integer::sum);
            byPlant.computeIfAbsent(s.plant, k -> new java.util.TreeMap<>()).merge(s.stage, 1, Integer::sum);
        }
        if (totals.isEmpty()) {
            out.add("§7nothing identified in the plot");
            return out;
        }
        for (var e : totals.entrySet()) {
            StringBuilder sb = new StringBuilder("§f" + pretty(e.getKey()) + " §7x" + e.getValue() + " §8· ");
            var stages = byPlant.get(e.getKey());
            if (stages.size() == 1 && stages.containsKey(-1)) {
                sb.append("§7no stage in the name");
            } else {
                boolean first = true;
                for (var st : stages.entrySet()) {
                    if (!first) sb.append("§8, ");
                    first = false;
                    sb.append(st.getKey() < 0 ? "§7none" : "§astage " + st.getKey());
                    sb.append("§7 x").append(st.getValue());
                }
                sb.append(" §8(max used: ").append(maxStage(e.getKey())).append(")");
            }
            out.add(sb.toString());
        }
        if (unknown > 0) out.add("§7" + unknown + " unidentified stands");
        return out;
    }

    private static int color(String item) {
        Integer c = COLORS.get(item);
        if (c != null) return c;
        int h = item.hashCode();
        return 0xFF000000 | (((h >> 16) & 0x7F) + 0x60) << 16 | (((h >> 8) & 0x7F) + 0x60) << 8 | ((h & 0x7F) + 0x60);
    }

    private static String pretty(String item) {
        return prettyWords(item);
    }

    private static String prettyBlock(String id) {
        int i = id.indexOf(':');
        return prettyWords(i < 0 ? id : id.substring(i + 1));
    }

    private static String prettyWords(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- render

    private static int effectivePhase() {
        int phase = TeslaMapsConfig.get().greenhousePhase;
        if (phase == PHASE_FLOOR || phase == PHASE_PLANTS) return phase;
        return floorOk < floorTotal ? PHASE_FLOOR : PHASE_PLANTS;
    }

    public static boolean modelsVisible() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return cfg.greenhousePlanner && anchor != null && hasLayout() && effectivePhase() == PHASE_PLANTS
                && com.teslamaps.utils.SkyblockUtils.isInGarden();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.greenhousePlanner || anchor == null) return;
        if (!com.teslamaps.utils.SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int phase = effectivePhase();
        List<Ghost> ghosts = phase == PHASE_FLOOR ? floorGhosts : plantGhosts;
        if (ghosts.isEmpty() && phase == PHASE_PLANTS) return;

        int alpha = Math.max(8, Math.min(255, (int) (cfg.greenhouseAlpha * 255))) << 24;
        boolean hideDone = cfg.greenhouseHideDone;

        boolean dim = cfg.greenhouseDimOthers && phase == PHASE_PLANTS;
        // while anything is missing or wrong, that IS the job - mutations and ripeness
        // only take over once the plot is done
        boolean clean = todoCells.isEmpty() && wrong == 0;
        boolean stages = cfg.greenhouseStages && focusPlant != null && clean;

        ESPRenderer.beginBoxBatch(matrices, cameraPos, true, cfg.greenhouseThroughWalls);
        for (Ghost g : ghosts) {
            // dimming is for finished clutter - an unplanted cell is the job, never fade it out
            boolean faint = dim && !g.focus && g.state != TODO && g.state != WRONG;
            if (g.model && cfg.greenhouseModels && phase == PHASE_PLANTS) continue;
            if (g.state == TODO || (g.state == MUTATION && clean)) {
                int a = faint ? (alpha >>> 2) & 0xFF000000 : alpha;
                ESPRenderer.batchBox(g.box, (g.color & 0x00FFFFFF) | Math.max(0x14000000, a), 0f);
            } else if (g.state == WRONG) {
                // a wrong plant is worth more than the ghost alpha, it has to be seen and pulled out
                ESPRenderer.batchBox(g.box, 0x90FF3030, 0f);
            } else if (g.state == OK && g.focus && stages) {
                ESPRenderer.batchBox(g.box, (g.color & 0x00FFFFFF) | 0x50000000, 0f);
            }
        }
        ESPRenderer.endBoxBatch();

        ESPRenderer.beginBoxBatch(matrices, cameraPos, false, cfg.greenhouseThroughWalls);
        for (Ghost g : ghosts) {
            // dimming is for finished clutter - an unplanted cell is the job, never fade it out
            boolean faint = dim && !g.focus && g.state != TODO && g.state != WRONG;
            switch (g.state) {
                case WRONG -> ESPRenderer.batchBox(g.box, 0xFFFF3030, 3.0f);
                // waiting for a mutation, not something to go and plant
                case MUTATION -> { if (clean) ESPRenderer.batchBox(g.box, 0xFFFF55FF, 2.5f); }
                case UNKNOWN -> { if (!hideDone) ESPRenderer.batchBox(g.box, faint ? 0x30FFFF55 : 0x80FFFF55, 1.5f); }
                case OK -> {
                    if (g.focus && stages) ESPRenderer.batchBox(g.box, g.color, 2.5f);
                    else if (!hideDone) ESPRenderer.batchBox(g.box, faint ? 0x2055FF55 : 0x6055FF55, 1.5f);
                }
                default -> ESPRenderer.batchBox(g.box, faint ? (g.color & 0x00FFFFFF) | 0x40000000 : g.color,
                        faint ? 1.0f : 2.0f);
            }
        }
        // plot border + the planner origin corner so the rotation can be lined up
        double by = anchor.getY() + (phase == PHASE_FLOOR ? 0.0 : 1.0);
        AABB border = new AABB(anchor.getX(), by, anchor.getZ(),
                anchor.getX() + PLOT, by + 0.02, anchor.getZ() + PLOT);
        ESPRenderer.batchBox(border, 0xFFFFFFFF, 2.5f);
        int[] o = mapCell(0, 0);
        AABB origin = new AABB(anchor.getX() + o[0], by, anchor.getZ() + o[1],
                anchor.getX() + o[0] + 1, by + 1, anchor.getZ() + o[1] + 1);
        ESPRenderer.batchBox(origin, 0xFFFFFF55, 3.0f);
        ESPRenderer.endBoxBatch();

        if (cfg.greenhouseLabels) {
            // nearest first in distance bands, otherwise the budget goes to whatever
            // happens to be early in the list and labels flicker while walking
            Vec3 eye = mc.player.getEyePosition();
            double[] bands = {9, 36, 81, 169};
            int drawn = 0;
            double prev = 0;
            for (double band : bands) {
                for (Ghost g : ghosts) {
                    boolean grownFocus = g.state == OK && g.focus && stages;
                    if (!grownFocus && (g.state == OK || g.state == UNKNOWN)) continue;
                    if (g.state == MUTATION && !clean) continue;
                    if (dim && !g.focus && g.state != WRONG && g.state != TODO) continue;
                    Vec3 c = g.box.getCenter();
                    double d = c.distanceToSqr(eye);
                    if (d > band || d <= prev) continue;
                    ESPRenderer.drawText(matrices, g.label, new Vec3(c.x, g.box.maxY + 0.15, c.z), 0.8f, cameraPos, false);
                    if (++drawn >= 48) break;
                }
                if (drawn >= 48) break;
                prev = band;
            }
        }

        String head = phase == PHASE_FLOOR
                ? "§6Step 1 · Floor §f" + floorOk + "§7/§f" + floorTotal
                : "§aStep 2 · Plants §f" + placed + "§7/§f" + total
                        + (mutTotal > 0 ? " §d· mutation " + mutGrown + "§7/§d" + mutTotal : "")
                        + (wrong > 0 ? " §c· " + wrong + " wrong" : "")
                        + (dim && focusPlant != null ? " §8· " + pretty(focusPlant) : "");
        ESPRenderer.drawText(matrices, head,
                new Vec3(anchor.getX() + PLOT / 2.0, anchor.getY() + 3.0, anchor.getZ() + PLOT / 2.0),
                1.2f, cameraPos);
        if (phase == PHASE_FLOOR && floorTotal < total + mutTotal) {
            ESPRenderer.drawText(matrices, "§7" + (total + mutTotal - floorTotal) + " plants have no floor block set §8· /tmap greenhouse learn",
                    new Vec3(anchor.getX() + PLOT / 2.0, anchor.getY() + 2.65, anchor.getZ() + PLOT / 2.0),
                    0.9f, cameraPos);
        }
    }
}
