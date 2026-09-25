package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class NoBlockBreakingMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noBlockBreaking(BlockPos pos, BlockState state, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.noBlockBreaking && (!c.noBlockBreakingGardenOnly || com.teslamaps.utils.SkyblockUtils.isInGarden())) ci.cancel();
    }
}
