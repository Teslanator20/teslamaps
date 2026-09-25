package com.teslamaps.mixin;

import com.teslamaps.features.hud.TitleStack;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class TitleStackMixin {
    @Shadow private int titleFadeInTime;
    @Shadow private int titleStayTime;
    @Shadow private int titleFadeOutTime;

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void teslamaps$stackTitle(Component title, CallbackInfo ci) {
        if (TitleStack.onTitle(title, titleFadeInTime, titleStayTime, titleFadeOutTime)) ci.cancel();
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void teslamaps$stackSubtitle(Component sub, CallbackInfo ci) {
        if (TitleStack.onSubtitle(sub)) ci.cancel();
    }

    @Inject(method = "setTimes", at = @At("TAIL"))
    private void teslamaps$stackTimes(int in, int stay, int out, CallbackInfo ci) {
        TitleStack.onTimes(titleFadeInTime, titleStayTime, titleFadeOutTime);
    }

    @Inject(method = "clearTitles", at = @At("HEAD"))
    private void teslamaps$stackClear(CallbackInfo ci) {
        if (TitleStack.enabled()) TitleStack.clear();
    }
}
