package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CollectionsPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int BAR_BG = 0xFF333333;

    private static final String[] CATEGORIES = {"Farming", "Mining", "Combat", "Foraging", "Fishing", "Boss"};
    private int selectedCategory = 0;
    private int scrollOffset = 0;

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
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        Font tr = Minecraft.getInstance().font;
        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        // category rail on the left, item grid on the right - same shape as the game menu
        for (int i = 0; i < CATEGORIES.length; i++) {
            int bx = left + 6;
            int by = top + 10 + i * 20;
            boolean selected = i == selectedCategory;
            boolean hovered = mouseX >= bx && mouseX < bx + 86 && mouseY >= by && mouseY < by + 18;

            if (selected) NeuStyle.slimBox(ctx, bx, by, 86, 18);
            ctx.text(tr, CATEGORIES[i], bx + 6, by + 5,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
        }

        Map<String, Long> collections = profile.getCollections();
        String[] items = COLLECTION_ITEMS.get(CATEGORIES[selectedCategory]);
        if (items == null) return;

        int gridX = left + 104;
        int gridY = top + 14;
        int columns = 8;

        ItemStack hovered = ItemStack.EMPTY;

        for (int i = 0; i < items.length; i++) {
            int sx = gridX + (i % columns) * ItemGrid.SLOT;
            int sy = gridY + (i / columns) * ItemGrid.SLOT;

            ctx.fill(sx, sy, sx + 17, sy + 17, 0x60000000);
            ctx.fill(sx, sy, sx + 17, sy + 1, 0x40FFFFFF);
            ctx.fill(sx, sy, sx + 1, sy + 17, 0x40FFFFFF);

            String id = items[i];
            long amount = collections.getOrDefault(id, 0L);
            ItemStack stack = stackFor(id, amount);
            if (!stack.isEmpty()) ctx.item(stack, sx + 1, sy + 1);

            if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                ctx.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0x40FFFFFF);
                hovered = stack;
            }
        }

        int total = 0;
        for (String id : items) if (collections.getOrDefault(id, 0L) > 0) total++;
        String summary = total + " / " + items.length + " started";
        ctx.text(tr, summary, gridX, top + NeuStyle.PANEL_HEIGHT - 28, NeuStyle.TEXT_GRAY);

        hoverStack(hovered);
    }

    private static final Map<String, ItemStack> stacks = new HashMap<>();

    /** One stack per collection, carrying the amount in the lore like the menu does. */
    private ItemStack stackFor(String id, long amount) {
        String key = id + ":" + amount;
        ItemStack cached = stacks.get(key);
        if (cached != null) return cached;

        String cleanId = id.split(":")[0];
        ItemStack base = bases.get(cleanId);
        if (base == null) {
            bases.put(cleanId, ItemStack.EMPTY);
            com.teslamaps.utils.SkyblockItems.resolve(cleanId, stack -> {
                bases.put(cleanId, stack);
                stacks.remove(key);
            });
            base = ItemStack.EMPTY;
        }

        ItemStack stack = base.isEmpty() ? new ItemStack(Items.PAPER) : base.copy();
        String name = formatCollectionName(id);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                com.teslamaps.profileviewer.data.LegacyText.parse(
                        (amount > 0 ? "§e" : "§7") + name));

        java.util.List<net.minecraft.network.chat.Component> lore = new java.util.ArrayList<>();
        lore.add(com.teslamaps.profileviewer.data.LegacyText.parse("§7Collected: §e" + formatNumber(amount)));
        if (amount == 0) lore.add(com.teslamaps.profileviewer.data.LegacyText.parse("§cNot started"));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));

        stacks.put(key, stack);
        return stack;
    }

    private static final Map<String, ItemStack> bases = new HashMap<>();

    private int lastLeft;
    private int lastTop;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 10 + i * 20;
            if (mouseX >= bx && mouseX < bx + 86 && mouseY >= by && mouseY < by + 18) {
                selectedCategory = i;
                return true;
            }
        }
        return false;
    }

    private String formatCollectionName(String id) {
        String name = id.replace("_ITEM", "").replace("_COLLECTION", "")
                .replace("_", " ").replace(":", " ");
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

}
