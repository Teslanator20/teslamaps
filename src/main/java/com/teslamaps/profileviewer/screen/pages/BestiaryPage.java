package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.BestiaryRepo;
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

/** The bestiary menu of the game: categories on the left, mob heads with their tier on the right. */
public class BestiaryPage extends ProfileViewerPage {
    private static final Map<String, ItemStack> icons = new HashMap<>();

    private int category = 0;
    private int sub = 0;
    private final List<int[]> subRects = new java.util.ArrayList<>();
    private int page = 0;
    private int pageCount = 1;
    private int railScroll = 0;
    private static final int RAIL_VISIBLE = 9;

    private final com.teslamaps.profileviewer.ui.SearchField search = new com.teslamaps.profileviewer.ui.SearchField();

    private int lastLeft;
    private int lastTop;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Bestiary";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public void onSelected() {
        BestiaryRepo.load();
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

        BestiaryRepo.load();
        if (!BestiaryRepo.isLoaded()) {
            ctx.text(tr, "Loading bestiary data...", left + 20, top + 20, NeuStyle.TEXT_GRAY);
            return;
        }

        List<BestiaryRepo.Category> categories = BestiaryRepo.categories();
        if (categories.isEmpty()) return;
        category = Math.min(category, categories.size() - 1);

        JsonObject kills = profile.getMemberData().has("bestiary")
                && profile.getMemberData().getAsJsonObject("bestiary").has("kills")
                ? profile.getMemberData().getAsJsonObject("bestiary").getAsJsonObject("kills") : new JsonObject();

        // search over every category at once, sitting on top of the rail
        search.draw(ctx, left + 6, top + 6, 104);

        // category rail, scrollable because the game has more than fits
        int visible = RAIL_VISIBLE;
        railScroll = Math.max(0, Math.min(railScroll, Math.max(0, categories.size() - visible)));
        for (int i = 0; i < visible && i + railScroll < categories.size(); i++) {
            BestiaryRepo.Category cat = categories.get(i + railScroll);
            int bx = left + 6;
            int by = top + 24 + i * 19;
            boolean selected = (i + railScroll) == category && search.isEmpty();
            boolean hovered = mouseX >= bx && mouseX < bx + 104 && mouseY >= by && mouseY < by + 17;

            if (selected) NeuStyle.slimBox(ctx, bx, by, 104, 17);
            ItemStack icon = iconFor(cat);
            if (!icon.isEmpty()) ctx.item(icon, bx + 1, by + 1);
            scaledText(ctx, tr, cat.name(), bx + 20, by + 4, 82,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
        }

        // thin scrollbar so the hidden categories are visible at a glance
        if (categories.size() > visible) {
            int trackX = left + 112;
            int trackY = top + 24;
            int trackH = visible * 19 - 2;
            ctx.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x40000000);
            int thumbH = Math.max(8, trackH * visible / categories.size());
            int thumbY = trackY + (trackH - thumbH) * railScroll / Math.max(1, categories.size() - visible);
            ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xA0FFFFFF);
        }

        BestiaryRepo.Category current = categories.get(category);

        // fishing and safari pick their group above the grid instead of filling the rail
        List<BestiaryRepo.Category> subs = search.isEmpty() ? current.subs() : List.of();
        int railWidth = 120;
        int areaX = left + railWidth;
        int areaW = NeuStyle.PANEL_WIDTH - railWidth - 8;
        int subRowY = top + 20;
        int subRowHeight = subs.isEmpty() ? 0 : 14;

        if (!subs.isEmpty()) {
            sub = Math.min(sub, subs.size() - 1);
            subRects.clear();
            int bx = areaX;
            for (int i = 0; i < subs.size(); i++) {
                String label = subs.get(i).name();
                int w = tr.width(label) + 10;
                if (bx + w > areaX + areaW) break;

                boolean active = i == sub;
                boolean over = mouseX >= bx && mouseX < bx + w && mouseY >= subRowY && mouseY < subRowY + 12;
                if (active) NeuStyle.slimBox(ctx, bx, subRowY - 1, w, 13);
                ctx.text(tr, label, bx + 5, subRowY + 2,
                        active ? NeuStyle.TEXT_YELLOW : over ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);

                subRects.add(new int[]{bx, subRowY - 1, w, 13, i});
                bx += w + 4;
            }
        } else {
            sub = 0;
            subRects.clear();
        }

        List<BestiaryRepo.Mob> mobs;
        if (!search.isEmpty()) {
            mobs = new java.util.ArrayList<>();
            for (BestiaryRepo.Category cat : categories) {
                if (cat.subs().isEmpty()) {
                    collectMatches(cat.mobs(), mobs);
                } else {
                    for (BestiaryRepo.Category subCat : cat.subs()) collectMatches(subCat.mobs(), mobs);
                }
            }
        } else {
            mobs = subs.isEmpty() ? current.mobs() : subs.get(sub).mobs();
        }

        // the grid is sized for this category, a handful of mobs get big slots instead of a big gap
        int areaY = top + 20 + subRowHeight;
        int areaH = NeuStyle.PANEL_HEIGHT - areaY + top - 16;

        int[] fit = ItemGrid.fitGrid(areaW, areaH, mobs.size());
        int slotSize = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = Math.max(1, columns * rows);

        pageCount = Math.max(1, (mobs.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        int gridX = areaX + Math.max(0, (areaW - columns * slotSize) / 2);
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

        ItemStack hovered = ItemStack.EMPTY;
        int totalTier = 0;
        for (BestiaryRepo.Mob mob : mobs) totalTier += BestiaryRepo.tier(mob, killsFor(kills, mob));

        Component title = LegacyText.parse(search.isEmpty() ? current.name()
                : "Results for \"" + search.text() + "\" (" + mobs.size() + ")");
        ctx.text(tr, title, areaX, top + 8, -1);
        // sits next to the title, at the bottom it ran into the last rail entry
        String summary = "Tiers: " + totalTier + "   Milestone: " + milestone(profile);
        ctx.text(tr, summary, areaX + areaW - tr.width(summary), top + 8, NeuStyle.TEXT_GRAY);

        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= mobs.size() && pageCount == 1) break;
            int sx = gridX + (i % columns) * slotSize;
            int sy = gridY + (i / columns) * slotSize;

            ItemGrid.slot(ctx, sx, sy, slotSize);

            if (index >= mobs.size()) continue;
            BestiaryRepo.Mob mob = mobs.get(index);
            long killCount = killsFor(kills, mob);
            int tier = BestiaryRepo.tier(mob, killCount);
            boolean maxed = tier >= BestiaryRepo.maxTier(mob) && BestiaryRepo.maxTier(mob) > 0;

            // a finished mob gets a green slot, so a category can be read at a glance
            if (maxed) ctx.fill(sx, sy, sx + slotSize - 1, sy + slotSize - 1, 0x6055FF55);

            ItemStack stack = mobStack(mob, killCount, tier);
            if (!stack.isEmpty()) ItemGrid.item(ctx, stack, sx, sy, slotSize);

            ItemGrid.count(ctx, String.valueOf(tier), sx, sy, slotSize, maxed ? 0xFFFFAA00 : 0xFFFFFFFF);

            if (mouseX >= sx && mouseX < sx + slotSize - 1 && mouseY >= sy && mouseY < sy + slotSize - 1) {
                ctx.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0x40FFFFFF);
                hovered = stack;
            }
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = gridX + (columns * slotSize - tr.width(pager)) / 2;
            pagerY = gridY + rows * slotSize + 2;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }

        hoverStack(hovered);
    }

    private int pagerX;
    private int pagerY;
    private int pagerW;

    private void collectMatches(List<BestiaryRepo.Mob> from, List<BestiaryRepo.Mob> out) {
        for (BestiaryRepo.Mob mob : from) {
            if (search.matches(com.teslamaps.utils.StringUtil.strip(mob.name()))) out.add(mob);
        }
    }

    /** Long category names shrink to fit the rail instead of being chopped off. */
    private static void scaledText(GuiGraphicsExtractor ctx, Font tr, String text, int x, int y,
                                   int maxWidth, int color) {
        int width = tr.width(text);
        if (width <= maxWidth) {
            ctx.text(tr, text, x, y, color);
            return;
        }
        float factor = Math.max(0.6f, (float) maxWidth / width);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y + (1 - factor) * 4);
        ctx.pose().scale(factor, factor);
        ctx.text(tr, text, 0, 0, color);
        ctx.pose().popMatrix();
    }

    private static int milestone(SkyblockProfile profile) {
        JsonObject bestiary = profile.getMemberData().has("bestiary")
                ? profile.getMemberData().getAsJsonObject("bestiary") : null;
        if (bestiary == null || !bestiary.has("milestone")) return 0;
        return asInt(bestiary.getAsJsonObject("milestone").get("last_claimed_milestone"));
    }

    private static long killsFor(JsonObject kills, BestiaryRepo.Mob mob) {
        long total = 0;
        for (String key : mob.keys()) total += asLong(kills.get(key));
        return total;
    }

    private static final Map<String, ItemStack> mobStacks = new HashMap<>();

    private static ItemStack mobStack(BestiaryRepo.Mob mob, long kills, int tier) {
        String key = mob.name() + ":" + kills;
        ItemStack cached = mobStacks.get(key);
        if (cached != null) return cached;

        // dragons and co carry a plain item instead of a skull; only then fall back to a head
        ItemStack stack = mob.texture() != null ? SkyblockItems.headFromTexture(mob.texture())
                : mob.item() != null ? com.teslamaps.profileviewer.ui.Kit.icon(mob.item())
                : ItemStack.EMPTY;
        if (stack.isEmpty()) stack = new ItemStack(Items.PLAYER_HEAD);
        else stack = stack.copy();

        stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(mob.name()));

        List<Component> lore = new ArrayList<>();
        lore.add(LegacyText.parse("§7Kills: §e" + kills));
        lore.add(LegacyText.parse("§7Tier: §a" + tier + "§7 / " + BestiaryRepo.maxTier(mob)));
        if (mob.cap() > 0) lore.add(LegacyText.parse("§7Cap: §e" + mob.cap()));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        mobStacks.put(key, stack);
        return stack;
    }

    private static ItemStack iconFor(BestiaryRepo.Category category) {
        if (category.icon() != null) {
            return icons.computeIfAbsent(category.icon(), SkyblockItems::headFromTexture);
        }
        if (category.iconItem() != null) {
            return icons.computeIfAbsent("item:" + category.iconItem(),
                    key -> com.teslamaps.profileviewer.ui.Kit.icon(category.iconItem()));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean charTyped(char chr) {
        if (search.charTyped(chr)) {
            page = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.keyPressed(keyCode)) {
            page = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.mouseClicked(mouseX, mouseY, button)) return true;

        for (int[] rect : subRects) {
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                sub = rect[4];
                page = 0;
                return true;
            }
        }

        List<BestiaryRepo.Category> categories = BestiaryRepo.categories();
        for (int i = 0; i < RAIL_VISIBLE && i + railScroll < categories.size(); i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 24 + i * 19;
            if (mouseX >= bx && mouseX < bx + 104 && mouseY >= by && mouseY < by + 17) {
                category = i + railScroll;
                sub = 0;
                page = 0;
                search.clear();
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
        // the rail scrolls on the left, the pages on the right
        if (mouseX < lastLeft + 120) {
            railScroll = (int) Math.max(0, railScroll - amount);
            return true;
        }
        if (pageCount <= 1) return false;
        page = (int) Math.max(0, Math.min(pageCount - 1, page - amount));
        return true;
    }
}
