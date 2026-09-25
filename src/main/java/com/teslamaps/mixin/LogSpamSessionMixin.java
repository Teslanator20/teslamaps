package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.teslamaps.features.LogSpamFilter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public class LogSpamSessionMixin {

    // empty signature = unsigned, skips the doomed check and its error per head
    @WrapOperation(method = "getPropertySignatureState", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/properties/Property;hasSignature()Z"))
    private boolean teslamaps$emptySignature(Property p, Operation<Boolean> op) {
        boolean has = op.call(p);
        if (has && LogSpamFilter.quiet()) {
            String sig = p.signature();
            if (sig == null || sig.isEmpty()) return false;
        }
        return has;
    }

    @WrapWithCondition(method = "unpackTextures", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V", ordinal = 0))
    private boolean teslamaps$badTextures(Logger log, String msg, Throwable t) {
        return !(t instanceof IllegalArgumentException && LogSpamFilter.quiet());
    }
}
