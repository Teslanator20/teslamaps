package com.teslamaps.mixin;

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

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void teslamaps$etherwarpSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return; // only act on the main-thread pass (avoids double handling)
        if (!TeslaMapsConfig.get().etherwarpCustomSound) return;

        if (packet.getSound().value() == SoundEvents.ENDER_DRAGON_HURT
                && packet.getVolume() == 1.0f
                && Math.abs(packet.getPitch() - 0.53968257f) < 0.0005f) {
            Etherwarp.playCustomSound();
            ci.cancel();
        }
    }
}
