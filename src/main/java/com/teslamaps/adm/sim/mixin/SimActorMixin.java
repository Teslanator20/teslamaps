package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.Sim;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class SimActorMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "/^handle/", at = @At("HEAD"), require = 0)
    private void teslamaps$adsActorIn(CallbackInfo ci) {
        Sim.enterPacket(this.player);
    }

    @Inject(method = "/^handle/", at = @At("RETURN"), require = 0)
    private void teslamaps$adsActorOut(CallbackInfo ci) {
        Sim.leavePacket(this.player);
    }
}
