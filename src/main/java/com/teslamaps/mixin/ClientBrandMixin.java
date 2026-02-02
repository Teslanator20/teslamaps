package com.teslamaps.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides mod from client brand reporting.
 * Returns vanilla brand to avoid detection.
 */
@Mixin(ClientBrandRetriever.class)
public class ClientBrandMixin {

    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void getClientModName(CallbackInfoReturnable<String> cir) {
        // Return vanilla brand
        cir.setReturnValue("vanilla");
    }
}
