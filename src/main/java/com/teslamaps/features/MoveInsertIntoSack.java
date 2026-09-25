package com.teslamaps.features;

// Ported from SkyHanni's MoveInsertIntoSackButton.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/** Shows the sack's Insert Into Sack chest three slots further left; clicks still hit the real slot. */
public final class MoveInsertIntoSack {

    private static AbstractContainerMenu swapped;

    private MoveInsertIntoSack() {}

    public static void register() {
        Feature.of("MoveInsertIntoSack").section("Inventory").when(() -> TeslaMapsConfig.get().moveInsertIntoSack)
                .tick(MoveInsertIntoSack::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !(cs.getMenu() instanceof ChestMenu menu)) return;
        if (menu == swapped) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).endsWith(" Sack")) return;
        int last = menu.getRowCount() * 9 - 1;
        int chest = last - 2, target = last - 5;
        if (target < 0) return;
        Slot a = menu.slots.get(chest);
        if (!a.getItem().is(Items.CHEST)) return;
        Slot b = menu.slots.get(target);
        int x = a.x, y = a.y;
        a.x = b.x;
        a.y = b.y;
        b.x = x;
        b.y = y;
        swapped = menu;
    }
}
