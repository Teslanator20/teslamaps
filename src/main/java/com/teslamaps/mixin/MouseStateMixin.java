package com.teslamaps.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseStateMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void teslamaps$trackButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        com.teslamaps.utils.MouseState.onButton(info.button(), action == 1);
    }
}
