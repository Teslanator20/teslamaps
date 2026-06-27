/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normal-mode terminal solver: highlights the correct slots directly in the
 * vanilla terminal GUI and blocks wrong clicks. Re-scans the open container
 * every frame, so it does not depend on any tick state. Ported from Odin's
 * TerminalSolver (Normal render mode).
 */
public class TerminalSolver {

    public enum Type { NONE, NUMBERS, PANES, STARTS_WITH, SELECT_ALL, RUBIX, MELODY }

    private static final Pattern STARTS_WITH_P = Pattern.compile("What starts with: '([A-Z])'\\?");
    private static final Pattern SELECT_ALL_P = Pattern.compile("Select all the ([A-Z ]+) items!");

    private static final int[] RUBIX_GRID = {12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final String[] RUBIX_CYCLE = {"RED", "ORANGE", "YELLOW", "GREEN", "BLUE"};
    private static final int[] MELODY_TERRACOTTA = {16, 25, 34, 43};
    private static final int[][] MELODY_LANES = {
            {9, 10, 11, 12, 13, 14, 15, 16, 17},
            {18, 19, 20, 21, 22, 23, 24, 25, 26},
            {27, 28, 29, 30, 31, 32, 33, 34, 35},
            {36, 37, 38, 39, 40, 41, 42, 43, 44}
    };

    private static long openTime = 0;
    private static String lastTitle = null;

    public static class Solution {
        public final Map<Integer, Integer> colors = new HashMap<>();   // slot -> ARGB
        public final Map<Integer, String> labels = new HashMap<>();    // slot -> number/letter
        public final Map<Integer, Boolean> rightClick = new HashMap<>(); // RUBIX: slot -> needs right click
        public int nextSlot = -1;                                       // NUMBERS/MELODY: the one slot to click
    }

    /** Current terminal type for the open screen, or NONE. */
    public static Type currentType() {
        return detectType();
    }

    /** Compute the solution for the currently open terminal, or null if none. */
    public static Solution solve() {
        Minecraft mc = Minecraft.getInstance();
        Type type = detectType();
        if (type == Type.NONE || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        return computeSolution(cs, type);
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.terminalSolver || c.customTerminalGui) return false;
        return detectType() != Type.NONE;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Type type = detectType();
        if (type == Type.NONE) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Solution sol = computeSolution(cs, type);

        for (Slot slot : cs.getMenu().slots) {
            Integer color = sol.colors.get(slot.index);
            if (color == null) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, color);
            String label = sol.labels.get(slot.index);
            if (label != null && (c.terminalGuiShowNumbers || type == Type.RUBIX)) {
                int tw = mc.font.width(label);
                ctx.text(mc.font, label, sx + 8 - tw / 2, sy + 4, 0xFFFFFFFF, true);
            }
        }
    }

    /** True during the first-click protection window after a terminal opens. */
    public static boolean inFirstClickBlock() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.blockWrongTerminalClicks || c.terminalFirstClickBlock <= 0) return false;
        if (detectType() == Type.NONE) return false;
        return System.currentTimeMillis() - openTime < c.terminalFirstClickBlock;
    }

    /** Called from the click mixin (normal mode only). Returns true if the click should be swallowed. */
    public static boolean blockClick(int slotIndex, int button) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.blockWrongTerminalClicks || c.customTerminalGui) return false;
        Type type = detectType();
        if (type == Type.NONE) return false;

        if (inFirstClickBlock()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        Solution sol = computeSolution(cs, type);

        // No solution found (or terminal already done) -> never block, to avoid locking the player out.
        if (sol.colors.isEmpty() && sol.nextSlot == -1) return false;

        return switch (type) {
            case NUMBERS -> sol.nextSlot != -1 && slotIndex != sol.nextSlot;
            case MELODY -> sol.nextSlot != -1 && slotIndex != sol.nextSlot;
            case RUBIX -> !sol.rightClick.containsKey(slotIndex) || button != (sol.rightClick.get(slotIndex) ? 1 : 0);
            case PANES, STARTS_WITH, SELECT_ALL -> !sol.colors.containsKey(slotIndex);
            default -> false;
        };
    }

    private static Solution computeSolution(AbstractContainerScreen<?> cs, Type type) {
        Solution sol = new Solution();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        switch (type) {
            case NUMBERS -> {
                Map<Integer, Integer> slotToNum = new HashMap<>();
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.index >= 54) continue;
                    ItemStack s = slot.getItem();
                    if (s.getItem() == Items.RED_STAINED_GLASS_PANE) {
                        int n = s.getCount();
                        if (n >= 1 && n <= 14) slotToNum.put(slot.index, n);
                    }
                }
                List<Integer> ordered = new ArrayList<>(slotToNum.keySet());
                ordered.sort((a, b) -> slotToNum.get(a) - slotToNum.get(b));
                if (!ordered.isEmpty()) sol.nextSlot = ordered.get(0);
                for (int i = 0; i < ordered.size() && i < 3; i++) {
                    int col = switch (i) {
                        case 0 -> TeslaMapsConfig.parseColor(c.terminalGuiOrderColor1);
                        case 1 -> TeslaMapsConfig.parseColor(c.terminalGuiOrderColor2);
                        default -> TeslaMapsConfig.parseColor(c.terminalGuiOrderColor3);
                    };
                    sol.colors.put(ordered.get(i), alpha(col));
                    sol.labels.put(ordered.get(i), String.valueOf(ordered.size() - i));
                }
            }
            case PANES -> {
                int col = alpha(TeslaMapsConfig.parseColor(c.terminalGuiPanesColor));
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.index >= 54) continue;
                    if (slot.getItem().getItem() == Items.RED_STAINED_GLASS_PANE) sol.colors.put(slot.index, col);
                }
            }
            case STARTS_WITH -> {
                Character letter = startsWithLetter();
                if (letter == null) break;
                int col = alpha(TeslaMapsConfig.parseColor(c.terminalGuiStartsWithColor));
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.index >= 54) continue;
                    ItemStack s = slot.getItem();
                    if (s.isEmpty() || s.hasFoil()) continue;
                    String name = strip(s.getHoverName().getString());
                    if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == letter) {
                        sol.colors.put(slot.index, col);
                        sol.labels.put(slot.index, String.valueOf(letter));
                    }
                }
            }
            case SELECT_ALL -> {
                String color = selectAllColor();
                if (color == null) break;
                int col = alpha(TeslaMapsConfig.parseColor(c.terminalGuiSelectColor));
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.index >= 54) continue;
                    ItemStack s = slot.getItem();
                    if (s.isEmpty() || s.hasFoil()) continue;
                    String name = strip(s.getHoverName().getString());
                    if (matchesColor(name, color)) {
                        sol.colors.put(slot.index, col);
                        sol.labels.put(slot.index, color.substring(0, 1));
                    }
                }
            }
            case RUBIX -> computeRubix(cs, sol);
            case MELODY -> computeMelody(cs, sol);
            default -> {}
        }
        return sol;
    }

    private static void computeRubix(AbstractContainerScreen<?> cs, Solution sol) {
        Map<Integer, String> slotColors = new HashMap<>();
        for (int slot : RUBIX_GRID) {
            String col = rubixColor(cs.getMenu().getSlot(slot).getItem().getItem());
            if (col != null) slotColors.put(slot, col);
        }
        if (slotColors.isEmpty()) return;

        String target = null;
        int min = Integer.MAX_VALUE;
        for (String t : RUBIX_CYCLE) {
            int total = 0;
            for (String cur : slotColors.values()) {
                if (cur.equals(t)) continue;
                total += Math.min(rubixDist(cur, t, true), rubixDist(cur, t, false));
            }
            if (total < min) { min = total; target = t; }
        }

        TeslaMapsConfig c = TeslaMapsConfig.get();
        for (Map.Entry<Integer, String> e : slotColors.entrySet()) {
            String cur = e.getValue();
            if (cur.equals(target)) continue;
            int fwd = rubixDist(cur, target, true), bwd = rubixDist(cur, target, false);
            boolean useLeft = fwd <= bwd;
            int clicks = useLeft ? fwd : bwd;
            int col = useLeft
                    ? TeslaMapsConfig.parseColor(clicks >= 2 ? c.terminalGuiRubixColor2 : c.terminalGuiRubixColor1)
                    : TeslaMapsConfig.parseColor(clicks >= 2 ? c.terminalGuiRubixColorBack2 : c.terminalGuiRubixColorBack1);
            sol.colors.put(e.getKey(), alpha(col));
            sol.labels.put(e.getKey(), useLeft ? String.valueOf(clicks) : "-" + clicks);
            sol.rightClick.put(e.getKey(), !useLeft);
        }
    }

    private static void computeMelody(AbstractContainerScreen<?> cs, Solution sol) {
        int lane = -1;
        for (int i = 0; i < MELODY_TERRACOTTA.length; i++) {
            if (cs.getMenu().getSlot(MELODY_TERRACOTTA[i]).getItem().getItem() == Items.LIME_TERRACOTTA) { lane = i; break; }
        }
        if (lane == -1) return;

        int magenta = -1;
        for (int top = 1; top <= 7; top++) {
            Item it = cs.getMenu().getSlot(top).getItem().getItem();
            if (it == Items.MAGENTA_STAINED_GLASS_PANE || it == Items.PURPLE_STAINED_GLASS_PANE || it == Items.PINK_STAINED_GLASS_PANE) {
                magenta = top; break;
            }
        }
        int green = -1;
        for (int i = 0; i < 7; i++) {
            if (cs.getMenu().getSlot(MELODY_LANES[lane][i]).getItem().getItem() == Items.LIME_STAINED_GLASS_PANE) green = i;
        }

        int melodyCol = alpha(TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiMelodyColor));
        if (magenta != -1) sol.colors.put(magenta, melodyCol);
        boolean aligned = green != -1 && magenta != -1 && green == magenta;
        sol.colors.put(MELODY_TERRACOTTA[lane], aligned ? 0xC055FF55 : (melodyCol & 0x55FFFFFF));
        sol.nextSlot = MELODY_TERRACOTTA[lane]; // only the terracotta button is clickable
    }

    // ---- detection helpers ----

    private static Type detectType() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { lastTitle = null; return Type.NONE; }
        String title = strip(cs.getTitle().getString());
        if (!title.equals(lastTitle)) { lastTitle = title; openTime = System.currentTimeMillis(); }

        if (title.equals("Click in order!")) return Type.NUMBERS;
        if (title.equals("Correct all the panes!")) return Type.PANES;
        if (title.startsWith("What starts with:")) return Type.STARTS_WITH;
        if (title.startsWith("Select all the")) return Type.SELECT_ALL;
        if (title.equals("Change all to same color!")) return Type.RUBIX;
        if (title.equals("Click the button on time!")) return Type.MELODY;
        return Type.NONE;
    }

    private static Character startsWithLetter() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        Matcher m = STARTS_WITH_P.matcher(strip(cs.getTitle().getString()));
        return m.find() ? m.group(1).charAt(0) : null;
    }

    private static String selectAllColor() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        Matcher m = SELECT_ALL_P.matcher(strip(cs.getTitle().getString()));
        return m.find() ? m.group(1).trim() : null;
    }

    private static boolean matchesColor(String itemName, String color) {
        String n = itemName.toUpperCase(), col = color.toUpperCase();
        switch (col) {
            case "BLUE":
                if (n.contains("LAPIS")) return true;
                if (n.startsWith("LIGHT BLUE")) return false;
                return n.startsWith("BLUE");
            case "BLACK":
                return n.contains("INK") || n.startsWith("BLACK");
            case "WHITE":
                return n.contains("BONE") || n.startsWith("WHITE");
            case "SILVER":
                return n.startsWith("LIGHT GRAY") || n.startsWith("SILVER");
            case "YELLOW":
                return n.contains("DANDELION") || n.startsWith("YELLOW");
            case "RED":
                if (n.contains("ROSE") && !n.contains("QUARTZ")) return true;
                return n.startsWith("RED");
            case "GREEN":
                if (n.contains("CACTUS")) return true;
                if (n.startsWith("LIME")) return false;
                return n.startsWith("GREEN");
            case "BROWN":
                return n.contains("COCOA") || n.startsWith("BROWN");
            case "LIGHT BLUE":
                return n.startsWith("LIGHT BLUE");
            default:
                return n.startsWith(col + " ") || n.equals(col);
        }
    }

    private static String rubixColor(Item item) {
        if (item == Items.RED_STAINED_GLASS_PANE) return "RED";
        if (item == Items.ORANGE_STAINED_GLASS_PANE) return "ORANGE";
        if (item == Items.YELLOW_STAINED_GLASS_PANE) return "YELLOW";
        if (item == Items.GREEN_STAINED_GLASS_PANE) return "GREEN";
        if (item == Items.BLUE_STAINED_GLASS_PANE) return "BLUE";
        return null;
    }

    private static int rubixDist(String from, String to, boolean forward) {
        int fi = idx(from), ti = idx(to);
        if (fi == -1 || ti == -1) return 999;
        int len = RUBIX_CYCLE.length;
        return forward ? (ti >= fi ? ti - fi : len - fi + ti) : (ti <= fi ? fi - ti : fi + len - ti);
    }

    private static int idx(String color) {
        for (int i = 0; i < RUBIX_CYCLE.length; i++) if (RUBIX_CYCLE[i].equals(color)) return i;
        return -1;
    }

    private static int alpha(int argb) {
        return (argb & 0x00FFFFFF) | 0x90000000;
    }

    private static String strip(String s) {
        if (s == null) return "";
        String r = ChatFormatting.stripFormatting(s);
        return (r == null ? s : r).trim();
    }
}
