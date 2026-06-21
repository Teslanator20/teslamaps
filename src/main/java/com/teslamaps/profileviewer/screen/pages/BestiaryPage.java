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
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BestiaryPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int BAR_BG = 0xFF333333;

    private int scrollOffset = 0;

    @Override
    public String getTabName() {
        return "Bestiary";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.ZOMBIE_HEAD);
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

        ctx.text(tr, "Bestiary", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject bestiary = getNestedObject(memberData, "bestiary.kills");
        if (bestiary == null) {
            ctx.text(tr, "No bestiary data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        JsonObject bestiaryRoot = getNestedObject(memberData, "bestiary");
        if (bestiaryRoot != null && bestiaryRoot.has("milestone")) {
            JsonObject milestone = bestiaryRoot.getAsJsonObject("milestone");
            int level = milestone.has("last_claimed_milestone") ?
                    milestone.get("last_claimed_milestone").getAsInt() : 0;
            ctx.text(tr, "Milestone Level: " + level, contentX, lineY, TEXT_WHITE);
            lineY += 20;
        }

        List<Map.Entry<String, Integer>> mobs = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : bestiary.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                mobs.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getAsInt()));
            }
        }
        mobs.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        ctx.text(tr, "Mob", contentX, lineY, TEXT_GRAY);
        ctx.text(tr, "Kills", contentX + 200, lineY, TEXT_GRAY);
        ctx.text(tr, "Tier", contentX + 280, lineY, TEXT_GRAY);
        lineY += 14;

        int listStartY = lineY;
        int listHeight = height - (lineY - y) - padding;
        int idx = 0;

        for (Map.Entry<String, Integer> entry : mobs) {
            int mobY = listStartY + idx * 14 - scrollOffset;
            if (mobY < listStartY - 14 || mobY > listStartY + listHeight) {
                idx++;
                continue;
            }

            String mobName = formatMobName(entry.getKey());
            int kills = entry.getValue();
            int tier = calculateBestiaryTier(kills);

            int color = kills > 0 ? TEXT_WHITE : TEXT_GRAY;
            ctx.text(tr, mobName, contentX, mobY, color);
            ctx.text(tr, formatNumber(kills), contentX + 200, mobY, color);
            ctx.text(tr, String.valueOf(tier), contentX + 280, mobY,
                    tier > 0 ? TEXT_GREEN : TEXT_GRAY);

            idx++;
            if (idx > 50) break; // Limit display
        }
    }

    private String formatMobName(String id) {
        String name = id.replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }
        if (sb.length() > 25) {
            return sb.substring(0, 22) + "...";
        }
        return sb.toString();
    }

    private int calculateBestiaryTier(int kills) {
        int[] tierThresholds = {10, 25, 75, 150, 250, 500, 1000, 2500, 5000, 10000};
        for (int i = tierThresholds.length - 1; i >= 0; i--) {
            if (kills >= tierThresholds[i]) return i + 1;
        }
        return 0;
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
        scrollOffset = (int) Math.max(0, scrollOffset - amount * 15);
        return true;
    }
}
