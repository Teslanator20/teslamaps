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

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Etherwarp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class EtherwarpSoundMixin {

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void teslamaps$etherwarpSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().etherwarpCustomSound) return;
        if (packet.getSound().value() != SoundEvents.ENDER_DRAGON_HURT) return;

        if (TeslaMapsConfig.get().debugMode) {
            TeslaMaps.LOGGER.info("[Etherwarp] ENDER_DRAGON_HURT sound vol={} pitch={}",
                    packet.getVolume(), packet.getPitch());
        }

        if (packet.getVolume() == 1.0f && Math.abs(packet.getPitch() - 0.53968257f) < 0.0005f) {
            Minecraft.getInstance().execute(Etherwarp::playCustomSound);
            ci.cancel();
        }
    }
}
