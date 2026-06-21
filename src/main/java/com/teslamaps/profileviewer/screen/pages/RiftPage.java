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

public class RiftPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_PURPLE = 0xFFAA00AA;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int BAR_BG = 0xFF333333;

    @Override
    public String getTabName() {
        return "Rift";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.ENDER_EYE);
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

        ctx.text(tr, "The Rift", contentX, lineY, TEXT_PURPLE);
        lineY += 20;

        JsonObject rift = getNestedObject(memberData, "rift");
        if (rift == null) {
            ctx.text(tr, "No Rift data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        ctx.text(tr, "Motes", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject currencies = profile.getMemberData().has("currencies") ?
                profile.getMemberData().getAsJsonObject("currencies") : null;
        if (currencies != null && currencies.has("motes_purse")) {
            double motes = currencies.get("motes_purse").getAsDouble();
            ctx.text(tr, "Current: " + formatNumber(motes), contentX, lineY, TEXT_PURPLE);
            lineY += 12;
        }

        if (rift.has("lifetime_motes_earned")) {
            double lifetimeMotes = rift.get("lifetime_motes_earned").getAsDouble();
            ctx.text(tr, "Lifetime: " + formatNumber(lifetimeMotes), contentX, lineY, TEXT_GRAY);
            lineY += 20;
        }

        ctx.text(tr, "Timecharms", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject timecharms = getNestedObject(rift, "gallery.secured_trophies");
        if (timecharms != null) {
            int count = timecharms.size();
            ctx.text(tr, "Collected: " + count, contentX, lineY, TEXT_WHITE);
            lineY += 20;
        } else {
            ctx.text(tr, "None collected", contentX, lineY, TEXT_GRAY);
            lineY += 20;
        }

        ctx.text(tr, "Enigma Souls", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject enigma = getNestedObject(rift, "enigma");
        if (enigma != null && enigma.has("found_souls")) {
            int souls = enigma.getAsJsonArray("found_souls").size();
            ctx.text(tr, "Found: " + souls + " / 42", contentX, lineY,
                    souls >= 42 ? TEXT_GREEN : TEXT_WHITE);
        } else {
            ctx.text(tr, "Found: 0 / 42", contentX, lineY, TEXT_GRAY);
        }
        lineY += 20;

        lineY = y + padding;

        ctx.text(tr, "Montezuma", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject castle = getNestedObject(rift, "castle");
        if (castle != null) {
            if (castle.has("grubber_stacks")) {
                int stacks = castle.get("grubber_stacks").getAsInt();
                ctx.text(tr, "Grubber Stacks: " + stacks, col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }
        } else {
            ctx.text(tr, "No castle data", col2X, lineY, TEXT_GRAY);
            lineY += 12;
        }

        lineY += 8;

        ctx.text(tr, "Village Plaza", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject village = getNestedObject(rift, "village_plaza");
        if (village != null) {
            if (village.has("murder")) {
                JsonObject murder = village.getAsJsonObject("murder");
                if (murder.has("step_index")) {
                    int step = murder.get("step_index").getAsInt();
                    ctx.text(tr, "Murder Mystery: Step " + step, col2X, lineY, TEXT_WHITE);
                    lineY += 12;
                }
            }
        }

        lineY += 8;
        ctx.text(tr, "Rift Slayer", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject slayer = getNestedObject(rift, "slayer");
        if (slayer != null && slayer.has("vampire")) {
            JsonObject vampire = slayer.getAsJsonObject("vampire");
            if (vampire.has("rift_level")) {
                int level = vampire.get("rift_level").getAsInt();
                ctx.text(tr, "Vampire Level: " + level, col2X, lineY, TEXT_GOLD);
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
