package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.teslamaps.profileviewer.data.MuseumRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The museum as a menu: every donated piece as its real item, paged. */
public class MuseumPage extends ProfileViewerPage {
    private JsonObject museumData;
    private boolean loading = false;

    private List<InventoryDecoder.Slot> donated = List.of();
    private List<InventoryDecoder.Slot> special = List.of();
    private Map<String, List<InventoryDecoder.Slot>> donatedById = Map.of();
    private boolean parsed = false;
    private boolean showSpecial = false;
    // 0 = all donated, then one per museum category
    private int category = 0;
    private final Map<String, List<Cell>> views = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> viewDonated = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> viewTotal = new java.util.concurrent.ConcurrentHashMap<>();

    private record Cell(InventoryDecoder.Slot slot, ItemStack missing, String name, String plain) {
        Cell(InventoryDecoder.Slot slot, ItemStack missing, String name) {
            this(slot, missing, name, com.teslamaps.utils.StringUtil.strip(name));
        }
    }

    private static final Map<String, String> CATEGORY_ICONS = Map.of(
            "combat", "diamond_sword", "farming", "golden_hoe", "mining", "stone_pickaxe",
            "fishing", "fishing_rod", "foraging", "jungle_sapling", "dungeoneering", "wither_skeleton_skull",
            "hunting", "lead");
    private int page = 0;
    private int pageCount = 1;

    private final com.teslamaps.profileviewer.ui.SearchField search = new com.teslamaps.profileviewer.ui.SearchField();

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
        return "Museum";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.GOLD_BLOCK);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        museumData = null;
        donated = List.of();
        special = List.of();
        donatedById = Map.of();
        views.clear();
        viewDonated.clear();
        viewTotal.clear();
        parsed = false;
        page = 0;
        search.clear();
        loadMuseumData();
    }

    @Override
    public void onSelected() {
        if (museumData == null && !loading) loadMuseumData();
    }

    private void loadMuseumData() {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        loading = true;
        HypixelApi.getMuseum(profile.getProfileId()).thenAccept(data -> {
            this.museumData = data;
            this.loading = false;
            parseItems();
        });
    }

    /** Every donated entry carries its own gzipped item nbt, same format as a container. */
    private void parseItems() {
        SkyblockProfile profile = getProfile();
        if (museumData == null || profile == null) return;

        JsonObject member = member();
        if (member == null) return;
        parsed = true;

        List<InventoryDecoder.Slot> donatedItems = new ArrayList<>();
        Map<String, List<InventoryDecoder.Slot>> byId = new HashMap<>();
        if (member.has("items") && member.get("items").isJsonObject()) {
            JsonObject items = member.getAsJsonObject("items");
            for (String key : items.keySet()) {
                JsonObject entry = items.getAsJsonObject(key);
                List<InventoryDecoder.Slot> slots = readSlots(entry);
                donatedItems.addAll(slots);
                byId.put(key, slots);
            }
        }

        List<InventoryDecoder.Slot> specialItems = new ArrayList<>();
        if (member.has("special") && member.get("special").isJsonArray()) {
            for (JsonElement element : member.getAsJsonArray("special")) {
                if (!element.isJsonObject()) continue;
                specialItems.addAll(readSlots(element.getAsJsonObject()));
            }
        }

        donated = donatedItems;
        special = specialItems;
        donatedById = byId;
        views.clear();
    }

    /** Donated first, then what is still missing; built once per category, not per frame. */
    private List<Cell> view(String key) {
        List<Cell> cached = views.get(key);
        if (cached != null) return cached;

        MuseumRepo.Data data = MuseumRepo.get();
        List<String> ids = data == null ? List.of() : data.items().getOrDefault(key, List.of());
        java.util.Set<String> covered = data == null ? java.util.Set.of()
                : MuseumRepo.covered(data, donatedById.keySet());

        List<Cell> have = new ArrayList<>();
        List<Cell> missing = new ArrayList<>();
        int donatedCount = 0;
        for (String id : ids) {
            List<InventoryDecoder.Slot> slots = donatedById.get(id);
            String name = displayName(data, id);
            if (slots != null && !slots.isEmpty()) {
                have.add(new Cell(slots.getFirst(), null, name));
                donatedCount++;
            } else if (covered.contains(id)) {
                // a higher tier sits in the museum, the game counts this one as donated too
                donatedCount++;
            } else {
                missing.add(new Cell(null, missingStack(name, data.armorToId().containsKey(id)), name));
            }
        }
        List<Cell> all = new ArrayList<>(have);
        all.addAll(missing);
        views.put(key, all);
        viewDonated.put(key, donatedCount);
        viewTotal.put(key, ids.size());
        return all;
    }

    private static String displayName(MuseumRepo.Data data, String id) {
        String helmet = data.armorToId().get(id);
        String name = com.teslamaps.utils.SkyblockItems.coloredName(helmet != null ? helmet : id);
        if (name == null) return "§f" + com.teslamaps.profileviewer.ui.Kit.prettify(id);
        if (helmet != null) {
            for (String piece : new String[]{" Helmet", " Hood", " Hat", " Mask", " Cap", " Crown"}) {
                if (name.endsWith(piece)) return name.substring(0, name.length() - piece.length()) + " Armor";
            }
        }
        return name;
    }

    private static ItemStack missingStack(String name, boolean armorSet) {
        ItemStack stack = new ItemStack(Items.DYE.gray());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                com.teslamaps.profileviewer.data.LegacyText.parse(name));
        List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
        if (armorSet) lore.add(com.teslamaps.profileviewer.data.LegacyText.parse("§7Armor Set"));
        lore.add(com.teslamaps.profileviewer.data.LegacyText.parse("§cMissing"));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private MuseumRepo.Data keysFrom;
    private List<String> catKeys = List.of();

    private List<String> categoryKeys() {
        MuseumRepo.Data data = MuseumRepo.get();
        if (data != keysFrom) {
            keysFrom = data;
            catKeys = data == null ? List.of() : new ArrayList<>(data.items().keySet());
        }
        return catKeys;
    }

    private static List<InventoryDecoder.Slot> readSlots(JsonObject entry) {
        if (!entry.has("items") || !entry.get("items").isJsonObject()) return List.of();
        JsonObject items = entry.getAsJsonObject("items");
        if (!items.has("data")) return List.of();

        List<InventoryDecoder.Slot> slots = new ArrayList<>();
        for (InventoryDecoder.Slot slot : InventoryDecoder.decodeBase64(items.get("data").getAsString())) {
            if (!slot.isEmpty()) slots.add(slot);
        }
        return slots;
    }

    private JsonObject member() {
        SkyblockProfile profile = getProfile();
        if (museumData == null || profile == null || !museumData.has("members")) return null;

        JsonObject members = museumData.getAsJsonObject("members");
        String owner = profile.getOwnerUuid() == null ? "" : profile.getOwnerUuid().replace("-", "");
        for (String key : members.keySet()) {
            if (key.replace("-", "").equalsIgnoreCase(owner)) return members.getAsJsonObject(key);
        }
        return members.size() == 1 ? members.getAsJsonObject(members.keySet().iterator().next()) : null;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        if (loading) {
            ctx.text(tr, "Loading museum...", left + 20, top + 20, NeuStyle.TEXT_GRAY);
            return;
        }
        if (museumData == null || museumData.has("error")) {
            ctx.text(tr, "No museum data", left + 20, top + 20, NeuStyle.TEXT_GRAY);
            return;
        }
        // parsed once, not per frame - an empty museum otherwise re-decoded everything each frame
        if (!parsed) parseItems();

        JsonObject member = member();
        long value = member != null ? asLong(member.get("value")) : 0;
        boolean appraisal = member != null && member.has("appraisal") && member.get("appraisal").getAsBoolean();

        ctx.text(tr, "Museum Value: " + formatCoins(value), left + 8, top + 8, NeuStyle.TEXT_GOLD);
        List<String> keys = categoryKeys();
        if (category > keys.size()) category = 0;
        String categoryKey = category > 0 ? keys.get(category - 1) : null;
        List<Cell> cells = categoryKey != null ? view(categoryKey) : null;
        String counts = categoryKey != null
                ? com.teslamaps.profileviewer.ui.Kit.prettify(categoryKey) + ": "
                        + viewDonated.getOrDefault(categoryKey, 0) + " / " + viewTotal.getOrDefault(categoryKey, 0) + " donated"
                : donated.size() + " donated, " + special.size() + " special" + (appraisal ? ", appraised" : "");
        ctx.text(tr, counts, left + NeuStyle.PANEL_WIDTH - 8 - tr.width(counts), top + 8, NeuStyle.TEXT_GRAY);

        // search first, top left, then the two switches like the tabs inside the museum menu
        String donatedLabel = "Donated";
        String specialLabel = "Special";
        int donatedX = left + 8 + 110 + 12;
        int specialX = donatedX + tr.width(donatedLabel) + 16;
        int switchY = top + 20;

        boolean allView = !showSpecial && categoryKey == null;
        if (allView) NeuStyle.slimBox(ctx, donatedX - 4, switchY - 3, tr.width(donatedLabel) + 8, 13);
        else if (showSpecial) NeuStyle.slimBox(ctx, specialX - 4, switchY - 3, tr.width(specialLabel) + 8, 13);
        ctx.text(tr, donatedLabel, donatedX, switchY, allView ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        ctx.text(tr, specialLabel, specialX, switchY, showSpecial ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);

        // one icon per museum category, donated and missing side by side
        chipX = specialX + tr.width(specialLabel) + 14;
        chipY = switchY - 4;
        for (int i = 0; i < keys.size(); i++) {
            int cx = chipX + i * 18;
            boolean selected = !showSpecial && category == i + 1;
            if (selected) NeuStyle.slimBox(ctx, cx - 1, chipY - 1, 18, 18);
            ctx.item(com.teslamaps.profileviewer.ui.Kit.icon(CATEGORY_ICONS.getOrDefault(keys.get(i), "paper")), cx, chipY);
            if (mouseX >= cx && mouseX < cx + 16 && mouseY >= chipY && mouseY < chipY + 16 && parent != null) {
                parent.setHoveredLines(List.of(
                        com.teslamaps.profileviewer.data.LegacyText.parse("§e" + com.teslamaps.profileviewer.ui.Kit.prettify(keys.get(i))),
                        com.teslamaps.profileviewer.data.LegacyText.parse("§7Donated and missing items")));
            }
        }
        chipCount = keys.size();

        donatedSwitchX = donatedX;
        donatedSwitchW = tr.width(donatedLabel);
        specialSwitchX = specialX;
        specialSwitchW = tr.width(specialLabel);
        switchRowY = switchY;

        search.draw(ctx, left + 8, switchY - 3, 110);

        if (cells != null && !showSpecial) {
            drawCells(ctx, tr, cells, left, top, mouseX, mouseY);
            return;
        }

        List<InventoryDecoder.Slot> shown = showSpecial ? special : donated;
        if (!search.isEmpty()) {
            List<InventoryDecoder.Slot> filtered = new ArrayList<>();
            for (InventoryDecoder.Slot slot : shown) {
                String name = slot.name != null ? com.teslamaps.utils.StringUtil.strip(slot.name) : slot.skyblockId;
                if (search.matches(name)) filtered.add(slot);
            }
            shown = filtered;
        }

        int areaW = NeuStyle.PANEL_WIDTH - 16;
        int areaY = top + 36;
        int areaH = NeuStyle.PANEL_HEIGHT - 36 - 14;

        // a handful of search hits kept the browse size instead of growing to fill the window
        int[] fit = search.isEmpty() ? ItemGrid.fitGrid(areaW, areaH, shown.size())
                : new int[]{ItemGrid.SLOT, Math.max(1, areaW / ItemGrid.SLOT),
                        Math.max(1, areaH / ItemGrid.SLOT)};
        int slotSize = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = Math.max(1, columns * rows);

        pageCount = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        // anchored top left - centering a handful of search hits parked them mid-window
        int gridX = left + 8;
        int gridY = areaY;

        ItemStack hovered = ItemGrid.draw(ctx, shown, gridX, gridY, columns, page * perPage,
                Math.min(perPage, Math.max(0, shown.size() - page * perPage)), mouseX, mouseY, slotSize);

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

    private void drawCells(GuiGraphicsExtractor ctx, Font tr, List<Cell> cells, int left, int top,
                           int mouseX, int mouseY) {
        List<Cell> shown = cells;
        if (!search.isEmpty()) {
            shown = new ArrayList<>();
            for (Cell cell : cells) {
                if (search.matches(cell.plain())) shown.add(cell);
            }
        }

        int areaW = NeuStyle.PANEL_WIDTH - 16;
        int areaY = top + 36;
        int areaH = NeuStyle.PANEL_HEIGHT - 36 - 14;
        int[] fit = search.isEmpty() ? ItemGrid.fitGrid(areaW, areaH, shown.size())
                : new int[]{ItemGrid.SLOT, Math.max(1, areaW / ItemGrid.SLOT), Math.max(1, areaH / ItemGrid.SLOT)};
        int slotSize = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = Math.max(1, columns * rows);
        pageCount = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        int gridX = left + 8;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= shown.size()) break;
            int sx = gridX + (i % columns) * slotSize;
            int sy = areaY + (i / columns) * slotSize;
            ItemGrid.slot(ctx, sx, sy, slotSize);

            Cell cell = shown.get(index);
            ItemStack stack = cell.slot() != null ? ItemGrid.stackFor(cell.slot()) : cell.missing();
            if (!stack.isEmpty()) ItemGrid.item(ctx, stack, sx, sy, slotSize);
            if (cell.slot() == null) ctx.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0x30FF0000);

            if (mouseX >= sx && mouseX < sx + slotSize - 1 && mouseY >= sy && mouseY < sy + slotSize - 1) {
                ctx.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0x40FFFFFF);
                hovered = stack;
            }
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = left + (NeuStyle.PANEL_WIDTH - tr.width(pager)) / 2;
            pagerY = areaY + rows * slotSize + 4;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }
        hoverStack(hovered);
    }

    private int chipX;
    private int chipY;
    private int chipCount;
    private int donatedSwitchX;
    private int donatedSwitchW;
    private int specialSwitchX;
    private int specialSwitchW;
    private int switchRowY;

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
        if (mouseY >= switchRowY - 3 && mouseY <= switchRowY + 10) {
            if (mouseX >= donatedSwitchX - 4 && mouseX <= donatedSwitchX + donatedSwitchW + 4) {
                showSpecial = false;
                category = 0;
                page = 0;
                return true;
            }
            if (mouseX >= specialSwitchX - 4 && mouseX <= specialSwitchX + specialSwitchW + 4) {
                showSpecial = true;
                page = 0;
                return true;
            }
        }
        if (mouseY >= chipY && mouseY < chipY + 16 && mouseX >= chipX && mouseX < chipX + chipCount * 18) {
            int index = (int) ((mouseX - chipX) / 18);
            if (index >= 0 && index < chipCount) {
                category = index + 1;
                showSpecial = false;
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
