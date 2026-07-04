/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.mixin;

import com.teslamaps.features.CustomFont;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Redirects the vanilla default font to the user's chosen bundled font (when enabled), which swaps
// the font for essentially all UI/HUD/map text. Custom fonts fall back to the vanilla glyphs they lack.
@Mixin(Style.class)
public class StyleFontMixin {

    @Inject(method = "getFont", at = @At("RETURN"), cancellable = true)
    private void teslamaps$customFont(CallbackInfoReturnable<FontDescription> cir) {
        FontDescription repl = CustomFont.override(cir.getReturnValue());
        if (repl != null) cir.setReturnValue(repl);
    }
}
