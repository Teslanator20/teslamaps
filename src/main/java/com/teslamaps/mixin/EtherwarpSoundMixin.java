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

/** Replaces the etherwarp sound (ender dragon hurt at a specific pitch) with a custom one. */
@Mixin(ClientPacketListener.class)
public class EtherwarpSoundMixin {

    // HEAD of handleSoundEvent: this runs BEFORE ensureRunningOnSameThread, so cancelling here
    // prevents the packet from being re-dispatched to the game thread -> fires exactly once.
    // The sound itself is played via mc.execute(...) so SoundManager is touched on the game thread.
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
