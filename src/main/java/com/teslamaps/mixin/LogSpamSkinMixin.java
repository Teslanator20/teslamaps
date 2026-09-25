package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.teslamaps.features.LogSpamFilter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.resources.SkinManager$1")
public class LogSpamSkinMixin {

    @WrapWithCondition(method = "lambda$load$0", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"))
    private static boolean teslamaps$signature(Logger log, String msg, Object profile) {
        return !LogSpamFilter.quiet();
    }
}
