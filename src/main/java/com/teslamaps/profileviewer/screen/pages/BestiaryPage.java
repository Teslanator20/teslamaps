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

import java.util.*;

/**
 * Bestiary page showing mob kill progress.
 */
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
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        JsonObject memberData = profile.getMemberData();
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.drawTextWithShadow(tr, "Bestiary", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        // Get bestiary data
        JsonObject bestiary = getNestedObject(memberData, "bestiary.kills");
        if (bestiary == null) {
            ctx.drawTextWithShadow(tr, "No bestiary data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        // Total milestone
        JsonObject bestiaryRoot = getNestedObject(memberData, "bestiary");
        if (bestiaryRoot != null && bestiaryRoot.has("milestone")) {
            JsonObject milestone = bestiaryRoot.getAsJsonObject("milestone");
            int level = milestone.has("last_claimed_milestone") ?
                    milestone.get("last_claimed_milestone").getAsInt() : 0;
            ctx.drawTextWithShadow(tr, "Milestone Level: " + level, contentX, lineY, TEXT_WHITE);
            lineY += 20;
        }

        // Collect and sort mobs by kills
        List<Map.Entry<String, Integer>> mobs = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : bestiary.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                mobs.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getAsInt()));
            }
        }
        mobs.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Headers
        ctx.drawTextWithShadow(tr, "Mob", contentX, lineY, TEXT_GRAY);
        ctx.drawTextWithShadow(tr, "Kills", contentX + 200, lineY, TEXT_GRAY);
        ctx.drawTextWithShadow(tr, "Tier", contentX + 280, lineY, TEXT_GRAY);
        lineY += 14;

        // Mob list
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
            ctx.drawTextWithShadow(tr, mobName, contentX, mobY, color);
            ctx.drawTextWithShadow(tr, formatNumber(kills), contentX + 200, mobY, color);
            ctx.drawTextWithShadow(tr, String.valueOf(tier), contentX + 280, mobY,
                    tier > 0 ? TEXT_GREEN : TEXT_GRAY);

            idx++;
            if (idx > 50) break; // Limit display
        }
    }

    private String formatMobName(String id) {
        // Convert API names to display names
        String name = id.replace("_", " ");
        // Title case
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }
        // Truncate if too long
        if (sb.length() > 25) {
            return sb.substring(0, 22) + "...";
        }
        return sb.toString();
    }

    private int calculateBestiaryTier(int kills) {
        // Simplified tier calculation
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
