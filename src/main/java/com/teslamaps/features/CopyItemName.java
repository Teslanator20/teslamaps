package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Middle click an item in any gui to copy its name. */
public class CopyItemName {

    private static final long RECENT_MS = 30_000;

    private static String lastCopied = "";
    private static long lastCopiedAt = 0;

    public static boolean onClick(Slot slot, int button) {
        if (button != 2 || slot == null) return false;
        return copy(slot.getItem());
    }

    public static boolean copy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String name = StringUtil.strip(stack.getHoverName().getString()).trim();
        // bazaar order items are named "BUY <item>" / "SELL <item>"
        if (name.startsWith("BUY ")) name = name.substring(4).trim();
        else if (name.startsWith("SELL ")) name = name.substring(5).trim();
        return copyText(name);
    }

    public static boolean copyText(String name) {
        if (!TeslaMapsConfig.get().middleClickCopyName) return false;
        if (name == null || name.isBlank()) return false;

        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(name);
        lastCopied = name;
        lastCopiedAt = System.currentTimeMillis();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Copied §f" + name));
        }
        return true;
    }

    /** Name copied in the last 30s, or null. */
    public static String recent() {
        if (lastCopied.isEmpty() || System.currentTimeMillis() - lastCopiedAt > RECENT_MS) return null;
        return lastCopied;
    }
}
