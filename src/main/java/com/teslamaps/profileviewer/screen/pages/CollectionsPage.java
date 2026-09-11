package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.CollectionRepo;
import com.teslamaps.profileviewer.data.LegacyText;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Collections the way the game groups them, names and tiers straight from hypixel. */
public class CollectionsPage extends ProfileViewerPage {
    private static final int COLUMNS = 8;
    private static final int ROWS = 5;
    private static final int PER_PAGE = COLUMNS * ROWS;

    private static final Map<String, ItemStack> bases = new HashMap<>();
    private static final Map<String, ItemStack> stacks = new HashMap<>();

    private int category = 0;
    private int page = 0;
    private int pageCount = 1;

    private int lastLeft;
    private int lastTop;
    private int pagerX;
    private int pagerY;
    private int pagerW;

    @Override
    public boolean isFixedLayout() {
        return true;
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
    public void onSelected() {
        CollectionRepo.load();
        page = 0;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        CollectionRepo.load();
        if (!CollectionRepo.isLoaded()) {
            ctx.text(tr, "Loading collections...", left + 20, top + 20, NeuStyle.TEXT_GRAY);
            return;
        }

        List<CollectionRepo.Category> categories = CollectionRepo.categories();
        if (categories.isEmpty()) return;
        category = Math.min(category, categories.size() - 1);

        Map<String, Long> collected = profile.getCollections();

        for (int i = 0; i < categories.size(); i++) {
            CollectionRepo.Category cat = categories.get(i);
            int bx = left + 6;
            int by = top + 10 + i * 20;
            boolean selected = i == category;
            boolean hovered = mouseX >= bx && mouseX < bx + 90 && mouseY >= by && mouseY < by + 18;

            if (selected) NeuStyle.slimBox(ctx, bx, by, 90, 18);
            ctx.text(tr, cat.name(), bx + 6, by + 5,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
        }

        CollectionRepo.Category current = categories.get(category);
        List<CollectionRepo.Entry> items = current.items();
        pageCount = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pageCount - 1);

        int gridX = left + 104;
        int gridY = top + 22;

        int maxed = 0;
        for (CollectionRepo.Entry entry : items) {
            long amount = collected.getOrDefault(entry.id(), 0L);
            if (CollectionRepo.tier(entry, amount) >= entry.maxTiers()) maxed++;
        }
        ctx.text(tr, current.name() + " - " + maxed + " / " + items.size() + " maxed",
                gridX, top + 10, NeuStyle.TEXT_YELLOW);

        ItemStack hovered = ItemStack.EMPTY;

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            int sx = gridX + (i % COLUMNS) * ItemGrid.SLOT;
            int sy = gridY + (i / COLUMNS) * ItemGrid.SLOT;

            ctx.fill(sx, sy, sx + 17, sy + 17, 0x60000000);
            ctx.fill(sx, sy, sx + 17, sy + 1, 0x40FFFFFF);
            ctx.fill(sx, sy, sx + 1, sy + 17, 0x40FFFFFF);

            if (index >= items.size()) continue;
            CollectionRepo.Entry entry = items.get(index);
            long amount = collected.getOrDefault(entry.id(), 0L);
            int tier = CollectionRepo.tier(entry, amount);

            ItemStack stack = stackFor(entry, amount, tier);
            if (!stack.isEmpty()) ctx.item(stack, sx + 1, sy + 1);

            if (tier > 0) {
                String tierText = String.valueOf(tier);
                ctx.text(tr, tierText, sx + 17 - tr.width(tierText), sy + 9,
                        tier >= entry.maxTiers() ? 0xFFFFAA00 : 0xFFFFFFFF);
            }

            if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                ctx.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0x40FFFFFF);
                hovered = stack;
            }
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = gridX + (COLUMNS * ItemGrid.SLOT - tr.width(pager)) / 2;
            pagerY = gridY + ROWS * ItemGrid.SLOT + 6;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }

        hoverStack(hovered);
    }

    private ItemStack stackFor(CollectionRepo.Entry entry, long amount, int tier) {
        String key = entry.id() + ":" + amount;
        ItemStack cached = stacks.get(key);
        if (cached != null) return cached;

        String cleanId = entry.id().split(":")[0];
        ItemStack base = bases.get(cleanId);
        if (base == null) {
            bases.put(cleanId, ItemStack.EMPTY);
            SkyblockItems.resolve(cleanId, stack -> {
                bases.put(cleanId, stack);
                stacks.remove(key);
            });
            base = ItemStack.EMPTY;
        }

        ItemStack stack = base.isEmpty() ? new ItemStack(Items.PAPER) : base.copy();
        stack.set(DataComponents.CUSTOM_NAME,
                LegacyText.parse((amount > 0 ? "§e" : "§7") + entry.name()));

        List<Component> lore = new ArrayList<>();
        lore.add(LegacyText.parse("§7Collected: §e" + formatXp(amount)));
        lore.add(LegacyText.parse("§7Tier: §a" + tier + "§7 / " + entry.maxTiers()));
        if (tier < entry.maxTiers() && tier < entry.tiers().size()) {
            long next = entry.tiers().get(tier);
            lore.add(LegacyText.parse("§7Next tier at §e" + formatXp(next)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));

        stacks.put(key, stack);
        return stack;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<CollectionRepo.Category> categories = CollectionRepo.categories();
        for (int i = 0; i < categories.size(); i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 10 + i * 20;
            if (mouseX >= bx && mouseX < bx + 90 && mouseY >= by && mouseY < by + 18) {
                category = i;
                page = 0;
                return true;
            }
        }

        if (pagerW > 0 && mouseY >= pagerY - 2 && mouseY <= pagerY + 10) {
            if (mouseX >= pagerX - 4 && mouseX <= pagerX + 10) {
                page = (page - 1 + pageCount) % pageCount;
                return true;
            }
            if (mouseX >= pagerX + pagerW - 10 && mouseX <= pagerX + pagerW + 4) {
                page = (page + 1) % pageCount;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (pageCount <= 1) return false;
        page = (int) Math.max(0, Math.min(pageCount - 1, page - amount));
        return true;
    }
}
