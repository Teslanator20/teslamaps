package com.teslamaps.features;

// Ported from Devonian's features/misc/Misc.kt + misc/hiders/HideOffhandSlotBackground.kt, GanKura's ScreenBackgroundMixin
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

public final class ContainerTweaks {

    private ContainerTweaks() {}

    private static boolean on(boolean sub) {
        return sub && TeslaMapsConfig.get().containerTweaks;
    }

    public static boolean noDim() {
        return on(TeslaMapsConfig.get().containerNoDim) && Minecraft.getInstance().level != null;
    }

    /** -1 = vanilla. */
    public static int tint() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return on(c.containerTint) ? TeslaMapsConfig.parseColor(c.containerTintColor) : -1;
    }

    public static boolean hideCraftingText() {
        return on(TeslaMapsConfig.get().hideCraftingText);
    }

    public static Identifier noItemIcon(Identifier icon) {
        return icon == InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD && on(TeslaMapsConfig.get().hideOffhandSlotIcon) ? null : icon;
    }

    public static boolean hideHighlight(Slot hovered) {
        if (hovered == null || !on(TeslaMapsConfig.get().noFillerPaneHighlight)) return false;
        ItemStack s = hovered.getItem();
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        if (!(bi.getBlock() instanceof StainedGlassPaneBlock) && bi.getBlock() != Blocks.GLASS_PANE) return false;
        return s.getHoverName().getString().isBlank();
    }
}
