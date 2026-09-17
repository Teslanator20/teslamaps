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

    // the icon resolves async through the item repo; a stack grabbed at build time froze the
    // barrier placeholder in, so the slot is kept and resolved per draw (cached in ItemGrid)
    private record Backpack(InventoryDecoder.Slot iconSlot, List<InventoryDecoder.Slot> contents) {
        ItemStack icon() {
            ItemStack stack = iconSlot == null ? ItemStack.EMPTY : ItemGrid.stackFor(iconSlot);
            return stack.isEmpty() ? new ItemStack(Items.BUNDLE) : stack;
        }
    }

    private List<Backpack> backpackCache;

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

        NeuStyle.divider(ctx, left + 112, top + 8, NeuStyle.PANEL_HEIGHT - 16);

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
                pagedFull = all;
                pagedPerPage = 45;
                pageCount = Math.max(1, (all.size() + 44) / 45);
                page = Math.min(page, pageCount - 1);
                slots = subList(all, page * 45, 45);
                title = "Ender Chest " + (page + 1) + " / " + pageCount;
            }
            case BACKPACKS -> {
                if (backpackCache == null) backpackCache = backpacks(inventory);
                pageCount = Math.max(1, backpackCache.size());
                page = Math.min(page, pageCount - 1);
                slots = backpackCache.isEmpty() ? List.of() : backpackCache.get(page).contents();
                title = "Backpack " + (page + 1) + " / " + pageCount;
            }
            case ACCESSORIES -> {
                List<InventoryDecoder.Slot> all = decoded(inventory,"bag_contents.talisman_bag");
                pagedFull = all;
                pagedPerPage = 45;
                pageCount = Math.max(1, (all.size() + 44) / 45);
                page = Math.min(page, pageCount - 1);
                slots = subList(all, page * 45, 45);
                ctx.text(tr, powerLine(profile), areaX, top + 10, NeuStyle.TEXT_LIGHT_PURPLE);
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

        // enough backpacks wrap the icon strip into a second row; the grid gives up that height
        if (section == Section.BACKPACKS && pageCount > Math.max(1, (areaW + 2) / 22)) {
            areaH -= 22;
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

        // horizontally every section anchors like the player inventory (nine columns plus the
        // armor column), so the grid keeps one left edge; vertically it centers as before
        int refWidth = 9 * slotSize + 14 + slotSize;
        int gridX = areaX + Math.max(0, (areaW - refWidth) / 2);
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
    private static final ItemStack ENDER_CHEST_ICON = new ItemStack(Items.ENDER_CHEST);
    private final List<int[]> chipRects = new ArrayList<>();

    private void drawPageChips(GuiGraphicsExtractor ctx, Font tr, int x, int width, int y,
                               int mouseX, int mouseY) {
        chipRects.clear();
        if (pageCount <= 1) return;

        boolean icons = (section == Section.BACKPACKS && backpackCache != null)
                || section == Section.ENDER_CHEST;
        int chipW = icons ? 20 : 16;
        int chipH = icons ? 20 : 13;
        int step = chipW + 2;
        int perRow = Math.max(1, (width + 2) / step);
        int rows = (pageCount + perRow - 1) / perRow;
        // balance the rows: 18 packs become 9/9, not 13/5
        perRow = (pageCount + rows - 1) / rows;
        // grow downward and clamp to the window bottom, so a second row never sits on the grid
        int startY = Math.min(y, lastTop + NeuStyle.PANEL_HEIGHT - rows * (chipH + 2) - 2);

        int hoveredPage = -1;
        for (int i = 0; i < pageCount; i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, pageCount - row * perRow);
            int rowX = x + Math.max(0, (width - inRow * step + 2) / 2);
            int cx = rowX + (i % perRow) * step;
            int cy = startY + row * (chipH + 2);

            boolean active = i == page;
            boolean over = mouseX >= cx && mouseX < cx + chipW && mouseY >= cy && mouseY < cy + chipH;
            if (over) hoveredPage = i;

            if (icons) {
                // the real backpack items, like the backpack menu shows them; ender chest pages
                // wear the ender chest with their number on it
                ItemGrid.slot(ctx, cx, cy, 20);
                ItemGrid.item(ctx, section == Section.BACKPACKS
                        ? backpackCache.get(i).icon() : ENDER_CHEST_ICON, cx, cy, 20);
                if (section == Section.ENDER_CHEST) {
                    ItemGrid.smallCount(ctx, String.valueOf(i + 1), cx, cy, 20,
                            active ? 0xFFFFFF55 : 0xFFFFFFFF);
                }
                if (active) {
                    ctx.fill(cx, cy, cx + 19, cy + 1, NeuStyle.TEXT_YELLOW);
                    ctx.fill(cx, cy + 18, cx + 19, cy + 19, NeuStyle.TEXT_YELLOW);
                    ctx.fill(cx, cy, cx + 1, cy + 19, NeuStyle.TEXT_YELLOW);
                    ctx.fill(cx + 18, cy, cx + 19, cy + 19, NeuStyle.TEXT_YELLOW);
                } else if (over) {
                    ctx.fill(cx + 1, cy + 1, cx + 18, cy + 18, 0x30FFFFFF);
                }
            } else {
                if (active) NeuStyle.slimBox(ctx, cx, cy, chipW, chipH);
                String label = String.valueOf(i + 1);
                ctx.text(tr, label, cx + (chipW - tr.width(label)) / 2, cy + 3,
                        active ? NeuStyle.TEXT_YELLOW : over ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
            }
            chipRects.add(new int[]{cx, cy, chipW, chipH, i});
        }

        if (hoveredPage >= 0) {
            String label = section == Section.BACKPACKS ? "Backpack " + (hoveredPage + 1)
                    : section.label + " " + (hoveredPage + 1);
            String header = section == Section.ACCESSORIES
                    ? "§d" + powerLine(getProfile()) : null;
            previewGrid(label, header, pageContents(hoveredPage), mouseX, mouseY);
        }
    }

    /** Splits a container into the current page and pads it to a full grid. */
    private List<InventoryDecoder.Slot> pageOf(List<InventoryDecoder.Slot> all, int perPage) {
        pagedFull = all;
        pagedPerPage = perPage;
        pageCount = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);
        return subList(all, page * perPage, perPage);
    }

    private List<InventoryDecoder.Slot> pagedFull = List.of();
    private int pagedPerPage = 45;

    /** What sits on page {@code index}, for the hover preview. */
    private List<InventoryDecoder.Slot> pageContents(int index) {
        if (section == Section.BACKPACKS) {
            return backpackCache != null && index < backpackCache.size()
                    ? backpackCache.get(index).contents() : List.of();
        }
        int from = index * pagedPerPage;
        if (from >= pagedFull.size()) return List.of();
        return pagedFull.subList(from, Math.min(from + pagedPerPage, pagedFull.size()));
    }

    /** Item-grid preview at the cursor, exactly like the game's backpack preview. */
    private void previewGrid(String title, String header, List<InventoryDecoder.Slot> contents, int mouseX, int mouseY) {
        if (parent == null || contents.isEmpty()) return;
        parent.setHoveredPreview(
                com.teslamaps.profileviewer.data.LegacyText.parse("§e" + title),
                header == null ? null : com.teslamaps.profileviewer.data.LegacyText.parse(header),
                contents, mouseX, mouseY);
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

    private static List<Backpack> backpacks(JsonObject inventory) {
        List<Backpack> packs = new ArrayList<>();
        if (inventory == null || !inventory.has("backpack_contents")) return packs;

        JsonObject contents = inventory.getAsJsonObject("backpack_contents");
        JsonObject icons = inventory.has("backpack_icons") && inventory.get("backpack_icons").isJsonObject()
                ? inventory.getAsJsonObject("backpack_icons") : new JsonObject();

        contents.keySet().stream().sorted((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        }).forEach(key -> {
            List<InventoryDecoder.Slot> pack = InventoryDecoder.decode(contents, key);
            if (pack.isEmpty()) return;

            // hypixel stores the backpack item itself separately, one-slot container per index
            InventoryDecoder.Slot iconSlot = null;
            List<InventoryDecoder.Slot> iconSlots = InventoryDecoder.decode(icons, key);
            for (InventoryDecoder.Slot slot : iconSlots) {
                if (!slot.isEmpty()) {
                    iconSlot = slot;
                    break;
                }
            }
            packs.add(new Backpack(iconSlot, pack));
        });
        return packs;
    }

    private static int magicalPower(SkyblockProfile profile) {
        JsonObject accessories = accessoryBag(profile);
        if (accessories == null) return 0;
        return asInt(accessories.get("highest_magical_power"));
    }

    /** "Magical Power: 1432 (Hurtful)" - the selected power stone when hypixel ships one. */
    private static String powerLine(SkyblockProfile profile) {
        String line = "Magical Power: " + magicalPower(profile);
        JsonObject accessories = accessoryBag(profile);
        if (accessories != null && accessories.has("selected_power")
                && accessories.get("selected_power").isJsonPrimitive()) {
            String power = accessories.get("selected_power").getAsString();
            if (!power.isEmpty()) {
                line += " (" + Character.toUpperCase(power.charAt(0)) + power.substring(1) + ")";
            }
        }
        return line;
    }

    private static JsonObject accessoryBag(SkyblockProfile profile) {
        return profile.getMemberData().has("accessory_bag_storage")
                ? profile.getMemberData().getAsJsonObject("accessory_bag_storage") : null;
    }
}
