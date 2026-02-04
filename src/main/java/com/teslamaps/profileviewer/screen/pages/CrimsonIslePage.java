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
 * Crimson Isle page showing Kuudra, faction rep, and dojo stats.
 */
public class CrimsonIslePage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_RED = 0xFFFF5555;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int MAGE_COLOR = 0xFFAA00AA;
    private static final int BARBARIAN_COLOR = 0xFFFF5500;
    private static final int BAR_BG = 0xFF333333;

    @Override
    public String getTabName() {
        return "Crimson";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.MAGMA_CREAM);
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

        // === Faction Reputation ===
        ctx.drawTextWithShadow(tr, "Faction Reputation", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject nether = getNestedObject(memberData, "nether_island_player_data");
        if (nether == null) {
            ctx.drawTextWithShadow(tr, "No Crimson Isle data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        // Selected faction
        String selectedFaction = nether.has("selected_faction") ?
                nether.get("selected_faction").getAsString() : "None";
        int factionColor = selectedFaction.equals("mages") ? MAGE_COLOR : BARBARIAN_COLOR;
        ctx.drawTextWithShadow(tr, "Faction: " + capitalize(selectedFaction), contentX, lineY, factionColor);
        lineY += 14;

        // Mages reputation
        int magesRep = nether.has("mages_reputation") ? nether.get("mages_reputation").getAsInt() : 0;
        ctx.drawTextWithShadow(tr, "Mages Rep: " + formatNumber(magesRep), contentX, lineY, MAGE_COLOR);
        lineY += 12;

        // Barbarians reputation
        int barbRep = nether.has("barbarians_reputation") ? nether.get("barbarians_reputation").getAsInt() : 0;
        ctx.drawTextWithShadow(tr, "Barbarian Rep: " + formatNumber(barbRep), contentX, lineY, BARBARIAN_COLOR);
        lineY += 20;

        // === Kuudra ===
        ctx.drawTextWithShadow(tr, "Kuudra Completions", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject kuudra = getNestedObject(nether, "kuudra_completed_tiers");
        if (kuudra != null) {
            String[] tiers = {"none", "hot", "burning", "fiery", "infernal"};
            String[] tierNames = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
            int[] tierColors = {TEXT_GRAY, TEXT_GOLD, 0xFFFF8800, TEXT_RED, 0xFFAA0000};

            for (int i = 0; i < tiers.length; i++) {
                int completions = kuudra.has(tiers[i]) ? kuudra.get(tiers[i]).getAsInt() : 0;
                ctx.drawTextWithShadow(tr, tierNames[i] + ": " + completions, contentX, lineY,
                        completions > 0 ? tierColors[i] : TEXT_GRAY);
                lineY += 12;
            }
        }

        // === Dojo (right column) ===
        lineY = y + padding;
        ctx.drawTextWithShadow(tr, "Dojo", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject dojo = getNestedObject(nether, "dojo");
        if (dojo != null) {
            String[] dojoTests = {
                    "dojo_points_mob_kb", "dojo_points_wall_jump", "dojo_points_archer",
                    "dojo_points_sword_swap", "dojo_points_snake", "dojo_points_lock_head", "dojo_points_fireball"
            };
            String[] testNames = {"Force", "Stamina", "Mastery", "Discipline", "Swiftness", "Control", "Tenacity"};

            for (int i = 0; i < dojoTests.length; i++) {
                int points = dojo.has(dojoTests[i]) ? dojo.get(dojoTests[i]).getAsInt() : 0;
                String rank = getDojoRank(points);
                int rankColor = getDojoRankColor(rank);

                ctx.drawTextWithShadow(tr, testNames[i] + ": " + points + " (" + rank + ")",
                        col2X, lineY, rankColor);
                lineY += 12;
            }
        }

        // Dojo belt
        if (nether.has("dojo")) {
            JsonObject dojoData = nether.getAsJsonObject("dojo");
            // Calculate total points and belt
            int totalPoints = 0;
            for (var entry : dojoData.entrySet()) {
                if (entry.getKey().startsWith("dojo_points_") && entry.getValue().isJsonPrimitive()) {
                    totalPoints += entry.getValue().getAsInt();
                }
            }
            String belt = getDojoBelt(totalPoints);
            lineY += 8;
            ctx.drawTextWithShadow(tr, "Belt: " + belt + " (" + totalPoints + " pts)", col2X, lineY, TEXT_GOLD);
        }
    }

    private String getDojoRank(int points) {
        if (points >= 1000) return "S";
        if (points >= 800) return "A";
        if (points >= 600) return "B";
        if (points >= 400) return "C";
        if (points >= 200) return "D";
        if (points > 0) return "F";
        return "-";
    }

    private int getDojoRankColor(String rank) {
        return switch (rank) {
            case "S" -> TEXT_GOLD;
            case "A" -> TEXT_GREEN;
            case "B" -> 0xFF55FFFF;
            case "C" -> TEXT_WHITE;
            case "D" -> TEXT_GRAY;
            default -> TEXT_GRAY;
        };
    }

    private String getDojoBelt(int totalPoints) {
        if (totalPoints >= 7000) return "Black";
        if (totalPoints >= 6000) return "Brown";
        if (totalPoints >= 5000) return "Blue";
        if (totalPoints >= 4000) return "Green";
        if (totalPoints >= 3000) return "Yellow";
        if (totalPoints >= 2000) return "White";
        return "None";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
