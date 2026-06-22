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
package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TerminalGuiManager;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TerminalGuiManager.shouldRenderCustomGui()) {
            TerminalGuiManager.render(context);
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
