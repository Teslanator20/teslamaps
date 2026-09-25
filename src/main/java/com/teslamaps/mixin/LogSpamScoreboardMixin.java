package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.teslamaps.features.LogSpamFilter;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Scoreboard.class)
public class LogSpamScoreboardMixin {

    @WrapWithCondition(method = "addPlayerTeam", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"))
    private boolean teslamaps$team(Logger log, String msg, Object name) {
        return !LogSpamFilter.quiet();
    }
}
