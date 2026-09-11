package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

        ctx.text(tr, "Combat Stats", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject playerStats = getNestedObject(memberData, "player_stats");
        if (playerStats != null) {
            JsonObject kills = playerStats.has("kills") ? playerStats.getAsJsonObject("kills") : null;
            int totalKills = 0;
            if (kills != null) {
                for (var entry : kills.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalKills += asInt(entry.getValue());
                    }
                }
            }
            ctx.text(tr, "Total Kills: " + formatNumber(totalKills), contentX, lineY, TEXT_WHITE);
            lineY += 12;

            JsonObject deaths = playerStats.has("deaths") ? playerStats.getAsJsonObject("deaths") : null;
            int totalDeaths = 0;
            if (deaths != null) {
                for (var entry : deaths.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        totalDeaths += asInt(entry.getValue());
                    }
                }
            }
            ctx.text(tr, "Total Deaths: " + formatNumber(totalDeaths), contentX, lineY, TEXT_WHITE);
            lineY += 12;

            double kd = totalDeaths > 0 ? (double) totalKills / totalDeaths : totalKills;
            ctx.text(tr, "K/D Ratio: " + String.format("%.2f", kd), contentX, lineY, TEXT_AQUA);
            lineY += 20;
        }

        ctx.text(tr, "Essence", contentX, lineY, TEXT_GREEN);
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
                        ctx.text(tr, essenceNames[i] + ": " + formatNumber(amount), contentX, lineY, essenceColors[i]);
                        lineY += 12;
                    }
                }
            }
        }

        lineY = y + padding;
        ctx.text(tr, "Miscellaneous", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        if (memberData.has("profile")) {
            JsonObject profileInfo = memberData.getAsJsonObject("profile");
            if (profileInfo.has("first_join")) {
                long firstJoin = profileInfo.get("first_join").getAsLong();
                String date = new java.text.SimpleDateFormat("MMM d, yyyy").format(new java.util.Date(firstJoin));
                ctx.text(tr, "First Join: " + date, col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }
        }

        if (playerStats != null) {
            int itemsFished = 0;
            if (playerStats.has("items_fished")) {
                JsonObject fished = playerStats.getAsJsonObject("items_fished");
                if (fished.has("total")) {
                    itemsFished = fished.get("total").getAsInt();
                }
            }
            if (itemsFished > 0) {
                ctx.text(tr, "Items Fished: " + formatNumber(itemsFished), col2X, lineY, TEXT_AQUA);
                lineY += 12;
            }

            if (playerStats.has("auctions")) {
                JsonObject auctions = playerStats.getAsJsonObject("auctions");
                // "sold" does not exist, the count sits in total_sold.total
                int sold = auctions.has("total_sold")
                        ? asInt(auctions.getAsJsonObject("total_sold").get("total"))
                        : asInt(auctions.get("completed"));
                long goldEarned = asLong(auctions.get("gold_earned"));
                ctx.text(tr, "Auctions Sold: " + formatNumber(sold), col2X, lineY, TEXT_WHITE);
                lineY += 12;
                ctx.text(tr, "Coins Earned: " + formatCoins(goldEarned), col2X, lineY, TEXT_GOLD);
                lineY += 12;
            }
        }

        lineY += 8;
        ctx.text(tr, "Slayer Bosses Killed", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject slayerBosses = getNestedObject(memberData, "slayer.slayer_bosses");
        if (slayerBosses != null) {
            String[] slayerTypes = {"zombie", "spider", "wolf", "enderman", "blaze", "vampire"};
            String[] slayerNames = {"Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Riftstalker"};
            int[] slayerColors = {0xFF00AA00, 0xFF333333, 0xFF888888, 0xFFAA00AA, 0xFFFF5500, 0xFFFF0000};

            for (int i = 0; i < slayerTypes.length; i++) {
                if (!slayerBosses.has(slayerTypes[i])) continue;
                JsonObject slayer = slayerBosses.getAsJsonObject(slayerTypes[i]);
                int xp = asInt(slayer.get("xp"));
                if (xp <= 0) continue;

                String line = slayerNames[i] + " " + slayerLevel(slayer) + ": " + formatNumber(xp) + " XP";
                ctx.text(tr, line, col2X, lineY, slayerColors[i]);

                if (mouseX >= col2X && mouseX <= col2X + tr.width(line) && mouseY >= lineY - 2 && mouseY <= lineY + 9) {
                    List<String> lines = new ArrayList<>();
                    lines.add(slayerNames[i] + " Slayer");
                    int total = 0;
                    for (int tier = 0; tier < 5; tier++) {
                        int kills = asInt(slayer.get("boss_kills_tier_" + tier));
                        int attempts = asInt(slayer.get("boss_attempts_tier_" + tier));
                        if (kills == 0 && attempts == 0) continue;
                        total += kills;
                        lines.add("Tier " + (tier + 1) + ": " + formatNumber(kills) + " kills"
                                + (attempts > kills ? " (" + formatNumber(attempts) + " started)" : ""));
                    }
                    lines.add("Total: " + formatNumber(total) + " kills");
                    tooltip = lines.toArray(new String[0]);
                    tooltipX = mouseX;
                    tooltipY = mouseY;
                }
                lineY += 12;
            }
        }

        if (tooltip != null) {
            drawTooltip(ctx, tr);
            tooltip = null;
        }
    }

    /** Level is the highest claimed level entry. */
    private static int slayerLevel(JsonObject slayer) {
        if (!slayer.has("claimed_levels")) return 0;
        int level = 0;
        for (String key : slayer.getAsJsonObject("claimed_levels").keySet()) {
            String number = key.replace("level_", "").replace("_special", "");
            try {
                level = Math.max(level, Integer.parseInt(number));
            } catch (NumberFormatException ignored) {
            }
        }
        return level;
    }

    private String[] tooltip;
    private int tooltipX;
    private int tooltipY;

    private void drawTooltip(GuiGraphicsExtractor ctx, Font tr) {
        int w = 0;
        for (String line : tooltip) w = Math.max(w, tr.width(line));
        int h = tooltip.length * 10 + 4;
        int tx = Math.min(tooltipX + 8, com.teslamaps.profileviewer.screen.NeuStyle.PANEL_WIDTH - w - 8);
        int ty = Math.max(2, tooltipY - h);
        com.teslamaps.profileviewer.screen.NeuStyle.window(ctx, tx - 3, ty - 2, w + 8, h);
        for (int i = 0; i < tooltip.length; i++) {
            ctx.text(tr, tooltip[i], tx, ty + 2 + i * 10,
                    i == 0 ? com.teslamaps.profileviewer.screen.NeuStyle.TEXT_YELLOW
                            : com.teslamaps.profileviewer.screen.NeuStyle.TEXT_GRAY);
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
