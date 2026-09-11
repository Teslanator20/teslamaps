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
    private static final int COLUMNS = 7;
    private static final int ROWS = 5;
    private static final int PER_PAGE = COLUMNS * ROWS;

    private static final Map<String, ItemStack> icons = new HashMap<>();

    private int category = 0;
    private int page = 0;
    private int pageCount = 1;
    private int railScroll = 0;

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

        // category rail, scrollable because the game has more than fits
        int visible = 9;
        railScroll = Math.max(0, Math.min(railScroll, Math.max(0, categories.size() - visible)));
        for (int i = 0; i < visible && i + railScroll < categories.size(); i++) {
            BestiaryRepo.Category cat = categories.get(i + railScroll);
            int bx = left + 6;
            int by = top + 8 + i * 20;
            boolean selected = (i + railScroll) == category;
            boolean hovered = mouseX >= bx && mouseX < bx + 104 && mouseY >= by && mouseY < by + 18;

            if (selected) NeuStyle.slimBox(ctx, bx, by, 104, 18);
            ItemStack icon = iconFor(cat.icon());
            if (!icon.isEmpty()) ctx.item(icon, bx + 1, by + 1);
            String name = cat.name();
            ctx.text(tr, name.length() > 13 ? name.substring(0, 13) : name, bx + 20, by + 5,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
        }

        BestiaryRepo.Category current = categories.get(category);
        List<BestiaryRepo.Mob> mobs = current.mobs();
        pageCount = Math.max(1, (mobs.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pageCount - 1);

        int gridX = left + 120;
        int gridY = top + 20;

        Component title = LegacyText.parse(current.name());
        ctx.text(tr, title, gridX, top + 8, -1);

        ItemStack hovered = ItemStack.EMPTY;
        int totalTier = 0;
        for (BestiaryRepo.Mob mob : mobs) totalTier += BestiaryRepo.tier(mob, killsFor(kills, mob));

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            int sx = gridX + (i % COLUMNS) * ItemGrid.SLOT;
            int sy = gridY + (i / COLUMNS) * ItemGrid.SLOT;

            ctx.fill(sx, sy, sx + 17, sy + 17, 0x60000000);
            ctx.fill(sx, sy, sx + 17, sy + 1, 0x40FFFFFF);
            ctx.fill(sx, sy, sx + 1, sy + 17, 0x40FFFFFF);

            if (index >= mobs.size()) continue;
            BestiaryRepo.Mob mob = mobs.get(index);
            long killCount = killsFor(kills, mob);
            int tier = BestiaryRepo.tier(mob, killCount);

            ItemStack stack = mobStack(mob, killCount, tier);
            if (!stack.isEmpty()) ctx.item(stack, sx + 1, sy + 1);

            String tierText = String.valueOf(tier);
            ctx.text(tr, tierText, sx + 17 - tr.width(tierText), sy + 9,
                    tier >= BestiaryRepo.maxTier(mob) ? 0xFFFFAA00 : 0xFFFFFFFF);

            if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                ctx.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0x40FFFFFF);
                hovered = stack;
            }
        }

        String summary = "Tiers: " + totalTier + "   Milestone: " + milestone(profile);
        ctx.text(tr, summary, gridX, top + NeuStyle.PANEL_HEIGHT - 30, NeuStyle.TEXT_GRAY);

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = gridX + (COLUMNS * ItemGrid.SLOT - tr.width(pager)) / 2;
            pagerY = gridY + ROWS * ItemGrid.SLOT + 4;
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

        ItemStack stack = mob.texture() != null
                ? SkyblockItems.headFromTexture(mob.texture()) : new ItemStack(Items.ZOMBIE_HEAD);
        if (stack.isEmpty()) stack = new ItemStack(Items.ZOMBIE_HEAD);

        stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(mob.name()));

        List<Component> lore = new ArrayList<>();
        lore.add(LegacyText.parse("§7Kills: §e" + kills));
        lore.add(LegacyText.parse("§7Tier: §a" + tier + "§7 / " + BestiaryRepo.maxTier(mob)));
        if (mob.cap() > 0) lore.add(LegacyText.parse("§7Cap: §e" + mob.cap()));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        mobStacks.put(key, stack);
        return stack;
    }

    private static ItemStack iconFor(String texture) {
        if (texture == null) return ItemStack.EMPTY;
        return icons.computeIfAbsent(texture, SkyblockItems::headFromTexture);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<BestiaryRepo.Category> categories = BestiaryRepo.categories();
        for (int i = 0; i < 9 && i + railScroll < categories.size(); i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 8 + i * 20;
            if (mouseX >= bx && mouseX < bx + 104 && mouseY >= by && mouseY < by + 18) {
                category = i + railScroll;
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
        // the rail scrolls on the left, the pages on the right
        if (mouseX < lastLeft + 116) {
            railScroll = (int) Math.max(0, railScroll - amount);
            return true;
        }
        if (pageCount <= 1) return false;
        page = (int) Math.max(0, Math.min(pageCount - 1, page - amount));
        return true;
    }
}
