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
package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.TerminalSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom terminal GUI: a scaled overlay that draws the terminal grid in the
 * centre of the screen and is driven entirely by {@link TerminalSolver}'s
 * fresh-scan solution. Clicking a highlighted slot forwards the correct click
 * (with the right mouse button for rubix) to the real container.
 */
public class TerminalGuiManager {

    private static final Map<Integer, float[]> slotBoxes = new HashMap<>(); // slot -> {x, y, size}

    public static boolean shouldRenderCustomGui() {
        if (!TeslaMapsConfig.get().customTerminalGui) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;
        return TerminalSolver.currentType() != TerminalSolver.Type.NONE;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!shouldRenderCustomGui()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        TerminalSolver.Solution sol = TerminalSolver.solve();
        if (sol == null) return;
        TerminalSolver.Type type = TerminalSolver.currentType();

        TeslaMapsConfig c = TeslaMapsConfig.get();
        int slotCount = cs.getMenu().slots.size() - 36; // terminal slots only
        int rows = (slotCount + 8) / 9;

        float size = 55f * c.terminalGuiSize;
        float gap = c.terminalGuiGap;
        float step = size + gap;
        int ww = mc.getWindow().getGuiScaledWidth();
        int wh = mc.getWindow().getGuiScaledHeight();
        float originX = ww / 2f - (9 * step - gap) / 2f;
        float originY = wh / 2f - (rows * step - gap) / 2f;

        // background
        int bg = TeslaMapsConfig.parseColor(c.terminalGuiBackgroundColor);
        ctx.fill((int) (originX - 8), (int) (originY - 8),
                (int) (originX + 9 * step - gap + 8), (int) (originY + rows * step - gap + 8), bg);

        slotBoxes.clear();
        for (int index = 0; index < slotCount; index++) {
            float x = originX + (index % 9) * step;
            float y = originY + (index / 9) * step;
            slotBoxes.put(index, new float[]{x, y, size});

            Integer color = sol.colors.get(index);
            if (color == null) continue;
            ctx.fill((int) x, (int) y, (int) (x + size), (int) (y + size), color);

            String label = sol.labels.get(index);
            if (label != null && (c.terminalGuiShowNumbers || type == TerminalSolver.Type.RUBIX)) {
                int tw = mc.font.width(label);
                ctx.text(mc.font, label, (int) (x + size / 2 - tw / 2f), (int) (y + size / 2 - 4), 0xFFFFFFFF, true);
            }
        }
    }

    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!shouldRenderCustomGui()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return false;
        if (TerminalSolver.inFirstClickBlock()) return true; // swallow during first-click protection
        TerminalSolver.Solution sol = TerminalSolver.solve();
        if (sol == null) return false;

        int hovered = -1;
        // click-anywhere disabled (redirected clicks to the correct slot = acting for the player)
        // if (TeslaMapsConfig.get().terminalClickAnywhere) {
        //     hovered = primarySlot(sol);
        // } else {
        for (Map.Entry<Integer, float[]> e : slotBoxes.entrySet()) {
            float[] b = e.getValue();
            if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[2]) {
                hovered = e.getKey();
                break;
            }
        }
        // only forward clicks on solution slots
        if (hovered != -1 && !sol.colors.containsKey(hovered)) return true;
        // }
        if (hovered == -1) return true; // consume click inside the overlay anyway

        Boolean right = sol.rightClick.get(hovered);
        int btn = (right != null && right) ? 1 : 0;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, hovered, btn, ContainerInput.PICKUP, mc.player);
        return true;
    }

    /** Best single slot to click next (for click-anywhere mode). */
    private static int primarySlot(TerminalSolver.Solution sol) {
        if (sol.nextSlot != -1) return sol.nextSlot;
        int best = -1;
        for (int slot : sol.colors.keySet()) if (best == -1 || slot < best) best = slot;
        return best;
    }

    public static void reset() {
        slotBoxes.clear();
    }
}
