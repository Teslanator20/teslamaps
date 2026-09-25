package com.teslamaps.features;

// Concept from Skytils' dungeonPotLock
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class OpheliaPotLock {

    public static final String[] LEVELS = {"Off", "I", "II", "III", "IV", "V", "VI", "VII"};

    private OpheliaPotLock() {}

    /** True = swallow this click: a shop item at Ophelia that is not the chosen Dungeon Potion. */
    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if ("Off".equals(c.opheliaPotLock) || !c.section("Inventory")) return false;
        if (slot == null || slot.container instanceof Inventory) return false;
        if (!StringUtil.stripTrim(screen.getTitle().getString()).equals("Ophelia")) return false;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return false;
        boolean shopItem = false;
        for (String l : ItemUtil.loreLines(st)) {
            if (l.trim().equals("Cost")) { shopItem = true; break; }
        }
        if (!shopItem) return false;
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        return !name.contains("Dungeon " + c.opheliaPotLock + " Potion");
    }
}
