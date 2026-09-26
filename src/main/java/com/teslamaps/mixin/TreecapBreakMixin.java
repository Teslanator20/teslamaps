package com.teslamaps.mixin;

import com.teslamaps.features.SbItemCooldowns;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class TreecapBreakMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void teslamaps$treecapStart(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        SbItemCooldowns.onStartBreak(pos);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void teslamaps$treecapBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        SbItemCooldowns.onBreak(pos);
    }
}
