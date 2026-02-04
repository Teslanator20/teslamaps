package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

/**
 * Collections page showing collection progress.
 */
public class CollectionsPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int BAR_BG = 0xFF333333;

    // Collection categories
    private static final String[] CATEGORIES = {"Farming", "Mining", "Combat", "Foraging", "Fishing", "Boss"};
    private int selectedCategory = 0;
    private int scrollOffset = 0;

    // Collection items per category (simplified)
    private static final Map<String, String[]> COLLECTION_ITEMS = new LinkedHashMap<>();
    static {
        COLLECTION_ITEMS.put("Farming", new String[]{
            "WHEAT", "CARROT_ITEM", "POTATO_ITEM", "PUMPKIN", "MELON", "SEEDS",
            "MUSHROOM_COLLECTION", "COCOA", "CACTUS", "SUGAR_CANE", "FEATHER",
            "LEATHER", "PORK", "RAW_CHICKEN", "MUTTON", "RABBIT", "NETHER_STALK"
        });
        COLLECTION_ITEMS.put("Mining", new String[]{
            "COBBLESTONE", "COAL", "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "LAPIS_LAZULI",
            "EMERALD", "REDSTONE", "QUARTZ", "OBSIDIAN", "GLOWSTONE_DUST", "GRAVEL",
            "ICE", "NETHERRACK", "SAND", "ENDER_STONE", "MITHRIL_ORE", "HARD_STONE",
            "GEMSTONE_COLLECTION", "SULPHUR_ORE", "MYCEL"
        });
        COLLECTION_ITEMS.put("Combat", new String[]{
            "ROTTEN_FLESH", "BONE", "STRING", "SPIDER_EYE", "GUNPOWDER", "ENDER_PEARL",
            "GHAST_TEAR", "SLIME_BALL", "BLAZE_ROD", "MAGMA_CREAM"
        });
        COLLECTION_ITEMS.put("Foraging", new String[]{
            "LOG", "LOG:1", "LOG:2", "LOG_2:1", "LOG_2", "LOG:3"
        });
        COLLECTION_ITEMS.put("Fishing", new String[]{
            "RAW_FISH", "RAW_FISH:1", "RAW_FISH:2", "RAW_FISH:3", "PRISMARINE_SHARD",
            "PRISMARINE_CRYSTALS", "CLAY_BALL", "LILY_PAD", "INK_SACK", "SPONGE"
        });
        COLLECTION_ITEMS.put("Boss", new String[]{
            "REVENANT_FLESH", "TARANTULA_WEB", "WOLF_TOOTH", "NULL_SPHERE"
        });
    }

    @Override
    public String getTabName() {
        return "Collections";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.PAINTING);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int padding = 15;
        int contentX = x + padding;

        // Category tabs
        int tabY = y + padding;
        int tabX = contentX;
        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            int tabW = tr.getWidth(cat) + 16;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW &&
                    mouseY >= tabY && mouseY < tabY + 18;
            boolean selected = i == selectedCategory;

            ctx.fill(tabX, tabY, tabX + tabW, tabY + 18,
                    selected ? 0xFF444444 : (hovered ? 0xFF3A3A3A : 0xFF333333));
            if (selected) {
                ctx.fill(tabX, tabY + 16, tabX + tabW, tabY + 18, TEXT_GREEN);
            }
            ctx.drawTextWithShadow(tr, cat, tabX + 8, tabY + 5, selected ? TEXT_WHITE : TEXT_GRAY);
            tabX += tabW + 4;
        }

        // Collections list
        int listY = tabY + 28;
        int listHeight = height - (listY - y) - padding;
        Map<String, Long> collections = profile.getCollections();
        String[] items = COLLECTION_ITEMS.get(CATEGORIES[selectedCategory]);

        if (items == null) return;

        int itemY = listY - scrollOffset;
        for (String item : items) {
            if (itemY > listY - 30 && itemY < listY + listHeight) {
                long amount = collections.getOrDefault(item, 0L);
                String displayName = formatCollectionName(item);

                // Name
                ctx.drawTextWithShadow(tr, displayName, contentX, itemY, amount > 0 ? TEXT_WHITE : TEXT_GRAY);

                // Amount
                String amountStr = formatNumber(amount);
                ctx.drawTextWithShadow(tr, amountStr, contentX + 150, itemY, amount > 0 ? TEXT_GREEN : TEXT_GRAY);

                // Progress bar (simplified - max tier at different amounts)
                int barX = contentX + 220;
                int barW = 100;
                double progress = Math.min(1.0, amount / 100000.0); // Simplified
                drawProgressBar(ctx, barX, itemY + 2, barW, 8, progress, BAR_BG, TEXT_GREEN);
            }
            itemY += 18;
        }
    }

    private String formatCollectionName(String id) {
        // Convert API names to display names
        String name = id.replace("_ITEM", "").replace("_COLLECTION", "")
                .replace("_", " ").replace(":", " ");
        // Title case
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int padding = 15;
        int tabY = 74 + padding; // Approximate
        int tabX = padding;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        for (int i = 0; i < CATEGORIES.length; i++) {
            int tabW = tr.getWidth(CATEGORIES[i]) + 16;
            if (mouseX >= tabX && mouseX < tabX + tabW &&
                    mouseY >= tabY && mouseY < tabY + 18) {
                selectedCategory = i;
                scrollOffset = 0;
                return true;
            }
            tabX += tabW + 4;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        String[] items = COLLECTION_ITEMS.get(CATEGORIES[selectedCategory]);
        if (items == null) return false;

        int maxScroll = Math.max(0, items.length * 18 - 200);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - amount * 15));
        return true;
    }
}
