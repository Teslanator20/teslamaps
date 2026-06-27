/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.utils.ItemUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class EstimatedValue {

    private static final String[] MASTER_STARS = {
        "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
    };

    // a single breakdown row; value is a preformatted right-hand string ("" = none); indent = enchant sub-line
    public record Line(String label, String value, boolean indent) {}

    /** Lightweight total used in tooltips / container & storage sums (no allocation of breakdown rows). */
    public static double compute(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        CompoundTag t = cd.copyTag();
        String id = t.getStringOr("id", "");
        if (id.isEmpty()) return 0;

        double total = PriceManager.getPrice(id);

        CompoundTag ench = t.getCompoundOrEmpty("enchantments");
        for (String e : ench.keySet()) {
            int lvl = ench.getIntOr(e, 0);
            if (lvl > 0) total += PriceManager.getPrice("ENCHANTMENT_" + e.toUpperCase() + "_" + lvl);
        }

        int hpb = t.getIntOr("hot_potato_count", 0);
        if (hpb > 0) {
            total += Math.min(hpb, 10) * PriceManager.getPrice("HOT_POTATO_BOOK");
            if (hpb > 10) total += (hpb - 10) * PriceManager.getPrice("FUMING_POTATO_BOOK");
        }

        if (t.getIntOr("rarity_upgrades", 0) > 0) total += PriceManager.getPrice("RECOMBOBULATOR_3000");
        if (t.getIntOr("art_of_war_count", 0) > 0) total += PriceManager.getPrice("THE_ART_OF_WAR");
        if (t.getIntOr("artOfPeaceApplied", 0) > 0) total += PriceManager.getPrice("THE_ART_OF_PEACE");

        for (String s : ItemUtil.abilityScrolls(stack)) total += PriceManager.getPrice(s);

        String power = t.getStringOr("power_ability_scroll", "");
        if (!power.isEmpty()) total += PriceManager.getPrice(power);

        if (t.getIntOr("ethermerge", 0) > 0) total += PriceManager.getPrice("ETHERWARP_CONDUIT");

        String enr = t.getStringOr("talisman_enrichment", "");
        if (!enr.isEmpty()) total += PriceManager.getPrice("TALISMAN_ENRICHMENT_" + enr.toUpperCase());

        int stars = Math.max(t.getIntOr("upgrade_level", 0), t.getIntOr("dungeon_item_level", 0));
        for (int i = 6; i <= stars && i - 6 < MASTER_STARS.length; i++) {
            total += PriceManager.getPrice(MASTER_STARS[i - 6]);
        }

        return total;
    }

    /** SkyHanni-style ordered breakdown rows for the hover panel. */
    public static List<Line> breakdown(ItemStack stack) {
        List<Line> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return out;
        CompoundTag t = cd.copyTag();
        String id = t.getStringOr("id", "");
        if (id.isEmpty()) return out;

        out.add(new Line("§fBase Item", coin(PriceManager.getPrice(id)), false));

        String modifier = t.getStringOr("modifier", "");
        if (!modifier.isEmpty()) {
            double rv = PriceManager.getPrice(ReforgeStones.stoneId(modifier));
            out.add(new Line("§dReforge: §7" + prettify(modifier), rv > 0 ? coin(rv) : "§8—", false));
        }

        if (t.getIntOr("rarity_upgrades", 0) > 0)
            out.add(new Line("§aRecombobulated", coin(PriceManager.getPrice("RECOMBOBULATOR_3000")), false));
        if (t.getIntOr("art_of_war_count", 0) > 0)
            out.add(new Line("§aThe Art of War", coin(PriceManager.getPrice("THE_ART_OF_WAR")), false));
        if (t.getIntOr("artOfPeaceApplied", 0) > 0)
            out.add(new Line("§aThe Art of Peace", coin(PriceManager.getPrice("THE_ART_OF_PEACE")), false));

        int stars = Math.max(t.getIntOr("upgrade_level", 0), t.getIntOr("dungeon_item_level", 0));
        int regularStars = Math.min(stars, 5);
        int masterStars = Math.max(0, Math.min(stars - 5, 5));
        if (regularStars > 0) out.add(new Line("§eStars §7(" + regularStars + "/5)", "", false));
        if (masterStars > 0) {
            double mv = 0;
            for (int i = 6; i <= stars && i - 6 < MASTER_STARS.length; i++) mv += PriceManager.getPrice(MASTER_STARS[i - 6]);
            out.add(new Line("§6Master Stars §7(" + masterStars + "/5)", coin(mv), false));
        }

        int hpb = t.getIntOr("hot_potato_count", 0);
        if (hpb > 0) {
            int regular = Math.min(hpb, 10);
            out.add(new Line("§cHot Potato Books §7(" + regular + "/10)", coin(regular * PriceManager.getPrice("HOT_POTATO_BOOK")), false));
            int fuming = Math.max(0, hpb - 10);
            if (fuming > 0) out.add(new Line("§cFuming Potato Books §7(" + fuming + "/5)", coin(fuming * PriceManager.getPrice("FUMING_POTATO_BOOK")), false));
        }

        // enchantments: total + the 5 most expensive
        CompoundTag ench = t.getCompoundOrEmpty("enchantments");
        List<Map.Entry<String, Double>> enchants = new ArrayList<>();
        double enchTotal = 0;
        for (String e : ench.keySet()) {
            int lvl = ench.getIntOr(e, 0);
            if (lvl <= 0) continue;
            double v = PriceManager.getPrice("ENCHANTMENT_" + e.toUpperCase() + "_" + lvl);
            enchants.add(Map.entry(prettify(e) + " " + lvl, v));
            enchTotal += v;
        }
        if (!enchants.isEmpty()) {
            enchants.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            out.add(new Line("§bEnchantments §7(" + enchants.size() + ")", coin(enchTotal), false));
            int show = Math.min(5, enchants.size());
            for (int i = 0; i < show; i++) out.add(new Line("§7" + enchants.get(i).getKey(), coin(enchants.get(i).getValue()), true));
            if (enchants.size() > show) out.add(new Line("§8+" + (enchants.size() - show) + " more", "", true));
        }

        for (String s : ItemUtil.abilityScrolls(stack)) out.add(new Line("§dScroll: §7" + prettify(s), coin(PriceManager.getPrice(s)), false));
        String power = t.getStringOr("power_ability_scroll", "");
        if (!power.isEmpty()) out.add(new Line("§dPower Scroll: §7" + prettify(power), coin(PriceManager.getPrice(power)), false));
        if (t.getIntOr("ethermerge", 0) > 0) out.add(new Line("§dEtherwarp Conduit", coin(PriceManager.getPrice("ETHERWARP_CONDUIT")), false));
        String enr = t.getStringOr("talisman_enrichment", "");
        if (!enr.isEmpty()) out.add(new Line("§dEnrichment §7(" + prettify(enr) + ")", coin(PriceManager.getPrice("TALISMAN_ENRICHMENT_" + enr.toUpperCase())), false));

        return out;
    }

    private static String coin(double v) {
        return v > 0 ? "§6" + fmt(v) : "§8—";
    }

    public static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fK", v / 1_000);
        return String.format("%.0f", v);
    }

    private static String prettify(String raw) {
        String[] words = raw.toLowerCase().replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
