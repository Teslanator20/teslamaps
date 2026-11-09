package com.teslamaps.features;

// Ported from NotEnoughUpdates' ItemTooltipListener (showReforgeStats, showGemstoneStats, hideDefaultReforgeStats)
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReforgeStoneStats {

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/%s.json";
    private static final Pattern GEM = Pattern.compile(
            "(ROUGH|FLAWED|FINE|FLAWLESS|PERFECT)_(RUBY|AMBER|SAPPHIRE|JADE|AMETHYST|TOPAZ|JASPER|OPAL|AQUAMARINE|CITRINE|ONYX|PERIDOT)_GEM");
    private static final Set<String> PERCENT = Set.of("bonus_attack_speed", "crit_damage", "crit_chance",
            "sea_creature_chance", "ability_damage");

    private static volatile JsonObject stones;
    private static volatile JsonObject gems;
    private static volatile boolean loading;

    private static String rarity = "LEGENDARY";
    private static boolean arrowLast;

    private static ItemStack lastStack;
    private static String lastRarity;
    private static List<Component> lastExtra = List.of();

    private ReforgeStoneStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature feature = Feature.of("ReforgeStoneStats")
                .when(() -> cfg().reforgeStoneStats || cfg().gemstoneStats)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Reforge Stone Stats", () -> c.reforgeStoneStats, v -> c.reforgeStoneStats = v)
                            .desc("Reforge stones list their stats, bonus and apply cost per rarity. Left/Right arrow switches the rarity.");
                    b.toggle("  Hide Hypixel's Legendary Stats", () -> c.reforgeStoneHideDefault, v -> c.reforgeStoneHideDefault = v)
                            .desc("Drops the stone's own \"(Legendary):\" stat block.");
                    b.toggle("Gemstone Stats", () -> c.gemstoneStats, v -> c.gemstoneStats = v)
                            .desc("Gemstones show their stat boost per item rarity, chisel bonus and removal cost. Arrows switch the rarity.");
                });
        RepoCache.onReload("ReforgeStoneStats", () -> { stones = null; gems = null; lastStack = null; load(); });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!stack.isEmpty() && lines.size() > 1 && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void load() {
        if (loading) return;
        loading = true;
        Thread t = new Thread(() -> {
            try {
                if (stones == null) stones = RepoCache.fetch("neu-reforgestones", String.format(REPO, "reforgestones"), 30).getAsJsonObject();
                if (gems == null) gems = RepoCache.fetch("neu-gemstones", String.format(REPO, "gemstones"), 30).getAsJsonObject();
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[ReforgeStoneStats] load failed: {}", e.toString());
            } finally {
                loading = false;
            }
        }, "teslamaps-reforgestones");
        t.setDaemon(true);
        t.start();
    }

    private static int arrow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return 0;
        long h = mc.getWindow().handle();
        boolean left = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LEFT);
        boolean right = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT);
        int dir = !arrowLast && (left || right) ? (left ? -1 : 1) : 0;
        arrowLast = left || right;
        return dir;
    }

    private static String pick(List<String> rarities) {
        int idx = rarities.indexOf(rarity);
        if (idx < 0) idx = rarities.size() - 1;
        int dir = arrow();
        if (dir != 0) {
            idx = Math.max(0, Math.min(rarities.size() - 1, idx + dir));
            rarity = rarities.get(idx);
        }
        return rarities.get(idx);
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stones == null || gems == null) {
            load();
            return;
        }
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        if (c.reforgeStoneStats && stones.has(id)) {
            if (c.reforgeStoneHideDefault) hideDefault(lines);
            reforge(stack, id, lines);
        } else if (c.gemstoneStats) {
            Matcher m = GEM.matcher(id);
            if (m.matches()) gem(stack, m.group(1), m.group(2), lines);
        }
    }

    private static void hideDefault(List<Component> lines) {
        int start = -1, cut = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String s = StringUtil.strip(lines.get(i).getString());
            if (s.contains("REFORGE STONE") || s.contains("Requires Mining Skill Level")) cut = i;
            if (s.contains("(Legendary):")) { start = i; break; }
        }
        if (start >= 0 && cut > start) lines.subList(start, cut).clear();
    }

    private static void reforge(ItemStack stack, String id, List<Component> lines) {
        JsonObject info = stones.getAsJsonObject(id);
        if (!info.has("requiredRarities")) return;
        List<String> rarities = new ArrayList<>();
        for (JsonElement e : info.getAsJsonArray("requiredRarities")) rarities.add(e.getAsString());
        if (rarities.isEmpty()) return;
        String rar = pick(rarities);
        if (stack != lastStack || !rar.equals(lastRarity)) {
            lastStack = stack;
            lastRarity = rar;
            lastExtra = buildReforge(info, rar);
        }
        int at = Math.min(2, lines.size());
        lines.addAll(at, lastExtra);
    }

    private static List<Component> buildReforge(JsonObject info, String rar) {
        List<Component> out = new ArrayList<>();
        out.add(Component.empty());
        String name = info.has("reforgeName") ? info.get("reforgeName").getAsString() : "";
        String ability = null;
        JsonElement ab = info.get("reforgeAbility");
        if (ab != null && ab.isJsonPrimitive()) ability = ab.getAsString();
        else if (ab != null && ab.isJsonObject() && ab.getAsJsonObject().has(rar)) ability = ab.getAsJsonObject().get(rar).getAsString();
        if (ability != null && !ability.isEmpty()) {
            wrap(out, "§9" + (name.isEmpty() ? "Bonus: " : name + " Bonus: ") + "§7" + ability);
            out.add(Component.empty());
        }
        out.add(Component.literal("§9Stats for " + rarityName(rar) + "§9: §8[← →]"));
        JsonElement st = info.get("reforgeStats");
        if (st != null && st.isJsonObject() && st.getAsJsonObject().has(rar) && st.getAsJsonObject().get(rar).isJsonObject()) {
            Map<String, JsonElement> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : st.getAsJsonObject().getAsJsonObject(rar).entrySet()) sorted.put(e.getKey(), e.getValue());
            for (Map.Entry<String, JsonElement> e : sorted.entrySet()) {
                if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) continue;
                String stat = StringUtil.titleCase(e.getKey().replace('_', ' '));
                out.add(Component.literal("  §7" + stat + ": §a+" + num(e.getValue().getAsDouble())
                        + (PERCENT.contains(e.getKey()) ? "%" : "")));
            }
        }
        JsonElement costs = info.get("reforgeCosts");
        long cost = -1;
        if (costs != null && costs.isJsonObject() && costs.getAsJsonObject().has(rar)) cost = costs.getAsJsonObject().get(rar).getAsLong();
        else if (costs != null && costs.isJsonPrimitive() && costs.getAsJsonPrimitive().isNumber()) cost = costs.getAsLong();
        if (cost >= 0) out.add(Component.literal("§9Apply Cost: §6" + String.format(Locale.ROOT, "%,d", cost) + " coins"));
        return out;
    }

    private static void gem(ItemStack stack, String tier, String type, List<Component> lines) {
        JsonObject types = gems.getAsJsonObject("gemstoneTypes");
        if (types == null || !types.has(type)) return;
        JsonObject info = types.getAsJsonObject(type);
        JsonObject stats = info.getAsJsonObject("stats");
        if (stats == null || !stats.has(tier)) return;
        JsonObject perRarity = stats.getAsJsonObject(tier);
        List<String> rarities = new ArrayList<>(perRarity.keySet());
        if (rarities.isEmpty()) return;
        String rar = pick(rarities);
        if (stack != lastStack || !rar.equals(lastRarity)) {
            lastStack = stack;
            lastRarity = rar;
            List<Component> out = new ArrayList<>();
            if (info.has("chiselBonus") && gems.has("chiselPercentages")) {
                String bonus = info.get("chiselBonus").getAsString()
                        .replace("{}", gems.getAsJsonObject("chiselPercentages").get(tier).getAsString());
                wrap(out, "§9Chisel bonus: " + bonus);
                out.add(Component.empty());
            }
            double v = perRarity.get(rar).getAsDouble();
            out.add(Component.literal("§9Stats for " + rarityName(rar) + "§9: §8[← →]"));
            out.add(Component.literal("  §7" + info.get("statName").getAsString() + ": §a+" + (v == 0 ? "???" : num(v))));
            if (gems.has("removalCosts") && gems.getAsJsonObject("removalCosts").has(tier)) {
                long cost = gems.getAsJsonObject("removalCosts").get(tier).getAsLong();
                out.add(Component.empty());
                out.add(Component.literal("§9Removal Cost: §6" + String.format(Locale.ROOT, "%,d", cost) + (cost == 1 ? " coin" : " coins")));
            }
            out.add(Component.empty());
            lastExtra = out;
        }
        int at = lines.size() > 1 && lines.get(1).getString().contains("Collection Item") ? Math.min(3, lines.size()) : 1;
        lines.addAll(at, lastExtra);
    }

    private static void wrap(List<Component> out, String text) {
        var font = Minecraft.getInstance().font;
        StringBuilder line = new StringBuilder();
        String color = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(next) > 170) {
                out.add(Component.literal(line.toString()));
                color = lastColor(line, color);
                line = new StringBuilder("  " + color + word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) out.add(Component.literal(line.toString()));
    }

    private static String lastColor(CharSequence s, String fallback) {
        for (int i = s.length() - 2; i >= 0; i--) {
            if (s.charAt(i) == '§' && "0123456789abcdefABCDEF".indexOf(s.charAt(i + 1)) >= 0) return "§" + s.charAt(i + 1);
        }
        return fallback;
    }

    private static String num(double v) {
        return v % 1 == 0 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String rarityName(String r) {
        String col = switch (r) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            default -> "§c";
        };
        return col + "§l" + r;
    }
}
