package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trophy Fish page showing fish catches by tier.
 */
public class TrophyFishPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int BRONZE = 0xFFCD7F32;
    private static final int SILVER = 0xFFC0C0C0;
    private static final int GOLD = 0xFFFFD700;
    private static final int DIAMOND = 0xFF55FFFF;

    private int scrollOffset = 0;

    // Trophy fish names
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
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        JsonObject memberData = profile.getMemberData();
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.drawTextWithShadow(tr, "Trophy Fish", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        // Get trophy fish data
        JsonObject trophyFish = getNestedObject(memberData, "trophy_fish");
        if (trophyFish == null) {
            ctx.drawTextWithShadow(tr, "No trophy fish data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        // Total catches
        int totalCatches = trophyFish.has("total_caught") ? trophyFish.get("total_caught").getAsInt() : 0;
        ctx.drawTextWithShadow(tr, "Total Caught: " + formatNumber(totalCatches), contentX, lineY, TEXT_WHITE);
        lineY += 20;

        // Column headers
        ctx.drawTextWithShadow(tr, "Fish", contentX, lineY, TEXT_GRAY);
        ctx.drawTextWithShadow(tr, "Bronze", contentX + 140, lineY, BRONZE);
        ctx.drawTextWithShadow(tr, "Silver", contentX + 190, lineY, SILVER);
        ctx.drawTextWithShadow(tr, "Gold", contentX + 240, lineY, GOLD);
        ctx.drawTextWithShadow(tr, "Diamond", contentX + 280, lineY, DIAMOND);
        lineY += 14;

        // Fish list
        int listStartY = lineY;
        int listHeight = height - (lineY - y) - padding;

        for (int i = 0; i < TROPHY_FISH.length; i++) {
            int fishY = listStartY + i * 14 - scrollOffset;
            if (fishY < listStartY - 14 || fishY > listStartY + listHeight) continue;

            String fishKey = TROPHY_FISH[i];
            String displayName = TROPHY_DISPLAY[i];

            // Get counts for each tier
            int bronze = trophyFish.has(fishKey + "_bronze") ? trophyFish.get(fishKey + "_bronze").getAsInt() : 0;
            int silver = trophyFish.has(fishKey + "_silver") ? trophyFish.get(fishKey + "_silver").getAsInt() : 0;
            int gold = trophyFish.has(fishKey + "_gold") ? trophyFish.get(fishKey + "_gold").getAsInt() : 0;
            int diamond = trophyFish.has(fishKey + "_diamond") ? trophyFish.get(fishKey + "_diamond").getAsInt() : 0;

            int total = bronze + silver + gold + diamond;
            int nameColor = total > 0 ? TEXT_WHITE : TEXT_GRAY;

            ctx.drawTextWithShadow(tr, displayName, contentX, fishY, nameColor);
            ctx.drawTextWithShadow(tr, String.valueOf(bronze), contentX + 140, fishY, bronze > 0 ? BRONZE : TEXT_GRAY);
            ctx.drawTextWithShadow(tr, String.valueOf(silver), contentX + 190, fishY, silver > 0 ? SILVER : TEXT_GRAY);
            ctx.drawTextWithShadow(tr, String.valueOf(gold), contentX + 240, fishY, gold > 0 ? GOLD : TEXT_GRAY);
            ctx.drawTextWithShadow(tr, String.valueOf(diamond), contentX + 280, fishY, diamond > 0 ? DIAMOND : TEXT_GRAY);
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
