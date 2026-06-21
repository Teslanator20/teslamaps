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
package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TrophyFishPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int BRONZE = 0xFFCD7F32;
    private static final int SILVER = 0xFFC0C0C0;
    private static final int GOLD = 0xFFFFD700;
    private static final int DIAMOND = 0xFF55FFFF;

    private int scrollOffset = 0;

    private static final String[] TROPHY_FISH = {
            "sulphur_skitter", "obfuscated_fish_1", "steaming_hot_flounder", "gusher",
            "blobfish", "obfuscated_fish_2", "slugfish", "flyfish",
            "obfuscated_fish_3", "lavahorse", "mana_ray", "volcanic_stonefish",
            "vanille", "skeleton_fish", "moldfin", "soul_fish",
            "karate_fish", "golden_fish"
    };

    private static final String[] TROPHY_DISPLAY = {
            "Sulphur Skitter", "Obfuscated 1", "Steaming Hot Flounder", "Gusher",
            "Blobfish", "Obfuscated 2", "Slugfish", "Flyfish",
            "Obfuscated 3", "Lavahorse", "Mana Ray", "Volcanic Stonefish",
            "Vanille", "Skeleton Fish", "Moldfin", "Soul Fish",
            "Karate Fish", "Golden Fish"
    };

    @Override
    public String getTabName() {
        return "Trophy Fish";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.TROPICAL_FISH);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        Font tr = Minecraft.getInstance().font;
        JsonObject memberData = profile.getMemberData();
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.text(tr, "Trophy Fish", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject trophyFish = getNestedObject(memberData, "trophy_fish");
        if (trophyFish == null) {
            ctx.text(tr, "No trophy fish data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        int totalCatches = trophyFish.has("total_caught") ? trophyFish.get("total_caught").getAsInt() : 0;
        ctx.text(tr, "Total Caught: " + formatNumber(totalCatches), contentX, lineY, TEXT_WHITE);
        lineY += 20;

        ctx.text(tr, "Fish", contentX, lineY, TEXT_GRAY);
        ctx.text(tr, "Bronze", contentX + 140, lineY, BRONZE);
        ctx.text(tr, "Silver", contentX + 190, lineY, SILVER);
        ctx.text(tr, "Gold", contentX + 240, lineY, GOLD);
        ctx.text(tr, "Diamond", contentX + 280, lineY, DIAMOND);
        lineY += 14;

        int listStartY = lineY;
        int listHeight = height - (lineY - y) - padding;

        for (int i = 0; i < TROPHY_FISH.length; i++) {
            int fishY = listStartY + i * 14 - scrollOffset;
            if (fishY < listStartY - 14 || fishY > listStartY + listHeight) continue;

            String fishKey = TROPHY_FISH[i];
            String displayName = TROPHY_DISPLAY[i];

            int bronze = trophyFish.has(fishKey + "_bronze") ? trophyFish.get(fishKey + "_bronze").getAsInt() : 0;
            int silver = trophyFish.has(fishKey + "_silver") ? trophyFish.get(fishKey + "_silver").getAsInt() : 0;
            int gold = trophyFish.has(fishKey + "_gold") ? trophyFish.get(fishKey + "_gold").getAsInt() : 0;
            int diamond = trophyFish.has(fishKey + "_diamond") ? trophyFish.get(fishKey + "_diamond").getAsInt() : 0;

            int total = bronze + silver + gold + diamond;
            int nameColor = total > 0 ? TEXT_WHITE : TEXT_GRAY;

            ctx.text(tr, displayName, contentX, fishY, nameColor);
            ctx.text(tr, String.valueOf(bronze), contentX + 140, fishY, bronze > 0 ? BRONZE : TEXT_GRAY);
            ctx.text(tr, String.valueOf(silver), contentX + 190, fishY, silver > 0 ? SILVER : TEXT_GRAY);
            ctx.text(tr, String.valueOf(gold), contentX + 240, fishY, gold > 0 ? GOLD : TEXT_GRAY);
            ctx.text(tr, String.valueOf(diamond), contentX + 280, fishY, diamond > 0 ? DIAMOND : TEXT_GRAY);
        }
    }

    private JsonObject getNestedObject(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (String part : parts) {
            if (current == null || !current.has(part)) return null;
            if (!current.get(part).isJsonObject()) return null;
            current = current.getAsJsonObject(part);
        }
        return current;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxScroll = Math.max(0, TROPHY_FISH.length * 14 - 200);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - amount * 15));
        return true;
    }
}
