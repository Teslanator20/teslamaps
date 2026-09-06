package com.teslamaps.dungeon.terminals;

import com.teslamaps.features.TerminalSolver;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the terminal session: which terminal is open, feeding it slot updates and
 * expiring unconfirmed clicks. Everything else reads {@link #getCurrentTerm()}.
 */
public class TerminalUtils {

    private static final Pattern TERM_SOLVER_REGEX =
            Pattern.compile("^(.{1,16}) activated a terminal! \\((\\d)/(\\d)\\)$");

    private static TerminalHandler currentTerm = null;
    private static TerminalHandler lastTermOpened = null;

    public static TerminalHandler getCurrentTerm() {
        return currentTerm;
    }

    public static TerminalHandler getLastTermOpened() {
        return lastTermOpened;
    }

    public static void onScreenOpen(Screen screen) {
        if (screen == null) { onScreenClose(); return; }
        TerminalHandler handler = TerminalTypes.openHandler(strip(screen.getTitle().getString()));
        if (handler == null) return;
        currentTerm = handler;
        lastTermOpened = handler;
        TerminalSolver.onTerminalOpen();
    }

    public static void onScreenClose() {
        if (currentTerm == null) return;
        currentTerm = null;
        TerminalSolver.onTerminalClose();
    }

    public static void onSetSlot(int slotIndex, ItemStack stack, List<Slot> slots, AbstractContainerMenu menu) {
        if (currentTerm == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs.getMenu() != menu) return;
        currentTerm.updateSlot(slotIndex, stack, slots);
    }

    /** Client tick: drop clicks the server never confirmed, then force a re-solve. */
    public static void onClientTick() {
        TerminalHandler term = currentTerm;
        if (term == null || term.clickedSlots.isEmpty()) return;
        if (System.currentTimeMillis() - term.lastClickTime < TerminalSolver.terminalReloadThreshold()) return;

        term.clickedSlots.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            AbstractContainerMenu menu = cs.getMenu();
            onSetSlot(0, ItemStack.EMPTY, menu.slots, menu);
        }
    }

    /** Fired once per server tick (ping packet with a non-zero id). */
    public static void onServerTick() {
        if (currentTerm != null) currentTerm.ticksOpened++;
    }

    public static void onChatMessage(String message) {
        Matcher m = TERM_SOLVER_REGEX.matcher(strip(message));
        if (!m.find()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !m.group(1).equals(mc.player.getName().getString())) return;
        if (lastTermOpened != null) TerminalSolver.onTerminalSolved(lastTermOpened);
    }

    public static boolean hasGlint(ItemStack stack) {
        return stack.getComponents().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null;
    }

    public static String strip(String s) {
        if (s == null) return "";
        String r = ChatFormatting.stripFormatting(s);
        return (r == null ? s : r).trim();
    }

    public static void modMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + message));
    }
}
