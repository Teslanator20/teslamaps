package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.PetData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.LegacyText;
import com.teslamaps.profileviewer.screen.ItemGrid;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
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
    private static final Map<String, ItemStack> petBases = new HashMap<>();
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

        String title = "Pets (" + pets.size() + ")";
        ctx.text(tr, title, left + NeuStyle.PANEL_WIDTH / 2 - tr.width(title) / 2, top + 6, NeuStyle.TEXT_YELLOW);

        // the grid is sized for this collection, so a small one gets big slots and no paging
        int areaW = NeuStyle.PANEL_WIDTH - 16;
        int areaY = top + 20;
        int areaH = NeuStyle.PANEL_HEIGHT - 20 - 16;

        int[] fit = ItemGrid.fitGrid(areaW, areaH, pets.size());
        int slotSize = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = Math.max(1, columns * rows);

        pageCount = Math.max(1, (pets.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        int gridX = left + (NeuStyle.PANEL_WIDTH - columns * slotSize) / 2;
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

        ItemStack hovered = ItemStack.EMPTY;

        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= pets.size() && pageCount == 1) break;
            int sx = gridX + (i % columns) * slotSize;
            int sy = gridY + (i / columns) * slotSize;

            ItemGrid.slot(ctx, sx, sy, slotSize);

            if (index >= pets.size()) continue;
            PetData pet = pets.get(index);

            ItemStack stack = stackFor(pet);
            if (!stack.isEmpty()) ItemGrid.item(ctx, stack, sx, sy, slotSize);

            if (pet.isActive()) {
                ctx.fill(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1, 0x3055FF55);
            }

            if (mouseX >= sx && mouseX < sx + slotSize - 1 && mouseY >= sy && mouseY < sy + slotSize - 1) {
                ctx.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0x40FFFFFF);
                hovered = stack;
            }
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = left + (NeuStyle.PANEL_WIDTH - tr.width(pager)) / 2;
            pagerY = gridY + rows * slotSize + 4;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }

        hoverStack(hovered);
    }

    /** Builds the pet the way the game shows it: level in the name, rarity colour, lore below. */
    private static ItemStack stackFor(PetData pet) {
        String key = pet.getType() + ";" + pet.getRarityOrdinal() + ";" + pet.getLevel()
                + ";" + pet.getCandyUsed() + ";" + pet.getHeldItem() + ";" + pet.isActive();
        ItemStack cached = petStacks.get(key);
        if (cached != null) return cached;

        // a skin replaces the whole look, and not every rarity of a pet exists in the repo -
        // asking only for this one is what left half the cats as plain bones
        String repoId = pet.getSkin() != null ? skinId(pet.getSkin()) : null;
        ItemStack base = repoId == null ? ItemStack.EMPTY : lookup(repoId, key);

        if (base.isEmpty()) {
            for (int rarity = pet.getRarityOrdinal(); rarity >= 0 && base.isEmpty(); rarity--) {
                base = lookup(pet.getType() + ";" + rarity, key);
            }
            for (int rarity = pet.getRarityOrdinal() + 1; rarity <= 5 && base.isEmpty(); rarity++) {
                base = lookup(pet.getType() + ";" + rarity, key);
            }
        }

        ItemStack stack = (base.isEmpty() ? new ItemStack(Items.BONE) : base.copy());

        net.minecraft.nbt.CompoundTag extra = new net.minecraft.nbt.CompoundTag();
        extra.putString("id", "PET");
        extra.putString("petInfo", "{\"type\":\"" + pet.getType() + "\",\"tier\":\"" + pet.getTier()
                + "\",\"exp\":" + (long) pet.getExp() + ",\"candyUsed\":" + pet.getCandyUsed()
                + (pet.getHeldItem() != null ? ",\"heldItem\":\"" + pet.getHeldItem() + "\"" : "")
                + (pet.getSkin() != null ? ",\"skin\":\"" + pet.getSkin() + "\"" : "")
                + ",\"active\":" + pet.isActive() + "}");
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(extra));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, pet.isActive());

        String color = pet.getRarityCode();
        stack.set(DataComponents.CUSTOM_NAME,
                LegacyText.parse("§7[Lvl " + pet.getLevel() + "] " + color + pet.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        if (pet.getHeldItem() != null) {
            lore.add(LegacyText.parse("§7Held Item: §6" + pretty(pet.getHeldItem())));
            lore.add(Component.empty());
        }
        if (pet.getLevel() >= pet.getMaxLevel()) {
            lore.add(LegacyText.parse("§bMAX LEVEL"));
        } else {
            int percent = (int) Math.round(pet.getProgress() * 100);
            lore.add(LegacyText.parse("§7Progress to Level " + (pet.getLevel() + 1) + ": §e" + percent + "%"));
            lore.add(LegacyText.parse(bar(pet.getProgress())));
        }
        lore.add(LegacyText.parse("§7Total XP: §e" + format(pet.getExp())));
        if (pet.getCandyUsed() > 0) {
            lore.add(LegacyText.parse("§a(" + pet.getCandyUsed() + "/10) Pet Candy Used"));
        }
        if (pet.getSkin() != null) {
            lore.add(LegacyText.parse("§7Skin: §d" + pretty(pet.getSkin())));
        }
        lore.add(Component.empty());
        lore.add(LegacyText.parse(color + pet.getTier().toUpperCase() + (pet.isActive() ? " PET §a(active)" : " PET")));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        petStacks.put(key, stack);
        return stack;
    }

    private static String skinId(String skin) {
        return skin.startsWith("PET_SKIN_") ? skin : "PET_SKIN_" + skin;
    }

    /** Repo lookup that kicks off the async fetch once and drops the cached stack when it lands. */
    private static ItemStack lookup(String repoId, String cacheKey) {
        ItemStack known = petBases.get(repoId);
        if (known != null) return known;

        petBases.put(repoId, ItemStack.EMPTY);
        SkyblockItems.resolve(repoId, stack -> {
            petBases.put(repoId, stack);
            petStacks.remove(cacheKey);
        });
        return ItemStack.EMPTY;
    }

    /** The green progress bar hypixel prints under the percentage. */
    private static String bar(double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * 20);
        return "§2" + "-".repeat(filled) + "§f" + "-".repeat(20 - filled);
    }

    private static String format(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000);
        return String.valueOf((long) value);
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
