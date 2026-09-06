package com.teslamaps.mixin;

import com.teslamaps.dungeon.termsim.TermSimGUI;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The terminal simulator runs on a fake client-side chest menu that reuses
 * container id 0 — the player's own inventory. Any container click that escaped
 * the simulator screen would therefore be applied to real items server-side, so
 * they are swallowed here and replayed against the simulator instead.
 */
@Mixin(value = Connection.class, priority = 500)
public class TermSimPacketMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void teslamaps$blockTermSimClicks(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!(packet instanceof ServerboundContainerClickPacket click)) return;
        if (!(Minecraft.getInstance().screen instanceof TermSimGUI sim)) return;
        if (click.containerInput() != ContainerInput.PICKUP_ALL) {
            sim.clickIndex(click.slotNum(), click.buttonNum());
        }
        ci.cancel();
    }
}
