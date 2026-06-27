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

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class FullbrightMixin {

    private static boolean teslamaps$wasFullbright = false;

    @Inject(method = "extract", at = @At("TAIL"))
    private void onExtract(LightmapRenderState state, float tickDelta, CallbackInfo ci) {
        boolean on = TeslaMapsConfig.get().fullbright;
        if (on) {
            state.brightness = 15.0f;
            state.darknessEffectScale = 0.0f;
        }
        if (on != teslamaps$wasFullbright) {
            state.needsUpdate = true;
            teslamaps$wasFullbright = on;
        }
    }
}
