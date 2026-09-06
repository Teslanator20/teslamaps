package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StartsWithHandler extends TerminalHandler {

    private static Set<Item> enchantOverrides;

    private final String letter;
    private final Map<Integer, Boolean> clickedOverrides = new HashMap<>();

    public StartsWithHandler(String letter) {
        super(TerminalTypes.STARTS_WITH);
        this.letter = letter;
    }

    static Set<Item> enchantOverrides() {
        if (enchantOverrides == null) {
            Set<Item> items = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item.components().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) items.add(item);
            }
            items.add(Items.GOLDEN_APPLE);
            enchantOverrides = items;
        }
        return enchantOverrides;
    }

    @Override
    public List<Integer> solve(List<Slot> slots, int updatedIndex) {
        clickedOverrides.computeIfPresent(updatedIndex, (k, v) -> true);

        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            var stack = slots.get(i).getItem();
            boolean matches = stack.getHoverName().getString().regionMatches(true, 0, letter, 0, letter.length());
            if (matches && !Boolean.TRUE.equals(clickedOverrides.get(i))
                    && (!TerminalUtils.hasGlint(stack) || enchantOverrides().contains(stack.getItem()))) out.add(i);
        }
        return out;
    }

    @Override
    public void click(int slotIndex, int button, boolean simulateClick) {
        if (canClick(slotIndex, button) && !clickedOverrides.containsKey(slotIndex)) {
            clickedOverrides.put(slotIndex, false);
        }
        super.click(slotIndex, button, simulateClick);
    }

    @Override
    protected SlotRender renderSlot(int slotIndex) {
        return new SlotRender(TerminalSolver.startsWithColor(), null);
    }
}
