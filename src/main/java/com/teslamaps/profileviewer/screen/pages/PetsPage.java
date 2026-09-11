package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.PetData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
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

/** The pet menu the way the game shows it: a slot grid with pages. */
public class PetsPage extends ProfileViewerPage {
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int PER_PAGE = COLUMNS * ROWS;

    private static final Map<String, ItemStack> petStacks = new HashMap<>();

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
        return "Pets";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.BONE);
    }

    @Override
    public void onSelected() {
        page = 0;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null || profile.getPets().isEmpty()) {
            ctx.text(tr, "No pets", x + 10, y + 10, NeuStyle.TEXT_GRAY);
            return;
        }

        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        List<PetData> pets = profile.getPets();
        pageCount = Math.max(1, (pets.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pageCount - 1);

        String title = "Pets (" + pets.size() + ")";
        ctx.text(tr, title, left + NeuStyle.PANEL_WIDTH / 2 - tr.width(title) / 2, top + 6, NeuStyle.TEXT_YELLOW);

        int gridWidth = COLUMNS * ItemGrid.SLOT;
        int gridX = left + (NeuStyle.PANEL_WIDTH - gridWidth) / 2;
        int gridY = top + 20;

        List<String> tooltip = null;

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            int sx = gridX + (i % COLUMNS) * ItemGrid.SLOT;
            int sy = gridY + (i / COLUMNS) * ItemGrid.SLOT;

            ctx.fill(sx, sy, sx + 17, sy + 17, 0x60000000);
            ctx.fill(sx, sy, sx + 17, sy + 1, 0x40FFFFFF);
            ctx.fill(sx, sy, sx + 1, sy + 17, 0x40FFFFFF);

            if (index >= pets.size()) continue;
            PetData pet = pets.get(index);

            ItemStack stack = stackFor(pet);
            if (!stack.isEmpty()) ctx.item(stack, sx + 1, sy + 1);

            if (pet.isActive()) {
                ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x3055FF55);
            }

            String level = String.valueOf(pet.getLevel());
            ctx.text(tr, level, sx + 17 - tr.width(level), sy + 9, 0xFFFFFFFF);

            if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                ctx.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0x40FFFFFF);
                tooltip = tooltipFor(pet);
            }
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = left + (NeuStyle.PANEL_WIDTH - tr.width(pager)) / 2;
            pagerY = gridY + ROWS * ItemGrid.SLOT + 6;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }

        ItemGrid.drawTooltip(ctx, tooltip, mouseX, mouseY);
    }

    private static ItemStack stackFor(PetData pet) {
        String id = pet.getType() + ";" + pet.getRarityOrdinal();
        ItemStack cached = petStacks.get(id);
        if (cached != null) return cached;
        petStacks.put(id, new ItemStack(Items.BONE));
        SkyblockItems.resolve(id, stack -> petStacks.put(id, stack));
        return petStacks.get(id);
    }

    private static List<String> tooltipFor(PetData pet) {
        List<String> lines = new ArrayList<>();
        lines.add(pet.getDisplayName());
        lines.add("Level " + pet.getLevel());
        lines.add("XP " + (long) pet.getExp());
        if (pet.getHeldItem() != null) lines.add("Held: " + pretty(pet.getHeldItem()));
        if (pet.getSkin() != null) lines.add("Skin: " + pretty(pet.getSkin()));
        if (pet.isActive()) lines.add("Active");
        return lines;
    }

    private static String pretty(String id) {
        String out = id.replace("PET_ITEM_", "").replace("PET_SKIN_", "").replace('_', ' ').toLowerCase();
        return out.isEmpty() ? out : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
