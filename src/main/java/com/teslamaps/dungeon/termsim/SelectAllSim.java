package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class SelectAllSim extends TermSimGUI {

    private final DyeColor color;

    public SelectAllSim() {
        this(DyeColor.values()[(int) (Math.random() * DyeColor.values().length)]);
    }

    public SelectAllSim(DyeColor color) {
        super("Select all the " + color.name().replace("LIGHT_GRAY", "SILVER").replace("_", " ") + " items!",
                TerminalTypes.SELECT.windowSize);
        this.color = color;
    }

    @Override
    public void create() {
        List<Integer> pool = new ArrayList<>();
        for (int row = 1; row <= 4; row++) for (int col = 1; col <= 7; col++) pool.add(row * 9 + col);
        int guaranteed = pool.get((int) (Math.random() * pool.size()));

        setSlots(slot -> {
            int row = slot.index / 9;
            int col = slot.index % 9;
            if (row < 1 || row > 4 || col < 1 || col > 7) return blackPane;

            List<Item> matching = getPossibleItems(color);
            ItemStack item = new ItemStack(matching.get((int) (Math.random() * matching.size())));
            if (slot.index == guaranteed) return item;
            if (Math.random() > 0.75) return item;

            List<DyeColor> others = new ArrayList<>();
            for (DyeColor dye : DyeColor.values()) if (dye != color) others.add(dye);
            List<Item> wrong = getPossibleItems(others.get((int) (Math.random() * others.size())));
            return new ItemStack(wrong.get((int) (Math.random() * wrong.size())));
        });
    }

    private static List<Item> getPossibleItems(DyeColor color) {
        String name = color.name().toLowerCase();
        List<Item> items = new ArrayList<>();
        items.add(byId(name + "_stained_glass"));
        items.add(byId(name + "_wool"));
        items.add(byId(name + "_concrete"));
        items.add(switch (color) {
            case WHITE -> Items.BONE_MEAL;
            case BLUE -> Items.LAPIS_LAZULI;
            case BLACK -> Items.INK_SAC;
            case BROWN -> Items.COCOA_BEANS;
            default -> byId(name + "_dye");
        });
        return items;
    }

    private static Item byId(String path) {
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("minecraft", path)).get().value();
    }

    @Override
    public void slotClick(Slot slot, int button) {
        List<Item> possibleItems = getPossibleItems(color);
        if (!possibleItems.contains(slot.getItem().getItem())) {
            TerminalUtils.modMessage("§cThat item is not: " + color.name().toUpperCase() + "!");
            return;
        }
        if (TerminalUtils.hasGlint(slot.getItem())) {
            TerminalUtils.modMessage("§cAlready selected!");
            return;
        }

        ItemStack stack = slot.getItem();
        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        setSlot(slot, stack);

        boolean allDone = true;
        for (Slot s : guiInventorySlots()) {
            if (!TerminalUtils.hasGlint(s.getItem()) && possibleItems.contains(s.getItem().getItem())) {
                allDone = false;
                break;
            }
        }
        if (allDone) onComplete(TerminalUtils.getLastTermOpened());

        super.slotClick(slot, button);
    }
}
