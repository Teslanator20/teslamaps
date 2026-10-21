// Ported from ADM's mixin/SimPartyLanMixin.java
package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.P3Party;
import java.net.InetAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public class SimPartyLanMixin {
    @ModifyArg(method = "publishServer(Lnet/minecraft/world/level/GameType;ZI)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"), index = 0)
    private InetAddress teslamaps$loopbackOnly(InetAddress address) {
        return P3Party.loopbackOnly ? InetAddress.getLoopbackAddress() : address;
    }

    @Redirect(method = "publishServer(Lnet/minecraft/world/level/GameType;ZI)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"))
    private void teslamaps$notAnnounced(LanServerPinger pinger) {
        if (!P3Party.loopbackOnly) pinger.start();
    }
}
