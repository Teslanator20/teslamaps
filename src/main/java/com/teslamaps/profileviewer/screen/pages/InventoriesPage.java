package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Inventories page showing inventory, ender chest, and storage info.
 */
public class InventoriesPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int TEXT_PURPLE = 0xFFAA00AA;

    private int selectedTab = 0;
    private static final String[] TABS = {"Inventory", "Ender Chest", "Backpacks", "Wardrobe", "Accessories"};

    @Override
    public String getTabName() {
        return "Inventories";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.CHEST);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        JsonObject memberData = profile.getMemberData();
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        // Sub-tabs
        int tabX = contentX;
        for (int i = 0; i < TABS.length; i++) {
            int color = (i == selectedTab) ? TEXT_GREEN : TEXT_GRAY;
            ctx.drawTextWithShadow(tr, TABS[i], tabX, lineY, color);
            tabX += tr.getWidth(TABS[i]) + 15;
        }
        lineY += 20;

        // Render selected inventory tab
        switch (selectedTab) {
            case 0 -> renderInventory(ctx, tr, memberData, contentX, lineY, width);
            case 1 -> renderEnderChest(ctx, tr, memberData, contentX, lineY, width);
            case 2 -> renderBackpacks(ctx, tr, memberData, contentX, lineY, width);
            case 3 -> renderWardrobe(ctx, tr, memberData, contentX, lineY, width);
            case 4 -> renderAccessories(ctx, tr, memberData, contentX, lineY, width);
        }
    }

    private void renderInventory(DrawContext ctx, TextRenderer tr, JsonObject memberData,
                                  int x, int lineY, int width) {
        ctx.drawTextWithShadow(tr, "Player Inventory", x, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject inventory = getNestedObject(memberData, "inventory.inv_contents");
        if (inventory == null || !inventory.has("data")) {
            ctx.drawTextWithShadow(tr, "No inventory data available", x, lineY, TEXT_GRAY);
            ctx.drawTextWithShadow(tr, "(Inventory API may be disabled)", x, lineY + 12, TEXT_GRAY);
            return;
        }

        // Show that inventory data exists
        String data = inventory.get("data").getAsString();
        ctx.drawTextWithShadow(tr, "Inventory data: " + (data.length() / 1024) + " KB", x, lineY, TEXT_WHITE);
        lineY += 12;

        // Equipment
        lineY += 8;
        ctx.drawTextWithShadow(tr, "Equipment", x, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject equipment = getNestedObject(memberData, "inventory.equipment_contents");
        if (equipment != null && equipment.has("data")) {
            ctx.drawTextWithShadow(tr, "Equipment data available", x, lineY, TEXT_WHITE);
        } else {
            ctx.drawTextWithShadow(tr, "No equipment data", x, lineY, TEXT_GRAY);
        }
    }

    private void renderEnderChest(DrawContext ctx, TextRenderer tr, JsonObject memberData,
                                   int x, int lineY, int width) {
        ctx.drawTextWithShadow(tr, "Ender Chest", x, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject enderChest = getNestedObject(memberData, "inventory.ender_chest_contents");
        if (enderChest == null || !enderChest.has("data")) {
            ctx.drawTextWithShadow(tr, "No ender chest data available", x, lineY, TEXT_GRAY);
            return;
        }

        String data = enderChest.get("data").getAsString();
        ctx.drawTextWithShadow(tr, "Ender chest data: " + (data.length() / 1024) + " KB", x, lineY, TEXT_WHITE);
    }

    private void renderBackpacks(DrawContext ctx, TextRenderer tr, JsonObject memberData,
                                  int x, int lineY, int width) {
        ctx.drawTextWithShadow(tr, "Backpacks", x, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject backpackContents = getNestedObject(memberData, "inventory.backpack_contents");
        if (backpackContents == null) {
            ctx.drawTextWithShadow(tr, "No backpack data available", x, lineY, TEXT_GRAY);
            return;
        }

        int count = backpackContents.size();
        ctx.drawTextWithShadow(tr, "Backpacks: " + count, x, lineY, TEXT_WHITE);
        lineY += 14;

        int idx = 0;
        for (var entry : backpackContents.entrySet()) {
            if (idx >= 18) {
                ctx.drawTextWithShadow(tr, "... and more", x, lineY, TEXT_GRAY);
                break;
            }
            ctx.drawTextWithShadow(tr, "Backpack " + entry.getKey(), x, lineY, TEXT_WHITE);
            lineY += 12;
            idx++;
        }
    }

    private void renderWardrobe(DrawContext ctx, TextRenderer tr, JsonObject memberData,
                                 int x, int lineY, int width) {
        ctx.drawTextWithShadow(tr, "Wardrobe", x, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject wardrobe = getNestedObject(memberData, "inventory.wardrobe_contents");
        if (wardrobe == null || !wardrobe.has("data")) {
            ctx.drawTextWithShadow(tr, "No wardrobe data available", x, lineY, TEXT_GRAY);
            return;
        }

        String data = wardrobe.get("data").getAsString();
        ctx.drawTextWithShadow(tr, "Wardrobe data: " + (data.length() / 1024) + " KB", x, lineY, TEXT_WHITE);
        lineY += 14;

        // Wardrobe equipped slot
        if (memberData.has("wardrobe_equipped_slot")) {
            int equipped = memberData.get("wardrobe_equipped_slot").getAsInt();
            ctx.drawTextWithShadow(tr, "Equipped slot: " + equipped, x, lineY, TEXT_GOLD);
        }
    }

    private void renderAccessories(DrawContext ctx, TextRenderer tr, JsonObject memberData,
                                    int x, int lineY, int width) {
        ctx.drawTextWithShadow(tr, "Accessory Bag", x, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject talisman = getNestedObject(memberData, "inventory.bag_contents.talisman_bag");
        if (talisman == null || !talisman.has("data")) {
            ctx.drawTextWithShadow(tr, "No accessory bag data available", x, lineY, TEXT_GRAY);
            return;
        }

        String data = talisman.get("data").getAsString();
        ctx.drawTextWithShadow(tr, "Accessory bag data: " + (data.length() / 1024) + " KB", x, lineY, TEXT_WHITE);
        lineY += 14;

        // Accessory bag storage (unlocked slots)
        if (memberData.has("accessory_bag_storage")) {
            JsonObject storage = memberData.getAsJsonObject("accessory_bag_storage");
            if (storage.has("highest_magical_power")) {
                int magicalPower = storage.get("highest_magical_power").getAsInt();
                ctx.drawTextWithShadow(tr, "Highest Magical Power: " + formatNumber(magicalPower),
                        x, lineY, TEXT_PURPLE);
                lineY += 12;
            }
            if (storage.has("bag_upgrades_purchased")) {
                int upgrades = storage.get("bag_upgrades_purchased").getAsInt();
                ctx.drawTextWithShadow(tr, "Bag Upgrades: " + upgrades, x, lineY, TEXT_WHITE);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicked on a sub-tab
        SkyblockProfile profile = getProfile();
        if (profile == null) return false;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int tabX = 15;
        int tabY = 15;

        for (int i = 0; i < TABS.length; i++) {
            int tabWidth = tr.getWidth(TABS[i]);
            if (mouseX >= tabX && mouseX <= tabX + tabWidth &&
                    mouseY >= tabY && mouseY <= tabY + 12) {
                selectedTab = i;
                return true;
            }
            tabX += tabWidth + 15;
        }
        return false;
    }

    private JsonObject getNestedObject(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (String part : parts) {
            if (current == null || !current.has(part)) return null;
            if (!current.get(part).isJsonObject()) return null;
            current = current.getAsJsonObject(part);
        }
        return current;
    }
}
