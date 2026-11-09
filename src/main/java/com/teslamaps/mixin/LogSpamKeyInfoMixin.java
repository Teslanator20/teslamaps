package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.authlib.services.MinecraftServicesKeyInfo;
import com.teslamaps.features.LogSpamFilter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MinecraftServicesKeyInfo.class, remap = false)
public class LogSpamKeyInfoMixin {

    @WrapWithCondition(method = "validateProperty", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private boolean teslamaps$signature(Logger log, String msg, Object property, Object e) {
        return !LogSpamFilter.quiet();
    }
}
