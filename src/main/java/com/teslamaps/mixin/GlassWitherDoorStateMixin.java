package com.teslamaps.mixin;

import com.teslamaps.render.DoorGlassModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// glass-drawn blocks must not hide what is behind them (section visibility) or the faces next to them
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class GlassWitherDoorStateMixin {

    @Shadow public abstract Block getBlock();

    @Inject(method = "isSolidRender", at = @At("HEAD"), cancellable = true)
    private void teslamaps$glassSolid(CallbackInfoReturnable<Boolean> cir) {
        if (DoorGlassModel.wraps(getBlock())) cir.setReturnValue(false);
    }

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true)
    private void teslamaps$glassOcclude(CallbackInfoReturnable<Boolean> cir) {
        if (DoorGlassModel.noOcclude(getBlock())) cir.setReturnValue(false);
    }
}
