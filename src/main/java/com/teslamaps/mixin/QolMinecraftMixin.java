package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class QolMinecraftMixin {

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void teslamaps$qolScreenSet(Screen screen, CallbackInfo ci) {
        com.teslamaps.features.HarpHelper.onScreenSet(screen);
        com.teslamaps.features.ContainerTriggers.onScreenSet(screen);
    }

    @ModifyExpressionValue(method = "resizeGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object teslamaps$harpGuiScale(Object original) {
        int harp = com.teslamaps.features.HarpHelper.guiScaleOverride();
        return harp >= 0 ? (Object) harp : original;
    }
}
