/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BasicPage extends ProfileViewerPage {
    private static final int BAR_BG = 0xFF333333;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int TEXT_AQUA = 0xFF55FFFF;

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
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) {
            Font tr = Minecraft.getInstance().font;
            ctx.text(tr, "No profile data", x + 10, y + 10, TEXT_WHITE);
            return;
        }

        Font tr = Minecraft.getInstance().font;
        int padding = 15;
        int contentX = x + padding;
        int contentWidth = width - padding * 2;

        int leftColumnWidth = 150;
        int statsY = y + padding;

        double sbLevel = profile.getSkyblockLevel();
        ctx.text(tr, "Skyblock Level", contentX, statsY, TEXT_WHITE);
        ctx.text(tr, String.format("%.2f", sbLevel), contentX, statsY + 12, TEXT_AQUA);
        statsY += 30;

        double skillAvg = profile.getSkillAverage();
        ctx.text(tr, "Skill Average", contentX, statsY, TEXT_WHITE);
        ctx.text(tr, String.format("%.2f", skillAvg), contentX, statsY + 12, TEXT_GREEN);
        statsY += 30;

        double purse = profile.getCoinPurse();
        double bank = profile.getBankBalance();
        ctx.text(tr, "Purse", contentX, statsY, TEXT_WHITE);
        ctx.text(tr, formatCoins(purse), contentX, statsY + 12, TEXT_GOLD);
        statsY += 30;

        ctx.text(tr, "Bank", contentX, statsY, TEXT_WHITE);
        ctx.text(tr, formatCoins(bank), contentX, statsY + 12, TEXT_GOLD);
        statsY += 30;

        int fairySouls = profile.getFairySouls();
        ctx.text(tr, "Fairy Souls", contentX, statsY, TEXT_WHITE);
        ctx.text(tr, fairySouls + " / 242", contentX, statsY + 12,
                fairySouls >= 242 ? TEXT_GREEN : TEXT_GRAY);
        statsY += 30;

        int skillsX = contentX + leftColumnWidth + 20;
        int skillsWidth = contentWidth - leftColumnWidth - 40;
        int skillY = y + padding;

        ctx.text(tr, "Skills", skillsX, skillY, TEXT_WHITE);
        skillY += 16;

        Map<String, SkillData> skills = profile.getSkills();
        int colorIndex = 0;

        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            SkillData skill = entry.getValue();
            int barColor = SKILL_COLORS[colorIndex % SKILL_COLORS.length];
            colorIndex++;

            String skillName = skill.getDisplayName();
            int level = skill.getLevel();
            String levelStr = String.valueOf(level);

            ctx.text(tr, skillName, skillsX, skillY, TEXT_WHITE);
            ctx.text(tr, levelStr, skillsX + skillsWidth - tr.width(levelStr), skillY, barColor);

            int barY = skillY + 11;
            int barHeight = 8;
            int barWidth = skillsWidth;

            drawProgressBar(ctx, skillsX, barY, barWidth, barHeight,
                    skill.getProgress(), BAR_BG, barColor);

            if (mouseX >= skillsX && mouseX < skillsX + barWidth &&
                    mouseY >= skillY && mouseY < barY + barHeight) {
                String xpText = skill.getFormattedProgress();
                int xpX = skillsX + (barWidth - tr.width(xpText)) / 2;
                ctx.text(tr, xpText, xpX, barY - 1, TEXT_WHITE);
            }

            skillY += 22;
        }
    }
}
