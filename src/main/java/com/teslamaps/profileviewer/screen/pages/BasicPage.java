package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Map;

/**
 * Basic page showing skills overview and profile stats.
 */
public class BasicPage extends ProfileViewerPage {
    // Colors
    private static final int BAR_BG = 0xFF333333;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int TEXT_AQUA = 0xFF55FFFF;

    // Skill bar colors
    private static final int[] SKILL_COLORS = {
            0xFF55FF55,  // Farming - green
            0xFF808080,  // Mining - gray
            0xFFFF5555,  // Combat - red
            0xFF00AA00,  // Foraging - dark green
            0xFF5555FF,  // Fishing - blue
            0xFFAA00AA,  // Enchanting - purple
            0xFFFFFF55,  // Alchemy - yellow
            0xFF8B4513,  // Carpentry - brown
            0xFFAA00AA,  // Runecrafting - purple
            0xFFFF69B4,  // Social - pink
            0xFFFFAA00   // Taming - gold
    };

    @Override
    public String getTabName() {
        return "Basic";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawTextWithShadow(tr, "No profile data", x + 10, y + 10, TEXT_WHITE);
            return;
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int padding = 15;
        int contentX = x + padding;
        int contentWidth = width - padding * 2;

        // === Left Column: Stats ===
        int leftColumnWidth = 150;
        int statsY = y + padding;

        // Skyblock Level
        double sbLevel = profile.getSkyblockLevel();
        ctx.drawTextWithShadow(tr, "Skyblock Level", contentX, statsY, TEXT_WHITE);
        ctx.drawTextWithShadow(tr, String.format("%.2f", sbLevel), contentX, statsY + 12, TEXT_AQUA);
        statsY += 30;

        // Skill Average
        double skillAvg = profile.getSkillAverage();
        ctx.drawTextWithShadow(tr, "Skill Average", contentX, statsY, TEXT_WHITE);
        ctx.drawTextWithShadow(tr, String.format("%.2f", skillAvg), contentX, statsY + 12, TEXT_GREEN);
        statsY += 30;

        // Coins
        double purse = profile.getCoinPurse();
        double bank = profile.getBankBalance();
        ctx.drawTextWithShadow(tr, "Purse", contentX, statsY, TEXT_WHITE);
        ctx.drawTextWithShadow(tr, formatCoins(purse), contentX, statsY + 12, TEXT_GOLD);
        statsY += 30;

        ctx.drawTextWithShadow(tr, "Bank", contentX, statsY, TEXT_WHITE);
        ctx.drawTextWithShadow(tr, formatCoins(bank), contentX, statsY + 12, TEXT_GOLD);
        statsY += 30;

        // Fairy Souls
        int fairySouls = profile.getFairySouls();
        ctx.drawTextWithShadow(tr, "Fairy Souls", contentX, statsY, TEXT_WHITE);
        ctx.drawTextWithShadow(tr, fairySouls + " / 242", contentX, statsY + 12,
                fairySouls >= 242 ? TEXT_GREEN : TEXT_GRAY);
        statsY += 30;

        // === Right Column: Skills ===
        int skillsX = contentX + leftColumnWidth + 20;
        int skillsWidth = contentWidth - leftColumnWidth - 40;
        int skillY = y + padding;

        ctx.drawTextWithShadow(tr, "Skills", skillsX, skillY, TEXT_WHITE);
        skillY += 16;

        Map<String, SkillData> skills = profile.getSkills();
        int colorIndex = 0;

        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            SkillData skill = entry.getValue();
            int barColor = SKILL_COLORS[colorIndex % SKILL_COLORS.length];
            colorIndex++;

            // Skill name and level
            String skillName = skill.getDisplayName();
            int level = skill.getLevel();
            String levelStr = String.valueOf(level);

            ctx.drawTextWithShadow(tr, skillName, skillsX, skillY, TEXT_WHITE);
            ctx.drawTextWithShadow(tr, levelStr, skillsX + skillsWidth - tr.getWidth(levelStr), skillY, barColor);

            // Progress bar
            int barY = skillY + 11;
            int barHeight = 8;
            int barWidth = skillsWidth;

            drawProgressBar(ctx, skillsX, barY, barWidth, barHeight,
                    skill.getProgress(), BAR_BG, barColor);

            // XP text on hover
            if (mouseX >= skillsX && mouseX < skillsX + barWidth &&
                    mouseY >= skillY && mouseY < barY + barHeight) {
                String xpText = skill.getFormattedProgress();
                int xpX = skillsX + (barWidth - tr.getWidth(xpText)) / 2;
                ctx.drawTextWithShadow(tr, xpText, xpX, barY - 1, TEXT_WHITE);
            }

            skillY += 22;
        }
    }
}
