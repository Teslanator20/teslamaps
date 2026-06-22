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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class EstimatedValue {

    private static final String[] MASTER_STARS = {
        "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
    };

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

    public static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fK", v / 1_000);
        return String.format("%.0f", v);
    }
}
