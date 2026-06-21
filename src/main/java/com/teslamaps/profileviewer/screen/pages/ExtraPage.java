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

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ExtraPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_RED = 0xFFFF5555;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int TEXT_AQUA = 0xFF55FFFF;

    @Override
    public String getTabName() {
        return "Extra";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
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
        int col2X = x + width / 2;

        int lineY = y + padding;

        ctx.text(tr, "Combat Stats", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject playerStats = getNestedObject(memberData, "player_stats");
        if (playerStats != null) {
            JsonObject kills = playerStats.has("kills") ? playerStats.getAsJsonObject("kills") : null;
            int totalKills = 0;
            if (kills != null) {
                for (var entry : kills.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalKills += entry.getValue().getAsInt();
                    }
                }
            }
            ctx.text(tr, "Total Kills: " + formatNumber(totalKills), contentX, lineY, TEXT_WHITE);
            lineY += 12;

            JsonObject deaths = playerStats.has("deaths") ? playerStats.getAsJsonObject("deaths") : null;
            int totalDeaths = 0;
            if (deaths != null) {
                for (var entry : deaths.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalDeaths += entry.getValue().getAsInt();
                    }
                }
            }
            ctx.text(tr, "Total Deaths: " + formatNumber(totalDeaths), contentX, lineY, TEXT_WHITE);
            lineY += 12;

            double kd = totalDeaths > 0 ? (double) totalKills / totalDeaths : totalKills;
            ctx.text(tr, "K/D Ratio: " + String.format("%.2f", kd), contentX, lineY, TEXT_AQUA);
            lineY += 20;
        }

        ctx.text(tr, "Essence", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject currencies = getNestedObject(memberData, "currencies");
        if (currencies != null && currencies.has("essence")) {
            JsonObject essence = currencies.getAsJsonObject("essence");
            String[] essenceTypes = {"WITHER", "DRAGON", "SPIDER", "UNDEAD", "DIAMOND", "GOLD", "ICE", "CRIMSON"};
            String[] essenceNames = {"Wither", "Dragon", "Spider", "Undead", "Diamond", "Gold", "Ice", "Crimson"};
            int[] essenceColors = {0xFF555555, 0xFFAA00AA, 0xFF333333, 0xFF00AA00, 0xFF55FFFF, 0xFFFFAA00, 0xFF55FFFF, 0xFFFF5555};

            for (int i = 0; i < essenceTypes.length; i++) {
                if (essence.has(essenceTypes[i])) {
                    JsonObject ess = essence.getAsJsonObject(essenceTypes[i]);
                    int amount = ess.has("current") ? ess.get("current").getAsInt() : 0;
                    if (amount > 0) {
                        ctx.text(tr, essenceNames[i] + ": " + formatNumber(amount), contentX, lineY, essenceColors[i]);
                        lineY += 12;
                    }
                }
            }
        }

        lineY = y + padding;
        ctx.text(tr, "Miscellaneous", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        if (memberData.has("profile")) {
            JsonObject profileInfo = memberData.getAsJsonObject("profile");
            if (profileInfo.has("first_join")) {
                long firstJoin = profileInfo.get("first_join").getAsLong();
                String date = new java.text.SimpleDateFormat("MMM d, yyyy").format(new java.util.Date(firstJoin));
                ctx.text(tr, "First Join: " + date, col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }
        }

        if (playerStats != null) {
            int itemsFished = 0;
            if (playerStats.has("items_fished")) {
                JsonObject fished = playerStats.getAsJsonObject("items_fished");
                if (fished.has("total")) {
                    itemsFished = fished.get("total").getAsInt();
                }
            }
            if (itemsFished > 0) {
                ctx.text(tr, "Items Fished: " + formatNumber(itemsFished), col2X, lineY, TEXT_AQUA);
                lineY += 12;
            }

            if (playerStats.has("auctions")) {
                JsonObject auctions = playerStats.getAsJsonObject("auctions");
                int sold = auctions.has("sold") ? auctions.get("sold").getAsInt() : 0;
                long goldEarned = auctions.has("gold_earned") ? auctions.get("gold_earned").getAsLong() : 0;
                ctx.text(tr, "Auctions Sold: " + formatNumber(sold), col2X, lineY, TEXT_WHITE);
                lineY += 12;
                ctx.text(tr, "Gold Earned: " + formatCoins(goldEarned), col2X, lineY, TEXT_GOLD);
                lineY += 12;
            }
        }

        lineY += 8;
        ctx.text(tr, "Slayer Bosses Killed", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject slayerBosses = getNestedObject(memberData, "slayer.slayer_bosses");
        if (slayerBosses != null) {
            String[] slayerTypes = {"zombie", "spider", "wolf", "enderman", "blaze", "vampire"};
            String[] slayerNames = {"Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Riftstalker"};
            int[] slayerColors = {0xFF00AA00, 0xFF333333, 0xFF888888, 0xFFAA00AA, 0xFFFF5500, 0xFFFF0000};

            for (int i = 0; i < slayerTypes.length; i++) {
                if (slayerBosses.has(slayerTypes[i])) {
                    JsonObject slayer = slayerBosses.getAsJsonObject(slayerTypes[i]);
                    int xp = slayer.has("xp") ? slayer.get("xp").getAsInt() : 0;
                    if (xp > 0) {
                        ctx.text(tr, slayerNames[i] + ": " + formatNumber(xp) + " XP", col2X, lineY, slayerColors[i]);
                        lineY += 12;
                    }
                }
            }
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
}
