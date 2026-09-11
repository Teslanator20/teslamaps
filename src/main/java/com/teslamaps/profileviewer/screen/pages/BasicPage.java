package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.api.PvApi;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BasicPage extends ProfileViewerPage {
    private java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> skinLookup;
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
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();

        // NEU draws everything against the window origin, our caller hands us an inset of 8
        int px = x - 8;
        int py = y - 8;

        if (profile == null) {
            ctx.text(tr, "No profile data", px + 20, py + 20, NeuStyle.TEXT);
            return;
        }

        NeuStyle.box(ctx, px + 14, py + 8, 100, 186);
        NeuStyle.box(ctx, px + 120, py + 8, 108, 186);
        NeuStyle.box(ctx, px + 234, py + 8, 183, 186);

        // player column
        String name = profiles != null ? profiles.getDisplayName() : "";
        ctx.text(tr, name, px + 64 - tr.width(name) / 2, py + 20, NeuStyle.TEXT);

        NeuStyle.slimBox(ctx, px + 23, py + 36, 82, 108);
        renderPlayerHead(ctx, px + 48, py + 60);

        String status = profiles != null ? profiles.getOnlineStatus() : "";
        ctx.text(tr, status, px + 64 - tr.width(status) / 2, py + 150,
                status.startsWith("Online") ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_RED);

        String sbLevel = String.format("SB Level %.2f", profile.getSkyblockLevel());
        ctx.text(tr, sbLevel, px + 64 - tr.width(sbLevel) / 2, py + 164, NeuStyle.TEXT_AQUA);

        // stat column
        int statX = px + 130;
        int statY = py + 18;

        double skillAvg = profile.getSkillAverage();
        double overflowAvg = profile.getOverflowSkillAverage();
        statY = stat(ctx, tr, statX, statY, "Skill Average", String.format("%.2f", skillAvg), NeuStyle.TEXT_GREEN,
                overflowAvg > skillAvg + 0.005 ? String.format("%.2f overflow", overflowAvg) : null);
        statY = stat(ctx, tr, statX, statY, "Total Skill XP", formatXp(profile.getTotalSkillXp()), NeuStyle.TEXT_AQUA, null);
        statY = stat(ctx, tr, statX, statY, "Purse", formatCoins(profile.getCoinPurse()), NeuStyle.TEXT_GOLD, null);
        statY = stat(ctx, tr, statX, statY, "Bank", formatCoins(profile.getBankBalance()), NeuStyle.TEXT_GOLD, null);

        int fairySouls = profile.getFairySouls();
        stat(ctx, tr, statX, statY, "Fairy Souls", fairySouls + " / 242",
                fairySouls >= 242 ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_GRAY, null);

        // skills
        int skillsX = px + 244;
        int skillsWidth = 163;
        int skillY = py + 18;

        Map<String, SkillData> skills = profile.getSkills();
        int colorIndex = 0;

        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            SkillData skill = entry.getValue();
            int barColor = SKILL_COLORS[colorIndex % SKILL_COLORS.length];
            colorIndex++;

            boolean overflow = skill.hasOverflow();
            String levelStr = String.valueOf(overflow ? skill.getOverflowLevel() : skill.getLevel());

            ctx.text(tr, skill.getDisplayName(), skillsX, skillY, NeuStyle.TEXT);
            ctx.text(tr, levelStr, skillsX + skillsWidth - tr.width(levelStr), skillY,
                    overflow ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_GRAY);

            int barY = skillY + 10;
            NeuStyle.bar(ctx, skillsX, barY, skillsWidth,
                    overflow ? skill.getOverflowProgress() : skill.getProgress(), barColor);

            if (mouseX >= skillsX && mouseX < skillsX + skillsWidth && mouseY >= skillY && mouseY < barY + 5) {
                String xpText = overflow ? skill.getFormattedOverflowProgress() : skill.getFormattedProgress();
                ctx.text(tr, xpText, skillsX + (skillsWidth - tr.width(xpText)) / 2, barY - 1, NeuStyle.TEXT);
            }

            skillY += 15;
        }
    }

    private int stat(GuiGraphicsExtractor ctx, Font tr, int x, int y, String label, String value, int color, String extra) {
        ctx.text(tr, label, x, y, NeuStyle.TEXT_GRAY);
        ctx.text(tr, value, x, y + 10, color);
        if (extra != null) {
            ctx.text(tr, extra, x + tr.width(value) + 4, y + 10, NeuStyle.TEXT_DARK);
        }
        return y + 26;
    }

    private void renderPlayerHead(GuiGraphicsExtractor ctx, int x, int y) {
        if (profiles == null) return;
        if (skinLookup == null) {
            skinLookup = Minecraft.getInstance().getSkinManager().createLookup(
                    new GameProfile(UUID.fromString(PvApi.dashed(profiles.getUuid())), profiles.getDisplayName()), false);
        }
        Identifier skin = skinLookup.get().body().texturePath();
        // face plus the hat layer, blown up like the player render NEU puts there
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8, 8, 8, 8, 32, 32, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40, 8, 8, 8, 32, 32, 64, 64);
    }
}
