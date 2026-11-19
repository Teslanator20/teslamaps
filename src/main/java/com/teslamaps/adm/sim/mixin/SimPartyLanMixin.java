// Ported from ADM's mixin/SimPartyLanMixin.java
package com.teslamaps.adm.sim.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.adm.sim.P3Party;
import java.net.InetAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(IntegratedServer.class)
public class SimPartyLanMixin {
    @ModifyArg(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"),
        index = 0, require = 0)
    private InetAddress teslamaps$loopbackOnly(InetAddress address) {
        return P3Party.loopbackOnly ? InetAddress.getLoopbackAddress() : address;
    }

    // other mods redirect this call too
    @WrapOperation(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"), require = 0)
    private void teslamaps$notAnnounced(LanServerPinger pinger, Operation<Void> original) {
        if (!P3Party.loopbackOnly) original.call(pinger);
    }
}
