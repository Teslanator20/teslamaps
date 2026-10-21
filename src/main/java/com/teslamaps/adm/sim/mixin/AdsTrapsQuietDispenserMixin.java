package com.teslamaps.adm.sim.mixin;

// Ported from ADM's mixin/AdsTrapsQuietDispenserMixin.java

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Empty room dispensers make no "dispenser fail" click (level event 1001) in the sim, as on Hypixel. */
@Mixin(DispenserBlock.class)
public class AdsTrapsQuietDispenserMixin {
    @Redirect(
            method = "dispenseFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(ILnet/minecraft/core/BlockPos;I)V"),
            require = 0)
    private void teslamaps$quietFail(ServerLevel level, int type, BlockPos pos, int data) {
        if (type == 1001 && SimServer.isSimLevel(level)) return;
        level.levelEvent(type, pos, data);
    }
}
