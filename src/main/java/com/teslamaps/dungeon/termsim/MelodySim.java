package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MelodySim extends TermSimGUI {

    private static MelodySim instance;

    public static MelodySim get() {
        if (instance == null) instance = new MelodySim();
        return instance;
    }

    private MelodySim() {
        super(TerminalTypes.MELODY.termName, TerminalTypes.MELODY.windowSize);
    }

    private int magentaColumn = 1;
    private int limeColumn = 2;
    private int currentRow = 1;
    private int limeDirection = 1;
    private int counter = 0;

    private static ItemStack named(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }

    @Override
    public void create() {
        currentRow = 1;
        magentaColumn = (int) (Math.random() * 5) + 1;
        limeColumn = 1;
        limeDirection = 1;
        setSlots(this::generateItemStack);
    }

    @Override
    protected void containerTick() {
        if (counter++ % 10 != 0) return;
        limeColumn += limeDirection;
        if (limeColumn == 1 || limeColumn == 5) limeDirection *= -1;
        updateRow(currentRow);
        super.containerTick();
    }

    @Override
    public void slotClick(Slot slot, int button) {
        if (slot.index % 9 != 7 || limeColumn != magentaColumn || slot.index / 9 != currentRow) return;

        int oldRow = currentRow;
        magentaColumn = (int) (Math.random() * 4) + 1;
        currentRow++;
        updateRow(0);
        updateRow(oldRow);
        if (currentRow <= 3) updateRow(currentRow);

        if (currentRow > 3) onComplete(TerminalUtils.getLastTermOpened());
        super.slotClick(slot, button);
    }

    private void updateRow(int row) {
        for (int col = 0; col <= 8; col++) {
            Slot slot = guiInventorySlots().get(row * 9 + col);
            setSlot(slot, generateItemStack(slot));
        }
    }

    private ItemStack generateItemStack(Slot slot) {
        int col = slot.index % 9;
        int row = slot.index / 9;
        if (col == magentaColumn && (row == 0 || row == 4)) return named(Items.MAGENTA_STAINED_GLASS_PANE);
        if (col == limeColumn && row == currentRow) return named(Items.LIME_STAINED_GLASS_PANE);
        if (col >= 1 && col <= 5 && row == currentRow) return named(Items.RED_STAINED_GLASS_PANE);
        if (col == 7 && row == currentRow) return named(Items.LIME_TERRACOTTA);
        if (col == 7 && row >= 1 && row <= 3) return named(Items.RED_TERRACOTTA);
        if (col >= 1 && col <= 5 && row >= 1 && row <= 3) return named(Items.WHITE_STAINED_GLASS_PANE);
        return blackPane;
    }
}
