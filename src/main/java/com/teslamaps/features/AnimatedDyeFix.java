package com.teslamaps.features;

// Ported from Skysoft's AnimatedDyeArmorCache.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.EnumMap;
import java.util.regex.Pattern;

/**
 * Hypixel sends worn armour with an animated dye as hideTooltip=true, so the own armour slots lose
 * their tooltip, rarity background and value. Keeps the last full stack per armour slot and shows
 * that one while item, count and id still match.
 */
public final class AnimatedDyeFix {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Pattern WARDROBE_TITLE = Pattern.compile("^\\(\\d+/\\d+\\) Armor Sets$");
    private static final Pattern WARDROBE_READY = Pattern.compile("^Slot \\d+: Ready$");

    private static final EnumMap<EquipmentSlot, ItemStack> stacks = new EnumMap<>(EquipmentSlot.class);
    private static Feature feature;

    private AnimatedDyeFix() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("AnimatedDyeFix").section("Inventory").when(() -> cfg().animatedDyeTooltipFix)
                .config("Inventory", b -> {
                    b.label("Animated Dye Armor");
                    b.toggle("Fix Animated Dye Armor Tooltip", () -> cfg().animatedDyeTooltipFix, v -> cfg().animatedDyeTooltipFix = v)
                            .desc("Worn armour with an animated dye keeps its tooltip, rarity background and value.");
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> stacks.clear());
    }

    private static boolean on() {
        return feature != null && feature.active();
    }

    /** From Inventory.setItem. */
    public static void observe(Inventory inv, int slot, ItemStack stack) {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || inv.player != mc.player) return;
        EquipmentSlot eq = Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);
        if (eq == null || eq.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        remember(eq, stack);
    }

    /** Picking a wardrobe set remembers its four pieces before Hypixel hides them. */
    public static void observeWardrobe(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput type) {
        if (!on() || slot == null || button != 0 || type != ContainerInput.PICKUP) return;
        if (!WARDROBE_TITLE.matcher(StringUtil.stripTrim(screen.getTitle().getString())).matches()) return;
        if (slot.index < 36 || slot.index > 44) return;
        if (!WARDROBE_READY.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString())).matches()) return;
        int column = slot.index - 36;
        for (int row = 0; row < 4; row++) {
            int i = row * 9 + column;
            if (i >= screen.getMenu().slots.size()) return;
            remember(ARMOR[row], screen.getMenu().slots.get(i).getItem());
        }
    }

    private static void remember(EquipmentSlot eq, ItemStack stack) {
        if (stack.isEmpty()) stacks.remove(eq);
        else if (!hidden(stack)) stacks.put(eq, stack.copy());
    }

    private static boolean hidden(ItemStack stack) {
        TooltipDisplay d = stack.get(DataComponents.TOOLTIP_DISPLAY);
        return d != null && d.hideTooltip();
    }

    /** The stack to show for a slot of the open screen. */
    public static ItemStack displayStack(Slot slot) {
        ItemStack stack = slot.getItem();
        if (!cfg().animatedDyeTooltipFix || stack.isEmpty() || !hidden(stack) || !on()) return stack;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return stack;
        if (cs.getMenu() != mc.player.inventoryMenu) return stack;
        int i = slot.index - InventoryMenu.ARMOR_SLOT_START;
        if (i < 0 || i >= ARMOR.length) return stack;
        ItemStack remembered = stacks.get(ARMOR[i]);
        if (remembered == null || hidden(remembered)) return stack;
        if (remembered.getItem() != stack.getItem() || remembered.getCount() != stack.getCount()) return stack;
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty() || !id.equals(ItemUtil.skyblockId(remembered))) return stack;
        return remembered;
    }
}
