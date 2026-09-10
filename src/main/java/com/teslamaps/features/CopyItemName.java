package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Middle click an item in a container to copy its name. */
public class CopyItemName {

    public static boolean onClick(Slot slot, int button) {
        if (button != 2 || slot == null) return false;
        if (!TeslaMapsConfig.get().middleClickCopyName) return false;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;

        String name = StringUtil.strip(stack.getHoverName().getString()).trim();
        if (name.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(name);
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Copied §f" + name));
        }
        return true;
    }
}
