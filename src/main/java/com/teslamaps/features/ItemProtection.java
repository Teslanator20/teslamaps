package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

// Blocks throwing away items worth more than a configured value. Only guards the two
// ways an item actually gets lost - the Q drop and dropping the cursor stack outside
// the slots - so normal moving around in inventories still works. Sneak overrides.
public class ItemProtection {

    private static long lastWarn = 0;

    private static double threshold() {
        return TeslaMapsConfig.get().itemProtectionValue * 1_000_000.0;
    }

    // ctrl = "yes I really mean it". Not sneak: shift is part of the sell gesture,
    // so a sneak override would cancel the guard exactly when it should fire.
    private static boolean overridden() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long handle = mc.getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static boolean enabled() {
        return TeslaMapsConfig.get().itemProtection && !overridden();
    }

    private static boolean tooValuable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return EstimatedValue.compute(stack) * stack.getCount() >= threshold();
    }

    /** Q key on the selected hotbar slot. */
    public static boolean blockSelectedDrop() {
        if (!enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack held = mc.player.getInventory().getSelectedItem();
        if (!tooValuable(held)) return false;
        warn(held);
        return true;
    }

    /** Clicking outside the slots while carrying an item drops it. */
    public static boolean blockCursorDrop(AbstractContainerScreen<?> screen, boolean overSlot) {
        if (overSlot || !enabled()) return false;
        ItemStack carried = screen.getMenu().getCarried();
        if (!tooValuable(carried)) return false;
        warn(carried);
        return true;
    }

    /** Q on a hovered slot inside any inventory screen, and Q while carrying a stack. */
    public static boolean blockSlotDrop(net.minecraft.world.inventory.Slot slot) {
        if (!enabled()) return false;
        ItemStack stack;
        if (slot != null) {
            stack = slot.getItem();
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return false;
            stack = screen.getMenu().getCarried();
        }
        if (!tooValuable(stack)) return false;
        warn(stack);
        return true;
    }

    // NPC shops whose title is the plain merchant name - shift-clicking sells there.
    // Best effort list, extend as they turn up in game.
    private static final java.util.Set<String> SELL_GUIS = java.util.Set.of(
            "Adventurer", "Alchemist", "Armorsmith", "Bartender", "Blacksmith", "Builder",
            "Farm Merchant", "Fish Merchant", "Lumber Merchant", "Mine Merchant",
            "Weaponsmith", "Ophelia", "Market");

    /** Salvage destroys the item outright, a shop shift-click sells it. Ctrl overrides. */
    public static boolean blockDestructiveClick(AbstractContainerScreen<?> screen,
                                                net.minecraft.world.inventory.Slot slot, boolean shift) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtection || !c.itemProtectionSell || slot == null) return false;
        if (overridden()) return false;

        String title = com.teslamaps.utils.StringUtil.strip(screen.getTitle().getString());
        boolean salvage = title.startsWith("Salvage Items");
        boolean shop = SELL_GUIS.contains(title);
        if (!salvage && !shop) return false;

        boolean ownInventory = slot.container instanceof net.minecraft.world.entity.player.Inventory;
        ItemStack stack = slot.getItem();
        if (!tooValuable(stack)) return false;

        // salvage: the item sits in the upper container. shop: sold out of your own inventory.
        if (salvage && !ownInventory) { warnKept(stack, "salvaging"); return true; }
        if (shop && ownInventory && shift) { warnKept(stack, "selling"); return true; }
        return false;
    }

    private static void warnKept(ItemStack stack, String what) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1500) return;
        lastWarn = now;

        mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fBlocked " + what + " §e"
                + stack.getHoverName().getString() + " §7(hold ctrl to override)"));
        LoudSound.play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
    }

    private static void warn(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1500) return; // no spam while a key is held
        lastWarn = now;

        mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fBlocked dropping §e"
                + stack.getHoverName().getString() + " §7(hold ctrl to override)"));
        LoudSound.play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
    }
}
