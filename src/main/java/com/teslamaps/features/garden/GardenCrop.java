package com.teslamaps.features.garden;

// Crop table after SkyHanni's CropType.kt
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public enum GardenCrop {
    WHEAT("Wheat", "THEORETICAL_HOE_WHEAT", 1.0, 93, "WHEAT", "ENCHANTED_WHEAT", "ENCHANTED_HAY_BALE", false),
    CARROT("Carrot", "THEORETICAL_HOE_CARROT", 3.0, 93, "CARROT_ITEM", "ENCHANTED_CARROT", null, true),
    POTATO("Potato", "THEORETICAL_HOE_POTATO", 3.0, 93, "POTATO_ITEM", "ENCHANTED_POTATO", "ENCHANTED_BAKED_POTATO", true),
    NETHER_WART("Nether Wart", "THEORETICAL_HOE_WARTS", 2.5, 93, "NETHER_STALK", "ENCHANTED_NETHER_STALK", "MUTANT_NETHER_STALK", true),
    PUMPKIN("Pumpkin", "PUMPKIN_DICER", 1.0, 155, "PUMPKIN", "ENCHANTED_PUMPKIN", "POLISHED_PUMPKIN", false),
    MELON("Melon Slice", "MELON_DICER", 5.0, 155, "MELON", "ENCHANTED_MELON", "ENCHANTED_MELON_BLOCK", false),
    COCOA_BEANS("Cocoa Beans", "COCO_CHOPPER", 3.0, 155, "INK_SACK:3", "ENCHANTED_COCOA", null, true),
    SUGAR_CANE("Sugar Cane", "THEORETICAL_HOE_CANE", 2.0, 328, "SUGAR_CANE", "ENCHANTED_SUGAR", "ENCHANTED_SUGAR_CANE", false),
    CACTUS("Cactus", "CACTUS_KNIFE", 2.0, 464, "CACTUS", "ENCHANTED_CACTUS_GREEN", "ENCHANTED_CACTUS", false),
    MUSHROOM("Mushroom", "FUNGI_CUTTER", 1.0, 233, "RED_MUSHROOM", "ENCHANTED_RED_MUSHROOM", null, false),
    SUNFLOWER("Sunflower", "THEORETICAL_HOE_SUNFLOWER", 2.0, 328, "DOUBLE_PLANT", "ENCHANTED_SUNFLOWER", "COMPACTED_SUNFLOWER", true),
    MOONFLOWER("Moonflower", "THEORETICAL_HOE_SUNFLOWER", 2.0, 328, "MOONFLOWER", "ENCHANTED_MOONFLOWER", "COMPACTED_MOONFLOWER", true),
    WILD_ROSE("Wild Rose", "THEORETICAL_HOE_WILD_ROSE", 2.0, 328, "WILD_ROSE", "ENCHANTED_WILD_ROSE", "COMPACTED_WILD_ROSE", true);

    public static final GardenCrop[] ALL = values();

    public final String cropName;
    public final String toolPrefix;
    public final double baseDrops;
    public final int defaultSpeed;
    /** Bazaar ids with 1, 160 and 25600 raw crops per item; the last may be null. */
    public final String[] items;
    public final boolean replenish;
    public final String key;

    GardenCrop(String name, String tool, double baseDrops, int speed, String raw, String ench, String block, boolean replenish) {
        this.cropName = name;
        this.toolPrefix = tool;
        this.baseDrops = baseDrops;
        this.defaultSpeed = speed;
        this.items = new String[]{raw, ench, block};
        this.replenish = replenish;
        this.key = name().toLowerCase(Locale.ROOT);
    }

    public static final int[] ITEM_AMOUNTS = {1, 160, 25600};

    /** Sugar cane and cactus break two blocks per swing. */
    public int multiplier() {
        return this == SUGAR_CANE || this == CACTUS ? 2 : 1;
    }

    public static GardenCrop byName(String name) {
        if (name == null) return null;
        String n = name.trim();
        if (n.equalsIgnoreCase("Melon")) return MELON;
        if (n.equalsIgnoreCase("Red Mushroom") || n.equalsIgnoreCase("Brown Mushroom")) return MUSHROOM;
        if (n.equalsIgnoreCase("Seeds")) return WHEAT;
        for (GardenCrop c : ALL) if (c.cropName.equalsIgnoreCase(n)) return c;
        return null;
    }

    public static GardenCrop byKey(String key) {
        if (key == null) return null;
        for (GardenCrop c : ALL) if (c.key.equals(key)) return c;
        return null;
    }

    /** Crop of a specialised farming tool, null for anything else. */
    public static GardenCrop byTool(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("THEORETICAL_HOE_SUNFLOWER")) return timeFlower();
        for (GardenCrop c : ALL) if (id.startsWith(c.toolPrefix)) return c;
        return null;
    }

    public static GardenCrop byBlock(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.WHEAT) return WHEAT;
        if (b == Blocks.CARROTS) return CARROT;
        if (b == Blocks.POTATOES) return POTATO;
        if (b == Blocks.CARVED_PUMPKIN || b == Blocks.PUMPKIN) return PUMPKIN;
        if (b == Blocks.SUGAR_CANE) return SUGAR_CANE;
        if (b == Blocks.MELON) return MELON;
        if (b == Blocks.CACTUS) return CACTUS;
        if (b == Blocks.COCOA) return COCOA_BEANS;
        if (b == Blocks.RED_MUSHROOM || b == Blocks.BROWN_MUSHROOM) return MUSHROOM;
        if (b == Blocks.NETHER_WART) return NETHER_WART;
        if (b == Blocks.ROSE_BUSH) return WILD_ROSE;
        if (b == Blocks.SUNFLOWER) return timeFlower();
        return null;
    }

    private static GardenCrop timeFlower() {
        Minecraft mc = Minecraft.getInstance();
        long time = mc.level == null ? 0 : mc.level.getOverworldClockTime() % 24000;
        return time >= 12000 ? MOONFLOWER : SUNFLOWER;
    }
}
