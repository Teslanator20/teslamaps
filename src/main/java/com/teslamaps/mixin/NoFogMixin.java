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
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class NoFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void teslamaps$noFog(Camera camera, int viewDistance, DeltaTracker deltaTracker, float fogScale,
                                 ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!TeslaMapsConfig.get().noFog) return;
        FogData data = cir.getReturnValue();
        if (data == null) return;
        data.environmentalStart = 1.0E8f;
        data.environmentalEnd = 1.0E9f;
        data.renderDistanceStart = 1.0E8f;
        data.renderDistanceEnd = 1.0E9f;
    }
}
