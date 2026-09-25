package com.teslamaps.mixin;

import com.teslamaps.features.ReaperTimer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ReaperBarMixin {

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void teslamaps$reaperVisible(CallbackInfoReturnable<Boolean> cir) {
        if (ReaperTimer.barActive && ReaperTimer.barFor((ItemStack) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void teslamaps$reaperWidth(CallbackInfoReturnable<Integer> cir) {
        if (ReaperTimer.barActive && ReaperTimer.barFor((ItemStack) (Object) this)) cir.setReturnValue(ReaperTimer.barWidth());
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void teslamaps$reaperColor(CallbackInfoReturnable<Integer> cir) {
        if (ReaperTimer.barActive && ReaperTimer.barFor((ItemStack) (Object) this)) cir.setReturnValue(ReaperTimer.barColor());
    }
}
