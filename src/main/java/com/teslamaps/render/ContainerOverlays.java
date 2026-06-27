/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.render;

import com.teslamaps.dungeon.termgui.TerminalGuiManager;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared draw pass for the extra overlays rendered on top of container screens.
 * Called from both the plain container screens (ContainerOverlayMixin) and the
 * recipe-book screens like the player inventory / crafting table
 * (RecipeBookOverlayMixin), since those use a different render path.
 */
public class ContainerOverlays {

    public static void renderAll(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (TerminalGuiManager.shouldRenderCustomGui()) {
            TerminalGuiManager.render(context);
        } else if (com.teslamaps.features.TerminalSolver.shouldRender()) {
            com.teslamaps.features.TerminalSolver.render(context);
        }

        if (LeapOverlay.shouldRender()) {
            LeapOverlay.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.croesus.CroesusProfit.shouldRender()) {
            com.teslamaps.features.croesus.CroesusProfit.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.ContainerValue.shouldRender()) {
            com.teslamaps.features.ContainerValue.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.ItemValueOverlay.shouldRender()) {
            com.teslamaps.features.ItemValueOverlay.render(context, mouseX, mouseY);
        }

        if (com.teslamaps.features.SlotHighlighter.shouldRender()) {
            com.teslamaps.features.SlotHighlighter.render(context);
        }

        if (com.teslamaps.features.Searchbar.shouldRender()) {
            com.teslamaps.features.Searchbar.render(context);
        }

        com.teslamaps.features.BackpackPreview.renderOpenPageBorder(context);
        com.teslamaps.features.BackpackPreview.render(context, mouseX, mouseY);
        com.teslamaps.features.NoCursorReset.onRender();

        if (com.teslamaps.features.StorageOverlay.active()) {
            com.teslamaps.features.StorageOverlay.render(context, mouseX, mouseY);
        } else {
            com.teslamaps.features.StorageOverlay.renderEnableButton(context);
        }
    }
}
