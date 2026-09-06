package com.teslamaps.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

public class EnchantColors {

    private record Enchant(String loreName, String type, int goodLevel, int maxLevel) {}

    private static Map<String, Enchant> enchants;

    private static final Pattern ENCHANT_PATTERN =
            Pattern.compile("(?<enchant>[A-Za-z][A-Za-z '\\-]+?) (?<level>[IVXLCDM]+|\\d+)(?=,\\s*|\\)|$| [\\d,]+$)");

    private static void loadData() {
        enchants = new HashMap<>();
        try (var in = EnchantColors.class.getResourceAsStream("/assets/teslamaps/enchants.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> type : root.entrySet()) {
                for (Map.Entry<String, JsonElement> e : type.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    enchants.put(e.getKey(), new Enchant(
                            o.get("loreName").getAsString(), type.getKey(),
                            o.get("goodLevel").getAsInt(), o.get("maxLevel").getAsInt()));
                }
            }
            TeslaMaps.LOGGER.info("[EnchantColors] Loaded {} enchants", enchants.size());
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[EnchantColors] Failed to load enchants.json", ex);
        }
    }

    public static void onTooltip(ItemStack stack, List<Component> lore) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.enchantColors || lore.size() < 2) return;
        if (ItemUtil.skyblockId(stack).isEmpty()) return;
        if (enchants == null) loadData();

        for (int i = 1; i < lore.size(); i++) {
            String plain = com.teslamaps.utils.StringUtil.strip(lore.get(i).getString());
            if (plain.isEmpty() || plain.contains("◆")) continue;

            Matcher m = ENCHANT_PATTERN.matcher(plain);
            if (!m.find()) continue;

            MutableComponent newLine = Component.empty().withStyle(ChatFormatting.GRAY);
            int lastEnd = 0;
            boolean found = false;

            do {
                int start = m.start(), end = m.end();
                if (start > lastEnd) newLine.append(plain.substring(lastEnd, start));

                String nameKey = m.group("enchant").toLowerCase().replace("'", "");
                String levelStr = m.group("level");
                Enchant data = enchants.get(nameKey);

                if (data != null) {
                    found = true;
                    int level = parseLevel(levelStr);
                    Style style = styleFor(data, level, c);
                    newLine.append(Component.literal(data.loreName()).withStyle(style));
                    newLine.append(" ");
                    String display = c.enchantLevelsAsNumbers ? String.valueOf(level) : levelStr;
                    newLine.append(Component.literal(display).withStyle(style));
                } else {
                    newLine.append(plain.substring(start, end));
                }
                lastEnd = end;
            } while (m.find());

            if (lastEnd < plain.length()) newLine.append(plain.substring(lastEnd));
            if (found) lore.set(i, newLine);
        }
    }

    private static Style styleFor(Enchant data, int level, TeslaMapsConfig c) {
        if ("ULTIMATE".equals(data.type()))
            return Style.EMPTY.withColor(TextColor.fromRgb(0xFF55FF)).withBold(true);
        String hex;
        if (level >= data.maxLevel()) hex = c.enchantColorMax;
        else if (level > data.goodLevel()) hex = c.enchantColorHigh;
        else if (level == data.goodLevel()) hex = c.enchantColorNormal;
        else hex = c.enchantColorBad;
        return Style.EMPTY.withColor(TextColor.fromRgb(TeslaMapsConfig.parseColor(hex) & 0xFFFFFF))
                .withBold(level >= data.maxLevel() && c.enchantBoldMax);
    }

    private static int parseLevel(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) { }
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
            };
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
    }
}
