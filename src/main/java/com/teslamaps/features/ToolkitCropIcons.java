package com.teslamaps.features;

// Ported from SkyHanni's ToolkitCropReplacer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class ToolkitCropIcons {

    private static final String[][] TOOLS = {
            {"THEORETICAL_HOE_WHEAT", "wheat"}, {"THEORETICAL_HOE_CARROT", "carrot"},
            {"THEORETICAL_HOE_POTATO", "potato"}, {"THEORETICAL_HOE_WARTS", "wart"},
            {"PUMPKIN_DICER", "pumpkin"}, {"MELON_DICER", "melon"}, {"COCO_CHOPPER", "cocoa"},
            {"THEORETICAL_HOE_CANE", "cane"}, {"CACTUS_KNIFE", "cactus"}, {"FUNGI_CUTTER", "mushroom"},
            {"THEORETICAL_HOE_SUNFLOWER", "flower"}, {"THEORETICAL_HOE_WILD_ROSE", "rose"},
    };

    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inToolkit;

    private ToolkitCropIcons() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ToolkitCropIcons").section("Garden").when(() -> cfg().toolkitCropIcons)
                .tick(ToolkitCropIcons::tick)
                .config("Garden", b -> b.toggle("Toolkit Crop Icons", () -> cfg().toolkitCropIcons, v -> cfg().toolkitCropIcons = v)
                        .desc("Farming Toolkit menu shows the crop instead of the tool icon. Name and lore stay."));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == lastScreen) return;
        lastScreen = mc.screen;
        inToolkit = mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Farming Toolkit");
    }

    /** Icon to draw for a slot; the stack itself when nothing changes. */
    public static ItemStack icon(Slot slot, ItemStack stack) {
        if (!inToolkit || stack.isEmpty() || slot.container instanceof Inventory) return stack;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen || feature == null || !feature.active()) return stack;
        int i = slot.index;
        if (!(i >= 10 && i <= 16) && !(i >= 20 && i <= 24)) return stack;
        String crop = crop(ItemUtil.skyblockId(stack));
        if (crop == null) return stack;
        if (crop.equals("flower")) {
            long time = mc.level == null ? 0 : mc.level.getOverworldClockTime() % 24000;
            crop = time >= 12000 ? "moonflower" : "sunflower";
        }
        return ICONS.computeIfAbsent(crop, k -> new ItemStack(item(k)));
    }

    private static String crop(String id) {
        if (id.isEmpty()) return null;
        for (String[] t : TOOLS) if (id.startsWith(t[0])) return t[1];
        return null;
    }

    private static Item item(String crop) {
        return switch (crop) {
            case "wheat" -> Items.WHEAT;
            case "carrot" -> Items.CARROT;
            case "potato" -> Items.POTATO;
            case "wart" -> Items.NETHER_WART;
            case "pumpkin" -> Items.CARVED_PUMPKIN;
            case "melon" -> Items.MELON_SLICE;
            case "cocoa" -> Items.COCOA_BEANS;
            case "cane" -> Items.SUGAR_CANE;
            case "cactus" -> Items.CACTUS;
            case "mushroom" -> Items.RED_MUSHROOM_BLOCK;
            case "moonflower" -> Items.BLUE_ORCHID;
            case "rose" -> Items.ROSE_BUSH;
            default -> Items.SUNFLOWER;
        };
    }
}
