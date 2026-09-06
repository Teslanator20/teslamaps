package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RubixHandler extends TerminalHandler {

    private static final int LAST_PANE_SLOT = 32;
    private static final List<DyeColor> RUBIX_COLOR_ORDER =
            List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.RED);

    private DyeColor lockedColor = null;
    private final Set<Integer> rightClickSlots = new HashSet<>();

    public RubixHandler() {
        super(TerminalTypes.RUBIX);
    }

    @Override
    public List<Integer> solve(List<Slot> slots, int updatedIndex) {
        List<int[]> panes = new ArrayList<>(); // {slotIndex, colorIndex}
        for (int i = 0; i < slots.size(); i++) {
            DyeColor color = paneColor(slots.get(i).getItem());
            if (color == null || color == DyeColor.BLACK) continue;
            panes.add(new int[]{i, RUBIX_COLOR_ORDER.indexOf(color)});
        }

        if (updatedIndex == LAST_PANE_SLOT && lockedColor == null) {
            int best = Integer.MAX_VALUE;
            for (DyeColor goal : RUBIX_COLOR_ORDER) {
                int total = 0;
                for (int count : clicksFor(goal, panes).values()) total += Math.abs(count);
                if (total < best) { best = total; lockedColor = goal; }
            }
        }

        Map<Integer, Integer> clicks = lockedColor == null ? Map.of() : clicksFor(lockedColor, panes);

        rightClickSlots.clear();
        clicks.forEach((slotIndex, count) -> { if (count < 0) rightClickSlots.add(slotIndex); });

        List<Integer> out = new ArrayList<>();
        clicks.forEach((slotIndex, count) -> {
            for (int i = 0; i < Math.abs(count); i++) out.add(slotIndex);
        });
        return out;
    }

    private Map<Integer, Integer> clicksFor(DyeColor goal, List<int[]> panes) {
        int goalIndex = RUBIX_COLOR_ORDER.indexOf(goal);
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (int[] pane : panes) {
            int forward = dist(pane[1], goalIndex);
            int clicks = (forward > 2 && TerminalSolver.rubixMode() != 1)
                    ? forward - RUBIX_COLOR_ORDER.size() : forward;
            if (clicks != 0) out.put(pane[0], clicks);
        }
        return out;
    }

    private static DyeColor paneColor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof StainedGlassPaneBlock pane) return pane.getColor();
        return null;
    }

    @Override
    public boolean canClick(int slotIndex, int button) {
        return solution.contains(slotIndex) && (button == 1) == rightClickSlots.contains(slotIndex);
    }

    private static int dist(int pane, int most) {
        return pane > most ? (most + RUBIX_COLOR_ORDER.size()) - pane : most - pane;
    }

    @Override
    protected SlotRender renderSlot(int slotIndex) {
        int remaining = 0;
        for (int slot : solution) if (slot == slotIndex) remaining++;
        if (remaining == 0) return null;

        int clicksRequired = rightClickSlots.contains(slotIndex) ? -remaining : remaining;
        int color = switch (clicksRequired) {
            case 1 -> TerminalSolver.rubixColor1();
            case 2 -> TerminalSolver.rubixColor2();
            case -1, 4 -> TerminalSolver.oppositeRubixColor1();
            default -> TerminalSolver.oppositeRubixColor2();
        };
        return new SlotRender(color, String.valueOf(clicksRequired));
    }
}
