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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class HandledScreenClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.handleClick(mouseX, mouseY, button)) cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.StorageOverlay.handleEnableClick(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }

        {
            net.minecraft.world.inventory.Slot hovered = ((HandledScreenAccessor) (Object) this).getFocusedSlot();
            if (hovered != null && com.teslamaps.features.TerminalSolver.blockClick(hovered.index, button)) {
                cir.setReturnValue(true); // consume -> no packet sent
                return;
            }
        }

        if (com.teslamaps.features.Searchbar.handleClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.SlotLock.blockSlot(((HandledScreenAccessor) (Object) this).getFocusedSlot())) {
            cir.setReturnValue(true);
            return;
        }

        if (TerminalGuiManager.handleMouseClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (LeapOverlay.handleClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
