package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class QolMinecraftMixin {

    @ModifyExpressionValue(method = "resizeGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object teslamaps$harpGuiScale(Object original) {
        int harp = com.teslamaps.features.HarpHelper.guiScaleOverride();
        return harp >= 0 ? (Object) harp : original;
    }
}
