package com.teslamaps.features.hunting;

// Ported from SkyOcean's SkyShardsRecipeTree.kt / SkyShardsTreeResolver.kt (SkyShards "SkyOcean Format" export)
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** A SkyShards fusion tree: parse the clipboard export, walk it against Hunting Box + inventory. */
public final class ShardFusionTree {

    public record Node(String shard, int quantity, String method, List<Node> inputs) {}

    private ShardFusionTree() {}

    /** Null when the text is not a SkyShards SkyOcean-format export. */
    public static Node parse(String text) {
        if (text == null) return null;
        String t = text.trim();
        int colon = t.indexOf(':');
        if (colon < 0 || !t.regionMatches(true, 0, "<SkyOceanRecipe>(V", 0, 18)) return null;
        try {
            byte[] raw = Base64.getMimeDecoder().decode(t.substring(colon + 1).trim());
            String json;
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonElement root = JsonParser.parseString(json);
            return root.isJsonObject() ? node(root.getAsJsonObject()) : null;
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("[ShardTree] parse failed: {}", e.toString());
            return null;
        }
    }

    private static Node node(JsonObject o) {
        String shard = o.has("shard") ? o.get("shard").getAsString() : "?";
        int qty = o.has("quantity") ? o.get("quantity").getAsInt() : 1;
        String method = o.has("method") ? o.get("method").getAsString().toLowerCase(Locale.ROOT) : "direct";
        List<Node> inputs = new ArrayList<>();
        if (o.has("inputs") && o.get("inputs").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("inputs")) if (e.isJsonObject()) inputs.add(node(e.getAsJsonObject()));
        }
        return new Node(shard, qty, method, List.copyOf(inputs));
    }

    public static ShardData.Shard shard(String raw) {
        if (raw == null) return null;
        String id = raw.contains(":") ? raw.substring(raw.lastIndexOf(':') + 1) : raw;
        ShardData.Shard s = ShardData.byShardId(id);
        if (s == null) s = ShardData.bySkyblockId(id.toUpperCase(Locale.ROOT));
        if (s == null) s = ShardData.byDisplayName(id);
        return s;
    }

    public static String name(String raw) {
        ShardData.Shard s = shard(raw);
        if (s == null) return "§f" + raw;
        String col = switch (s.rarity()) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            default -> "§f";
        };
        return col + s.displayName();
    }

    /** HUD lines plus the number of nodes still missing (index 0 of {@code missing}). */
    public static List<String> lines(Node root, int[] missing) {
        ShardData.load();
        List<String> out = new ArrayList<>();
        out.add("§6Shards: " + name(root.shard()) + " §7x" + root.quantity());
        walk(root, root.quantity(), 0, "", true, new HashMap<>(), out, missing);
        return out;
    }

    private static void walk(Node n, long need, int depth, String indent, boolean last,
                             Map<String, Long> used, List<String> out, int[] missing) {
        if (need <= 0) return;
        ShardData.Shard s = shard(n.shard());
        long owned = 0;
        String key = s != null ? s.bazaarId() : n.shard();
        if (s != null) owned = ShardData.inBox(s) + ShardData.inInventory(s) - used.getOrDefault(key, 0L);
        long have = Math.max(0, Math.min(owned, need));
        used.merge(key, have, Long::sum);
        long lack = need - have;
        String branch = depth == 0 ? "" : indent + (last ? "└ " : "├ ");
        String tag = n.method().equals("cycle") ? " §8(cycle)" : "";
        out.add("§7" + branch + (lack == 0 ? "§a✔ " : "§c✖ ") + name(n.shard()) + " §7" + have + "/" + need + tag);
        if (lack == 0) return;
        if (n.inputs().isEmpty()) {
            missing[0]++;
            return;
        }
        double factor = (double) lack / Math.max(1, n.quantity());
        String childIndent = depth == 0 ? "" : indent + (last ? "  " : "│ ");
        for (int i = 0; i < n.inputs().size(); i++) {
            Node c = n.inputs().get(i);
            walk(c, (long) Math.ceil(c.quantity() * factor), depth + 1, childIndent, i == n.inputs().size() - 1, used, out, missing);
        }
    }
}
