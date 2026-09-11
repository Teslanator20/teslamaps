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
            "obfuscated_fish_3", "lava_horse", "mana_ray", "volcanic_stonefish",
            "vanille", "skeleton_fish", "moldfin", "soul_fish",
            "karate_fish", "golden_fish"
    };

    private static final String[] TROPHY_DISPLAY = {
            "Sulphur Skitter", "Obfuscated 1", "Steaming Hot Flounder", "Gusher",
            "Blobfish", "Obfuscated 2", "Slugfish", "Flyfish",
            "Obfuscated 3", "Lava Horse", "Mana Ray", "Volcanic Stonefish",
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

        int columnWidth = 205;
        for (int c = 0; c < 2; c++) {
            int cx = contentX + c * columnWidth;
            ctx.text(tr, "Fish", cx, lineY, TEXT_GRAY);
            right(ctx, tr, "B", cx + 130, lineY, BRONZE);
            right(ctx, tr, "S", cx + 152, lineY, SILVER);
            right(ctx, tr, "G", cx + 174, lineY, GOLD);
            right(ctx, tr, "D", cx + 196, lineY, DIAMOND);
        }
        lineY += 12;

        int listStartY = lineY;
        int listHeight = height - (lineY - y) - padding;

        ctx.enableScissor(x, listStartY, x + width, listStartY + listHeight);
        int perColumn = (TROPHY_FISH.length + 1) / 2;
        for (int i = 0; i < TROPHY_FISH.length; i++) {
            int column = i / perColumn;
            int row = i % perColumn;
            int cx = contentX + column * columnWidth;
            int fishY = listStartY + row * 11;
            if (fishY > listStartY + listHeight) continue;

            String fishKey = TROPHY_FISH[i];
            String displayName = TROPHY_DISPLAY[i];

            int bronze = trophyFish.has(fishKey + "_bronze") ? trophyFish.get(fishKey + "_bronze").getAsInt() : 0;
            int silver = trophyFish.has(fishKey + "_silver") ? trophyFish.get(fishKey + "_silver").getAsInt() : 0;
            int gold = trophyFish.has(fishKey + "_gold") ? trophyFish.get(fishKey + "_gold").getAsInt() : 0;
            int diamond = trophyFish.has(fishKey + "_diamond") ? trophyFish.get(fishKey + "_diamond").getAsInt() : 0;

            int total = bronze + silver + gold + diamond;
            int nameColor = total > 0 ? TEXT_WHITE : TEXT_GRAY;

            scaledName(ctx, tr, displayName, cx, fishY, 96, nameColor);
            right(ctx, tr, String.valueOf(bronze), cx + 130, fishY, bronze > 0 ? BRONZE : TEXT_GRAY);
            right(ctx, tr, String.valueOf(silver), cx + 152, fishY, silver > 0 ? SILVER : TEXT_GRAY);
            right(ctx, tr, String.valueOf(gold), cx + 174, fishY, gold > 0 ? GOLD : TEXT_GRAY);
            right(ctx, tr, String.valueOf(diamond), cx + 196, fishY, diamond > 0 ? DIAMOND : TEXT_GRAY);
        }
        ctx.disableScissor();
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

    private void right(GuiGraphicsExtractor ctx, Font tr, String text, int rightEdge, int y, int color) {
        ctx.text(tr, text, rightEdge - tr.width(text), y, color);
    }

    /** Long fish names get squeezed instead of running under the counts. */
    private void scaledName(GuiGraphicsExtractor ctx, Font tr, String text, int x, int y, int maxWidth, int color) {
        int width = tr.width(text);
        if (width <= maxWidth) {
            ctx.text(tr, text, x, y, color);
            return;
        }
        float factor = Math.max(0.55f, (float) maxWidth / width);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y + 1);
        ctx.pose().scale(factor, factor);
        ctx.text(tr, text, 0, 0, color);
        ctx.pose().popMatrix();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxScroll = Math.max(0, TROPHY_FISH.length * 14 - 200);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - amount * 15));
        return true;
    }
}
