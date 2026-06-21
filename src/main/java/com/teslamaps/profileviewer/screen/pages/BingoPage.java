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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BingoPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int CELL_SIZE = 40;
    private static final int GRID_SIZE = 5;

    private JsonObject bingoData;
    private boolean loading = true;

    @Override
    public String getTabName() {
        return "Bingo";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.FILLED_MAP);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        loadBingoData();
    }

    private void loadBingoData() {
        if (profiles == null) return;
        loading = true;
        HypixelApi.getBingo(profiles.getUuid()).thenAccept(data -> {
            this.bingoData = data;
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

        ctx.text(tr, "Bingo Card", contentX, lineY, TEXT_GREEN);
        lineY += 20;

        if (loading) {
            ctx.text(tr, "Loading bingo data...", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (bingoData == null || bingoData.has("error")) {
            String error = bingoData != null && bingoData.has("error") ?
                    bingoData.get("error").getAsString() : "Failed to load bingo data";
            ctx.text(tr, error, contentX, lineY, TEXT_GRAY);
            return;
        }

        Set<Integer> completedGoals = new HashSet<>();
        if (bingoData.has("events")) {
            JsonArray events = bingoData.getAsJsonArray("events");
            if (!events.isEmpty()) {
                JsonObject latestEvent = events.get(events.size() - 1).getAsJsonObject();
                if (latestEvent.has("completed_goals")) {
                    JsonArray completed = latestEvent.getAsJsonArray("completed_goals");
                    for (JsonElement elem : completed) {
                        completedGoals.add(elem.getAsInt());
                    }
                }
            }
        }

        int gridX = contentX + (width - padding * 2 - CELL_SIZE * GRID_SIZE) / 2;
        int gridY = lineY;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int goalIndex = row * GRID_SIZE + col;
                int cellX = gridX + col * CELL_SIZE;
                int cellY = gridY + row * CELL_SIZE;

                boolean completed = completedGoals.contains(goalIndex);

                int bgColor = completed ? 0xFF225522 : 0xFF333333;
                ctx.fill(cellX, cellY, cellX + CELL_SIZE - 2, cellY + CELL_SIZE - 2, bgColor);

                int borderColor = completed ? TEXT_GREEN : 0xFF555555;
                drawBorder(ctx, cellX, cellY, CELL_SIZE - 2, CELL_SIZE - 2, borderColor);

                String num = String.valueOf(goalIndex + 1);
                int numX = cellX + (CELL_SIZE - 2 - tr.width(num)) / 2;
                int numY = cellY + (CELL_SIZE - 2 - 8) / 2;
                ctx.text(tr, num, numX, numY, completed ? TEXT_GREEN : TEXT_GRAY);

                if (completed) {
                    ctx.text(tr, "", cellX + 2, cellY + 2, TEXT_GREEN);
                }
            }
        }

        int statsY = gridY + GRID_SIZE * CELL_SIZE + 15;
        int completedCount = completedGoals.size();
        int totalGoals = GRID_SIZE * GRID_SIZE;

        ctx.text(tr, "Completed: " + completedCount + "/" + totalGoals,
                contentX, statsY, TEXT_WHITE);

        int bingos = countBingos(completedGoals);
        if (bingos > 0) {
            ctx.text(tr, "Bingos: " + bingos, contentX + 150, statsY, TEXT_GOLD);
        }
    }

    private int countBingos(Set<Integer> completed) {
        int count = 0;

        for (int row = 0; row < GRID_SIZE; row++) {
            boolean bingo = true;
            for (int col = 0; col < GRID_SIZE; col++) {
                if (!completed.contains(row * GRID_SIZE + col)) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) count++;
        }

        for (int col = 0; col < GRID_SIZE; col++) {
            boolean bingo = true;
            for (int row = 0; row < GRID_SIZE; row++) {
                if (!completed.contains(row * GRID_SIZE + col)) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) count++;
        }

        boolean diag1 = true, diag2 = true;
        for (int i = 0; i < GRID_SIZE; i++) {
            if (!completed.contains(i * GRID_SIZE + i)) diag1 = false;
            if (!completed.contains(i * GRID_SIZE + (GRID_SIZE - 1 - i))) diag2 = false;
        }
        if (diag1) count++;
        if (diag2) count++;

        return count;
    }
}
