package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PanesSim extends TermSimGUI {

    private static PanesSim instance;

    public static PanesSim get() {
        if (instance == null) instance = new PanesSim();
        return instance;
    }

    private PanesSim() {
        super(TerminalTypes.PANES.termName, TerminalTypes.PANES.windowSize);
    }

    private static ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }

    private static ItemStack greenPane() { return pane(Items.STAINED_GLASS_PANE.lime()); }
    private static ItemStack redPane() { return pane(Items.STAINED_GLASS_PANE.red()); }

    @Override
    public void create() {
        setSlots(slot -> {
            int row = slot.index / 9;
            int col = slot.index % 9;
            if (row >= 1 && row <= 3 && col >= 2 && col <= 6) {
                return Math.random() > 0.75 ? greenPane() : redPane();
            }
            return blackPane;
        });
    }

    @Override
    public void slotClick(Slot slot, int button) {
        setSlot(slot, slot.getItem().getItem() == Items.STAINED_GLASS_PANE.red() ? greenPane() : redPane());

        boolean anyRed = false;
        for (Slot s : guiInventorySlots()) {
            if (s.getItem().getItem() == Items.STAINED_GLASS_PANE.red()) { anyRed = true; break; }
        }
        if (!anyRed) onComplete(TerminalUtils.getLastTermOpened());

        super.slotClick(slot, button);
    }
}
