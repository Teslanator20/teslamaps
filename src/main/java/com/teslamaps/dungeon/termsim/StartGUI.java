package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.features.TerminalSimulator;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class StartGUI extends TermSimGUI {

    private static StartGUI instance;

    public static StartGUI get() {
        if (instance == null) instance = new StartGUI();
        return instance;
    }

    private StartGUI() {
        super("Terminal Simulator", 27);
    }

    private boolean areYouSure = false;

    private static ItemStack buildTermItem(TerminalTypes type, Item item, String color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(color + type.termName));
        stack.set(DataComponents.LORE, new ItemLore(getTermPB(type)));
        return stack;
    }

    private static List<ItemStack> buildTermItems() {
        return List.of(
                buildTermItem(TerminalTypes.PANES, Items.LIME_DYE, "§a"),
                buildTermItem(TerminalTypes.RUBIX, Items.RED_DYE, "§6"),
                buildTermItem(TerminalTypes.NUMBERS, Items.CYAN_DYE, "§3"),
                buildTermItem(TerminalTypes.STARTS_WITH, Items.PINK_DYE, "§5"),
                buildTermItem(TerminalTypes.SELECT, Items.BROWN_DYE, "§b"),
                buildTermItem(TerminalTypes.MELODY, Items.PURPLE_DYE, "§d"));
    }

    private static ItemStack button(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    public static List<Component> getTermPB(TerminalTypes type) {
        Float pb = TerminalSimulator.termSimPBs().get(type.name());
        if (pb == null) return List.of(Component.literal("§7No PB set!"));
        return List.of(Component.literal("§7Personal Best: §d" + String.format("%.2f", pb) + "s"));
    }

    @Override
    public void create() {
        List<ItemStack> termItems = buildTermItems();
        setSlots(slot -> switch (slot.index) {
            case 4 -> button(Items.BLACK_DYE, "§cReset PBs!");
            case 13 -> button(Items.WHITE_DYE, "§7Random");
            case 10, 11, 12 -> termItems.get(slot.index - 10);
            case 14, 15, 16 -> termItems.get(slot.index - 11);
            default -> blackPane;
        });
    }

    @Override
    public void slotClick(Slot slot, int button) {
        switch (slot.index) {
            case 4 -> {
                if (!areYouSure) {
                    TerminalUtils.modMessage("§cAre you sure you want to reset your PBs? Click again to confirm.");
                    areYouSure = true;
                    TickScheduler.schedule(60, () -> {
                        if (!areYouSure) return;
                        TerminalUtils.modMessage("§aPBs reset cancelled.");
                        areYouSure = false;
                    });
                    return;
                }
                TerminalSimulator.resetPBs();
                create();
                TerminalUtils.modMessage("§aPBs reset!");
                areYouSure = false;
            }
            case 10 -> PanesSim.get().open(ping);
            case 11 -> RubixSim.get().open(ping);
            case 12 -> NumbersSim.get().open(ping);
            case 13 -> TerminalSimulator.openRandomTerminal(ping);
            case 14 -> new StartsWithSim().open(ping);
            case 15 -> new SelectAllSim().open(ping);
            case 16 -> MelodySim.get().open(ping);
            default -> { }
        }
    }
}
