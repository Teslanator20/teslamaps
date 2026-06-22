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

import com.teslamaps.features.BackpackPreview;
import com.teslamaps.features.ScrollableTooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class ScrollableTooltipMixin {
    // tooltip(Font, List, int mouseX, int mouseY, positioner, Identifier) -> offset mouseY (2nd int arg)
    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int teslamaps$scrollTooltip(int mouseY) {
        return ScrollableTooltip.applyY(mouseY);
    }

    // hide the vanilla tooltip while the backpack/ender grid overlay is shown
    @Inject(method = "tooltip", at = @At("HEAD"), cancellable = true)
    private void teslamaps$suppressForGrid(CallbackInfo ci) {
        if (BackpackPreview.suppressTooltip()) ci.cancel();
    }
}
