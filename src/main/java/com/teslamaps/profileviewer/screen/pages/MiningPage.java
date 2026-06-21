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

public class MiningPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_AQUA = 0xFF55FFFF;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int MITHRIL_COLOR = 0xFF55FFAA;
    private static final int GEMSTONE_COLOR = 0xFFFF55FF;
    private static final int GLACITE_COLOR = 0xFF55FFFF;
    private static final int BAR_BG = 0xFF333333;

    @Override
    public String getTabName() {
        return "Mining";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
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

        JsonObject mining = getNestedObject(memberData, "mining_core");
        if (mining == null) {
            ctx.text(tr, "No mining data available", contentX, lineY, TEXT_GRAY);
            return;
        }

        ctx.text(tr, "Heart of the Mountain", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        double hotmXp = mining.has("experience") ? mining.get("experience").getAsDouble() : 0;
        int hotmLevel = calculateHotmLevel(hotmXp);

        ctx.text(tr, "Level: " + hotmLevel, contentX, lineY, TEXT_AQUA);
        lineY += 12;

        double progress = getHotmProgress(hotmXp, hotmLevel);
        drawProgressBar(ctx, contentX, lineY, 200, 10, progress, BAR_BG, TEXT_AQUA);
        lineY += 20;

        ctx.text(tr, "Powder", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        int mithrilPowder = mining.has("powder_mithril") ? mining.get("powder_mithril").getAsInt() : 0;
        int mithrilSpent = mining.has("powder_spent_mithril") ? mining.get("powder_spent_mithril").getAsInt() : 0;
        ctx.text(tr, "Mithril: " + formatNumber(mithrilPowder) + " (+" + formatNumber(mithrilSpent) + " spent)",
                contentX, lineY, MITHRIL_COLOR);
        lineY += 12;

        int gemstonePowder = mining.has("powder_gemstone") ? mining.get("powder_gemstone").getAsInt() : 0;
        int gemstoneSpent = mining.has("powder_spent_gemstone") ? mining.get("powder_spent_gemstone").getAsInt() : 0;
        ctx.text(tr, "Gemstone: " + formatNumber(gemstonePowder) + " (+" + formatNumber(gemstoneSpent) + " spent)",
                contentX, lineY, GEMSTONE_COLOR);
        lineY += 12;

        if (mining.has("powder_glacite")) {
            int glacitePowder = mining.get("powder_glacite").getAsInt();
            int glaciteSpent = mining.has("powder_spent_glacite") ? mining.get("powder_spent_glacite").getAsInt() : 0;
            ctx.text(tr, "Glacite: " + formatNumber(glacitePowder) + " (+" + formatNumber(glaciteSpent) + " spent)",
                    contentX, lineY, GLACITE_COLOR);
            lineY += 12;
        }

        lineY += 8;

        ctx.text(tr, "Commissions", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        if (mining.has("commissions_milestone")) {
            int milestone = mining.get("commissions_milestone").getAsInt();
            ctx.text(tr, "Milestone: " + milestone, contentX, lineY, TEXT_WHITE);
            lineY += 12;
        }

        lineY += 8;
        ctx.text(tr, "Crystal Nucleus", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject crystals = mining.has("crystals") ? mining.getAsJsonObject("crystals") : null;
        if (crystals != null) {
            String[] crystalNames = {"jade_crystal", "amber_crystal", "topaz_crystal", "sapphire_crystal", "amethyst_crystal", "jasper_crystal", "ruby_crystal", "opal_crystal"};
            String[] displayNames = {"Jade", "Amber", "Topaz", "Sapphire", "Amethyst", "Jasper", "Ruby", "Opal"};
            int[] colors = {0xFF55FF55, 0xFFFFAA00, 0xFFFFFF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFF5555, 0xFFFF0000, 0xFFFFFFFF};

            for (int i = 0; i < crystalNames.length; i++) {
                if (crystals.has(crystalNames[i])) {
                    JsonObject crystal = crystals.getAsJsonObject(crystalNames[i]);
                    String state = crystal.has("state") ? crystal.get("state").getAsString() : "NOT_FOUND";
                    int total = crystal.has("total_placed") ? crystal.get("total_placed").getAsInt() : 0;

                    String stateDisplay = switch (state) {
                        case "FOUND" -> "[Found]";
                        case "PLACED" -> "[Placed]";
                        default -> "[Not Found]";
                    };

                    ctx.text(tr, displayNames[i] + " " + stateDisplay + " (" + total + " placed)",
                            contentX, lineY, colors[i]);
                    lineY += 12;
                }
            }
        }
    }

    private int calculateHotmLevel(double xp) {
        double[] xpTable = {0, 3000, 9000, 25000, 60000, 100000, 150000, 210000, 290000, 400000};
        for (int i = xpTable.length - 1; i >= 0; i--) {
            if (xp >= xpTable[i]) return i + 1;
        }
        return 1;
    }

    private double getHotmProgress(double xp, int level) {
        double[] xpTable = {0, 3000, 9000, 25000, 60000, 100000, 150000, 210000, 290000, 400000};
        if (level >= 10) return 1.0;
        double current = xpTable[level - 1];
        double next = xpTable[level];
        return (xp - current) / (next - current);
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
