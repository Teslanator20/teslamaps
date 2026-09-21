package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

/** Pets menu: plain right-click is swallowed, shift+right-click is sent as the right-click instead. */
public class PetShiftClick {

    private static final Pattern PETS_TITLE = Pattern.compile("(\\(\\d+/\\d+\\) )?Pets");
    private static final Pattern PET_NAME = Pattern.compile("\\[Lvl \\d+] .+");

    public static boolean handle(AbstractContainerScreen<?> cs, Slot slot, int slotId, int button, ContainerInput type) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.section("Inventory") || !c.petShiftTakeOut) return false;
        if (button != 1 || slot == null || slot.container instanceof Inventory) return false;
        if (type != ContainerInput.PICKUP && type != ContainerInput.QUICK_MOVE) return false;
        if (!PETS_TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return false;
        if (!isPet(slot.getItem())) return false;

        if (type == ContainerInput.PICKUP) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(cs.getMenu().containerId, slotId, 1, ContainerInput.PICKUP, mc.player);
        }
        return true;
    }

    private static boolean isPet(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return PET_NAME.matcher(StringUtil.stripTrim(stack.getHoverName().getString())).matches();
    }
}
