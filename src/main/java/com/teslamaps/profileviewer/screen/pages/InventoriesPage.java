package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.ui.Kit;
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
        WARDROBE("Wardrobe", Items.LEATHER_CHESTPLATE),
        ENDER_CHEST("Ender Chest", Items.ENDER_CHEST),
        BACKPACKS("Backpacks", Items.BUNDLE),
        ACCESSORIES("Accessories", Items.GOLDEN_APPLE),
        VAULT("Personal Vault", Items.IRON_DOOR),
        FISHING("Fishing Bag", Items.FISHING_ROD),
        POTIONS("Potion Bag", Items.POTION),
        QUIVER("Quiver", Items.ARROW),
        CANDY("Candy Bag", Items.SUGAR),
        CARNIVAL("Carnival Mask Bag", Items.CARVED_PUMPKIN),
        MISC("Misc", Items.BUNDLE),
        SACKS("Sacks", Items.LEATHER);

        final String label;
        final net.minecraft.world.item.Item icon;

        Section(String label, net.minecraft.world.item.Item icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private Section section = Section.INVENTORY;
    private Section miscSub = Section.POTIONS;

    private static final List<Section> MISC_SUBS = List.of(Section.POTIONS, Section.QUIVER, Section.FISHING,
            Section.CANDY, Section.CARNIVAL);
    private static final String[] MISC_LABELS = {"Potion Bag", "Quiver", "Fishing Bag", "Candy Bag", "Mask Bag"};

    private static List<Section> rail() {
        return visibleSections();
    }

    private final List<int[]> miscRects = new ArrayList<>();
    private List<String> quiverSummary;
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
        wardrobeCache = null;
        sackCache = null;
        if (com.teslamaps.config.TeslaMapsConfig.get().pvSacks) com.teslamaps.features.croesus.PriceManager.ensureFresh();
        SkyblockProfile profile = getProfile();
        JsonObject bag = profile == null ? null : accessoryBag(profile);
        powerTooltip = profile == null ? List.of() : com.teslamaps.profileviewer.data.PowerStats.tooltip(bag);
        int points = com.teslamaps.profileviewer.data.PowerStats.tuningPoints(bag);
        tuningLabel = points > 0 ? "Tuning: " + points + " pts" : null;
        powerText = profile == null ? "" : powerLine(profile);
        quiverSummary = null;
    }

    /** shared_inventory lives next to inventory, not inside it. */
    private List<InventoryDecoder.Slot> decodedShared(SkyblockProfile profile, String key) {
        JsonObject member = profile.getMemberData();
        JsonObject shared = member.has("shared_inventory") && member.get("shared_inventory").isJsonObject()
                ? member.getAsJsonObject("shared_inventory") : null;
        if (shared == null) return List.of();
        return decodeCache.computeIfAbsent("shared:" + key, k -> InventoryDecoder.decode(shared, key));
    }

    /** Arrow types with their totals, the way NEU's quiver info lists them. */
    private List<String> quiverSummary(List<InventoryDecoder.Slot> quiver) {
        if (quiverSummary != null) return quiverSummary;
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (InventoryDecoder.Slot slot : quiver) {
            if (slot.isEmpty()) continue;
            String name = slot.name != null ? slot.name : slot.skyblockId;
            counts.merge(name, slot.count, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        lines.add("§eQuiver");
        int total = 0;
        for (var entry : counts.entrySet()) {
            lines.add(entry.getKey() + " §7x§f" + com.teslamaps.profileviewer.ui.Kit.dots(entry.getValue()));
            total += entry.getValue();
        }
        lines.add("§7Total: §f" + com.teslamaps.profileviewer.ui.Kit.dots(total));
        quiverSummary = lines;
        return lines;
    }

    private static int itemCount(List<InventoryDecoder.Slot> slots) {
        int total = 0;
        for (InventoryDecoder.Slot slot : slots) if (!slot.isEmpty()) total += slot.count;
        return total;
    }

    private static List<Section> visibleSections() {
        boolean sacks = com.teslamaps.config.TeslaMapsConfig.get().pvSacks;
        if (com.teslamaps.profileviewer.PvExtras.miscBags()) return sacks ? RAIL_MISC_SACKS : RAIL_MISC;
        return sacks ? RAIL_SACKS : RAIL;
    }

    private static List<Section> railOf(boolean misc, boolean sacks) {
        List<Section> out = new ArrayList<>();
        for (Section s : Section.values()) {
            if (s == Section.SACKS && !sacks) continue;
            boolean miscOnly = s == Section.CANDY || s == Section.CARNIVAL || s == Section.MISC;
            boolean grouped = s == Section.FISHING || s == Section.POTIONS || s == Section.QUIVER || s == Section.CANDY || s == Section.CARNIVAL;
            if (misc ? grouped : miscOnly) continue;
            out.add(s);
        }
        return List.copyOf(out);
    }

    private static final List<Section> RAIL = railOf(false, false);
    private static final List<Section> RAIL_SACKS = railOf(false, true);
    private static final List<Section> RAIL_MISC = railOf(true, false);
    private static final List<Section> RAIL_MISC_SACKS = railOf(true, true);

    private List<net.minecraft.network.chat.Component> powerTooltip = List.of();
    private String tuningLabel;
    private String powerText = "";

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
        List<Section> rail = rail();
        if (!rail.contains(section)) section = MISC_SUBS.contains(section) && rail.contains(Section.MISC) ? Section.MISC : Section.INVENTORY;
        for (int i = 0; i < rail.size(); i++) {
            Section s = rail.get(i);
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
        miscRects.clear();
        Section shownSection = section == Section.MISC ? miscSub : section;

        switch (shownSection) {
            case INVENTORY -> {
                slots = reorderPlayerInventory(decoded(inventory,"inv_contents"));
                sideArmor = decoded(inventory,"inv_armor");
                sideEquipment = decoded(inventory,"equipment_contents");
            }
            case WARDROBE -> {
                if (wardrobeCache == null) wardrobeCache = wardrobe(profile.getMemberData(), inventory);
                pageCount = Math.max(1, wardrobeCache.size());
                page = Math.min(page, pageCount - 1);
                slots = wardrobeCache.isEmpty() ? List.of() : wardrobeCache.get(page).slots();
                title = wardrobeCache.isEmpty() ? "Wardrobe" : wardrobeCache.get(page).title();
            }
            case SACKS -> {
                int prices = com.teslamaps.features.croesus.PriceManager.version();
                if (sackCache == null || sackPrices != prices) {
                    sackPrices = prices;
                    sackCache = sacks(inventory);
                }
                slots = pageOf(sackCache, 45);
                title = sackTitle;
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
                ctx.text(tr, powerText, areaX, top + 10, NeuStyle.TEXT_LIGHT_PURPLE);
                if (extra(com.teslamaps.config.TeslaMapsConfig.get().pvPowerStats)) {
                    if (tuningLabel != null) {
                        ctx.text(tr, tuningLabel, areaX + areaW - tr.width(tuningLabel), top + 10, NeuStyle.TEXT_GRAY);
                    }
                    boolean overPower = mouseY >= top + 8 && mouseY < top + 19 && mouseX >= areaX
                            && mouseX < areaX + Math.max(tr.width(powerText), areaW);
                    if (overPower && parent != null) parent.setHoveredLines(powerTooltip);
                }
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
            case CANDY -> {
                slots = pageOf(decodedShared(profile, "candy_inventory_contents"), 45);
                title = "Candy Bag";
            }
            case CARNIVAL -> {
                slots = pageOf(decodedShared(profile, "carnival_mask_inventory_contents"), 45);
                title = "Carnival Mask Bag";
            }
            default -> slots = List.of();
        }

        if (section == Section.MISC) {
            int cx = areaX;
            for (int i = 0; i < MISC_SUBS.size(); i++) {
                String label = MISC_LABELS[i];
                int w = tr.width(label);
                boolean active = MISC_SUBS.get(i) == miscSub;
                boolean over = mouseX >= cx - 2 && mouseX < cx + w + 2 && mouseY >= top + 7 && mouseY < top + 19;
                ctx.text(tr, label, cx, top + 10,
                        active ? NeuStyle.TEXT_YELLOW : over ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);
                miscRects.add(new int[]{cx - 2, top + 7, w + 4, 12, i});
                if (over && parent != null) {
                    List<String> lines = switch (MISC_SUBS.get(i)) {
                        case QUIVER -> quiverSummary(decoded(inventory, "bag_contents.quiver"));
                        case CANDY -> List.of("§eCandy Bag", "§7Candy: §f"
                                + com.teslamaps.profileviewer.ui.Kit.dots(itemCount(decodedShared(profile, "candy_inventory_contents"))));
                        default -> null;
                    };
                    if (lines != null) {
                        List<net.minecraft.network.chat.Component> parsed = new ArrayList<>(lines.size());
                        for (String line : lines) parsed.add(com.teslamaps.profileviewer.data.LegacyText.parse(line));
                        parent.setHoveredLines(parsed);
                    }
                }
                cx += w + 10;
            }
        } else if (title != null) {
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

        // the player inventory shows armor and equipment as extra columns on the right
        boolean sideColumns = section == Section.INVENTORY;
        int sideGap = sideColumns ? 16 : 0;
        int slotSize = ItemGrid.fitSlot(areaW - sideGap, areaH, columns + (sideColumns ? 2 : 0), rows);

        // horizontally every section anchors like the player inventory (nine columns plus the
        // armor and equipment columns), so the grid keeps one left edge; vertically it centers as before
        int refWidth = 9 * slotSize + 16 + 2 * slotSize;
        int gridX = areaX + Math.max(0, (areaW - refWidth) / 2);
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

        ItemStack hovered = ItemGrid.draw(ctx, slots, gridX, gridY, columns, 0, count, mouseX, mouseY, slotSize);

        if (sideColumns) {
            int sideX = gridX + 9 * slotSize + 10;
            ItemStack armorHover = ItemGrid.draw(ctx, reverse(sideArmor), sideX, gridY, 1, 0, 4,
                    mouseX, mouseY, slotSize);
            if (hovered.isEmpty()) hovered = armorHover;
            ItemStack equipHover = ItemGrid.draw(ctx, reverse(sideEquipment), sideX + slotSize + 2, gridY, 1, 0, 4,
                    mouseX, mouseY, slotSize);
            if (hovered.isEmpty()) hovered = equipHover;
        }

        if (section == Section.WARDROBE && wardrobeCache != null && !wardrobeCache.isEmpty()) {
            int column = wardrobeCache.get(page).equipped();
            if (column >= 0) {
                int ex = gridX + column * slotSize;
                ctx.fill(ex, gridY - 3, ex + slotSize - 1, gridY - 1, NeuStyle.TEXT_GREEN);
            }
        }

        if (section == Section.SACKS) {
            for (int i = 0; i < count; i++) {
                InventoryDecoder.Slot slot = slots.get(i);
                if (slot.isEmpty()) continue;
                String amount = sackAmounts.get(slot);
                if (amount != null) ItemGrid.smallCount(ctx, amount, gridX + (i % columns) * slotSize,
                        gridY + (i / columns) * slotSize, slotSize, 0xFFFFFFFF);
            }
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
                    : section == Section.WARDROBE && wardrobeCache != null && hoveredPage < wardrobeCache.size()
                    ? wardrobeCache.get(hoveredPage).title()
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
        if (section == Section.WARDROBE) {
            return wardrobeCache != null && index < wardrobeCache.size()
                    ? wardrobeCache.get(index).slots() : List.of();
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
    private List<InventoryDecoder.Slot> sideEquipment = List.of();

    /** Hypixel stores armor boots first; the menu shows the helmet on top. */
    private static List<InventoryDecoder.Slot> reverse(List<InventoryDecoder.Slot> slots) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(slots);
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Section> rail = rail();
        for (int i = 0; i < rail.size(); i++) {
            int bx = lastLeft + 6;
            int by = lastTop + 6 + i * 19;
            if (mouseX >= bx && mouseX < bx + 100 && mouseY >= by && mouseY < by + 18) {
                section = rail.get(i);
                page = 0;
                return true;
            }
        }

        for (int[] rect : miscRects) {
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                miscSub = MISC_SUBS.get(rect[4]);
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

    private record WardrobePage(String title, List<InventoryDecoder.Slot> slots, int equipped) {}

    private List<WardrobePage> wardrobeCache;
    private List<InventoryDecoder.Slot> sackCache;
    private final java.util.Map<InventoryDecoder.Slot, String> sackAmounts = new java.util.IdentityHashMap<>();
    private String sackTitle = "Sacks";
    private int sackPrices = -1;

    private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
    private static final String[] EQUIPMENT_PIECES = {"EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4"};

    /** Armor sets, then equipment sets, nine per page, one column per set. Empty pages are left out. */
    private List<WardrobePage> wardrobe(JsonObject member, JsonObject inventory) {
        List<WardrobePage> out = new ArrayList<>();
        List<InventoryDecoder.Slot> armor = reverse(decoded(inventory, "inv_armor"));
        List<InventoryDecoder.Slot> equipment = reverse(decoded(inventory, "equipment_contents"));
        JsonObject loadout = child(member, "loadout");

        List<InventoryDecoder.Slot> contents = decoded(inventory, "wardrobe_contents");
        int equipped = inventory != null ? asInt(inventory.get("wardrobe_equipped_slot")) : 0;
        if (!contents.isEmpty()) {
            List<InventoryDecoder.Slot> all = new ArrayList<>(contents);
            int pages = Math.max(1, (all.size() + 35) / 36);
            if (equipped > 0) pages = Math.max(pages, (equipped + 8) / 9);
            while (all.size() < pages * 36) all.add(InventoryDecoder.Slot.EMPTY);
            // the worn set is missing from the wardrobe data, it sits in inv_armor
            if (equipped > 0) {
                int base = ((equipped - 1) / 9) * 36 + (equipped - 1) % 9;
                for (int i = 0; i < 4 && i < armor.size(); i++) all.set(base + i * 9, armor.get(i));
            }
            addPages(out, "Wardrobe", all, pages, equipped);
        } else {
            JsonObject sets = child(loadout, "armor");
            if (sets != null) {
                int worn = asInt(sets.get("equipped_set"));
                addPages(out, "Wardrobe", loadoutSlots(sets, ARMOR_PIECES, worn, armor), pagesOf(sets), worn);
            }
        }

        JsonObject sets = child(loadout, "equipment");
        if (sets != null) {
            int worn = asInt(sets.get("equipped_set"));
            addPages(out, "Equipment Sets", loadoutSlots(sets, EQUIPMENT_PIECES, worn, equipment), pagesOf(sets), worn);
        }
        return out;
    }

    private static JsonObject child(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static int pagesOf(JsonObject sets) {
        int max = 0;
        for (String key : sets.keySet()) {
            try {
                max = Math.max(max, Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return (max + 8) / 9;
    }

    /** loadout.armor / loadout.equipment: set id -> piece -> one item container. */
    private static List<InventoryDecoder.Slot> loadoutSlots(JsonObject sets, String[] pieces, int worn,
                                                            List<InventoryDecoder.Slot> wornItems) {
        int pages = pagesOf(sets);
        List<InventoryDecoder.Slot> all = new ArrayList<>(pages * 36);
        for (int i = 0; i < pages * 36; i++) all.add(InventoryDecoder.Slot.EMPTY);
        for (int page = 0; page < pages; page++) {
            for (int column = 0; column < 9; column++) {
                int id = page * 9 + column + 1;
                JsonObject set = child(sets, String.valueOf(id));
                for (int row = 0; row < pieces.length; row++) {
                    InventoryDecoder.Slot slot = InventoryDecoder.Slot.EMPTY;
                    if (set != null) {
                        for (InventoryDecoder.Slot s : InventoryDecoder.decode(set, pieces[row])) {
                            if (!s.isEmpty()) {
                                slot = s;
                                break;
                            }
                        }
                    }
                    if (slot.isEmpty() && id == worn && row < wornItems.size()) slot = wornItems.get(row);
                    all.set(page * 36 + row * 9 + column, slot);
                }
            }
        }
        return all;
    }

    private static void addPages(List<WardrobePage> out, String name, List<InventoryDecoder.Slot> all,
                                 int pages, int equipped) {
        List<WardrobePage> kept = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            List<InventoryDecoder.Slot> slots = all.subList(page * 36, Math.min(all.size(), page * 36 + 36));
            if (slots.stream().allMatch(InventoryDecoder.Slot::isEmpty)) continue;
            int column = equipped > 0 && (equipped - 1) / 9 == page ? (equipped - 1) % 9 : -1;
            kept.add(new WardrobePage(null, List.copyOf(slots), column));
        }
        for (int i = 0; i < kept.size(); i++) {
            WardrobePage p = kept.get(i);
            String title = kept.size() > 1 ? name + " " + (i + 1) + " / " + kept.size() : name;
            out.add(new WardrobePage(title, p.slots(), p.equipped()));
        }
    }

    /** inventory.sacks_counts as item slots, most valuable first. */
    private List<InventoryDecoder.Slot> sacks(JsonObject inventory) {
        sackAmounts.clear();
        JsonObject counts = child(inventory, "sacks_counts");
        if (counts == null) {
            sackTitle = "Sacks";
            return List.of();
        }

        record Entry(String id, long amount, double value) {}
        List<Entry> entries = new ArrayList<>();
        long items = 0;
        double total = 0;
        for (var e : counts.entrySet()) {
            long amount = asLong(e.getValue());
            if (amount <= 0) continue;
            double value = amount * unitPrice(e.getKey());
            entries.add(new Entry(e.getKey(), amount, value));
            items += amount;
            total += value;
        }
        entries.sort((a, b) -> a.value() != b.value() ? Double.compare(b.value(), a.value())
                : Long.compare(b.amount(), a.amount()));

        List<InventoryDecoder.Slot> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            String name = com.teslamaps.utils.SkyblockItems.coloredName(e.id());
            if (name == null) name = "§f" + Kit.prettify(e.id());
            List<String> lore = new ArrayList<>(2);
            lore.add("§7Amount: §f" + Kit.dots(e.amount()));
            if (e.value() > 0) lore.add("§7Value: §6" + Kit.compact((long) e.value()) + " coins");
            InventoryDecoder.Slot slot = InventoryDecoder.Slot.of(e.id(), name, lore, 1);
            sackAmounts.put(slot, Kit.compact(e.amount()));
            out.add(slot);
        }
        sackTitle = "Sacks - " + Kit.compact(items) + " items, " + Kit.compact((long) total) + " coins";
        return out;
    }

    /** Insta-sell, NPC/BIN as fallback. */
    static double unitPrice(String id) {
        Double sell = com.teslamaps.features.croesus.PriceManager.bazaarSellOrNull(id);
        if (sell != null && sell > 0) return sell;
        return com.teslamaps.features.croesus.PriceManager.getPrice(id);
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
