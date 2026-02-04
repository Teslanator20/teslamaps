package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Garden page showing crop milestones, visitor milestones, and plots.
 */
public class GardenPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int BAR_BG = 0xFF333333;

    private JsonObject gardenData;
    private boolean loading = true;

    private static final String[] CROPS = {
            "WHEAT", "CARROT", "POTATO", "PUMPKIN", "MELON",
            "MUSHROOM", "CACTUS", "SUGAR_CANE", "NETHER_WART", "COCOA_BEANS"
    };

    private static final String[] CROP_NAMES = {
            "Wheat", "Carrot", "Potato", "Pumpkin", "Melon",
            "Mushroom", "Cactus", "Sugar Cane", "Nether Wart", "Cocoa Beans"
    };

    @Override
    public String getTabName() {
        return "Garden";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.GOLDEN_HOE);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        loadGardenData();
    }

    @Override
    public void onSelected() {
        if (gardenData == null && !loading) {
            loadGardenData();
        }
    }

    private void loadGardenData() {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        loading = true;
        HypixelApi.getGarden(profile.getProfileId()).thenAccept(data -> {
            this.gardenData = data;
            this.loading = false;
        });
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int padding = 15;
        int contentX = x + padding;
        int col2X = x + width / 2;

        int lineY = y + padding;

        ctx.drawTextWithShadow(tr, "Garden", contentX, lineY, TEXT_GREEN);
        lineY += 20;

        if (loading) {
            ctx.drawTextWithShadow(tr, "Loading garden data...", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (gardenData == null || gardenData.has("error")) {
            String error = gardenData != null && gardenData.has("error") ?
                    gardenData.get("error").getAsString() : "Failed to load garden data";
            ctx.drawTextWithShadow(tr, error, contentX, lineY, TEXT_GRAY);
            return;
        }

        // Garden level
        if (gardenData.has("garden_experience")) {
            long exp = gardenData.get("garden_experience").getAsLong();
            int level = calculateGardenLevel(exp);
            ctx.drawTextWithShadow(tr, "Garden Level: " + level, contentX, lineY, TEXT_GOLD);
            lineY += 20;
        }

        // === Crop Milestones ===
        ctx.drawTextWithShadow(tr, "Crop Milestones", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject cropMilestones = gardenData.has("resources_collected") ?
                gardenData.getAsJsonObject("resources_collected") : null;

        for (int i = 0; i < CROPS.length && i < 5; i++) {
            String cropKey = CROPS[i];
            String cropName = CROP_NAMES[i];

            long collected = 0;
            if (cropMilestones != null && cropMilestones.has(cropKey)) {
                collected = cropMilestones.get(cropKey).getAsLong();
            }

            int milestone = calculateCropMilestone(collected);
            ctx.drawTextWithShadow(tr, cropName + ": " + milestone, contentX, lineY,
                    milestone > 0 ? TEXT_WHITE : TEXT_GRAY);
            lineY += 12;
        }

        // Second column - remaining crops
        lineY = y + padding + 36;
        for (int i = 5; i < CROPS.length; i++) {
            String cropKey = CROPS[i];
            String cropName = CROP_NAMES[i];

            long collected = 0;
            if (cropMilestones != null && cropMilestones.has(cropKey)) {
                collected = cropMilestones.get(cropKey).getAsLong();
            }

            int milestone = calculateCropMilestone(collected);
            ctx.drawTextWithShadow(tr, cropName + ": " + milestone, col2X, lineY,
                    milestone > 0 ? TEXT_WHITE : TEXT_GRAY);
            lineY += 12;
        }

        // === Visitor Stats (right column) ===
        lineY = y + padding;
        ctx.drawTextWithShadow(tr, "Visitors", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        if (gardenData.has("commission_data")) {
            JsonObject commissions = gardenData.getAsJsonObject("commission_data");

            if (commissions.has("visits")) {
                JsonObject visits = commissions.getAsJsonObject("visits");
                int totalVisits = 0;
                for (var entry : visits.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalVisits += entry.getValue().getAsInt();
                    }
                }
                ctx.drawTextWithShadow(tr, "Total Visits: " + formatNumber(totalVisits), col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }
        }

        // Composter
        lineY += 8;
        ctx.drawTextWithShadow(tr, "Composter", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        if (gardenData.has("composter_data")) {
            JsonObject composter = gardenData.getAsJsonObject("composter_data");

            if (composter.has("organic_matter")) {
                long organicMatter = composter.get("organic_matter").getAsLong();
                ctx.drawTextWithShadow(tr, "Organic Matter: " + formatNumber(organicMatter), col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }

            if (composter.has("fuel_units")) {
                long fuel = composter.get("fuel_units").getAsLong();
                ctx.drawTextWithShadow(tr, "Fuel: " + formatNumber(fuel), col2X, lineY, TEXT_WHITE);
            }
        }
    }

    private int calculateGardenLevel(long exp) {
        // Simplified garden level calculation
        long[] thresholds = {0, 70, 100, 140, 240, 600, 1500, 2000, 2500, 3000,
                10000, 10000, 10000, 10000, 10000};
        long total = 0;
        for (int i = 0; i < thresholds.length; i++) {
            total += thresholds[i];
            if (exp < total) return i;
        }
        return 15;
    }

    private int calculateCropMilestone(long collected) {
        // Simplified milestone calculation
        long[] thresholds = {100, 150, 250, 500, 1500, 5000, 15000, 50000, 150000, 500000};
        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (collected >= thresholds[i]) return i + 1;
        }
        return 0;
    }
}
