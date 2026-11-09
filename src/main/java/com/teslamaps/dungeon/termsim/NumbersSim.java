package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NumbersSim extends TermSimGUI {

    private static NumbersSim instance;

    public static NumbersSim get() {
        if (instance == null) instance = new NumbersSim();
        return instance;
    }

    private NumbersSim() {
        super(TerminalTypes.NUMBERS.termName, TerminalTypes.NUMBERS.windowSize);
    }

    @Override
    public void create() {
        List<Integer> used = new ArrayList<>();
        for (int i = 1; i <= 10; i++) used.add(i);
        Collections.shuffle(used);

        setSlots(slot -> {
            int row = slot.index / 9;
            int col = slot.index % 9;
            if (row >= 1 && row <= 2 && col >= 2 && col <= 6) {
                int number = used.remove(0);
                ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.red(), number);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("§a" + number));
                return stack;
            }
            return blackPane;
        });
    }

    @Override
    public void slotClick(Slot slot, int button) {
        Slot lowest = null;
        int lowestCount = Integer.MAX_VALUE;
        for (Slot s : guiInventorySlots()) {
            int count = s.getItem().getItem() == Items.STAINED_GLASS_PANE.red() ? s.getItem().getCount() : 1000;
            if (count < lowestCount) { lowestCount = count; lowest = s; }
        }
        if (lowest != slot) return;

        ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.lime(), slot.getItem().getCount());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        setSlot(slot, stack);

        boolean anyRed = false;
        for (Slot s : guiInventorySlots()) {
            if (s.getItem().getItem() == Items.STAINED_GLASS_PANE.red()) { anyRed = true; break; }
        }
        if (!anyRed) onComplete(TerminalUtils.getLastTermOpened());

        super.slotClick(slot, button);
    }
}
