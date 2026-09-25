package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.api.PvApi;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.PlayerHeads;
import com.teslamaps.profileviewer.screen.PvPanorama;
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
        board = profile == null ? java.util.List.of() : buildBoard(profile);
        hypixelExp = Double.NaN;
        if (profile != null && profiles != null && com.teslamaps.profileviewer.PvExtras.ranks()) {
            com.teslamaps.profileviewer.data.EliteRanks.request(profiles.getUuid(), profile.getProfileId());
        }
    }

    private record BoardRow(String key, String value, int color) {}

    private static final int FAIRY_SOUL_MAX = 248;
    private java.util.List<BoardRow> board = java.util.List.of();
    private double hypixelExp = Double.NaN;
    private String hypixelLevel = "?";
    private String islandMode;
    private String islandStatus;

    // Ported from skyblock-pv's GeneralPage infoPanel
    private static java.util.List<BoardRow> buildBoard(SkyblockProfile profile) {
        com.google.gson.JsonObject full = profile.getFullProfileJson();
        com.google.gson.JsonObject member = profile.getMemberData();
        String mode = full != null && full.has("game_mode") && full.get("game_mode").isJsonPrimitive()
                ? full.get("game_mode").getAsString() : "";
        String modeName = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "ironman" -> "Ironman";
            case "island" -> "Stranded";
            case "bingo" -> "Bingo";
            case "" -> "Classic";
            default -> com.teslamaps.profileviewer.ui.Kit.prettify(mode);
        };
        com.google.gson.JsonObject info = member.has("profile") && member.get("profile").isJsonObject()
                ? member.getAsJsonObject("profile") : null;
        long first = info == null ? 0 : asLong(info.get("first_join"));
        boolean cookie = info != null && info.has("cookie_buff_active") && info.get("cookie_buff_active").isJsonPrimitive()
                && info.get("cookie_buff_active").getAsBoolean();
        com.google.gson.JsonObject stats = member.has("player_stats") && member.get("player_stats").isJsonObject()
                ? member.getAsJsonObject("player_stats") : null;
        long crit = stats == null ? 0 : (long) asDouble(stats.get("highest_critical_damage"));
        int souls = profile.getFairySouls();

        java.util.List<BoardRow> rows = new java.util.ArrayList<>();
        rows.add(new BoardRow("Mode", modeName, NeuStyle.TEXT));
        rows.add(new BoardRow("Hypixel", null, NeuStyle.TEXT_AQUA));
        rows.add(new BoardRow("Fairy Souls", souls > FAIRY_SOUL_MAX ? String.valueOf(souls) : souls + "/" + FAIRY_SOUL_MAX,
                souls >= FAIRY_SOUL_MAX ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_LIGHT_PURPLE));
        rows.add(new BoardRow("Created", first > 0
                ? new java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.ENGLISH).format(new java.util.Date(first)) : "-",
                NeuStyle.TEXT_GRAY));
        rows.add(new BoardRow("Cookie", cookie ? "Active" : "Inactive", cookie ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_DARK));
        rows.add(new BoardRow("Crit Record", crit > 0 ? com.teslamaps.profileviewer.ui.Kit.compact(crit) : "-", NeuStyle.TEXT_RED));
        return rows;
    }

    private static double asDouble(com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0;
        return element.getAsDouble();
    }

    private void drawBoard(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w) {
        double exp = profiles != null ? profiles.getNetworkExp() : -1;
        if (Double.compare(exp, hypixelExp) != 0) {
            hypixelExp = exp;
            hypixelLevel = exp < 0 ? "?" : String.valueOf((int) (-3.5 + Math.sqrt(12.25 + 0.0008 * exp)));
        }
        for (int i = 0; i < board.size(); i++) {
            BoardRow row = board.get(i);
            drawAligned(ctx, tr, row.key(), row.value() == null ? hypixelLevel : row.value(), x, y + i * 9, w, row.color());
        }
    }

    private record Link(int x, int y, int w, int h, Class<? extends com.teslamaps.profileviewer.screen.ProfileViewerPage> page) {}

    private final java.util.List<Link> links = new java.util.ArrayList<>();

    /** Detail page a skill cell jumps to, null when there is none. */
    private static Class<? extends com.teslamaps.profileviewer.screen.ProfileViewerPage> skillPage(String key) {
        return switch (key) {
            case "farming" -> GardenPage.class;
            case "mining" -> MiningPage.class;
            case "combat" -> BestiaryPage.class;
            case "foraging" -> ForagingPage.class;
            case "fishing" -> TrophyFishPage.class;
            case "taming" -> PetsPage.class;
            default -> null;
        };
    }

    private void link(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY,
                      Class<? extends com.teslamaps.profileviewer.screen.ProfileViewerPage> page) {
        if (page == null || !com.teslamaps.profileviewer.PvExtras.deepLinks()) return;
        links.add(new Link(x, y, w, h, page));
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            ctx.fill(x, y, x + w, y + h, 0x20FFFFFF);
        }
    }

    private static String[] withExtra(String[] lines, String... extra) {
        java.util.List<String> out = new java.util.ArrayList<>(java.util.List.of(lines));
        for (String line : extra) if (line != null) out.add(line);
        return out.toArray(new String[0]);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || parent == null) return false;
        for (Link link : links) {
            if (mouseX >= link.x() && mouseX < link.x() + link.w() && mouseY >= link.y() && mouseY < link.y() + link.h()) {
                return parent.openPage(link.page());
            }
        }
        return false;
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

        links.clear();
        if (profile == null) {
            ctx.text(tr, "No profile data", left + 20, top + 20, NeuStyle.TEXT);
            return;
        }

        // left column: the player in a box with the name as its nametag, status/guild/coins below
        String name = profiles != null ? profiles.getDisplayName() : "";
        net.minecraft.network.chat.Component titled = com.teslamaps.profileviewer.data.LegacyText.parse(
                (profiles != null ? profiles.getRankPrefix() : "") + name);

        NeuStyle.slimBox(ctx, left + 23, top + 8, 81, 112);
        if (extra(com.teslamaps.config.TeslaMapsConfig.get().pvPanorama) && profiles != null) {
            PvPanorama.draw(ctx, PvPanorama.fileFor(profiles.getStatusMode()), left + 24, top + 9, 79, 110, mouseX);
        }

        // vanilla style nametag floating over the head: translucent plate, name centered
        int tagWidth = tr.width(titled);
        float tagScale = Math.min(1f, 71f / Math.max(1, tagWidth));
        int shown = Math.round(tagWidth * tagScale);
        int plateLeft = left + 63 - shown / 2 - 2;
        int plateRight = left + 63 + shown / 2 + 2;
        ctx.fill(plateLeft, top + 13, plateRight, top + 24, 0x45000000);
        ctx.pose().pushMatrix();
        ctx.pose().translate(left + 63 - shown / 2f, top + 15 + (1 - tagScale) * 3);
        ctx.pose().scale(tagScale, tagScale);
        ctx.text(tr, titled, 0, 0, -1);
        ctx.pose().popMatrix();

        // the player under his nametag, looking at the cursor like the vanilla inventory does
        if (profiles != null) {
            PvPlayerRender.setArmor(profiles.getUuid(), name, armor);
            // entity renders live in screen space, not inside our scaled panel
            PvPlayerRender.draw(ctx, profiles.getUuid(), name,
                    screenX(left + 26), screenY(top + 26),
                    screenX(left + 101), screenY(top + 117),
                    screenX(mouseX), screenY(mouseY));
        }

        String status = profiles != null ? profiles.getOnlineStatus() : "";
        boolean boardOn = extra(com.teslamaps.config.TeslaMapsConfig.get().pvInfoBoard);
        if (boardOn && profiles != null && profiles.getStatusMode() != null) {
            String mode = profiles.getStatusMode();
            if (!mode.equals(islandMode)) {
                islandMode = mode;
                islandStatus = "Online - " + SkyblockProfiles.islandName(mode);
            }
            status = islandStatus;
        }
        drawCentered(ctx, tr, status, left + 63, top + 126,
                status.startsWith("Online") ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_RED);

        String guild = profiles != null ? profiles.getGuildName() : null;
        if (guild != null && !guild.isEmpty()) {
            drawCentered(ctx, tr, guild, left + 63, top + 137, NeuStyle.TEXT_TURQUOISE);
        }

        drawCentered(ctx, tr, "Purse: " + formatCoins(profile.getCoinPurse()), left + 63, top + 152, NeuStyle.TEXT_GOLD);
        if (profile.hasBankData()) {
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
        } else {
            // hypixel only ships banking when the profile has the bank api enabled
            drawCentered(ctx, tr, "Bank: API off", left + 63, top + 162, NeuStyle.TEXT_DARK);
        }

        if (profiles != null && com.teslamaps.features.capes.CapeSync.isUser(profiles.getUuid())) {
            // same gradient the [TeslaMaps] chat prefix wears; the fade shader is calibrated to
            // the chat position, so here the gradient is baked per character instead
            net.minecraft.network.chat.Component tag = fadeTag();
            float factor = 0.75f;
            ctx.pose().pushMatrix();
            ctx.pose().translate(left + 63 - tr.width(tag) * factor / 2, top + 176);
            ctx.pose().scale(factor, factor);
            ctx.text(tr, tag, 0, 0, -1);
            ctx.pose().popMatrix();
        }

        // middle column: skyblock level with its own bar, like NEU's skull block
        int sbX = left + 162;
        int sbY = top + 74;
        double sbLevel = profile.getSkyblockLevel();
        drawCentered(ctx, tr, "SkyBlock Level", sbX + 9, sbY - 24, NeuStyle.TEXT_GRAY);
        // the level with its progress as decimals (613.85) instead of a bar plus 85/100
        drawCentered(ctx, tr, String.format(java.util.Locale.ROOT, "%.2f", sbLevel),
                sbX + 9, sbY - 12, sbLevelColor((int) sbLevel));
        boolean levelLink = com.teslamaps.profileviewer.PvExtras.levelPage();
        link(ctx, sbX - 36, sbY - 27, 90, 26, mouseX, mouseY, levelLink ? LevelPage.class : null);
        String sbRank = com.teslamaps.profileviewer.PvExtras.rankLine("skyblockxp");
        boolean sbClick = levelLink && com.teslamaps.profileviewer.PvExtras.deepLinks();
        if ((sbRank != null || sbClick) && mouseX >= sbX - 36 && mouseX < sbX + 54 && mouseY >= sbY - 27 && mouseY < sbY - 1) {
            tooltip = withExtra(new String[]{"SkyBlock Level"}, sbRank, sbClick ? "§eClick to open the level page" : null);
            tooltipX = mouseX;
            tooltipY = mouseY;
        }

        double skillAvg = profile.getSkillAverage();
        double overflowAvg = profile.getOverflowSkillAverage();
        ctx.text(tr, "Skill Average", left + 132, sbY + 48, NeuStyle.TEXT_GRAY);
        String avg = String.format("%.2f", skillAvg);
        ctx.text(tr, avg, left + 132, sbY + 58, NeuStyle.TEXT_GREEN);
        if (overflowAvg > skillAvg + 0.005) {
            ctx.text(tr, String.format("(%.2f)", overflowAvg), left + 134 + tr.width(avg), sbY + 58, NeuStyle.TEXT_GOLD);
        }

        // catacombs as a jump into the dungeon page
        if (com.teslamaps.profileviewer.PvExtras.deepLinks() && profile.getDungeonData() != null && !boardOn) {
            int cataY = sbY + 74;
            ctx.text(tr, "Catacombs", left + 132, cataY, NeuStyle.TEXT_GRAY);
            ctx.text(tr, String.valueOf(profile.getDungeonData().getCatacombsLevel()), left + 132, cataY + 10, NeuStyle.TEXT_RED);
            link(ctx, left + 129, cataY - 2, 70, 22, mouseX, mouseY, DungeonPage.class);
            if (mouseX >= left + 129 && mouseX < left + 199 && mouseY >= cataY - 2 && mouseY < cataY + 20) {
                tooltip = withExtra(new String[]{"Catacombs " + profile.getDungeonData().getCatacombsLevel()},
                        com.teslamaps.profileviewer.PvExtras.rankLine("catacombs"), "§eClick to open Dungeons");
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }


        if (boardOn) drawBoard(ctx, tr, left + 128, top + 144, 100);

        // six and six, with catacombs as the twelfth entry so both columns are even
        Map<String, SkillData> skills = profile.getSkills();
        int position = 0;
        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            SkillData skill = entry.getValue();
            int column = position / 6;
            int row = position % 6;
            // starts a touch lower so the column sits centered between the window edges
            int sx = left + 237 + 86 * column;
            int sy = top + 36 + 25 * row;
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

            Class<? extends com.teslamaps.profileviewer.screen.ProfileViewerPage> target = skillPage(entry.getKey());
            link(ctx, sx - 1, sy - 10, 82, 24, mouseX, mouseY, target);
            if (mouseX > sx && mouseX < sx + 80 && mouseY > sy - 9 && mouseY < sy + 14) {
                tooltip = withExtra(new String[]{
                        skill.getDisplayName(),
                        "Progress: " + (overflow ? skill.getFormattedOverflowProgress() : skill.getFormattedProgress()),
                        "Total XP: " + skill.getFormattedTotalXp(),
                }, com.teslamaps.profileviewer.PvExtras.rankLine(entry.getKey()),
                        target != null && com.teslamaps.profileviewer.PvExtras.deepLinks()
                                ? "§eClick to open " + target.getSimpleName().replace("Page", "") : null);
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
        for (String line : tooltip) w = Math.max(w, tr.width(com.teslamaps.profileviewer.data.LegacyText.parse(line)));
        int h = tooltip.length * 10 + 4;
        int tx = Math.min(tooltipX + 8, NeuStyle.PANEL_WIDTH - w - 8);
        int ty = Math.max(2, tooltipY - h);
        NeuStyle.window(ctx, tx - 3, ty - 2, w + 8, h);
        for (int i = 0; i < tooltip.length; i++) {
            ctx.text(tr, com.teslamaps.profileviewer.data.LegacyText.parse(tooltip[i]), tx, ty + 2 + i * 10,
                    i == 0 ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        }
    }

    private void drawCentered(GuiGraphicsExtractor ctx, Font tr, String text, int centerX, int y, int color) {
        ctx.text(tr, text, centerX - tr.width(text) / 2, y, color);
    }

    private static net.minecraft.network.chat.Component fadeTag;
    private static String fadeTagKey;

    /** "TeslaMaps user" with the prefix fade baked in per character. */
    private static net.minecraft.network.chat.Component fadeTag() {
        var config = com.teslamaps.config.TeslaMapsConfig.get();
        String key = config.prefixFadeColorA + "|" + config.prefixFadeColorB;
        if (fadeTag != null && key.equals(fadeTagKey)) return fadeTag;

        int from = com.teslamaps.config.TeslaMapsConfig.parseColor(config.prefixFadeColorA) & 0xFFFFFF;
        int to = com.teslamaps.config.TeslaMapsConfig.parseColor(config.prefixFadeColorB) & 0xFFFFFF;
        String text = "TeslaMaps user";
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        for (int i = 0; i < text.length(); i++) {
            float t = i / (float) (text.length() - 1);
            int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
            int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
            int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
            int rgb = (r << 16) | (g << 8) | b;
            out.append(net.minecraft.network.chat.Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb))));
        }
        fadeTag = out;
        fadeTagKey = key;
        return out;
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
