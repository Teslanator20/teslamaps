package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public class P3SimFluidMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsStill(ServerLevel level, BlockPos pos, BlockState state, FluidState fluid, CallbackInfo ci) {
        if (SimServer.server != null && level.getServer() == SimServer.server) ci.cancel();
    }
}
