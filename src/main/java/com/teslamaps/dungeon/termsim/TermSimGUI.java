package com.teslamaps.dungeon.termsim;

import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.features.TerminalSolver;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Function;

/**
 * A client-side stand-in for a real terminal window, backed by a fake chest menu.
 * Subclasses fill the grid in {@link #create()} and react to clicks in
 * {@link #slotClick(Slot, int)}.
 */
public class TermSimGUI extends ContainerScreen {

    public final String name;
    public final int size;
    private final SimpleContainer inv;

    public final ItemStack blackPane;
    protected long ping = 0L;

    protected TermSimGUI(String name, int size) {
        this(name, size, new SimpleContainer(size));
    }

    private TermSimGUI(String name, int size, SimpleContainer inv) {
        super(new ChestMenu(menuType(size), 0, inventory(), inv, size / 9), inventory(), Component.literal(name));
        this.name = name;
        this.size = size;
        this.inv = inv;
        this.blackPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        this.blackPane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
    }

    private static MenuType<ChestMenu> menuType(int size) {
        if (size <= 9) return MenuType.GENERIC_9x1;
        if (size <= 18) return MenuType.GENERIC_9x2;
        if (size <= 27) return MenuType.GENERIC_9x3;
        if (size <= 36) return MenuType.GENERIC_9x4;
        if (size <= 45) return MenuType.GENERIC_9x5;
        return MenuType.GENERIC_9x6;
    }

    private static Inventory inventory() {
        var player = Minecraft.getInstance().player;
        return new Inventory(player, new PlayerEquipment(player));
    }

    protected List<Slot> guiInventorySlots() {
        return menu.slots.subList(0, size);
    }

    public void create() {
        setSlots(slot -> blackPane);
    }

    public void open(long terminalPing) {
        Minecraft.getInstance().setScreen(this);
        create();
        ping = terminalPing;
    }

    public void slotClick(Slot slot, int button) {
        playTermSimSound();
    }

    /** Terminal finished: close the session, record the time and go back to the menu. */
    protected void onComplete(TerminalHandler handler) {
        TerminalUtils.onScreenClose();
        if (handler != null) TerminalSolver.onTerminalSolved(handler);
        StartGUI.get().open(ping);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput clickType) {
        if (TerminalSolver.onSlotClick(slotId, button)) return;
        if (slot != null) delaySlotClick(slot, button);
    }

    public void clickIndex(int index, int button) {
        List<Slot> slots = guiInventorySlots();
        if (index < 0 || index >= slots.size()) return;
        delaySlotClick(slots.get(index), button);
    }

    private void delaySlotClick(Slot slot, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StartGUI) { slotClick(slot, button); return; }
        if (slot.container != inv || slot.getItem().getItem() == Items.BLACK_STAINED_GLASS_PANE) return;
        if (ping <= 0L) { slotClick(slot, button); return; }
        TickScheduler.schedule((int) Math.max(ping / 50, 0), () -> {
            if (mc.screen == this) slotClick(slot, button);
        });
    }

    protected void setSlots(Function<Slot, ItemStack> block) {
        for (Slot slot : guiInventorySlots()) setSlot(slot, block.apply(slot));
    }

    protected void setSlot(Slot slot, ItemStack stack) {
        menu.setItem(slot.index, menu.incrementStateId(), stack);
    }

    protected void playTermSimSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }
}
