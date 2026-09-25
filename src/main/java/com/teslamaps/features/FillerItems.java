package com.teslamaps.features;

// Ported from Skyblocker's AbstractContainerScreenMixin (hideEmptyTooltips / FILLER_ITEMS)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class FillerItems {

    private static final Set<String> NAMES = Set.of("", "Locked Page", "Quick Crafting Slot", "Preparing");

    private static boolean active(AbstractContainerScreen<?> screen) {
        if (!TeslaMapsConfig.get().hideFillerItems) return false;
        if (!SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()) return false;
        String title = StringUtil.strip(screen.getTitle().getString());
        return !title.startsWith("Ultrasequencer") && !title.startsWith("Chronomatron")
                && !title.startsWith("Superpairs") && !title.startsWith("Experiment");
    }

    public static boolean isFiller(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || slot.container instanceof Inventory) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !active(screen)) return false;
        String name = StringUtil.strip(stack.getHoverName().getString()).trim();
        if (name.startsWith("Locked Backpack Slot")) return true;
        if (!NAMES.contains(name)) return false;
        return !name.isEmpty() || ItemUtil.loreLines(stack).stream().allMatch(l -> StringUtil.strip(l).isBlank());
    }

    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot) {
        return isFiller(screen, slot);
    }

    public static boolean hideTooltip(AbstractContainerScreen<?> screen, Slot hovered) {
        if (hovered == null || hovered.getItem().isEmpty()) return false;
        if (!StringUtil.strip(hovered.getItem().getHoverName().getString()).isBlank()) return false;
        return isFiller(screen, hovered);
    }
}
