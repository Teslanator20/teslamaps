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
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MuseumPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;

    private JsonObject museumData;
    private boolean loading = true;

    @Override
    public String getTabName() {
        return "Museum";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.GOLD_BLOCK);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        loadMuseumData();
    }

    @Override
    public void onSelected() {
        if (museumData == null && !loading) {
            loadMuseumData();
        }
    }

    private void loadMuseumData() {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        loading = true;
        HypixelApi.getMuseum(profile.getProfileId()).thenAccept(data -> {
            this.museumData = data;
            this.loading = false;
        });
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.text(tr, "Museum", contentX, lineY, TEXT_GREEN);
        lineY += 20;

        if (loading) {
            ctx.text(tr, "Loading museum data...", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (museumData == null || museumData.has("error")) {
            String error = museumData != null && museumData.has("error") ?
                    museumData.get("error").getAsString() : "Failed to load museum data";
            ctx.text(tr, error, contentX, lineY, TEXT_GRAY);
            return;
        }

        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        JsonObject members = museumData.has("members") ?
                museumData.getAsJsonObject("members") : null;
        if (members == null) {
            ctx.text(tr, "No museum data for this profile", contentX, lineY, TEXT_GRAY);
            return;
        }

        JsonObject memberMuseum = members.has(profile.getOwnerUuid()) ?
                members.getAsJsonObject(profile.getOwnerUuid()) : null;
        if (memberMuseum == null) {
            ctx.text(tr, "No museum data for this player", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (memberMuseum.has("value")) {
            long value = memberMuseum.get("value").getAsLong();
            ctx.text(tr, "Museum Value: " + formatCoins(value), contentX, lineY, TEXT_GOLD);
            lineY += 16;
        }

        if (memberMuseum.has("items")) {
            JsonObject items = memberMuseum.getAsJsonObject("items");
            int itemCount = items.size();
            ctx.text(tr, "Items Donated: " + itemCount, contentX, lineY, TEXT_WHITE);
            lineY += 16;
        }

        lineY += 8;
        ctx.text(tr, "Special Items", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        if (memberMuseum.has("special")) {
            JsonObject special = memberMuseum.getAsJsonObject("special");
            int count = 0;
            for (var entry : special.entrySet()) {
                if (count >= 15) {
                    ctx.text(tr, "... and more", contentX, lineY, TEXT_GRAY);
                    break;
                }
                String itemName = formatItemName(entry.getKey());
                ctx.text(tr, "- " + itemName, contentX, lineY, TEXT_WHITE);
                lineY += 12;
                count++;
            }
        }

        lineY += 8;
        ctx.text(tr, "Armor Sets", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        if (memberMuseum.has("armor")) {
            JsonObject armor = memberMuseum.getAsJsonObject("armor");
            int count = 0;
            for (var entry : armor.entrySet()) {
                if (count >= 10) {
                    ctx.text(tr, "... and more", contentX, lineY, TEXT_GRAY);
                    break;
                }
                String setName = formatItemName(entry.getKey());
                ctx.text(tr, "- " + setName, contentX, lineY, TEXT_WHITE);
                lineY += 12;
                count++;
            }
        }
    }

    private String formatItemName(String id) {
        String name = id.replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
