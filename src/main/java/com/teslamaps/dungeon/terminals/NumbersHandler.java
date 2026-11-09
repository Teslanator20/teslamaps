package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class NumbersHandler extends TerminalHandler {

    public NumbersHandler() {
        super(TerminalTypes.NUMBERS);
    }

    @Override
    public List<Integer> solve(List<Slot> slots, int updatedIndex) {
        List<Integer> inf = com.teslamaps.adm.practice.AdmTerms.numbersOrder();
        if (inf != null) return inf;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getItem().getItem() == Items.STAINED_GLASS_PANE.red()) out.add(i);
        }
        out.sort((a, b) -> Integer.compare(slots.get(a).getItem().getCount(), slots.get(b).getItem().getCount()));
        return out;
    }

    @Override
    public void simulateClick(int slotIndex, int clickType) {
        if (!solution.isEmpty()) solution.remove(0);
    }

    @Override
    public boolean canClick(int slotIndex, int button) {
        return !solution.isEmpty() && slotIndex == solution.get(0);
    }

    @Override
    protected SlotRender renderSlot(int slotIndex) {
        int solutionIndex = solution.indexOf(slotIndex);
        int color = switch (solutionIndex) {
            case 0 -> TerminalSolver.orderColor();
            case 1 -> TerminalSolver.orderColor2();
            case 2 -> TerminalSolver.orderColor3();
            case 3 -> TerminalSolver.orderColor4();
            default -> 0x00000000;
        };
        String text = TerminalSolver.showNumbers() && !com.teslamaps.adm.practice.AdmTerms.hideNumberText()
                ? String.valueOf(Math.abs((solution.size() - 10) - solutionIndex) + 1) : null;
        return new SlotRender(color, text);
    }
}
