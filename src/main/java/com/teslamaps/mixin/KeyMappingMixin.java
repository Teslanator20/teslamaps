package com.teslamaps.mixin;

import com.teslamaps.features.FishCatchKeySwap;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Mutable
    @Shadow
    private int clickCount;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void tmap$isDown(CallbackInfoReturnable<Boolean> cir) {
        FishCatchKeySwap.overrideIsDown((KeyMapping) (Object) this, cir);
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void tmap$consumeClick(CallbackInfoReturnable<Boolean> cir) {
        if (FishCatchKeySwap.overrideConsumeClick((KeyMapping) (Object) this, cir)) {
            this.clickCount = 0;
        }
    }
}
