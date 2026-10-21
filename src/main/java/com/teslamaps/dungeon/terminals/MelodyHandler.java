package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class MelodyHandler extends TerminalHandler {

    public MelodyHandler() {
        super(TerminalTypes.MELODY);
    }

    @Override
    public List<Integer> solve(List<Slot> slots, int updatedIndex) {
        int magentaPane = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getItem().getItem() == Items.MAGENTA_STAINED_GLASS_PANE) { magentaPane = i; break; }
        }
        int greenPane = -1, greenClay = -1;
        for (int i = 0; i < slots.size(); i++) {
            var item = slots.get(i).getItem().getItem();
            if (item == Items.LIME_STAINED_GLASS_PANE) greenPane = i;
            if (item == Items.LIME_TERRACOTTA) greenClay = i;
        }

        List<Integer> out = new ArrayList<>();
        out.add(greenPane);
        out.add(magentaPane);
        out.add(magentaPane + 36);
        if (greenPane % 9 == magentaPane % 9) out.add(greenClay);
        return out;
    }

    @Override
    public boolean canClick(int slotIndex, int button) {
        return slotIndex == 16 || slotIndex == 25 || slotIndex == 34;
    }

    @Override
    protected SlotRender renderSlot(int slotIndex) {
        int row = slotIndex / 9;
        int color = (row == 0 || row == 4)
                ? TerminalSolver.melodyColumColor()
                : TerminalSolver.melodyPointerColor();
        return new SlotRender(color, null);
    }
}
