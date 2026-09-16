package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.api.PvApi;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.PlayerHeads;
import com.teslamaps.profileviewer.screen.PvPlayerRender;
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

    private java.util.List<com.teslamaps.profileviewer.data.InventoryDecoder.Slot> armor = java.util.List.of();

    @Override
    public void init(com.teslamaps.profileviewer.screen.ProfileViewerScreen parent,
                     com.teslamaps.profileviewer.data.SkyblockProfiles profiles) {
        super.init(parent, profiles);
        // decoded once - doing it per frame rebuilt four slot objects a frame and flooded the item cache
        SkyblockProfile profile = getProfile();
        armor = profile == null ? java.util.List.of() : armorSlots(profile);
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

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

        // everything below is NEU's grid, measured from the window origin
        int left = x - 8;
        int top = y - 8;

        if (profile == null) {
            ctx.text(tr, "No profile data", left + 20, top + 20, NeuStyle.TEXT);
            return;
        }

        // left column: name over the head, the player, then status/guild/coins below
        String name = profiles != null ? profiles.getDisplayName() : "";
        net.minecraft.network.chat.Component titled = com.teslamaps.profileviewer.data.LegacyText.parse(
                (profiles != null ? profiles.getRankPrefix() : "") + name);
        ctx.text(tr, titled, left + 63 - tr.width(titled) / 2, top + 6, -1);

        // rank and guild only exist with an own hypixel key; say so instead of silently missing
        String apiKey = com.teslamaps.config.TeslaMapsConfig.get().hypixelApiKey;
        boolean keyMissing = apiKey == null || apiKey.isBlank() || apiKey.length() < 30;
        if (keyMissing && profiles != null && profiles.getRankPrefix().isEmpty()
                && mouseX >= left + 8 && mouseX <= left + 118 && mouseY >= top + 4 && mouseY <= top + 14) {
            tooltip = new String[]{
                    "Rank & guild need a Hypixel API key",
                    "Set one with /tmap apikey <key>",
            };
            tooltipX = mouseX;
            tooltipY = mouseY;
        }

        // the player preview, looking at the cursor like the vanilla inventory does
        NeuStyle.slimBox(ctx, left + 23, top + 16, 81, 108);
        if (profiles != null) {
            PvPlayerRender.setArmor(profiles.getUuid(), name, armor);
            // entity renders live in screen space, not inside our scaled panel
            PvPlayerRender.draw(ctx, profiles.getUuid(), name,
                    screenX(left + 26), screenY(top + 19),
                    screenX(left + 101), screenY(top + 121),
                    screenX(mouseX), screenY(mouseY));
        }

        String status = profiles != null ? profiles.getOnlineStatus() : "";
        drawCentered(ctx, tr, status, left + 63, top + 128,
                status.startsWith("Online") ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_RED);

        String guild = profiles != null ? profiles.getGuildName() : null;
        if (guild != null && !guild.isEmpty()) {
            drawCentered(ctx, tr, guild, left + 63, top + 138, NeuStyle.TEXT_TURQUOISE);
        }

        drawCentered(ctx, tr, "Purse: " + formatCoins(profile.getCoinPurse()), left + 63, top + 152, NeuStyle.TEXT_GOLD);
        drawCentered(ctx, tr, "Bank: " + formatCoins(profile.getBankBalance()), left + 63, top + 162, NeuStyle.TEXT_GOLD);
        if (mouseX >= left + 8 && mouseX <= left + 118 && mouseY >= top + 160 && mouseY <= top + 171) {
            tooltip = new String[]{
                    "Bank",
                    "Coop: " + formatCoins(profile.getCoopBank()),
                    "Personal: " + formatCoins(profile.getPersonalBank()),
            };
            tooltipX = mouseX;
            tooltipY = mouseY;
        }

        if (profiles != null && com.teslamaps.features.capes.CapeSync.isUser(profiles.getUuid())) {
            scaledCentered(ctx, tr, "TeslaMaps user", left + 63, top + 176, 0.75f, NeuStyle.TEXT_AQUA);
        }

        // middle column: skyblock level with its own bar, like NEU's skull block
        int sbX = left + 162;
        int sbY = top + 74;
        double sbLevel = profile.getSkyblockLevel();
        drawCentered(ctx, tr, "SkyBlock Level", sbX + 9, sbY - 24, NeuStyle.TEXT_GRAY);
        drawCentered(ctx, tr, String.valueOf((int) sbLevel), sbX + 9, sbY - 12, sbLevelColor((int) sbLevel));
        double sbProgress = sbLevel - (long) sbLevel;
        ctx.text(tr, (int) (sbProgress * 100) + "/100", sbX - 30, sbY + 20, NeuStyle.TEXT_YELLOW);
        NeuStyle.bar(ctx, sbX - 30, sbY + 30, 80, sbProgress, 0xFF55FFFF);

        double skillAvg = profile.getSkillAverage();
        double overflowAvg = profile.getOverflowSkillAverage();
        ctx.text(tr, "Skill Average", left + 132, sbY + 48, NeuStyle.TEXT_GRAY);
        String avg = String.format("%.2f", skillAvg);
        ctx.text(tr, avg, left + 132, sbY + 58, NeuStyle.TEXT_GREEN);
        if (overflowAvg > skillAvg + 0.005) {
            ctx.text(tr, String.format("(%.2f)", overflowAvg), left + 134 + tr.width(avg), sbY + 58, NeuStyle.TEXT_GOLD);
        }


        // six and six, with catacombs as the twelfth entry so both columns are even
        Map<String, SkillData> skills = profile.getSkills();
        int position = 0;
        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            SkillData skill = entry.getValue();
            int column = position / 6;
            int row = position % 6;
            int sx = left + 237 + 86 * column;
            int sy = top + 24 + 25 * row;
            position++;
            if (position > 12) break;

            boolean overflow = skill.hasOverflow();
            int level = overflow ? skill.getOverflowLevel() : skill.getLevel();

            ctx.item(skillIcon(entry.getKey()), sx, sy - 9);
            String levelStr = String.valueOf(level);
            int levelWidth = tr.width(levelStr);
            scaledText(ctx, tr, skill.getDisplayName(), sx + 17, sy - 3, 80 - 19 - levelWidth - 3, NeuStyle.TEXT);
            ctx.text(tr, levelStr, sx + 80 - levelWidth, sy - 4,
                    overflow ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT);
            NeuStyle.bar(ctx, sx, sy + 8, 80,
                    overflow ? skill.getOverflowProgress() : skill.getProgress());

            if (mouseX > sx && mouseX < sx + 80 && mouseY > sy - 9 && mouseY < sy + 14) {
                tooltip = new String[]{
                        skill.getDisplayName(),
                        "Progress: " + (overflow ? skill.getFormattedOverflowProgress() : skill.getFormattedProgress()),
                        "Total XP: " + skill.getFormattedTotalXp(),
                };
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }

        if (tooltip != null) {
            drawTooltip(ctx, tr);
            tooltip = null;
        }
    }

    /** Armor comes from the profile so the render wears it, like NEU does. */
    private static java.util.List<com.teslamaps.profileviewer.data.InventoryDecoder.Slot> armorSlots(SkyblockProfile profile) {
        com.google.gson.JsonObject inventory = profile.getMemberData().has("inventory")
                ? profile.getMemberData().getAsJsonObject("inventory") : null;
        return com.teslamaps.profileviewer.data.InventoryDecoder.decode(inventory, "inv_armor");
    }

    private String[] tooltip;
    private int tooltipX;
    private int tooltipY;

    private void drawTooltip(GuiGraphicsExtractor ctx, Font tr) {
        int w = 0;
        for (String line : tooltip) w = Math.max(w, tr.width(line));
        int h = tooltip.length * 10 + 4;
        int tx = Math.min(tooltipX + 8, NeuStyle.PANEL_WIDTH - w - 8);
        int ty = Math.max(2, tooltipY - h);
        NeuStyle.window(ctx, tx - 3, ty - 2, w + 8, h);
        for (int i = 0; i < tooltip.length; i++) {
            ctx.text(tr, tooltip[i], tx, ty + 2 + i * 10, i == 0 ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        }
    }

    private void drawCentered(GuiGraphicsExtractor ctx, Font tr, String text, int centerX, int y, int color) {
        ctx.text(tr, text, centerX - tr.width(text) / 2, y, color);
    }

    private void scaledCentered(GuiGraphicsExtractor ctx, Font tr, String text, int centerX, int y,
                                float factor, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(centerX - tr.width(text) * factor / 2, y);
        ctx.pose().scale(factor, factor);
        ctx.text(tr, text, 0, 0, color);
        ctx.pose().popMatrix();
    }

    /** Skill names are longer than the column, so they shrink like NEU's scaled strings do. */
    private void scaledText(GuiGraphicsExtractor ctx, Font tr, String text, int x, int y, int maxWidth, int color) {
        int width = tr.width(text);
        if (width <= maxWidth) {
            ctx.text(tr, text, x, y - 1, color);
            return;
        }
        float factor = Math.max(0.5f, (float) maxWidth / width);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(factor, factor);
        ctx.text(tr, text, 0, 0, color);
        ctx.pose().popMatrix();
    }

    /** NEU puts the label left and the value right inside a fixed width. */
    private void drawAligned(GuiGraphicsExtractor ctx, Font tr, String label, String value, int x, int y, int w, int valueColor) {
        ctx.text(tr, label, x, y, NeuStyle.TEXT);
        ctx.text(tr, value, x + w - tr.width(value), y, valueColor);
    }

    /** Hypixel's level bracket colours, one step every 40 levels. */
    private static int sbLevelColor(int level) {
        int[] colors = {
                0xFFAAAAAA, // 0    gray
                0xFFFFFFFF, // 40   white
                0xFFFFFF55, // 80   yellow
                0xFF55FF55, // 120  green
                0xFF00AA00, // 160  dark green
                0xFF55FFFF, // 200  aqua
                0xFF00AAAA, // 240  dark aqua
                0xFF5555FF, // 280  blue
                0xFFFF55FF, // 320  light purple
                0xFFAA00AA, // 360  dark purple
                0xFFFFAA00, // 400  gold
                0xFFFF5555, // 440  red
                0xFFAA0000, // 480  dark red
        };
        return colors[Math.max(0, Math.min(colors.length - 1, level / 40))];
    }

    private static ItemStack skillIcon(String key) {
        return new ItemStack(switch (key) {
            case "farming" -> Items.GOLDEN_HOE;
            case "mining" -> Items.STONE_PICKAXE;
            case "combat" -> Items.STONE_SWORD;
            case "foraging" -> Items.JUNGLE_SAPLING;
            case "fishing" -> Items.FISHING_ROD;
            case "enchanting" -> Items.ENCHANTING_TABLE;
            case "alchemy" -> Items.BREWING_STAND;
            case "carpentry" -> Items.CRAFTING_TABLE;
            case "runecrafting" -> Items.MAGMA_CREAM;
            case "social" -> Items.EMERALD;
            case "taming" -> Items.WOLF_SPAWN_EGG;
            case "hunting" -> Items.LEAD;
            default -> Items.PAPER;
        });
    }
}
