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
        EQUIPMENT("Equipment", Items.DIAMOND_CHESTPLATE),
        VAULT("Personal Vault", Items.IRON_DOOR),
        FISHING("Fishing Bag", Items.FISHING_ROD),
        POTIONS("Potion Bag", Items.POTION),
        QUIVER("Quiver", Items.ARROW),
        SACKS("Sacks", Items.BUNDLE);

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
                sideEquipment = decoded(inventory,"equipment_contents");
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
            case SACKS -> {
                slots = List.of();
                title = null;
                drawSacks(ctx, tr, profile, areaX, top + 10, areaW, mouseX, mouseY);
            }
            case EQUIPMENT -> {
                List<InventoryDecoder.Slot> armor = decoded(inventory,"inv_armor");
                List<InventoryDecoder.Slot> equipment = decoded(inventory,"equipment_contents");
                List<InventoryDecoder.Slot> merged = new ArrayList<>(armor);
                merged.addAll(equipment);
                slots = merged;
                title = "Armor and Equipment";
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

        if (section == Section.SACKS) return;

        if (slots.isEmpty()) {
            ctx.text(tr, "Nothing here, or the api is off", areaX, areaY + 20, NeuStyle.TEXT_GRAY);
            return;
        }

        int columns = section == Section.EQUIPMENT ? 4 : 9;
        int count = Math.min(slots.size(), columns * 9);
        int rows = Math.max(1, (count + columns - 1) / columns);

        // the player inventory shows armor and equipment as two extra columns on the right
        boolean sideColumns = section == Section.INVENTORY;
        int sideGap = sideColumns ? 14 : 0;
        int slotSize = ItemGrid.fitSlot(areaW - sideGap, areaH, columns + (sideColumns ? 2 : 0), rows);

        int totalWidth = columns * slotSize + (sideColumns ? sideGap + 2 * slotSize : 0);
        int gridX = areaX + Math.max(0, (areaW - totalWidth) / 2);
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

        ItemStack hovered = ItemGrid.draw(ctx, slots, gridX, gridY, columns, 0, count, mouseX, mouseY, slotSize);

        if (sideColumns) {
            int sideX = gridX + 9 * slotSize + 10;
            ItemStack armorHover = ItemGrid.draw(ctx, reverse(sideArmor), sideX, gridY, 1, 0, 4,
                    mouseX, mouseY, slotSize);
            ItemStack equipHover = ItemGrid.draw(ctx, sideEquipment, sideX + slotSize + 4, gridY, 1, 0, 4,
                    mouseX, mouseY, slotSize);
            if (hovered.isEmpty()) hovered = !armorHover.isEmpty() ? armorHover : equipHover;
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            int px = gridX + (columns * slotSize - tr.width(pager)) / 2;
            int py = Math.max(gridY + rows * slotSize + 8, top + NeuStyle.PANEL_HEIGHT - 24);
            ctx.text(tr, pager, px, py, NeuStyle.TEXT);
            pagerX = px;
            pagerY = py;
            pagerW = tr.width(pager);
        } else {
            pagerW = 0;
        }

        hoverStack(hovered);
    }

    /** Splits a container into the current page and pads it to a full grid. */
    private List<InventoryDecoder.Slot> pageOf(List<InventoryDecoder.Slot> all, int perPage) {
        pageCount = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);
        return subList(all, page * perPage, perPage);
    }

    /** Sacks are plain id to amount, so they get a list instead of a slot grid. */
    private void drawSacks(GuiGraphicsExtractor ctx, Font tr, SkyblockProfile profile,
                           int x, int y, int areaWidth, int mouseX, int mouseY) {
        JsonObject inventory = profile.getMemberData().has("inventory")
                ? profile.getMemberData().getAsJsonObject("inventory") : null;
        if (inventory == null || !inventory.has("sacks_counts")) {
            ctx.text(tr, "No sack data", x, y + 20, NeuStyle.TEXT_GRAY);
            return;
        }

        JsonObject sacks = inventory.getAsJsonObject("sacks_counts");
        List<java.util.Map.Entry<String, com.google.gson.JsonElement>> entries =
                new ArrayList<>(sacks.entrySet());
        entries.removeIf(e -> asInt(e.getValue()) <= 0);
        entries.sort((a, b) -> Integer.compare(asInt(b.getValue()), asInt(a.getValue())));

        // as many columns as the free space takes, so the list is not two columns in a wide window
        int columns = Math.max(2, areaWidth / 105);
        int columnWidth = areaWidth / columns;
        int rows = 14;
        int perPage = columns * rows;

        pageCount = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        ctx.text(tr, "Sacks (" + entries.size() + " types)  -  " + (page + 1) + " / " + pageCount,
                x, y, NeuStyle.TEXT_YELLOW);

        int column = 0;
        int row = 0;
        for (int i = page * perPage; i < Math.min(entries.size(), page * perPage + perPage); i++) {
            var entry = entries.get(i);
            int cx = x + column * columnWidth;
            int cy = y + 14 + row * 11;
            String name = com.teslamaps.utils.SkyblockItems.name(entry.getKey());
            if (name == null || name.isEmpty()) name = capitalize(entry.getKey());

            String amount = formatXp(asInt(entry.getValue()));
            // clip against the real amount width, cutting at a fixed letter count still collided
            name = com.teslamaps.profileviewer.ui.Kit.clip(name, columnWidth - 16 - tr.width(amount));

            ctx.text(tr, name, cx, cy, NeuStyle.TEXT_GRAY);
            ctx.text(tr, amount, cx + columnWidth - 10 - tr.width(amount), cy, NeuStyle.TEXT);

            row++;
            if (row >= rows) {
                row = 0;
                column++;
            }
        }

        String pager = "< " + (page + 1) + " / " + pageCount + " >";
        pagerX = x + (areaWidth - tr.width(pager)) / 2;
        pagerY = y + 14 + rows * 11 + 6;
        pagerW = tr.width(pager);
        ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
    }

    private static String capitalize(String key) {
        String[] words = key.replace('_', ' ').toLowerCase().split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private List<InventoryDecoder.Slot> sideArmor = List.of();
    private List<InventoryDecoder.Slot> sideEquipment = List.of();

    /** Hypixel stores armor boots first; the menu shows the helmet on top. */
    private static List<InventoryDecoder.Slot> reverse(List<InventoryDecoder.Slot> slots) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(slots);
        java.util.Collections.reverse(out);
        return out;
    }

    private int pagerX;
    private int pagerY;
    private int pagerW;

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
