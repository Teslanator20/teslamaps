package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Extra stats page showing kills, deaths, essence, and misc stats.
 */
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
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        JsonObject memberData = profile.getMemberData();
        int padding = 15;
        int contentX = x + padding;
        int col2X = x + width / 2;

        int lineY = y + padding;

        // === Left Column: Combat Stats ===
        ctx.drawTextWithShadow(tr, "Combat Stats", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        // Player stats
        JsonObject playerStats = getNestedObject(memberData, "player_stats");
        if (playerStats != null) {
            // Kills
            JsonObject kills = playerStats.has("kills") ? playerStats.getAsJsonObject("kills") : null;
            int totalKills = 0;
            if (kills != null) {
                for (var entry : kills.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalKills += entry.getValue().getAsInt();
                    }
                }
            }
            ctx.drawTextWithShadow(tr, "Total Kills: " + formatNumber(totalKills), contentX, lineY, TEXT_WHITE);
            lineY += 12;

            // Deaths
            JsonObject deaths = playerStats.has("deaths") ? playerStats.getAsJsonObject("deaths") : null;
            int totalDeaths = 0;
            if (deaths != null) {
                for (var entry : deaths.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalDeaths += entry.getValue().getAsInt();
                    }
                }
            }
            ctx.drawTextWithShadow(tr, "Total Deaths: " + formatNumber(totalDeaths), contentX, lineY, TEXT_WHITE);
            lineY += 12;

            // K/D Ratio
            double kd = totalDeaths > 0 ? (double) totalKills / totalDeaths : totalKills;
            ctx.drawTextWithShadow(tr, "K/D Ratio: " + String.format("%.2f", kd), contentX, lineY, TEXT_AQUA);
            lineY += 20;
        }

        // === Essence ===
        ctx.drawTextWithShadow(tr, "Essence", contentX, lineY, TEXT_GREEN);
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
                        ctx.drawTextWithShadow(tr, essenceNames[i] + ": " + formatNumber(amount), contentX, lineY, essenceColors[i]);
                        lineY += 12;
                    }
                }
            }
        }

        // === Right Column: Misc Stats ===
        lineY = y + padding;
        ctx.drawTextWithShadow(tr, "Miscellaneous", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        // First join
        if (memberData.has("profile")) {
            JsonObject profileInfo = memberData.getAsJsonObject("profile");
            if (profileInfo.has("first_join")) {
                long firstJoin = profileInfo.get("first_join").getAsLong();
                String date = new java.text.SimpleDateFormat("MMM d, yyyy").format(new java.util.Date(firstJoin));
                ctx.drawTextWithShadow(tr, "First Join: " + date, col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }
        }

        // Fishing stats
        if (playerStats != null) {
            int itemsFished = 0;
            if (playerStats.has("items_fished")) {
                JsonObject fished = playerStats.getAsJsonObject("items_fished");
                if (fished.has("total")) {
                    itemsFished = fished.get("total").getAsInt();
                }
            }
            if (itemsFished > 0) {
                ctx.drawTextWithShadow(tr, "Items Fished: " + formatNumber(itemsFished), col2X, lineY, TEXT_AQUA);
                lineY += 12;
            }

            // Auctions
            if (playerStats.has("auctions")) {
                JsonObject auctions = playerStats.getAsJsonObject("auctions");
                int sold = auctions.has("sold") ? auctions.get("sold").getAsInt() : 0;
                long goldEarned = auctions.has("gold_earned") ? auctions.get("gold_earned").getAsLong() : 0;
                ctx.drawTextWithShadow(tr, "Auctions Sold: " + formatNumber(sold), col2X, lineY, TEXT_WHITE);
                lineY += 12;
                ctx.drawTextWithShadow(tr, "Gold Earned: " + formatCoins(goldEarned), col2X, lineY, TEXT_GOLD);
                lineY += 12;
            }
        }

        // Slayer stats summary
        lineY += 8;
        ctx.drawTextWithShadow(tr, "Slayer Bosses Killed", col2X, lineY, TEXT_GREEN);
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
                        ctx.drawTextWithShadow(tr, slayerNames[i] + ": " + formatNumber(xp) + " XP", col2X, lineY, slayerColors[i]);
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
