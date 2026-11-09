package com.teslamaps.dungeon.terminals;

import com.teslamaps.dungeon.termsim.TermSimGUI;
import com.teslamaps.features.TerminalSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class TerminalHandler {

    /** Fill color plus optional centered label for one solution slot. */
    public record SlotRender(int color, String text) {}

    public final TerminalTypes type;
    /** {slotIndex, button} pairs of clicks that have not been confirmed by the server yet. */
    public final List<int[]> clickedSlots = new ArrayList<>();
    public final long timeOpened = System.currentTimeMillis();
    public final List<Integer> solution = new ArrayList<>();
    public long lastClickTime = 0L;
    public int ticksOpened = -1;

    protected TerminalHandler(TerminalTypes type) {
        this.type = type;
    }

    public void updateSlot(int slotIndex, ItemStack itemStack, List<Slot> slots) {
        if (slots.isEmpty() || slotIndex < 0 || slotIndex >= type.windowSize - 9
                || itemStack.getItem() == Items.STAINED_GLASS_PANE.black()) return;

        int index = -1;
        for (int i = 0; i < clickedSlots.size(); i++) {
            if (clickedSlots.get(i)[0] == slotIndex) { index = i; break; }
        }
        if (index >= 0) clickedSlots.subList(0, index + 1).clear();

        solution.clear();
        solution.addAll(solve(slots.subList(0, type.windowSize - 9), slotIndex));
        if (TerminalSolver.hideClicked()) {
            for (int[] click : new ArrayList<>(clickedSlots)) simulateClick(click[0], click[1]);
        }
    }

    protected abstract SlotRender renderSlot(int slotIndex);

    public SlotRender getSlotRendering(int slotIndex) {
        return solution.contains(slotIndex) ? renderSlot(slotIndex) : null;
    }

    public void simulateClick(int slotIndex, int clickType) {
        int index = solution.indexOf(slotIndex);
        if (index == -1) return;
        solution.remove(index);
    }

    public abstract List<Integer> solve(List<Slot> slots, int updatedIndex);

    public void click(int slotIndex, int button, boolean simulateClick) {
        if (!canClick(slotIndex, button) || shouldProtect()) return;

        int mappedButton = (button == 1 && type == TerminalTypes.RUBIX)
                ? com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT : com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE;
        clickedSlots.add(new int[]{slotIndex, mappedButton});
        lastClickTime = System.currentTimeMillis();

        if (simulateClick) simulateClick(slotIndex, mappedButton);

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TermSimGUI sim) {
            sim.clickIndex(slotIndex, mappedButton);
            return;
        }
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slotIndex, mappedButton,
                mappedButton == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE ? ContainerInput.CLONE : ContainerInput.PICKUP, mc.player);
    }

    public boolean canClick(int slotIndex, int button) {
        return solution.contains(slotIndex);
    }

    public boolean shouldProtect() {
        if (com.teslamaps.adm.practice.AdmTerms.noProtection()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (System.currentTimeMillis() - timeOpened < TerminalSolver.firstClickProt()) return true;
        boolean singlePlayer = mc.hasSingleplayerServer();
        return !singlePlayer && TerminalSolver.shouldFirstClickProtWithTicks()
                && ticksOpened < TerminalSolver.firstClickProtTicks();
    }
}
