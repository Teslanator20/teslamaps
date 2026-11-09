package com.teslamaps.mixin;

import com.teslamaps.features.FixDoubleClicks;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class PacketSendMixin {

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void teslamaps$fixDoubleClicks(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundUseItemOnPacket && FixDoubleClicks.shouldCancelUseOn()) ci.cancel();
        else if (packet instanceof net.minecraft.network.protocol.game.ServerboundUseItemPacket use) {
            com.teslamaps.features.HypeSound.onUseItem(use.hand());
            com.teslamaps.features.AbilityRadius.onUseItem(use.hand());
            com.teslamaps.features.Etherwarp.onUseSent(null, use.yRot(), use.xRot());
        } else if (packet instanceof ServerboundUseItemOnPacket on) {
            net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
            if (p != null) com.teslamaps.features.Etherwarp.onUseSent(on.hitResult().getBlockPos(), p.getYRot(), p.getXRot());
        }
    }
}
