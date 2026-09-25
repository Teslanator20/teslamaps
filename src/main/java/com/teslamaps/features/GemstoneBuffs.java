package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Gemstone stat bonus summed from the pink "(+x)" lore parts, and the applied power scroll. */
public class GemstoneBuffs {

    private static final Pattern GEM_PART = Pattern.compile("§d\\(\\+([\\d,.]+)(%?)\\)");

    private static final Map<String, String> STAT_GEM = Map.ofEntries(
            Map.entry("Health", "RUBY"), Map.entry("Defense", "AMETHYST"), Map.entry("Strength", "JASPER"),
            Map.entry("Intelligence", "SAPPHIRE"), Map.entry("Crit Damage", "ONYX"), Map.entry("True Defense", "OPAL"),
            Map.entry("Pristine", "TOPAZ"), Map.entry("Mining Fortune", "JADE"), Map.entry("Mining Speed", "AMBER"),
            Map.entry("Sea Creature Chance", "AQUAMARINE"), Map.entry("Foraging Fortune", "CITRINE"),
            Map.entry("Farming Fortune", "PERIDOT"));
    private static final Map<String, String> SCROLL_STAT = Map.ofEntries(
            Map.entry("RUBY", "Health"), Map.entry("AMETHYST", "Defense"), Map.entry("JASPER", "Strength"),
            Map.entry("SAPPHIRE", "Intelligence"), Map.entry("ONYX", "Crit Damage"), Map.entry("OPAL", "True Defense"),
            Map.entry("TOPAZ", "Pristine"), Map.entry("JADE", "Mining Fortune"), Map.entry("AMBER", "Mining Speed"),
            Map.entry("AQUAMARINE", "Sea Creature Chance"), Map.entry("CITRINE", "Foraging Fortune"),
            Map.entry("PERIDOT", "Farming Fortune"));

    private static Feature feature;
    private static ItemLore lastLore;
    private static CustomData lastData;
    private static List<String> lastSummary = List.of(), lastDetail = List.of();

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GemstoneBuffs").when(() -> cfg().gemstoneBuffs || cfg().powerScrollBuff)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Gemstone Buffs In Tooltip", () -> c.gemstoneBuffs, v -> c.gemstoneBuffs = v)
                            .desc("Sums the stats your gemstones add. Hold Shift for one line per stat.");
                    b.toggle("  Power Scroll Buff", () -> c.powerScrollBuff, v -> c.powerScrollBuff = v)
                            .desc("Names the applied power scroll and the stat its ability buff gives.");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active()) onTooltip(stack, lines);
        });
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        ItemLore lore = stack.get(DataComponents.LORE);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (lore == null || data == null) return;
        if (lore != lastLore || data != lastData) {
            lastLore = lore;
            lastData = data;
            build(lore, data);
        }
        boolean shift = Minecraft.getInstance().hasShiftDown();
        for (String s : shift ? lastDetail : lastSummary) lines.add(Component.literal(s));
    }

    private static void build(ItemLore lore, CustomData data) {
        List<String> summary = new ArrayList<>(), detail = new ArrayList<>();
        if (cfg().gemstoneBuffs) {
            Map<String, double[]> stats = new LinkedHashMap<>();
            Map<String, Boolean> percent = new LinkedHashMap<>();
            for (Component line : lore.lines()) {
                String f = ChatText.formatted(line);
                if (!f.contains("§d(+")) continue;
                String plain = StringUtil.stripTrim(f);
                int colon = plain.indexOf(':');
                if (colon <= 0) continue;
                String stat = plain.substring(0, colon).trim();
                Matcher m = GEM_PART.matcher(f);
                while (m.find()) {
                    try {
                        double v = Double.parseDouble(m.group(1).replace(",", ""));
                        stats.computeIfAbsent(stat, k -> new double[1])[0] += v;
                        percent.put(stat, !m.group(2).isEmpty());
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!stats.isEmpty()) {
                StringBuilder sb = new StringBuilder("§dGemstone Buffs: ");
                detail.add("§dGemstone Buffs:");
                boolean first = true;
                for (Map.Entry<String, double[]> e : stats.entrySet()) {
                    String val = "+" + num(e.getValue()[0]) + (percent.get(e.getKey()) ? "%" : "");
                    if (!first) sb.append("§7, ");
                    sb.append("§d").append(val).append(" §7").append(e.getKey());
                    first = false;
                    String gem = STAT_GEM.get(e.getKey());
                    String gemName = gem == null ? "" : " §8(§" + EstimatedValue.gemColor(gem) + StringUtil.titleCase(gem) + "§8)";
                    detail.add(" §7" + e.getKey() + ": §d" + val + gemName);
                }
                summary.add(sb.toString());
            }
        }
        if (cfg().powerScrollBuff) {
            String scroll = data.copyTag().getStringOr("power_ability_scroll", "");
            if (!scroll.isEmpty()) {
                String gem = scroll.replace("_POWER_SCROLL", "");
                String stat = SCROLL_STAT.get(gem);
                String line = "§7Power Scroll: §" + EstimatedValue.gemColor(gem) + StringUtil.titleCase(gem)
                        + (stat == null ? "" : " §8(ability buffs " + stat + ")");
                summary.add(line);
                detail.add(line);
            }
        }
        lastSummary = summary;
        lastDetail = detail;
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
