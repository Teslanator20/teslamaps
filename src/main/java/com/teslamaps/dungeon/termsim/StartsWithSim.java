package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StartsWithSim extends TermSimGUI {

    private static final List<String> LETTERS = List.of("A", "B", "C", "G", "D", "M", "N", "R", "S", "T", "W");
    private static Set<Item> enchantOverrides;

    private final String letter;
    private final List<Integer> clickedOverrides = new ArrayList<>();

    public StartsWithSim() {
        this(LETTERS.get((int) (Math.random() * LETTERS.size())));
    }

    public StartsWithSim(String letter) {
        super("What starts with: '" + letter + "'?", TerminalTypes.STARTS_WITH.windowSize);
        this.letter = letter;
    }

    private static Set<Item> enchantOverrides() {
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

    private static boolean hasRealGlint(ItemStack stack) {
        return TerminalUtils.hasGlint(stack) && !enchantOverrides().contains(stack.getItem());
    }

    private boolean startsWithLetter(ItemStack stack) {
        return stack.getHoverName().getString().regionMatches(true, 0, letter, 0, letter.length());
    }

    @Override
    public void create() {
        setSlots(slot -> {
            int row = slot.index / 9;
            int col = slot.index % 9;
            if (row < 1 || row > 3 || col < 1 || col > 7) return blackPane;
            if (slot.index == (int) (Math.random() * 7) + 10) return getLetterItemStack(false);
            if (Math.random() > 0.7f) return getLetterItemStack(false);
            return getLetterItemStack(true);
        });
    }

    @Override
    public void slotClick(Slot slot, int button) {
        if (!startsWithLetter(slot.getItem())) {
            TerminalUtils.modMessage("§cThat item does not start with: '" + letter + "'!");
            return;
        }
        if (hasRealGlint(slot.getItem()) || clickedOverrides.contains(slot.index)) {
            TerminalUtils.modMessage("§cAlready selected!");
            return;
        }

        if (enchantOverrides().contains(slot.getItem().getItem())) clickedOverrides.add(slot.index);
        ItemStack stack = slot.getItem();
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        setSlot(slot, stack);

        boolean allDone = true;
        for (Slot s : guiInventorySlots()) {
            if (startsWithLetter(s.getItem()) && !hasRealGlint(s.getItem()) && !clickedOverrides.contains(s.index)) {
                allDone = false;
                break;
            }
        }
        if (allDone) onComplete(TerminalUtils.getLastTermOpened());

        super.slotClick(slot, button);
    }

    private ItemStack getLetterItemStack(boolean filterNot) {
        List<Item> matching = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            boolean starts = id.regionMatches(true, 0, letter, 0, letter.length());
            if (starts != filterNot && !id.toLowerCase().contains("pane") && item != Items.AIR) matching.add(item);
        }
        if (matching.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(matching.get((int) (Math.random() * matching.size())));
    }
}
