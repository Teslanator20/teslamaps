package com.teslamaps.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class EnchantColors {

    public static final String[] LAYOUTS = {"Normal", "Compressed", "Stacked"};

    private record Enchant(String loreName, String type, int goodLevel, int maxLevel,
                           String nbtNum, int[] stackLevels, String statLabel) {
        boolean ultimate() { return "ULTIMATE".equals(type); }
        boolean stacking() { return "STACKING".equals(type); }
    }

    private static Map<String, Enchant> enchants;

    private static final Pattern ENCHANT_PATTERN =
            Pattern.compile("(?<enchant>[A-Za-z][A-Za-z '\\-]+?) (?<level>[IVXLCDM]+|\\d+)(?<stack> [\\d,.]+[kKmMbB]?)?(?=,\\s*|\\)|$)");

    private static void loadData() {
        enchants = new HashMap<>();
        try (var in = EnchantColors.class.getResourceAsStream("/assets/teslamaps/enchants.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> type : root.entrySet()) {
                for (Map.Entry<String, JsonElement> e : type.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    int[] levels = null;
                    if (o.has("stackLevel")) {
                        var arr = o.getAsJsonArray("stackLevel");
                        levels = new int[arr.size()];
                        for (int i = 0; i < levels.length; i++) levels[i] = arr.get(i).getAsInt();
                        Arrays.sort(levels);
                    }
                    String label = o.has("statLabel") ? splitCamel(o.get("statLabel").getAsString()) : null;
                    enchants.put(e.getKey(), new Enchant(
                            o.get("loreName").getAsString(), type.getKey(),
                            o.get("goodLevel").getAsInt(), o.get("maxLevel").getAsInt(),
                            o.has("nbtNum") ? o.get("nbtNum").getAsString() : null, levels, label));
                }
            }
            TeslaMaps.LOGGER.info("[EnchantColors] Loaded {} enchants", enchants.size());
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[EnchantColors] Failed to load enchants.json", ex);
        }
    }

    private static String splitCamel(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (i == 0) out.append(Character.toUpperCase(ch));
            else {
                if (Character.isUpperCase(ch)) out.append(' ');
                out.append(ch);
            }
        }
        return out.toString().replace("Xp", "XP");
    }

    // ---------------------------------------------------------------- rules (NEU format)

    public static final String RULE_CODES = "0123456789abcdefz";
    public static final String[] RULE_CMPS = {">", ">=", "=", "<=", "<"};
    public static final String SHARE_PREFIX = "NEUEC/";

    public record Rule(Pattern name, String cmp, int level, char color, int mods) {
        boolean matches(String enchant, int lvl) {
            if (!name.matcher(enchant).matches()) return false;
            return switch (cmp) {
                case ">" -> lvl > level;
                case ">=" -> lvl >= level;
                case "=" -> lvl == level;
                case "<=" -> lvl <= level;
                case "<" -> lvl < level;
                default -> false;
            };
        }

        Style apply(Style base) {
            Style st = color == 'z' ? base.withColor(com.teslamaps.render.gui.ChromaHook.color())
                    : base.withColor(ChatFormatting.getByCode(color));
            return st.withBold((mods & 1) != 0).withItalic((mods & 2) != 0).withObfuscated((mods & 4) != 0)
                    .withUnderlined((mods & 8) != 0).withStrikethrough((mods & 16) != 0);
        }
    }

    /** name:cmp:level:colour:modifiers, e.g. {@code Sharpness:>=:6:6:1}. Null if invalid. */
    public static Rule parseRule(String saved) {
        if (saved == null) return null;
        String[] parts = saved.split(":", 5);
        if (parts.length < 4 || parts[0].isEmpty()) return null;
        String cmp = parts[1];
        if (!Arrays.asList(RULE_CMPS).contains(cmp)) return null;
        if (parts[3].length() != 1) return null;
        char color = Character.toLowerCase(parts[3].charAt(0));
        if (RULE_CODES.indexOf(color) < 0) return null;
        try {
            int level = Integer.parseInt(parts[2].trim());
            int mods = parts.length > 4 && !parts[4].isBlank() ? Integer.parseInt(parts[4].trim()) : 0;
            return new Rule(Pattern.compile(parts[0], Pattern.CASE_INSENSITIVE), cmp, level, color, mods);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Rule> rules = List.of();
    private static List<String> rulesFrom = List.of();

    private static List<Rule> rules(TeslaMapsConfig c) {
        if (!c.enchantCustomRules || c.enchantColorRules == null) return List.of();
        if (!c.enchantColorRules.equals(rulesFrom)) {
            List<Rule> out = new ArrayList<>();
            for (String s : c.enchantColorRules) {
                Rule r = parseRule(s);
                if (r != null) out.add(r);
            }
            rules = out;
            rulesFrom = new ArrayList<>(c.enchantColorRules);
        }
        return rules;
    }

    // ---------------------------------------------------------------- cache

    private static ItemStack cacheStack;
    private static int cacheStamp;
    private static List<Component> cacheBefore = List.of(), cacheAfter = List.of();

    // hashed every call: screen edits change fields without a save
    private static int stamp(TeslaMapsConfig c) {
        int h = System.identityHashCode(c);
        h = 31 * h + (c.enchantColors ? 1 : 0);
        h = 31 * h + (c.enchantLevelsAsNumbers ? 1 : 0);
        h = 31 * h + (c.enchantBoldMax ? 1 : 0);
        h = 31 * h + (c.enchantChromaMax ? 1 : 0);
        h = 31 * h + (c.enchantChromaBold ? 1 : 0);
        h = 31 * h + (c.enchantHideDescriptions ? 1 : 0);
        h = 31 * h + (c.enchantStackingProgress ? 1 : 0);
        h = 31 * h + (c.enchantCustomRules ? 1 : 0);
        h = 31 * h + Objects.hashCode(c.enchantColorMax);
        h = 31 * h + Objects.hashCode(c.enchantColorHigh);
        h = 31 * h + Objects.hashCode(c.enchantColorNormal);
        h = 31 * h + Objects.hashCode(c.enchantColorBad);
        h = 31 * h + Objects.hashCode(c.enchantLayout);
        h = 31 * h + Objects.hashCode(c.enchantColorRules);
        return h;
    }

    private static boolean sameLore(List<Component> a, List<Component> b) {
        int n = a.size();
        if (n != b.size()) return false;
        for (int i = 0; i < n; i++) {
            Component x = a.get(i), y = b.get(i);
            if (x != y && !x.equals(y)) return false;
        }
        return true;
    }

    public static void onTooltip(ItemStack stack, List<Component> lore) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean restructure = layout(c) != 0 || c.enchantHideDescriptions;
        if (!c.enchantColors && !restructure && !c.enchantStackingProgress) return;
        if (lore.size() < 2) return;
        if (ItemUtil.skyblockId(stack).isEmpty()) return;
        if (enchants == null) loadData();

        int stamp = stamp(c);
        if (stack == cacheStack && stamp == cacheStamp && sameLore(lore, cacheBefore)) {
            lore.clear();
            lore.addAll(cacheAfter);
            return;
        }
        List<Component> before = new ArrayList<>(lore);
        process(stack, lore, c, restructure);
        cacheStack = stack;
        cacheStamp = stamp;
        cacheBefore = before;
        cacheAfter = new ArrayList<>(lore);
    }

    // ---------------------------------------------------------------- parsing

    private record Flat(String text, Style[] styles) {}

    private record Replacement(int end, String text) {}

    /** One enchant on a line: [start, end) is the enchant incl. a stacking counter, no comma. */
    private record Hit(int start, int end, Enchant data, String name, boolean counter) {}

    private record Parsed(Flat flat, Style[] out, Map<Integer, Replacement> repl, List<Hit> hits,
                          boolean exclusive, Style comma) {}

    private static void process(ItemStack stack, List<Component> lore, TeslaMapsConfig c, boolean restructure) {
        Parsed[] parsed = new Parsed[lore.size()];
        for (int i = 1; i < lore.size(); i++) {
            Parsed p = parse(lore.get(i), c);
            if (p == null) continue;
            parsed[i] = p;
            if (c.enchantColors && p.out != p.flat.styles) {
                lore.set(i, build(p, 0, p.flat.text.length()));
            }
        }

        List<Enchant> stacking = new ArrayList<>();
        if (c.enchantStackingProgress) {
            for (Parsed p : parsed) {
                if (p == null) continue;
                for (Hit h : p.hits) if (h.data != null && h.data.stacking() && !stacking.contains(h.data)) stacking.add(h.data);
            }
        }

        if (restructure && hasEnchantTag(stack)) restructure(stack, lore, parsed, c);

        if (!stacking.isEmpty()) {
            CompoundTag tag = customData(stack);
            if (tag != null) {
                for (Enchant e : stacking) {
                    Component line = progressLine(e, tag);
                    if (line != null) lore.add(Math.max(1, lore.size() - 1), line);
                }
            }
        }
    }

    private static Parsed parse(Component line, TeslaMapsConfig c) {
        Flat flat = flatten(line);
        String text = flat.text;
        if (text.isEmpty() || text.contains("◆")) return null;

        Matcher m = ENCHANT_PATTERN.matcher(text);
        List<Hit> hits = null;
        Style[] out = null;
        Map<Integer, Replacement> repl = null;
        Style comma = null;
        while (m.find()) {
            if (!startsSegment(text, m.start())) continue;
            String name = m.group("enchant");
            Enchant data = enchants.get(name.toLowerCase().replace("'", ""));
            if (hits == null) hits = new ArrayList<>();
            hits.add(new Hit(m.start(), m.end(), data, name, m.group("stack") != null));
            if (!c.enchantColors) continue;

            int level = parseLevel(m.group("level"));
            Rule rule = null;
            for (Rule r : rules(c)) if (r.matches(data != null ? data.loreName : name, level)) { rule = r; break; }
            if (data == null && rule == null) continue;
            if (out == null) out = flat.styles.clone();
            int colorEnd = m.end("level");
            for (int i = m.start(); i < colorEnd; i++)
                out[i] = rule != null ? rule.apply(flat.styles[i]) : styleFor(flat.styles[i], data, level, c);
            if (c.enchantLevelsAsNumbers && level > 0) {
                if (repl == null) repl = new HashMap<>();
                repl.put(m.start("level"), new Replacement(colorEnd, String.valueOf(level)));
            }
        }
        if (hits == null) return null;

        boolean exclusive = true;
        int pos = 0;
        for (Hit h : hits) {
            if (!text.substring(pos, h.start).replace(",", "").isBlank()) exclusive = false;
            int ci = text.indexOf(',', h.end);
            if (comma == null && ci >= 0 && ci < text.length()) comma = flat.styles[ci];
            pos = h.end;
        }
        if (!text.substring(pos).replace(",", "").isBlank()) exclusive = false;

        return new Parsed(flat, out == null ? flat.styles : out, repl, hits, exclusive, comma);
    }

    private static boolean startsSegment(String text, int start) {
        int i = start;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        return i == 0 || text.charAt(i - 1) == ',';
    }

    /** Line as characters + per-character style; literal § codes are folded into the styles. */
    private static Flat flatten(Component component) {
        StringBuilder text = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        component.visit((FormattedText.StyledContentConsumer<Object>) (style, part) -> {
            Style cur = style;
            for (int i = 0; i < part.length(); i++) {
                char ch = part.charAt(i);
                if (ch == '§' && i + 1 < part.length()) {
                    ChatFormatting f = ChatFormatting.getByCode(part.charAt(++i));
                    if (f == null) continue;
                    if (f == ChatFormatting.RESET) cur = style;
                    else if (f.isColor()) cur = style.withColor(f).withBold(false).withItalic(false)
                            .withUnderlined(false).withStrikethrough(false).withObfuscated(false);
                    else cur = cur.applyFormat(f);
                    continue;
                }
                text.append(ch);
                styles.add(cur);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new Flat(text.toString(), styles.toArray(new Style[0]));
    }

    private static MutableComponent build(Parsed p, int from, int to) {
        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        int i = from;
        while (i < to) {
            Style style = p.out[i];
            Replacement r = p.repl == null ? null : p.repl.get(i);
            String piece = r == null ? String.valueOf(p.flat.text.charAt(i)) : r.text;
            if (runStyle != null && !runStyle.equals(style)) {
                result.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = style;
            run.append(piece);
            i = r == null ? i + 1 : Math.min(to, r.end);
        }
        if (!run.isEmpty()) result.append(Component.literal(run.toString()).withStyle(runStyle));
        return result;
    }

    // ---------------------------------------------------------------- layout

    private record Entry(Component piece, Enchant data, String name, List<Component> desc) {}

    private static void restructure(ItemStack stack, List<Component> lore, Parsed[] parsed, TeslaMapsConfig c) {
        int start = -1;
        for (int i = 1; i < parsed.length; i++) {
            Parsed p = parsed[i];
            if (p != null && p.exclusive && p.hits.stream().anyMatch(h -> h.data != null)) { start = i; break; }
        }
        if (start < 0) return;
        int end = start;
        while (end + 1 < lore.size() && !lore.get(end + 1).getString().isBlank()) end++;

        boolean book = "ENCHANTED_BOOK".equals(ItemUtil.skyblockId(stack));
        boolean hideDesc = c.enchantHideDescriptions && !book;
        List<Entry> entries = new ArrayList<>();
        List<Component> normal = new ArrayList<>();
        int maxPerLine = 0;
        boolean counters = false;
        Style comma = null;
        for (int i = start; i <= end; i++) {
            Parsed p = parsed[i];
            if (p != null && p.exclusive && p.hits.stream().anyMatch(h -> h.data != null)) {
                maxPerLine = Math.max(maxPerLine, p.hits.size());
                if (comma == null) comma = p.comma;
                for (Hit h : p.hits) {
                    counters |= h.counter;
                    entries.add(new Entry(build(p, h.start, h.end), h.data, h.name, new ArrayList<>()));
                }
                normal.add(lore.get(i));
            } else {
                if (!entries.isEmpty()) entries.get(entries.size() - 1).desc.add(lore.get(i));
                if (!hideDesc) normal.add(lore.get(i));
            }
        }
        if (entries.isEmpty()) return;
        Style commaStyle = comma != null ? comma : Style.EMPTY.withColor(ChatFormatting.BLUE);

        List<Component> out;
        int layout = layout(c);
        if (layout == 0) {
            out = normal;
        } else {
            entries.sort((a, b) -> {
                int ra = rank(a.data), rb = rank(b.data);
                if (ra != rb) return Integer.compare(ra, rb);
                String na = a.data != null ? a.data.loreName : a.name;
                String nb = b.data != null ? b.data.loreName : b.name;
                return na.compareTo(nb);
            });
            out = new ArrayList<>();
            if (layout == 1 && !counters && !(book && maxPerLine == 1)) {
                MutableComponent line = null;
                for (int i = 0; i < entries.size(); i++) {
                    if (line == null) line = Component.empty();
                    else line.append(Component.literal(", ").withStyle(commaStyle));
                    line.append(entries.get(i).piece);
                    if (i % 3 == 2) { out.add(line); line = null; }
                }
                if (line != null) out.add(line);
            } else {
                for (Entry e : entries) {
                    out.add(e.piece);
                    if (!hideDesc) out.addAll(e.desc);
                }
            }
        }

        lore.subList(start, end + 1).clear();
        lore.addAll(start, out);
    }

    private static int layout(TeslaMapsConfig c) {
        return "Compressed".equals(c.enchantLayout) ? 1 : "Stacked".equals(c.enchantLayout) ? 2 : 0;
    }

    private static int rank(Enchant e) {
        if (e == null) return 2;
        if (e.ultimate()) return 0;
        return e.stacking() ? 1 : 2;
    }

    // ---------------------------------------------------------------- stacking

    private static CompoundTag customData(ItemStack stack) {
        CustomData d = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    private static boolean hasEnchantTag(ItemStack stack) {
        CompoundTag tag = customData(stack);
        return tag != null && tag.getCompound("enchantments").map(t -> !t.isEmpty()).orElse(false);
    }

    private static Component progressLine(Enchant e, CompoundTag tag) {
        if (e.nbtNum == null || e.stackLevels == null) return null;
        long progress = tag.getLong(e.nbtNum).orElse(0L);
        if (progress <= 0) return null;
        long next = -1;
        for (int lvl : e.stackLevels) if (lvl > progress) { next = lvl; break; }
        String label = e.statLabel != null ? e.statLabel : e.loreName;
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(NumberUtil.formatSuffixed(progress)).withStyle(ChatFormatting.RED))
                .append(Component.literal(next < 0 ? " (Maxed)" : " / " + NumberUtil.formatSuffixed(next))
                        .withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- colours

    private static Style styleFor(Style base, Enchant data, int level, TeslaMapsConfig c) {
        if (data.ultimate())
            return base.withColor(TextColor.fromRgb(0xFF55FF)).withBold(true);
        if (level >= data.maxLevel() && c.enchantChromaMax)
            return base.withColor(com.teslamaps.render.gui.ChromaHook.color()).withBold(c.enchantChromaBold);
        String hex;
        if (level >= data.maxLevel()) hex = c.enchantColorMax;
        else if (level > data.goodLevel()) hex = c.enchantColorHigh;
        else if (level == data.goodLevel()) hex = c.enchantColorNormal;
        else hex = c.enchantColorBad;
        return base.withColor(TextColor.fromRgb(TeslaMapsConfig.parseColor(hex) & 0xFFFFFF))
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
