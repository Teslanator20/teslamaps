package com.teslamaps.adm.practice.mixin;

import com.teslamaps.adm.practice.AdmSimonSays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// Block packets on arrival (netty thread): the SS solver stamps lamps with the server tick they came in.
@Mixin(ClientPacketListener.class)
public class AdmBlockPacketMixin {
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void teslamaps$admBlockPacket(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) return;
        AdmSimonSays.onPacket(List.of(packet.getPos().immutable()), List.of(packet.getBlockState()));
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
    private void teslamaps$admSectionPacket(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) return;
        List<BlockPos> pos = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        packet.runUpdates((p, s) -> { pos.add(p.immutable()); states.add(s); });
        AdmSimonSays.onPacket(pos, states);
    }
}
