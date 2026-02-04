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
 * Museum page showing donated items and museum value.
 */
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
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.drawTextWithShadow(tr, "Museum", contentX, lineY, TEXT_GREEN);
        lineY += 20;

        if (loading) {
            ctx.drawTextWithShadow(tr, "Loading museum data...", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (museumData == null || museumData.has("error")) {
            String error = museumData != null && museumData.has("error") ?
                    museumData.get("error").getAsString() : "Failed to load museum data";
            ctx.drawTextWithShadow(tr, error, contentX, lineY, TEXT_GRAY);
            return;
        }

        // Get member data from museum response
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        JsonObject members = museumData.has("members") ?
                museumData.getAsJsonObject("members") : null;
        if (members == null) {
            ctx.drawTextWithShadow(tr, "No museum data for this profile", contentX, lineY, TEXT_GRAY);
            return;
        }

        JsonObject memberMuseum = members.has(profile.getOwnerUuid()) ?
                members.getAsJsonObject(profile.getOwnerUuid()) : null;
        if (memberMuseum == null) {
            ctx.drawTextWithShadow(tr, "No museum data for this player", contentX, lineY, TEXT_GRAY);
            return;
        }

        // Museum value
        if (memberMuseum.has("value")) {
            long value = memberMuseum.get("value").getAsLong();
            ctx.drawTextWithShadow(tr, "Museum Value: " + formatCoins(value), contentX, lineY, TEXT_GOLD);
            lineY += 16;
        }

        // Items donated count
        if (memberMuseum.has("items")) {
            JsonObject items = memberMuseum.getAsJsonObject("items");
            int itemCount = items.size();
            ctx.drawTextWithShadow(tr, "Items Donated: " + itemCount, contentX, lineY, TEXT_WHITE);
            lineY += 16;
        }

        // Special items donated
        lineY += 8;
        ctx.drawTextWithShadow(tr, "Special Items", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        if (memberMuseum.has("special")) {
            JsonObject special = memberMuseum.getAsJsonObject("special");
            int count = 0;
            for (var entry : special.entrySet()) {
                if (count >= 15) {
                    ctx.drawTextWithShadow(tr, "... and more", contentX, lineY, TEXT_GRAY);
                    break;
                }
                String itemName = formatItemName(entry.getKey());
                ctx.drawTextWithShadow(tr, "- " + itemName, contentX, lineY, TEXT_WHITE);
                lineY += 12;
                count++;
            }
        }

        // Armor sets
        lineY += 8;
        ctx.drawTextWithShadow(tr, "Armor Sets", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        if (memberMuseum.has("armor")) {
            JsonObject armor = memberMuseum.getAsJsonObject("armor");
            int count = 0;
            for (var entry : armor.entrySet()) {
                if (count >= 10) {
                    ctx.drawTextWithShadow(tr, "... and more", contentX, lineY, TEXT_GRAY);
                    break;
                }
                String setName = formatItemName(entry.getKey());
                ctx.drawTextWithShadow(tr, "- " + setName, contentX, lineY, TEXT_WHITE);
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
