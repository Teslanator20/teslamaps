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

import java.util.ArrayList;
import java.util.List;

/** The museum as a menu: every donated piece as its real item, paged. */
public class MuseumPage extends ProfileViewerPage {
    private JsonObject museumData;
    private boolean loading = false;

    private List<InventoryDecoder.Slot> donated = List.of();
    private List<InventoryDecoder.Slot> special = List.of();
    private boolean showSpecial = false;
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
        page = 0;
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

        List<InventoryDecoder.Slot> donatedItems = new ArrayList<>();
        if (member.has("items") && member.get("items").isJsonObject()) {
            JsonObject items = member.getAsJsonObject("items");
            for (String key : items.keySet()) {
                JsonObject entry = items.getAsJsonObject(key);
                donatedItems.addAll(readSlots(entry));
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
        if (donated.isEmpty() && special.isEmpty()) parseItems();

        JsonObject member = member();
        long value = member != null ? asLong(member.get("value")) : 0;
        boolean appraisal = member != null && member.has("appraisal") && member.get("appraisal").getAsBoolean();

        ctx.text(tr, "Museum Value: " + formatCoins(value), left + 8, top + 8, NeuStyle.TEXT_GOLD);
        String counts = donated.size() + " donated, " + special.size() + " special"
                + (appraisal ? ", appraised" : "");
        ctx.text(tr, counts, left + NeuStyle.PANEL_WIDTH - 8 - tr.width(counts), top + 8, NeuStyle.TEXT_GRAY);

        // two switches, like the tabs inside the museum menu
        String donatedLabel = "Donated";
        String specialLabel = "Special";
        int donatedX = left + 8;
        int specialX = donatedX + tr.width(donatedLabel) + 16;
        int switchY = top + 20;

        if (!showSpecial) NeuStyle.slimBox(ctx, donatedX - 4, switchY - 3, tr.width(donatedLabel) + 8, 13);
        else NeuStyle.slimBox(ctx, specialX - 4, switchY - 3, tr.width(specialLabel) + 8, 13);
        ctx.text(tr, donatedLabel, donatedX, switchY, showSpecial ? NeuStyle.TEXT_GRAY : NeuStyle.TEXT_YELLOW);
        ctx.text(tr, specialLabel, specialX, switchY, showSpecial ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);

        donatedSwitchX = donatedX;
        donatedSwitchW = tr.width(donatedLabel);
        specialSwitchX = specialX;
        specialSwitchW = tr.width(specialLabel);
        switchRowY = switchY;

        List<InventoryDecoder.Slot> shown = showSpecial ? special : donated;

        int areaW = NeuStyle.PANEL_WIDTH - 16;
        int areaY = top + 36;
        int areaH = NeuStyle.PANEL_HEIGHT - 36 - 14;

        int[] fit = ItemGrid.fitGrid(areaW, areaH, shown.size());
        int slotSize = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = Math.max(1, columns * rows);

        pageCount = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        int gridX = left + (NeuStyle.PANEL_WIDTH - columns * slotSize) / 2;
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

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

    private int donatedSwitchX;
    private int donatedSwitchW;
    private int specialSwitchX;
    private int specialSwitchW;
    private int switchRowY;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= switchRowY - 3 && mouseY <= switchRowY + 10) {
            if (mouseX >= donatedSwitchX - 4 && mouseX <= donatedSwitchX + donatedSwitchW + 4) {
                showSpecial = false;
                page = 0;
                return true;
            }
            if (mouseX >= specialSwitchX - 4 && mouseX <= specialSwitchX + specialSwitchW + 4) {
                showSpecial = true;
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
