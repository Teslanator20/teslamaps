package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashSet;
import java.util.Set;

/**
 * Bingo page showing bingo card progress.
 */
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
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.drawTextWithShadow(tr, "Bingo Card", contentX, lineY, TEXT_GREEN);
        lineY += 20;

        if (loading) {
            ctx.drawTextWithShadow(tr, "Loading bingo data...", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (bingoData == null || bingoData.has("error")) {
            String error = bingoData != null && bingoData.has("error") ?
                    bingoData.get("error").getAsString() : "Failed to load bingo data";
            ctx.drawTextWithShadow(tr, error, contentX, lineY, TEXT_GRAY);
            return;
        }

        // Get completed goals
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

        // Draw 5x5 bingo grid
        int gridX = contentX + (width - padding * 2 - CELL_SIZE * GRID_SIZE) / 2;
        int gridY = lineY;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int goalIndex = row * GRID_SIZE + col;
                int cellX = gridX + col * CELL_SIZE;
                int cellY = gridY + row * CELL_SIZE;

                boolean completed = completedGoals.contains(goalIndex);

                // Cell background
                int bgColor = completed ? 0xFF225522 : 0xFF333333;
                ctx.fill(cellX, cellY, cellX + CELL_SIZE - 2, cellY + CELL_SIZE - 2, bgColor);

                // Border
                int borderColor = completed ? TEXT_GREEN : 0xFF555555;
                drawBorder(ctx, cellX, cellY, CELL_SIZE - 2, CELL_SIZE - 2, borderColor);

                // Goal number
                String num = String.valueOf(goalIndex + 1);
                int numX = cellX + (CELL_SIZE - 2 - tr.getWidth(num)) / 2;
                int numY = cellY + (CELL_SIZE - 2 - 8) / 2;
                ctx.drawTextWithShadow(tr, num, numX, numY, completed ? TEXT_GREEN : TEXT_GRAY);

                // Checkmark for completed
                if (completed) {
                    ctx.drawTextWithShadow(tr, "✓", cellX + 2, cellY + 2, TEXT_GREEN);
                }
            }
        }

        // Stats below grid
        int statsY = gridY + GRID_SIZE * CELL_SIZE + 15;
        int completedCount = completedGoals.size();
        int totalGoals = GRID_SIZE * GRID_SIZE;

        ctx.drawTextWithShadow(tr, "Completed: " + completedCount + "/" + totalGoals,
                contentX, statsY, TEXT_WHITE);

        // Check for bingo (5 in a row)
        int bingos = countBingos(completedGoals);
        if (bingos > 0) {
            ctx.drawTextWithShadow(tr, "Bingos: " + bingos, contentX + 150, statsY, TEXT_GOLD);
        }
    }

    private int countBingos(Set<Integer> completed) {
        int count = 0;

        // Check rows
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

        // Check columns
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

        // Check diagonals
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
