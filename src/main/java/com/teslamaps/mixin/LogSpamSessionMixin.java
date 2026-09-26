package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.teslamaps.features.LogSpamFilter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public class LogSpamSessionMixin {

    @WrapWithCondition(method = "unpackTextures", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V", ordinal = 0))
    private boolean teslamaps$badTextures(Logger log, String msg, Throwable t) {
        return !(t instanceof IllegalArgumentException && LogSpamFilter.quiet());
    }
}
