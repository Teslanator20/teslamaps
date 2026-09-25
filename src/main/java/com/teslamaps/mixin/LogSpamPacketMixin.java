package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.teslamaps.features.LogSpamFilter;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public class LogSpamPacketMixin {

    @WrapWithCondition(method = "handleSetEntityPassengersPacket", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;)V"))
    private boolean teslamaps$passengers(Logger log, String msg) {
        return !LogSpamFilter.quiet();
    }

    @WrapWithCondition(method = "handlePlayerInfoUpdate", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private boolean teslamaps$playerInfo(Logger log, String msg, Object a, Object b) {
        return !LogSpamFilter.quiet();
    }

    @WrapWithCondition(method = "handleUnknownCustomPayload", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"))
    private boolean teslamaps$badlion(Logger log, String msg, Object id) {
        return !(id instanceof Identifier i && "badlion".equals(i.getNamespace()) && LogSpamFilter.quiet());
    }

    @WrapWithCondition(method = {"handleSetScore", "handleResetScore"}, at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"), require = 2)
    private boolean teslamaps$score(Logger log, String msg, Object objective) {
        return !LogSpamFilter.quiet();
    }

    @WrapWithCondition(method = "handleSetPlayerTeamPacket", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;[Ljava/lang/Object;)V"))
    private boolean teslamaps$team(Logger log, String msg, Object... args) {
        return !LogSpamFilter.quiet();
    }
}
