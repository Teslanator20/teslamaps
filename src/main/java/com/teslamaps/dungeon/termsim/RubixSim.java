package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

import java.util.List;

public class RubixSim extends TermSimGUI {

    private static final List<DyeColor> ORDER =
            List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.RED);
    private static final List<Item> PANES = List.of(
            Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE,
            Items.BLUE_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE);
    private static final int[] INDICES = {12, 13, 14, 21, 22, 23, 30, 31, 32};

    private static RubixSim instance;

    public static RubixSim get() {
        if (instance == null) instance = new RubixSim();
        return instance;
    }

    private RubixSim() {
        super(TerminalTypes.RUBIX.termName, TerminalTypes.RUBIX.windowSize);
    }

    @Override
    public void create() {
        setSlots(slot -> {
            int row = slot.index / 9;
            int col = slot.index % 9;
            return (row >= 1 && row <= 3 && col >= 3 && col <= 5) ? getPane() : blackPane;
        });
    }

    @Override
    public void slotClick(Slot slot, int button) {
        DyeColor current = paneColor(slot.getItem());
        if (current == null || !ORDER.contains(current)) return;

        int index = ORDER.indexOf(current);
        setSlot(slot, button == 1 ? genStack(index - 1) : genStack((index + 1) % ORDER.size()));

        List<Slot> slots = guiInventorySlots();
        Item first = slots.get(12).getItem().getItem();
        boolean allSame = true;
        for (int i : INDICES) {
            if (slots.get(i).getItem().getItem() != first) { allSame = false; break; }
        }
        if (allSame) onComplete(TerminalUtils.getLastTermOpened());

        super.slotClick(slot, button);
    }

    private static DyeColor paneColor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof StainedGlassPaneBlock pane) return pane.getColor();
        return null;
    }

    private static ItemStack getPane() {
        double r = Math.random();
        if (r <= 0.2) return genStack(0);
        if (r <= 0.4) return genStack(1);
        if (r <= 0.6) return genStack(2);
        if (r <= 0.8) return genStack(3);
        return genStack(4);
    }

    private static ItemStack genStack(int meta) {
        Item item = (meta >= 0 && meta < PANES.size()) ? PANES.get(meta) : PANES.get(PANES.size() - 1);
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }
}
