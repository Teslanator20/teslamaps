package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** The containers of the account, laid out like the menus they come from. */
public class InventoriesPage extends ProfileViewerPage {
    private enum Section {
        INVENTORY("Inventory", Items.CHEST),
        ENDER_CHEST("Ender Chest", Items.ENDER_CHEST),
        BACKPACKS("Backpacks", Items.BUNDLE),
        ACCESSORIES("Accessories", Items.GOLDEN_APPLE),
        VAULT("Personal Vault", Items.IRON_DOOR),
        FISHING("Fishing Bag", Items.FISHING_ROD),
        POTIONS("Potion Bag", Items.POTION),
        QUIVER("Quiver", Items.ARROW);

        final String label;
        final net.minecraft.world.item.Item icon;

        Section(String label, net.minecraft.world.item.Item icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private Section section = Section.INVENTORY;
    private int page = 0;

    // decoding rebuilt every Slot every frame, so the item cache behind them never hit and the
    // hovered stack was a different object each frame
    private final java.util.Map<String, List<InventoryDecoder.Slot>> decodeCache = new java.util.HashMap<>();

    @Override
    public void init(com.teslamaps.profileviewer.screen.ProfileViewerScreen parent,
                     com.teslamaps.profileviewer.data.SkyblockProfiles profiles) {
        super.init(parent, profiles);
        decodeCache.clear();
        backpackCache = null;
    }

    private List<List<InventoryDecoder.Slot>> backpackCache;

    private List<InventoryDecoder.Slot> decoded(JsonObject inventory, String path) {
        if (inventory == null) return List.of();
        return decodeCache.computeIfAbsent(path, key -> InventoryDecoder.decode(inventory, key));
    }

    private int lastLeft;
    private int lastTop;
    private int pageCount = 1;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Inventories";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.CHEST);
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
        if (profile == null) return;

        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        JsonObject inventory = profile.getMemberData().has("inventory")
                ? profile.getMemberData().getAsJsonObject("inventory") : null;

        // left rail with one button per container
        for (int i = 0; i < Section.values().length; i++) {
            Section s = Section.values()[i];
            int bx = left + 6;
            int by = top + 6 + i * 19;
            boolean selected = s == section;
            boolean hovered = mouseX >= bx && mouseX < bx + 100 && mouseY >= by && mouseY < by + 18;

            if (selected) NeuStyle.slimBox(ctx, bx, by, 100, 18);
            ctx.item(new ItemStack(s.icon), bx + 2, by + 1);
            ctx.text(tr, s.label, bx + 21, by + 5,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
        }

        // everything right of the rail belongs to the container; the grid grows to fill it
        int areaX = left + 118;
        int areaW = NeuStyle.PANEL_WIDTH - 118 - 8;
        // room above for the title and below for the pager, the grid used to touch both
        int areaY = top + 26;
        int areaH = NeuStyle.PANEL_HEIGHT - 26 - 34;

        List<InventoryDecoder.Slot> slots;
        String title = section.label;
        pageCount = 1;

        switch (section) {
            case INVENTORY -> {
                slots = reorderPlayerInventory(decoded(inventory,"inv_contents"));
                sideArmor = decoded(inventory,"inv_armor");
            }
            case ENDER_CHEST -> {
                List<InventoryDecoder.Slot> all = decoded(inventory,"ender_chest_contents");
                pageCount = Math.max(1, (all.size() + 44) / 45);
                page = Math.min(page, pageCount - 1);
                slots = subList(all, page * 45, 45);
                title = "Ender Chest " + (page + 1) + " / " + pageCount;
            }
            case BACKPACKS -> {
                if (backpackCache == null) backpackCache = backpacks(inventory);
                List<List<InventoryDecoder.Slot>> packs = backpackCache;
                pageCount = Math.max(1, packs.size());
                page = Math.min(page, pageCount - 1);
                slots = packs.isEmpty() ? List.of() : packs.get(page);
                title = "Backpack " + (page + 1) + " / " + pageCount;
            }
            case ACCESSORIES -> {
                List<InventoryDecoder.Slot> all = decoded(inventory,"bag_contents.talisman_bag");
                pageCount = Math.max(1, (all.size() + 44) / 45);
                page = Math.min(page, pageCount - 1);
                slots = subList(all, page * 45, 45);
                int magicalPower = magicalPower(profile);
                ctx.text(tr, "Magical Power: " + magicalPower, areaX, top + 10, NeuStyle.TEXT_LIGHT_PURPLE);
                title = null;
            }
            case FISHING -> {
                slots = pageOf(decoded(inventory,"bag_contents.fishing_bag"), 45);
                title = "Fishing Bag";
            }
            case POTIONS -> {
                slots = pageOf(decoded(inventory,"bag_contents.potion_bag"), 45);
                title = "Potion Bag";
            }
            case QUIVER -> {
                slots = pageOf(decoded(inventory,"bag_contents.quiver"), 45);
                title = "Quiver";
            }
            case VAULT -> {
                slots = decoded(inventory,"personal_vault_contents");
                title = "Personal Vault";
            }
            default -> slots = List.of();
        }

        if (title != null) {
            ctx.text(tr, title, areaX, top + 10, NeuStyle.TEXT_YELLOW);
        }

        if (slots.isEmpty()) {
            ctx.text(tr, "Nothing here, or the api is off", areaX, areaY + 20, NeuStyle.TEXT_GRAY);
            return;
        }

        int columns = 9;
        int count = Math.min(slots.size(), columns * 9);
        int rows = Math.max(1, (count + columns - 1) / columns);

        // the player inventory shows the armor as an extra column on the right
        boolean sideColumns = section == Section.INVENTORY;
        int sideGap = sideColumns ? 14 : 0;
        int slotSize = ItemGrid.fitSlot(areaW - sideGap, areaH, columns + (sideColumns ? 1 : 0), rows);

        int totalWidth = columns * slotSize + (sideColumns ? sideGap + slotSize : 0);
        int gridX = areaX + Math.max(0, (areaW - totalWidth) / 2);
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

        ItemStack hovered = ItemGrid.draw(ctx, slots, gridX, gridY, columns, 0, count, mouseX, mouseY, slotSize);

        if (sideColumns) {
            int sideX = gridX + 9 * slotSize + 10;
            ItemStack armorHover = ItemGrid.draw(ctx, reverse(sideArmor), sideX, gridY, 1, 0, 4,
                    mouseX, mouseY, slotSize);
            if (hovered.isEmpty()) hovered = armorHover;
        }

        drawPageChips(ctx, tr, areaX, areaW,
                Math.max(gridY + rows * slotSize + 6, top + NeuStyle.PANEL_HEIGHT - 26), mouseX, mouseY);

        hoverStack(hovered);
    }

    // one numbered chip per page, so every backpack / accessory page is one click away
    private final List<int[]> chipRects = new ArrayList<>();

    private void drawPageChips(GuiGraphicsExtractor ctx, Font tr, int x, int width, int y,
                               int mouseX, int mouseY) {
        chipRects.clear();
        if (pageCount <= 1) return;

        // fixed chip size over the full area width; 18 backpacks overflowed a grid-width row
        int chipW = 16;
        int perRow = Math.max(1, (width + 2) / (chipW + 2));
        int rows = (pageCount + perRow - 1) / perRow;
        int startY = y - (rows - 1) * 15;

        for (int i = 0; i < pageCount; i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, pageCount - row * perRow);
            int rowX = x + Math.max(0, (width - inRow * (chipW + 2) + 2) / 2);
            int cx = rowX + (i % perRow) * (chipW + 2);
            int cy = startY + row * 15;

            boolean active = i == page;
            boolean over = mouseX >= cx && mouseX < cx + chipW && mouseY >= cy && mouseY < cy + 13;
            if (active) NeuStyle.slimBox(ctx, cx, cy, chipW, 13);
            String label = String.valueOf(i + 1);
            ctx.text(tr, label, cx + (chipW - tr.width(label)) / 2, cy + 3,
                    active ? NeuStyle.TEXT_YELLOW : over ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
            chipRects.add(new int[]{cx, cy, chipW, 13, i});
        }
    }

    /** Splits a container into the current page and pads it to a full grid. */
    private List<InventoryDecoder.Slot> pageOf(List<InventoryDecoder.Slot> all, int perPage) {
        pageCount = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);
        return subList(all, page * perPage, perPage);
    }

    private List<InventoryDecoder.Slot> sideArmor = List.of();

    /** Hypixel stores armor boots first; the menu shows the helmet on top. */
    private static List<InventoryDecoder.Slot> reverse(List<InventoryDecoder.Slot> slots) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(slots);
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < Section.values().length; i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 6 + i * 19;
            if (mouseX >= bx && mouseX < bx + 100 && mouseY >= by && mouseY < by + 18) {
                section = Section.values()[i];
                page = 0;
                return true;
            }
        }

        for (int[] chip : chipRects) {
            if (mouseX >= chip[0] && mouseX < chip[0] + chip[2]
                    && mouseY >= chip[1] && mouseY < chip[1] + chip[3]) {
                page = chip[4];
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

    private static List<InventoryDecoder.Slot> subList(List<InventoryDecoder.Slot> all, int from, int size) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(size);
        for (int i = from; i < from + size; i++) {
            out.add(i < all.size() ? all.get(i) : InventoryDecoder.Slot.EMPTY);
        }
        return out;
    }

    /** Hypixel stores the hotbar first; the player menu shows it last. */
    private static List<InventoryDecoder.Slot> reorderPlayerInventory(List<InventoryDecoder.Slot> slots) {
        if (slots.size() < 36) return slots;
        List<InventoryDecoder.Slot> out = new ArrayList<>(36);
        out.addAll(slots.subList(9, 36));
        out.addAll(slots.subList(0, 9));
        return out;
    }

    private static List<List<InventoryDecoder.Slot>> backpacks(JsonObject inventory) {
        List<List<InventoryDecoder.Slot>> packs = new ArrayList<>();
        if (inventory == null || !inventory.has("backpack_contents")) return packs;

        JsonObject contents = inventory.getAsJsonObject("backpack_contents");
        contents.keySet().stream().sorted((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        }).forEach(key -> {
            List<InventoryDecoder.Slot> pack = InventoryDecoder.decode(contents, key);
            if (!pack.isEmpty()) packs.add(pack);
        });
        return packs;
    }

    private static int magicalPower(SkyblockProfile profile) {
        JsonObject accessories = profile.getMemberData().has("accessory_bag_storage")
                ? profile.getMemberData().getAsJsonObject("accessory_bag_storage") : null;
        if (accessories == null) return 0;
        return asInt(accessories.get("highest_magical_power"));
    }
}
