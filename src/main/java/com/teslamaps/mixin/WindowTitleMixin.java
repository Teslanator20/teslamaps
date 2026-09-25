package com.teslamaps.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class WindowTitleMixin {

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void teslamaps$customTitle(CallbackInfoReturnable<String> cir) {
        String t = com.teslamaps.features.WindowTweaks.title();
        if (t != null) cir.setReturnValue(t);
    }
}
