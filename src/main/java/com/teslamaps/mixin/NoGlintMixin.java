package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class NoGlintMixin {
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noGlint(CallbackInfoReturnable<Boolean> cir) {
        if (TeslaMapsConfig.get().noEnchantGlint) cir.setReturnValue(false);
    }
}
