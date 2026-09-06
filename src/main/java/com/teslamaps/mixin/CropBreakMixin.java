package com.teslamaps.mixin;

import com.teslamaps.features.FarmSpeed;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports broken blocks to the speed counter. The server replants crops
 * instantly, so block updates never show a harvest.
 * <p>
 * destroyBlock is the one place that runs exactly once per block actually
 * broken. Both startDestroyBlock and continueDestroyBlock call into it, and
 * hooking those directly counted every tick the button stayed down instead.
 */
@Mixin(MultiPlayerGameMode.class)
public class CropBreakMixin {

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void teslamaps$cropBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        FarmSpeed.onBreakAttempt(pos);
    }
}
