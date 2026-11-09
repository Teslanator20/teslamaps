package com.teslamaps.adm.practice;

import com.teslamaps.dungeon.terminals.TerminalTypes;
import com.teslamaps.dungeon.termsim.TermSimGUI;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

// Ported from ADM's InfNumbersSim.kt + LeapNumbersSim.kt: /termsim inf, a numbers terminal that never ends.
public final class InfNumbersSim extends TermSimGUI {

    private static final List<Integer> CELLS = new ArrayList<>();
    static {
        for (int r = 1; r <= 2; r++) for (int c = 2; c <= 6; c++) CELLS.add(r * 9 + c);
    }
    private static final int SHOWN = 4;
    private static final int FRESH = 6;

    private static InfNumbersSim instance;

    private final Random rng = new Random();
    private static final List<Integer> queue = new ArrayList<>();
    private final ArrayDeque<Integer> bag = new ArrayDeque<>();
    private long openedMs, lastMs, firstMs;
    private final List<Long> gaps = new ArrayList<>();

    private InfNumbersSim() {
        super(TerminalTypes.NUMBERS.termName, TerminalTypes.NUMBERS.windowSize);
    }

    public static InfNumbersSim get() {
        if (instance == null) instance = new InfNumbersSim();
        return instance;
    }

    /** This simulator is on screen: the numbers solver orders it by the queue, no numbers. */
    public static boolean active() {
        return instance != null && Minecraft.getInstance().gui.screen() == instance;
    }

    public static List<Integer> order() {
        return new ArrayList<>(queue);
    }

    /** The SS Practice keybind with Infinileap in hand opens this. False: not holding it. */
    static boolean openFromLeap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return false;
        if (!"INFINITE_SPIRIT_LEAP".equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return false;
        get().open(0L);
        return true;
    }

    @Override
    public void create() {
        queue.clear(); bag.clear(); gaps.clear();
        while (queue.size() < SHOWN) queue.add(pick());
        setSlots(slot -> CELLS.contains(slot.index) ? pane(queue.contains(slot.index)) : blackPane);
        openedMs = System.currentTimeMillis(); lastMs = 0L; firstMs = 0L;
    }

    private int pick() {
        if (bag.isEmpty()) {
            List<Integer> fresh = new ArrayList<>();
            for (int c : CELLS) if (!queue.contains(c)) fresh.add(c);
            Collections.shuffle(fresh, rng);
            fresh = new ArrayList<>(fresh.subList(0, Math.min(FRESH, fresh.size())));
            bag.addAll(fresh);
            List<Integer> rest = new ArrayList<>();
            for (int c : CELLS) if (!fresh.contains(c)) rest.add(c);
            Collections.shuffle(rest, rng);
            bag.addAll(rest);
        }
        return bag.removeFirst();
    }

    private static ItemStack pane(boolean lit) {
        ItemStack s = new ItemStack(lit ? Items.STAINED_GLASS_PANE.red() : Items.STAINED_GLASS_PANE.lime());
        s.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return s;
    }

    @Override
    public void slotClick(Slot slot, int button) {
        if (queue.isEmpty() || slot.index != queue.get(0)) return;
        long now = System.currentTimeMillis();
        if (lastMs == 0L) firstMs = now - openedMs; else gaps.add(now - lastMs);
        lastMs = now;
        queue.remove(0);
        setSlot(slot, pane(false));
        List<Slot> slots = guiInventorySlots();
        while (queue.size() < SHOWN) {
            int added = pick();
            queue.add(added);
            setSlot(slots.get(added), pane(true));
        }
        super.slotClick(slot, button);
    }

    @Override
    public void removed() {
        super.removed();
        if (gaps.isEmpty()) return;
        double avg = gaps.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double first = firstMs / 1000.0;
        PracticeUtil.msg("§7Inf numbers: §f" + (gaps.size() + 1) + "§7 clicks §8· §f" + fmt(avg) + "s§7 between §8· §7a numbers ≈ §f"
                + fmt(first + (CELLS.size() - 1) * avg) + "s §8(first click " + fmt(first) + " + " + (CELLS.size() - 1) + " × " + fmt(avg) + ")");
        gaps.clear();
    }

    private static String fmt(double v) {
        return v >= 1 ? String.format(Locale.ROOT, "%.2f", v) : String.format(Locale.ROOT, "%.3f", v);
    }
}
