package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class PanesHandler extends TerminalHandler {

    public PanesHandler() {
        super(TerminalTypes.PANES);
    }

    @Override
    public List<Integer> solve(List<Slot> slots, int updatedIndex) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getItem().getItem() == Items.RED_STAINED_GLASS_PANE) out.add(i);
        }
        return out;
    }

    @Override
    protected SlotRender renderSlot(int slotIndex) {
        return new SlotRender(TerminalSolver.panesColor(), null);
    }
}
