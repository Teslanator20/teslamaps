package com.teslamaps.mixin;

import com.teslamaps.features.FarmingSensitivity;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks look movement outright when the farming sensitivity is set to zero.
 * <p>
 * Setting the vanilla option to 0 is not actually zero: it runs through
 * {@code (sensitivity * 0.6 + 0.2)^3}, leaving a small residual turn.
 */
@Mixin(MouseHandler.class)
public class FarmingSensitivityMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void teslamaps$freezeTurn(double delta, CallbackInfo ci) {
        if (FarmingSensitivity.blocksTurning()) ci.cancel();
    }
}
