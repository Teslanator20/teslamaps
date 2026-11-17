package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hunting.ShardData;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.profileviewer.ui.SearchField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Attribute levels from attributes.stacks and the Hunting Box from shards.owned. */
public class HuntingPage extends ProfileViewerPage {

    private record Cell(InventoryDecoder.Slot slot, String search, int level, double progress, String corner) {}

    private static boolean boxMode = false;

    private final SearchField search = new SearchField();

    @Override
    public boolean wantsText() { return search.focused(); }
    private List<Cell> attributes;
    private List<Cell> box;
    private List<Cell> filtered = List.of();
    private String filteredFor;
    private boolean filteredBox;
    private int page;
    private int pageCount = 1;
    private int ownedAttributes;
    private long boxTotal;

    private int lastLeft, lastTop, attrX, attrW, boxX, boxW;

    @Override
    public boolean isAvailable(SkyblockProfiles profiles) {
        return TeslaMapsConfig.get().pvHunting;
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Hunting";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.PRISMARINE_SHARD);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        ShardData.load();
        attributes = null;
        box = null;
        filteredFor = null;
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

        if (!ShardData.loaded()) {
            ctx.text(tr, "Loading shard data...", x, y + 6, NeuStyle.TEXT_GRAY);
            return;
        }
        if (attributes == null) build(profile.getMemberData());

        String attrLabel = "Attributes";
        String boxLabel = "Hunting Box";
        attrX = left + 10;
        attrW = tr.width(attrLabel) + 10;
        boxX = attrX + attrW + 6;
        boxW = tr.width(boxLabel) + 10;
        NeuStyle.window(ctx, attrX, top + 4, attrW, 14);
        NeuStyle.window(ctx, boxX, top + 4, boxW, 14);
        if (boxMode) NeuStyle.slimBox(ctx, boxX, top + 4, boxW, 14);
        else NeuStyle.slimBox(ctx, attrX, top + 4, attrW, 14);
        ctx.text(tr, attrLabel, attrX + 5, top + 7, boxMode ? NeuStyle.TEXT_GRAY : NeuStyle.TEXT_YELLOW);
        ctx.text(tr, boxLabel, boxX + 5, top + 7, boxMode ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);

        String summary = boxMode ? Kit.compact(boxTotal) + " shards, " + box.size() + " types"
                : ownedAttributes + "/" + attributes.size() + " unlocked";
        ctx.text(tr, summary, boxX + boxW + 8, top + 7, NeuStyle.TEXT_GRAY);
        search.draw(ctx, left + NeuStyle.PANEL_WIDTH - 110, top + 4, 100);

        if (filteredFor == null || !filteredFor.equals(search.text()) || filteredBox != boxMode) {
            filteredFor = search.text();
            filteredBox = boxMode;
            filtered = new ArrayList<>();
            for (Cell cell : boxMode ? box : attributes) {
                if (search.isEmpty() || cell.search().contains(search.text().toLowerCase(Locale.ROOT))) filtered.add(cell);
            }
            page = 0;
        }

        if (filtered.isEmpty()) {
            ctx.text(tr, boxMode ? "Empty, or the api is off" : "No match", left + 10, top + 30, NeuStyle.TEXT_GRAY);
            return;
        }

        int areaX = left + 10;
        int areaY = top + 24;
        int areaW = NeuStyle.PANEL_WIDTH - 20;
        int areaH = NeuStyle.PANEL_HEIGHT - 24 - 18;
        int[] fit = ItemGrid.fitGrid(areaW, areaH, filtered.size());
        int size = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = columns * rows;
        pageCount = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);
        int gridX = areaX + (areaW - columns * size) / 2;

        ItemStack hovered = ItemStack.EMPTY;
        int from = page * perPage;
        for (int i = 0; i < perPage && from + i < filtered.size(); i++) {
            Cell cell = filtered.get(from + i);
            int sx = gridX + (i % columns) * size;
            int sy = areaY + (i / columns) * size;
            ItemGrid.slot(ctx, sx, sy, size);
            ItemStack stack = ItemGrid.stackFor(cell.slot());
            ItemGrid.item(ctx, stack, sx, sy, size);
            if (!boxMode && cell.level() == 0) ctx.fill(sx + 1, sy + 1, sx + size - 2, sy + size - 2, 0x90000000);
            if (!boxMode && cell.level() > 0 && cell.level() < 10) {
                int w = (int) ((size - 3) * cell.progress());
                ctx.fill(sx + 1, sy + size - 3, sx + 1 + w, sy + size - 2, NeuStyle.TEXT_GREEN);
            }
            if (cell.corner() != null) {
                ItemGrid.smallCount(ctx, cell.corner(), sx, sy, size,
                        !boxMode && cell.level() >= 10 ? 0xFFFFAA00 : 0xFFFFFFFF);
            }
            if (mouseX >= sx && mouseX < sx + size - 1 && mouseY >= sy && mouseY < sy + size - 1) {
                ctx.fill(sx + 1, sy + 1, sx + size - 2, sy + size - 2, 0x40FFFFFF);
                hovered = stack;
            }
        }

        if (pageCount > 1) {
            String label = "Page " + (page + 1) + " / " + pageCount + "  (scroll)";
            ctx.text(tr, label, left + (NeuStyle.PANEL_WIDTH - tr.width(label)) / 2,
                    top + NeuStyle.PANEL_HEIGHT - 14, NeuStyle.TEXT_GRAY);
        }
        hoverStack(hovered);
    }

    private void build(JsonObject member) {
        Map<String, Integer> syphoned = new HashMap<>();
        JsonObject stacks = object(object(member, "attributes"), "stacks");
        if (stacks != null) {
            for (Map.Entry<String, JsonElement> e : stacks.entrySet()) {
                ShardData.Shard shard = ShardData.bySkyblockId("ATTRIBUTE_SHARD_" + e.getKey().toUpperCase(Locale.ROOT));
                if (shard != null) syphoned.merge(shard.bazaarId(), asInt(e.getValue()), Integer::sum);
            }
        }

        List<Cell> attr = new ArrayList<>();
        List<Cell> unowned = new ArrayList<>();
        ownedAttributes = 0;
        for (ShardData.Shard shard : ShardData.all()) {
            if (!ShardData.isConsumable(shard)) continue;
            int amount = syphoned.getOrDefault(shard.bazaarId(), 0);
            int[] tiers = ShardData.tiers(shard, amount);
            int level = tiers[0];
            int[] costs = ShardData.levelling(shard.rarity());
            double progress = 0;
            if (costs != null && level < costs.length && costs[level] > 0) {
                progress = (costs[level] - tiers[1]) / (double) costs[level];
            }
            List<String> lore = new ArrayList<>();
            lore.add("§7" + Kit.prettify(shard.rarity()) + " " + shard.alignment() + " Shard");
            lore.add("");
            lore.add("§b" + shard.abilityName() + " §f" + level + "§7/10");
            lore.add("§7Syphoned: §f" + amount);
            lore.add(level >= 10 ? "§6Maxed" : "§7Need §e" + tiers[1] + " §7more for the next level, §e" + tiers[2] + " §7for max");
            String name = rarityColor(shard.rarity()) + shard.displayName() + " Shard";
            Cell cell = new Cell(InventoryDecoder.Slot.of(shard.bazaarId(), name, lore, 1),
                    (shard.displayName() + " " + shard.abilityName() + " " + shard.alignment()).toLowerCase(Locale.ROOT),
                    level, progress, level > 0 ? String.valueOf(level) : null);
            if (level > 0) {
                ownedAttributes++;
                attr.add(cell);
            } else {
                unowned.add(cell);
            }
        }
        attr.sort((a, b) -> Integer.compare(b.level(), a.level()));
        attr.addAll(unowned);
        attributes = attr;

        List<Cell> owned = new ArrayList<>();
        boxTotal = 0;
        JsonObject shards = object(member, "shards");
        if (shards != null && shards.has("owned") && shards.get("owned").isJsonArray()) {
            List<long[]> order = new ArrayList<>();
            List<Cell> cells = new ArrayList<>();
            for (JsonElement element : shards.getAsJsonArray("owned")) {
                if (!element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                if (!o.has("type") || !o.get("type").isJsonPrimitive()) continue;
                long amount = asLong(o.get("amount_owned"));
                if (amount <= 0) continue;
                String key = o.get("type").getAsString().toUpperCase(Locale.ROOT);
                if (!key.startsWith("SHARD_")) key = "SHARD_" + key;
                ShardData.Shard shard = ShardData.byBazaarId(key);
                String name = shard == null ? "§f" + Kit.prettify(key.substring(6)) + " Shard"
                        : rarityColor(shard.rarity()) + shard.displayName() + " Shard";
                List<String> lore = new ArrayList<>();
                if (shard != null) lore.add("§7" + Kit.prettify(shard.rarity()) + " " + shard.alignment() + " Shard");
                lore.add("§7Owned: §f" + Kit.dots(amount));
                boxTotal += amount;
                order.add(new long[]{amount, cells.size()});
                cells.add(new Cell(InventoryDecoder.Slot.of(key, name, lore, 1),
                        com.teslamaps.utils.StringUtil.strip(name).toLowerCase(Locale.ROOT), 0, 0, Kit.compact(amount)));
            }
            order.sort((a, b) -> Long.compare(b[0], a[0]));
            for (long[] o : order) owned.add(cells.get((int) o[1]));
        }
        box = owned;
    }

    private static String rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§f";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.mouseClicked(mouseX, mouseY, button)) return true;
        if (mouseY >= lastTop + 4 && mouseY < lastTop + 18) {
            if (mouseX >= attrX && mouseX < attrX + attrW) {
                boxMode = false;
                return true;
            }
            if (mouseX >= boxX && mouseX < boxX + boxW) {
                boxMode = true;
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

    @Override
    public boolean charTyped(char chr) {
        return search.charTyped(chr);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return search.keyPressed(keyCode);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }
}
