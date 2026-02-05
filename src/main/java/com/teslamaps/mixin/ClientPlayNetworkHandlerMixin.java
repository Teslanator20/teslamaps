package com.teslamaps.mixin;

import com.teslamaps.dungeon.puzzle.TerminalManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept slot update packets for event-driven terminal solving.
 * Instead of polling every 100ms, we react immediately when slots change.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        // Notify terminal manager of slot update
        TerminalManager.onSlotUpdate(packet.getSyncId(), packet.getSlot(), packet.getStack());
    }
}
