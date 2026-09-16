package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trophy fishing, laid out like the rest of the viewer: two column table with the real fish items. */
public class TrophyFishPage extends ProfileViewerPage {
    private static final int BRONZE = 0xFFCD7F32;
    private static final int SILVER = 0xFFC0C0C0;
    private static final int GOLD = 0xFFFFD700;
    private static final int DIAMOND = 0xFF55FFFF;

    private record Fish(String key, String id, String display) {}

    private static final List<Fish> FISH = List.of(
            new Fish("sulphur_skitter", "SULPHUR_SKITTER_BRONZE", "Sulphur Skitter"),
            new Fish("obfuscated_fish_1", "OBFUSCATED_FISH_1_BRONZE", "Obfuscated 1"),
            new Fish("steaming_hot_flounder", "STEAMING_HOT_FLOUNDER_BRONZE", "Steaming-Hot Flounder"),
            new Fish("gusher", "GUSHER_BRONZE", "Gusher"),
            new Fish("blobfish", "BLOBFISH_BRONZE", "Blobfish"),
            new Fish("obfuscated_fish_2", "OBFUSCATED_FISH_2_BRONZE", "Obfuscated 2"),
            new Fish("slugfish", "SLUGFISH_BRONZE", "Slugfish"),
            new Fish("flyfish", "FLYFISH_BRONZE", "Flyfish"),
            new Fish("obfuscated_fish_3", "OBFUSCATED_FISH_3_BRONZE", "Obfuscated 3"),
            new Fish("lava_horse", "LAVA_HORSE_BRONZE", "Lava Horse"),
            new Fish("mana_ray", "MANA_RAY_BRONZE", "Mana Ray"),
            new Fish("volcanic_stonefish", "VOLCANIC_STONEFISH_BRONZE", "Volcanic Stonefish"),
            new Fish("vanille", "VANILLE_BRONZE", "Vanille"),
            new Fish("skeleton_fish", "SKELETON_FISH_BRONZE", "Skeleton Fish"),
            new Fish("moldfin", "MOLDFIN_BRONZE", "Moldfin"),
            new Fish("soul_fish", "SOUL_FISH_BRONZE", "Soul Fish"),
            new Fish("karate_fish", "KARATE_FISH_BRONZE", "Karate Fish"),
            new Fish("golden_fish", "GOLDEN_FISH_BRONZE", "Golden Fish"));

    private static final Map<String, ItemStack> icons = new HashMap<>();

    private static ItemStack icon(Fish fish) {
        ItemStack known = icons.get(fish.id());
        if (known != null) return known;
        icons.put(fish.id(), new ItemStack(Items.TROPICAL_FISH));
        SkyblockItems.resolve(fish.id(), stack -> {
            if (!stack.isEmpty()) icons.put(fish.id(), stack);
        });
        return icons.get(fish.id());
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Trophy Fish";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.TROPICAL_FISH);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        Font tr = Minecraft.getInstance().font;
        int left = x - 8;
        int top = y - 8;

        JsonObject trophyFish = profile.getMemberData().has("trophy_fish")
                && profile.getMemberData().get("trophy_fish").isJsonObject()
                ? profile.getMemberData().getAsJsonObject("trophy_fish") : null;

        ctx.text(tr, "Trophy Fish", left + 10, top + 8, NeuStyle.TEXT_GREEN);
        if (trophyFish == null) {
            ctx.text(tr, "No trophy fish data", left + 10, top + 24, NeuStyle.TEXT_GRAY);
            return;
        }

        // header totals: caught overall, unique species, one total per tier
        int totalCaught = asInt(trophyFish.get("total_caught"));
        int unique = 0;
        int[] tierTotals = new int[4];
        String[] suffixes = {"_bronze", "_silver", "_gold", "_diamond"};
        for (Fish fish : FISH) {
            boolean any = false;
            for (int t = 0; t < 4; t++) {
                int count = asInt(trophyFish.get(fish.key() + suffixes[t]));
                tierTotals[t] += count;
                any |= count > 0;
            }
            if (any) unique++;
        }

        String totals = "Total: " + String.format("%,d", totalCaught) + "   Unique: " + unique + "/" + FISH.size();
        ctx.text(tr, totals, left + NeuStyle.PANEL_WIDTH - 10 - tr.width(totals), top + 8, NeuStyle.TEXT);

        int summaryY = top + 21;
        int sx = left + 10;
        sx = summary(ctx, tr, "Bronze", tierTotals[0], sx, summaryY, BRONZE);
        sx = summary(ctx, tr, "Silver", tierTotals[1], sx, summaryY, SILVER);
        sx = summary(ctx, tr, "Gold", tierTotals[2], sx, summaryY, GOLD);
        summary(ctx, tr, "Diamond", tierTotals[3], sx, summaryY, DIAMOND);

        // two column table, every count in its own fixed column so nothing runs together
        int columnWidth = (NeuStyle.PANEL_WIDTH - 20 - 10) / 2;
        int nameWidth = columnWidth - 20 - 4 * 26 - 6;
        int headerY = top + 36;
        int rowsTop = headerY + 12;
        int rowHeight = 16;

        for (int c = 0; c < 2; c++) {
            int cx = left + 10 + c * (columnWidth + 10);
            ctx.text(tr, "Fish", cx + 20, headerY, NeuStyle.TEXT_GRAY);
            String[] heads = {"B", "S", "G", "D"};
            int[] headColors = {BRONZE, SILVER, GOLD, DIAMOND};
            for (int t = 0; t < 4; t++) {
                int rightEdge = cx + columnWidth - (3 - t) * 26;
                right(ctx, tr, heads[t], rightEdge, headerY, headColors[t]);
            }
        }

        int perColumn = (FISH.size() + 1) / 2;
        List<String> hoverLines = null;
        for (int i = 0; i < FISH.size(); i++) {
            Fish fish = FISH.get(i);
            int column = i / perColumn;
            int row = i % perColumn;
            int cx = left + 10 + column * (columnWidth + 10);
            int cy = rowsTop + row * rowHeight;

            boolean hovered = mouseX >= cx - 2 && mouseX < cx + columnWidth + 2
                    && mouseY >= cy - 1 && mouseY < cy + rowHeight - 1;
            if (row % 2 == 0) ctx.fill(cx - 2, cy - 1, cx + columnWidth + 2, cy + rowHeight - 1, 0x18FFFFFF);
            if (hovered) ctx.fill(cx - 2, cy - 1, cx + columnWidth + 2, cy + rowHeight - 1, 0x30FFFFFF);

            int[] counts = new int[4];
            int total = 0;
            for (int t = 0; t < 4; t++) {
                counts[t] = asInt(trophyFish.get(fish.key() + suffixes[t]));
                total += counts[t];
            }

            ctx.item(icon(fish), cx, cy - 1);
            ctx.text(tr, com.teslamaps.profileviewer.ui.Kit.clip(fish.display(), nameWidth), cx + 20, cy + 3,
                    total > 0 ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);

            int[] colors = {BRONZE, SILVER, GOLD, DIAMOND};
            for (int t = 0; t < 4; t++) {
                int rightEdge = cx + columnWidth - (3 - t) * 26;
                right(ctx, tr, String.valueOf(counts[t]), rightEdge, cy + 3,
                        counts[t] > 0 ? colors[t] : NeuStyle.TEXT_DARK);
            }

            if (hovered) {
                hoverLines = List.of(
                        "§b" + fish.display(),
                        "§7Total: §f" + String.format("%,d", total),
                        "§7Bronze: §f" + counts[0],
                        "§7Silver: §f" + counts[1],
                        "§7Gold: §f" + counts[2],
                        "§7Diamond: §f" + counts[3]);
            }
        }

        if (hoverLines != null && parent != null) {
            List<net.minecraft.network.chat.Component> parsed = new ArrayList<>(hoverLines.size());
            for (String line : hoverLines) parsed.add(com.teslamaps.profileviewer.data.LegacyText.parse(line));
            parent.setHoveredLines(parsed);
        }
    }

    private int summary(GuiGraphicsExtractor ctx, Font tr, String label, int value, int x, int y, int color) {
        String text = label + ": " + String.format("%,d", value);
        ctx.text(tr, text, x, y, color);
        return x + tr.width(text) + 14;
    }

    private void right(GuiGraphicsExtractor ctx, Font tr, String text, int rightEdge, int y, int color) {
        ctx.text(tr, text, rightEdge - tr.width(text), y, color);
    }
}
