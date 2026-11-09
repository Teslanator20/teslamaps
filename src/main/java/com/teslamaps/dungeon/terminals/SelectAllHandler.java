package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SelectAllHandler extends TerminalHandler {

    private final Set<String> validPrefixes;

    public SelectAllHandler(DyeColor color) {
        super(TerminalTypes.SELECT);
        this.validPrefixes = switch (color) {
            case BLACK      -> Set.of("black", "ink");
            case BLUE       -> Set.of("blue", "lapis");
            case BROWN      -> Set.of("brown", "cocoa");
            case WHITE      -> Set.of("white", "bone", "wool");
            case GREEN      -> Set.of("green", "cactus");
            case RED        -> Set.of("red", "rose");
            case YELLOW     -> Set.of("yellow", "dandelion");
            case LIGHT_GRAY -> Set.of("silver", "light gray");
            default         -> Set.of(color.name().toLowerCase().replace('_', ' '));
        };
    }

    @Override
    public List<Integer> solve(List<Slot> slots, int updatedIndex) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            var stack = slots.get(i).getItem();
            if (stack.getItem() == Items.STAINED_GLASS_PANE.black() || TerminalUtils.hasGlint(stack)) continue;
            String name = stack.getHoverName().getString().toLowerCase();
            for (String prefix : validPrefixes) {
                if (name.startsWith(prefix)) { out.add(i); break; }
            }
        }
        return out;
    }

    @Override
    protected SlotRender renderSlot(int slotIndex) {
        return new SlotRender(TerminalSolver.selectColor(), null);
    }
}
